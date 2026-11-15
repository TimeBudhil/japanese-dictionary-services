package baddee.japanese_dictionary_services.vocabulary.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import baddee.japanese_dictionary_services.SecurityConfig;
import baddee.japanese_dictionary_services.vocabulary.VocabEntryDetail;
import baddee.japanese_dictionary_services.vocabulary.VocabSense;
import baddee.japanese_dictionary_services.vocabulary.VocabularyApi;
import baddee.japanese_dictionary_services.vocabulary.VocabPageTooDeepException;
import baddee.japanese_dictionary_services.vocabulary.integration.VocabularySecurityConfig;

/**
 * Controller unit tests (D22): MockMvc + Mockito + Hamcrest, no database or Elasticsearch.
 * {@link VocabularyApi} is mocked.
 *
 * <p>The real security chains (root catch-all + this module's) are imported, so these tests
 * also check that the vocabulary paths are open, CORS works, and other {@code /api} paths stay
 * denied. {@code RequestIdFilter} and {@code UnexpectedErrorHandler} are picked up by
 * {@code @WebMvcTest} itself (a filter and a controller advice).
 */
@WebMvcTest(VocabularyController.class)
@Import({ SecurityConfig.class, VocabularySecurityConfig.class })
class VocabularyControllerTests {

	private static final String BASE = "/api/v1/vocabulary";
	private static final String ALLOWED_ORIGIN = "http://localhost:5173"; // from application.properties

	// Fixtures copy real JMdict entries as the updater stores them (D77), including the
	// expanded POS/misc text (not codes, X10).

	/** 食べる: two kanji writings, one kana reading, two senses, common. */
	private static final VocabEntryDetail TABERU = new VocabEntryDetail(1358280L, true,
			List.of("食べる", "喰べる"), List.of("たべる"),
			List.of(new VocabSense(List.of("to eat"), List.of("Ichidan verb", "transitive verb"), List.of()),
					new VocabSense(List.of("to live on (e.g. a salary)", "to live off", "to subsist on"),
							List.of("Ichidan verb", "transitive verb"), List.of())));

	/** たんま: kana-only (no kanji writing), not common, one misc tag. */
	private static final VocabEntryDetail TANMA = new VocabEntryDetail(2083020L, false,
			List.of(), List.of("タンマ", "たんま"),
			List.of(new VocabSense(List.of("truce (during a children's game)", "time out", "King's X"),
					List.of("noun (common) (futsuumeishi)"), List.of("children's language"))));

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private VocabularyApi vocabulary;

	@Nested
	class GetEntry {

