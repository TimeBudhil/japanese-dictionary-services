package baddee.japanese_dictionary_services.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base for the {@code <Module>ModuleIT} classes (D99): boots the full application, real security
 * chains included, against a Postgres and an Elasticsearch container, and calls it through MockMvc.
 *
 * <p>The containers start once per JVM and are shared by every subclass (and the one cached
 * Spring context). They are filled from the same {@code docker/} files as the compose stack:
 * Postgres runs {@code docker/postgres/init} (schema, seed, read-only role), and ES gets
 * {@code docker/elasticsearch/mappings} and {@code seed} over HTTP, as {@code init.sh} does.
 * Images match {@code docker-compose.yml}. The app connects as {@code dictionary_reader}, not the
 * owner, so a write the app attempts would fail here as it would in production (D23).
 *
 * <p>Tests only read, so they share the data and need no reset. Paths are relative to the
 * module root, where Maven runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

	private static final Path DOCKER = Path.of("docker");
	private static final String READER_PASSWORD = "dictionary_reader";

	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
			.withDatabaseName("dictionary")
			.withUsername("dictionary_owner")
			.withPassword("dictionary_owner")
			.withEnv("DB_READER_PASSWORD", READER_PASSWORD)
			.withEnv("DICTIONARY_SEED", "true")
			// Mode 0755 so the entrypoint executes the scripts rather than sourcing them.
			.withCopyFileToContainer(MountableFile.forHostPath(DOCKER.resolve("postgres/init/01-seed-data.sh"), 0755),
					"/docker-entrypoint-initdb.d/01-seed-data.sh")
			.withCopyFileToContainer(MountableFile.forHostPath(DOCKER.resolve("postgres/init/02-readonly-role.sh"), 0755),
					"/docker-entrypoint-initdb.d/02-readonly-role.sh")
			.withCopyFileToContainer(MountableFile.forHostPath(DOCKER.resolve("postgres/seed/schema.sql")),
					"/seed/schema.sql")
			.withCopyFileToContainer(MountableFile.forHostPath(DOCKER.resolve("postgres/seed/seed.sql")),
					"/seed/seed.sql");

	static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
			"docker.elastic.co/elasticsearch/elasticsearch:9.4.8")
			.withEnv("xpack.security.enabled", "false")
			.withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
			.waitingFor(Wait.forHttp("/_cluster/health?wait_for_status=yellow&timeout=30s")
					.forPort(9200).forStatusCode(200));

	static {
		postgres.start();
		elasticsearch.start();
		loadElasticsearchSeed();
	}

	@DynamicPropertySource
	static void containers(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "dictionary_reader");
		registry.add("spring.datasource.password", () -> READER_PASSWORD);
		registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearch.getHttpHostAddress());
	}

	@Autowired
	protected MockMvc mockMvc;

	/** Same steps as docker/elasticsearch/init.sh: create each index from its mapping, then bulk-load its seed. */
	private static void loadElasticsearchSeed() {
		String base = "http://" + elasticsearch.getHttpHostAddress();
		HttpClient http = HttpClient.newHttpClient();
		try (Stream<Path> mappings = Files.list(DOCKER.resolve("elasticsearch/mappings"))) {
			for (Path mapping : mappings.filter(p -> p.toString().endsWith(".json")).toList()) {
				String index = mapping.getFileName().toString().replaceFirst("\\.json$", "");
				send(http, HttpRequest.newBuilder(URI.create(base + "/" + index))
						.header("Content-Type", "application/json")
						.PUT(HttpRequest.BodyPublishers.ofFile(mapping)).build());
				Path seed = DOCKER.resolve("elasticsearch/seed/" + index + ".ndjson");
				if (Files.exists(seed)) {
					String body = send(http, HttpRequest.newBuilder(URI.create(base + "/" + index + "/_bulk?refresh=true"))
							.header("Content-Type", "application/x-ndjson")
							.POST(HttpRequest.BodyPublishers.ofFile(seed)).build());
					if (!body.contains("\"errors\":false")) {
						throw new IllegalStateException("Bulk load of " + index + " reported errors: " + body);
					}
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not load the Elasticsearch seed", e);
		}
	}

	private static String send(HttpClient http, HttpRequest request) {
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() / 100 != 2) {
				throw new IllegalStateException(request.method() + " " + request.uri() + " returned "
						+ response.statusCode() + ": " + response.body());
			}
			return response.body();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
