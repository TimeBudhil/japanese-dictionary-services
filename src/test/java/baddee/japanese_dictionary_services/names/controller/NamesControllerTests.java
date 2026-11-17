package baddee.japanese_dictionary_services.names.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
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
import baddee.japanese_dictionary_services.names.NameDetail;
import baddee.japanese_dictionary_services.names.NamesApi;
import baddee.japanese_dictionary_services.names.integration.NamesSecurityConfig;

/**
 * Controller unit tests (D22) for the names endpoints, with {@link NamesApi} mocked and the
 * real security chains imported (root catch-all + this module's).
 */
@WebMvcTest(NamesController.class)
@Import({ SecurityConfig.class, NamesSecurityConfig.class })
class NamesControllerTests {

	private static final String NAMES = "/api/v1/names";
	private static final String ALLOWED_ORIGIN = "http://localhost:5173"; // from application.properties

	// Fixtures copy real rows from a full load (D77).

	/** 田中: two name types, in JMnedict order. */
	private static final NameDetail TANAKA = new NameDetail(5543705, List.of("田中"), List.of("たなか"),
			List.of("family or surname", "place name"), List.of("Tanaka"));

	private static final NameDetail TANAKA_KAKUEI = new NameDetail(5543762, List.of("田中角栄"),
			List.of("たなかかくえい"), List.of("full name of a particular person"), List.of("Tanaka Kakuei"));

	/** さくら: kana-only, so its kanji array is empty. */
	private static final NameDetail SAKURA = new NameDetail(5002725, List.of(), List.of("さくら"),
			List.of("female given name or forename"), List.of("Sakura"));

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private NamesApi names;

	@Nested
	class GetName {

		@Test
		void returnsTheNameWithListsInOrder() throws Exception {
			given(names.findByEntSeq(5543705)).willReturn(Optional.of(TANAKA));

			mvc.perform(get(NAMES + "/5543705"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.entSeq", is(5543705)))
				.andExpect(jsonPath("$.kanji", contains("田中")))
				.andExpect(jsonPath("$.readings", contains("たなか")))
				.andExpect(jsonPath("$.nameTypes", contains("family or surname", "place name")))
				.andExpect(jsonPath("$.translations", contains("Tanaka")));
		}

		@Test
		void kanaOnlyNameHasEmptyKanjiList() throws Exception {
			given(names.findByEntSeq(5002725)).willReturn(Optional.of(SAKURA));

			mvc.perform(get(NAMES + "/5002725"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.kanji", hasSize(0)))
				.andExpect(jsonPath("$.readings", contains("さくら")));
		}

		@Test
		void unknownIdIs404ProblemDetail() throws Exception {
			given(names.findByEntSeq(anyLong())).willReturn(Optional.empty());

			mvc.perform(get(NAMES + "/1"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail", containsString("1")));
		}

		@Test
		void nonNumericIdIs400() throws Exception {
			mvc.perform(get(NAMES + "/abc")).andExpect(status().isBadRequest());
			verifyNoInteractions(names);
		}

		@Test
		void unexpectedErrorIs500WithoutInternals() throws Exception {
			given(names.findByEntSeq(anyLong())).willThrow(new IllegalStateException("db password is hunter2"));

			mvc.perform(get(NAMES + "/5543705"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail", is("An unexpected error occurred.")))
				.andExpect(content().string(not(containsString("hunter2"))));
		}
	}

	@Nested
	class Search {

		@Test
		void returnsNamesInServiceOrderWithPageMetadata() throws Exception {
			given(names.searchEntries("田中", PageRequest.of(0, 20)))
				.willReturn(new PageImpl<>(List.of(TANAKA, TANAKA_KAKUEI), PageRequest.of(0, 20), 475));

			mvc.perform(get(NAMES).param("q", "田中"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[0].entSeq", is(5543705)))
				.andExpect(jsonPath("$.content[1].translations", contains("Tanaka Kakuei")))
				.andExpect(jsonPath("$.page.totalElements", is(475)))
				.andExpect(jsonPath("$.page.size", is(20)));
		}

		@Test
		void passesPaging() throws Exception {
			given(names.searchEntries(eq("kakuei"), eq(PageRequest.of(3, 100))))
				.willReturn(new PageImpl<>(List.of()));

			mvc.perform(get(NAMES).param("q", "kakuei").param("page", "3").param("size", "100"))
				.andExpect(status().isOk());
			verify(names).searchEntries("kakuei", PageRequest.of(3, 100));
		}

		@Test
		void missingOrBlankQueryIs400() throws Exception {
			mvc.perform(get(NAMES))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
			mvc.perform(get(NAMES).param("q", "  ")).andExpect(status().isBadRequest());
			verifyNoInteractions(names);
		}

		@Test
		void tooLongQueryIs400() throws Exception {
			mvc.perform(get(NAMES).param("q", "a".repeat(NamesController.MAX_QUERY_LENGTH + 1)))
				.andExpect(status().isBadRequest());
			verifyNoInteractions(names);
		}

		@Test
		void outOfRangePagingIs400() throws Exception {
			mvc.perform(get(NAMES).param("q", "田中").param("size", "101")).andExpect(status().isBadRequest());
			mvc.perform(get(NAMES).param("q", "田中").param("size", "0")).andExpect(status().isBadRequest());
			mvc.perform(get(NAMES).param("q", "田中").param("page", "-1")).andExpect(status().isBadRequest());
			verifyNoInteractions(names);
		}
	}

	@Nested
	class SecurityAndHeaders {

		@Test
		void otherApiPathsAreDeniedByRootChain() throws Exception {
			mvc.perform(get("/api/v1/unclaimed")).andExpect(status().isForbidden());
		}

		@Test
		void corsAndRequestIdOnAllowedOrigin() throws Exception {
			given(names.findByEntSeq(5543705)).willReturn(Optional.of(TANAKA));

			mvc.perform(get(NAMES + "/5543705").header("X-Request-Id", "abc-123").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Request-Id", "abc-123"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
		}

		@Test
		void preflightFromUnknownOriginIsRejected() throws Exception {
			mvc.perform(options(NAMES)
					.header(HttpHeaders.ORIGIN, "https://evil.example")
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden());
		}

		@Test
		void writeMethodsAreNotSupported() throws Exception {
			mvc.perform(post(NAMES).param("q", "田中")).andExpect(status().isMethodNotAllowed());
			mvc.perform(post(NAMES + "/5543705")).andExpect(status().isMethodNotAllowed());
			verifyNoInteractions(names);
		}
	}
}
