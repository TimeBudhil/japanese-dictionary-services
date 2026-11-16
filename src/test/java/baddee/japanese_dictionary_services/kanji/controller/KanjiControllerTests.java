package baddee.japanese_dictionary_services.kanji.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
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
import baddee.japanese_dictionary_services.kanji.KanjiApi;
import baddee.japanese_dictionary_services.kanji.KanjiDetail;
import baddee.japanese_dictionary_services.kanji.KanjiFilters;
import baddee.japanese_dictionary_services.kanji.KanjiSummary;
import baddee.japanese_dictionary_services.kanji.RadicalGroup;
import baddee.japanese_dictionary_services.kanji.integration.KanjiSecurityConfig;

/**
 * Controller unit tests (D22) for the kanji and radicals endpoints, with {@link KanjiApi}
 * mocked and the real security chains imported (root catch-all + this module's).
 */
@WebMvcTest({ KanjiController.class, RadicalController.class })
@Import({ SecurityConfig.class, KanjiSecurityConfig.class })
class KanjiControllerTests {

	private static final String KANJI = "/api/v1/kanji";
	private static final String RADICALS = "/api/v1/radicals";
	private static final String ALLOWED_ORIGIN = "http://localhost:5173"; // from application.properties

	// Fixtures copy real rows from a full load (D77).

	/** 日: every field filled, 4 stroke paths. */
	private static final KanjiDetail HI = new KanjiDetail("日", "65e5",
			List.of("ニチ", "ジツ"), List.of("ひ", "-び", "-か"),
			List.of("あ", "あき", "いる", "く", "くさ", "こう", "す", "たち", "に", "にっ", "につ", "へ"),
			List.of("day", "sun", "Japan", "counter for days"), 4, 1, true, 4, 1, 72, List.of("日"),
			List.of("M31.5,24.5c1.12,1.12,1.74,2.75,1.74,4.75c0,1.6-0.16,38.11-0.09,53.5c0.02,3.82,0.05,6.35,0.09,6.75",
					"M33.48,26c0.8-0.05,37.67-3.01,40.77-3.25c3.19-0.25,5,1.75,5,4.25c0,4-0.22,40.84-0.23,56c0,3.48,0,5.72,0,6",
					"M34.22,55.25c7.78-0.5,35.9-2.5,44.06-2.75",
					"M34.23,86.5c10.52-0.75,34.15-2.12,43.81-2.25"));

	/** ⺉: a bare row (KanjiVG only): no codepoint, readings, meanings or radicals. Paths shortened. */
	private static final KanjiDetail BARE = new KanjiDetail("⺉", null,
			List.of(), List.of(), List.of(), List.of(), null, null, false, null, null, null, List.of(),
			List.of("M1,1", "M2,2"));

	/** 㐆: KanjiDic2 data but no stroke paths and no grade, JLPT or frequency. */
	private static final KanjiDetail NO_STROKES = new KanjiDetail("㐆", "3406",
			List.of(), List.of(), List.of(),
			List.of("to follow", "to trust to", "to put confidence in", "to depend on", "to turn around",
					"to turn the body"),
			6, null, false, null, null, 4, List.of(), List.of());

	/** 猫: its radicals include RADKFILE stand-ins (犯 for 犭, 艾 for 艹, D88). */
	private static final KanjiSummary NEKO = new KanjiSummary("猫", List.of("ビョウ"), List.of("ねこ"),
			List.of("cat"), 11, 8, true, 1702);

	private static final KanjiSummary HI_SUMMARY = new KanjiSummary("日", List.of("ニチ", "ジツ"),
			List.of("ひ", "-び", "-か"), List.of("day", "sun", "Japan", "counter for days"), 4, 1, true, 1);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private KanjiApi kanji;

	@Nested
	class GetKanji {

