package baddee.japanese_dictionary_services;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Needs the local Postgres + Elasticsearch stack (docker compose up -d). Integration tests come later (D22).")
class JapaneseDictionaryServicesApplicationTests {

	@Test
	void contextLoads() {
	}

}