		@Test
		void returnsTheEntry() throws Exception {
			given(vocabulary.findByEntSeq(1358280L)).willReturn(Optional.of(TABERU));

			mvc.perform(get(BASE + "/1358280"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.entSeq", is(1358280)))
				.andExpect(jsonPath("$.common", is(true)))
				.andExpect(jsonPath("$.kanji", contains("食べる", "喰べる")))
				.andExpect(jsonPath("$.readings", contains("たべる")))
				.andExpect(jsonPath("$.senses", hasSize(2)))
				.andExpect(jsonPath("$.senses[0].glosses", contains("to eat")))
				.andExpect(jsonPath("$.senses[0].partsOfSpeech", contains("Ichidan verb", "transitive verb")))
				.andExpect(jsonPath("$.senses[1].glosses", contains("to live on (e.g. a salary)", "to live off", "to subsist on")));
		}

		@Test
		void kanaOnlyEntryHasEmptyKanjiAndKeepsMiscTags() throws Exception {
			given(vocabulary.findByEntSeq(2083020L)).willReturn(Optional.of(TANMA));

			mvc.perform(get(BASE + "/2083020"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.kanji", hasSize(0)))
				.andExpect(jsonPath("$.common", is(false)))
				.andExpect(jsonPath("$.senses[0].miscTags", contains("children's language")));
		}

		@Test
		void unknownIdIs404ProblemDetail() throws Exception {
			given(vocabulary.findByEntSeq(42L)).willReturn(Optional.empty());

			mvc.perform(get(BASE + "/42"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status", is(404)))
				.andExpect(jsonPath("$.detail", containsString("42")));
		}

		@Test
		void nonNumericIdIs400() throws Exception {
			mvc.perform(get(BASE + "/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

			verifyNoInteractions(vocabulary);
		}

		@Test
		void unexpectedErrorIs500WithoutInternals() throws Exception {
			given(vocabulary.findByEntSeq(anyLong())).willThrow(new IllegalStateException("db password is hunter2"));

			mvc.perform(get(BASE + "/1"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail", is("An unexpected error occurred.")))
				.andExpect(content().string(not(containsString("hunter2"))));
		}
	}

	@Nested
	class Search {

		@Test
		void returnsEntriesInServiceOrderWithPageMetadata() throws Exception {
			given(vocabulary.searchEntries("た", PageRequest.of(1, 2)))
				.willReturn(new PageImpl<>(List.of(TABERU, TANMA), PageRequest.of(1, 2), 7));

			mvc.perform(get(BASE).param("q", "た").param("page", "1").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[0].entSeq", is(1358280)))
				.andExpect(jsonPath("$.content[1].entSeq", is(2083020)))
				// via-dto serialization (D28): stable "page" object, not Spring's PageImpl internals
				.andExpect(jsonPath("$.page.number", is(1)))
				.andExpect(jsonPath("$.page.size", is(2)))
				.andExpect(jsonPath("$.page.totalElements", is(7)))
				.andExpect(jsonPath("$.page.totalPages", is(4)));
		}

		@Test
		void defaultsToFirstPageOfTwenty() throws Exception {
			given(vocabulary.searchEntries(anyString(), any())).willReturn(new PageImpl<>(List.of()));

			mvc.perform(get(BASE).param("q", "eat")).andExpect(status().isOk());

			verify(vocabulary).searchEntries(eq("eat"), eq(PageRequest.of(0, 20)));
		}

		@Test
		void acceptsMaxPageSize() throws Exception {
			given(vocabulary.searchEntries(anyString(), any())).willReturn(new PageImpl<>(List.of()));

			mvc.perform(get(BASE).param("q", "eat").param("size", "100")).andExpect(status().isOk());

			verify(vocabulary).searchEntries(eq("eat"), eq(PageRequest.of(0, 100)));
		}

		@Test
		void missingQueryIs400() throws Exception {
			mvc.perform(get(BASE))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
			verifyNoInteractions(vocabulary);
		}

		@Test
		void blankQueryIs400() throws Exception {
			mvc.perform(get(BASE).param("q", "   ")).andExpect(status().isBadRequest());
			verifyNoInteractions(vocabulary);
		}

		@Test
		void tooLongQueryIs400() throws Exception {
			String tooLong = "あ".repeat(VocabularyController.MAX_QUERY_LENGTH + 1);
			mvc.perform(get(BASE).param("q", tooLong)).andExpect(status().isBadRequest());
			verifyNoInteractions(vocabulary);
		}

		@Test
		void sizeOverMaxIs400NotClamped() throws Exception {
			mvc.perform(get(BASE).param("q", "eat").param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
			verifyNoInteractions(vocabulary);
		}

		@Test
		void sizeZeroIs400() throws Exception {
			mvc.perform(get(BASE).param("q", "eat").param("size", "0")).andExpect(status().isBadRequest());
			verifyNoInteractions(vocabulary);
		}

		@Test
		void negativePageIs400() throws Exception {
			mvc.perform(get(BASE).param("q", "eat").param("page", "-1")).andExpect(status().isBadRequest());
			verifyNoInteractions(vocabulary);
		}

		@Test
		void pagePastResultWindowIs400ProblemDetail() throws Exception {
			given(vocabulary.searchEntries(anyString(), any())).willThrow(new VocabPageTooDeepException(10_000));

			mvc.perform(get(BASE).param("q", "eat").param("page", "500").param("size", "100"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail", containsString("10000")));
		}
	}

	@Nested
	class SecurityAndHeaders {

		@Test
		void otherApiPathsAreDeniedByRootChain() throws Exception {
			mvc.perform(get("/api/v1/unclaimed")).andExpect(status().isForbidden());
		}

		@Test
		void echoesSafeRequestIdAndCorsExposesIt() throws Exception {
			given(vocabulary.findByEntSeq(1358280L)).willReturn(Optional.of(TABERU));

			mvc.perform(get(BASE + "/1358280")
					.header("X-Request-Id", "abc-123")
					.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Request-Id", "abc-123"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("X-Request-Id")));
		}

		@Test
		void preflightFromAllowedOriginIsAccepted() throws Exception {
			mvc.perform(options(BASE)
					.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("GET")));
		}

		@Test
		void preflightFromUnknownOriginIsRejected() throws Exception {
			mvc.perform(options(BASE)
					.header(HttpHeaders.ORIGIN, "https://evil.example")
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden());
		}

		@Test
		void preflightForWriteMethodIsRejected() throws Exception {
			mvc.perform(options(BASE)
					.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden());
		}

		@Test
		void writeMethodsAreNotSupported() throws Exception {
			mvc.perform(post(BASE).param("q", "eat")).andExpect(status().isMethodNotAllowed());
			verifyNoInteractions(vocabulary);
		}
	}
}