		@Test
		void returnsTheKanjiWithStrokePathsInOrder() throws Exception {
			given(kanji.findByCharacter("日")).willReturn(Optional.of(HI));

			mvc.perform(get(KANJI + "/日"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.character", is("日")))
				.andExpect(jsonPath("$.codepoint", is("65e5")))
				.andExpect(jsonPath("$.onyomi", contains("ニチ", "ジツ")))
				.andExpect(jsonPath("$.kunyomi", contains("ひ", "-び", "-か")))
				.andExpect(jsonPath("$.meanings", contains("day", "sun", "Japan", "counter for days")))
				.andExpect(jsonPath("$.strokeCount", is(4)))
				.andExpect(jsonPath("$.grade", is(1)))
				.andExpect(jsonPath("$.joyo", is(true)))
				.andExpect(jsonPath("$.oldJlpt", is(4)))
				.andExpect(jsonPath("$.frequency", is(1)))
				.andExpect(jsonPath("$.radicals", contains("日")))
				.andExpect(jsonPath("$.strokePaths", hasSize(4)))
				.andExpect(jsonPath("$.strokePaths[2]", is("M34.22,55.25c7.78-0.5,35.9-2.5,44.06-2.75")));
		}

		@Test
		void bareRowIsReturnedUnchanged() throws Exception {
			given(kanji.findByCharacter("⺉")).willReturn(Optional.of(BARE));

			mvc.perform(get(KANJI + "/⺉"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.codepoint", nullValue()))
				.andExpect(jsonPath("$.meanings", hasSize(0)))
				.andExpect(jsonPath("$.strokeCount", nullValue()))
				.andExpect(jsonPath("$.joyo", is(false)))
				.andExpect(jsonPath("$.strokePaths", hasSize(2)));
		}

		@Test
		void kanjiWithoutStrokePathsHasEmptyListAndNullNumbers() throws Exception {
			given(kanji.findByCharacter("㐆")).willReturn(Optional.of(NO_STROKES));

			mvc.perform(get(KANJI + "/㐆"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.strokePaths", hasSize(0)))
				.andExpect(jsonPath("$.grade", nullValue()))
				.andExpect(jsonPath("$.frequency", nullValue()))
				.andExpect(jsonPath("$.meanings", hasSize(6)));
		}

		@Test
		void unknownCharacterIs404ProblemDetail() throws Exception {
			given(kanji.findByCharacter(anyString())).willReturn(Optional.empty());

			mvc.perform(get(KANJI + "/あ"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail", containsString("あ")));
		}

		@Test
		void unexpectedErrorIs500WithoutInternals() throws Exception {
			given(kanji.findByCharacter(anyString())).willThrow(new IllegalStateException("db password is hunter2"));

			mvc.perform(get(KANJI + "/日"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail", is("An unexpected error occurred.")))
				.andExpect(content().string(not(containsString("hunter2"))));
		}
	}

	@Nested
	class SearchByRadicals {

		@Test
		void returnsSummariesInServiceOrderWithPageMetadata() throws Exception {
			given(kanji.findByRadicals(List.of("犯", "田"), KanjiFilters.NONE, PageRequest.of(0, 20)))
				.willReturn(new PageImpl<>(List.of(NEKO), PageRequest.of(0, 20), 1));

			mvc.perform(get(KANJI).param("radicals", "犯,田"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].character", is("猫")))
				.andExpect(jsonPath("$.content[0].meanings", contains("cat")))
				.andExpect(jsonPath("$.content[0].strokePaths").doesNotExist())
				.andExpect(jsonPath("$.content[0].nanori").doesNotExist())
				.andExpect(jsonPath("$.page.totalElements", is(1)));
		}

		@Test
		void acceptsRepeatedParameterAndPassesFiltersAndPaging() throws Exception {
			given(kanji.findByRadicals(any(), any(), any())).willReturn(new PageImpl<>(List.of(HI_SUMMARY)));

			mvc.perform(get(KANJI).param("radicals", "日").param("radicals", " 一 ")
					.param("strokes", "4").param("grade", "1").param("joyo", "true")
					.param("page", "2").param("size", "100"))
				.andExpect(status().isOk());

			verify(kanji).findByRadicals(eq(List.of("日", "一")), eq(new KanjiFilters(4, 1, true)),
					eq(PageRequest.of(2, 100)));
		}

		@Test
		void missingRadicalsIs400() throws Exception {
			mvc.perform(get(KANJI))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
			verifyNoInteractions(kanji);
		}

		@Test
		void blankRadicalIs400() throws Exception {
			mvc.perform(get(KANJI).param("radicals", "日").param("radicals", " "))
				.andExpect(status().isBadRequest());
			verifyNoInteractions(kanji);
		}

		@Test
		void tooManyRadicalsIs400() throws Exception {
			String many = String.join(",", java.util.Collections.nCopies(KanjiController.MAX_RADICALS + 1, "日"));
			mvc.perform(get(KANJI).param("radicals", many)).andExpect(status().isBadRequest());
			verifyNoInteractions(kanji);
		}

		@Test
		void outOfRangeFiltersAre400() throws Exception {
			mvc.perform(get(KANJI).param("radicals", "日").param("grade", "11")).andExpect(status().isBadRequest());
			mvc.perform(get(KANJI).param("radicals", "日").param("strokes", "0")).andExpect(status().isBadRequest());
			mvc.perform(get(KANJI).param("radicals", "日").param("joyo", "maybe")).andExpect(status().isBadRequest());
			verifyNoInteractions(kanji);
		}

		@Test
		void outOfRangePagingIs400() throws Exception {
			mvc.perform(get(KANJI).param("radicals", "日").param("size", "101")).andExpect(status().isBadRequest());
			mvc.perform(get(KANJI).param("radicals", "日").param("page", "-1")).andExpect(status().isBadRequest());
			verifyNoInteractions(kanji);
		}
	}

	@Nested
	class ListRadicals {

		@Test
		void returnsGroupsByStrokeCount() throws Exception {
			given(kanji.findRadicalGroups()).willReturn(List.of(
					new RadicalGroup(1, List.of(new RadicalGroup.Radical("一", 990), new RadicalGroup.Radical("｜", 646))),
					new RadicalGroup(3, List.of(new RadicalGroup.Radical("犯", 57)))));

			mvc.perform(get(RADICALS))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].strokeCount", is(1)))
				.andExpect(jsonPath("$[0].radicals[0].character", is("一")))
				.andExpect(jsonPath("$[0].radicals[0].kanjiCount", is(990)))
				.andExpect(jsonPath("$[1].radicals[0].character", is("犯")));
		}
	}

	@Nested
	class SecurityAndHeaders {

		@Test
		void otherApiPathsAreDeniedByRootChain() throws Exception {
			mvc.perform(get("/api/v1/unclaimed")).andExpect(status().isForbidden());
		}

		@Test
		void corsWorksOnBothPathTrees() throws Exception {
			given(kanji.findByCharacter("日")).willReturn(Optional.of(HI));
			given(kanji.findRadicalGroups()).willReturn(List.of());

			mvc.perform(get(KANJI + "/日").header("X-Request-Id", "abc-123").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Request-Id", "abc-123"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
			mvc.perform(get(RADICALS).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
		}

		@Test
		void preflightFromUnknownOriginIsRejected() throws Exception {
			mvc.perform(options(RADICALS)
					.header(HttpHeaders.ORIGIN, "https://evil.example")
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden());
		}

		@Test
		void writeMethodsAreNotSupported() throws Exception {
			mvc.perform(post(KANJI).param("radicals", "日")).andExpect(status().isMethodNotAllowed());
			mvc.perform(post(RADICALS)).andExpect(status().isMethodNotAllowed());
			verifyNoInteractions(kanji);
		}
	}
}
