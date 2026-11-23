package baddee.japanese_dictionary_services.kanji;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import baddee.japanese_dictionary_services.support.IntegrationTest;

/**
 * Checks R2.1 to R2.4 over HTTP against the seed data (D99). Expected values are read off
 * docker/postgres/seed/seed.sql: 10 active kanji, 17 radicals, ⺉ and 㐆 contain none.
 */
class KanjiModuleIT extends IntegrationTest {

	private static final String KANJI = "/api/v1/kanji";

	@Test
	void kanjiReturnsReadingsMeaningsAndStrokePaths() throws Exception {
		mockMvc.perform(get(KANJI + "/猫"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.character").value("猫"))
				.andExpect(jsonPath("$.onyomi", contains("ビョウ")))
				.andExpect(jsonPath("$.kunyomi", contains("ねこ")))
				.andExpect(jsonPath("$.meanings", contains("cat")))
				.andExpect(jsonPath("$.strokeCount").value(11))
				.andExpect(jsonPath("$.strokePaths", not(empty())))
				.andExpect(jsonPath("$.strokePaths[0]", notNullValue()));
	}

	@Test
	void bareRowIsReturnedUnchanged() throws Exception {
		mockMvc.perform(get(KANJI + "/⺉"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.character").value("⺉"))
				.andExpect(jsonPath("$.codepoint").doesNotExist())
				.andExpect(jsonPath("$.onyomi", empty()))
				.andExpect(jsonPath("$.meanings", empty()))
				.andExpect(jsonPath("$.radicals", empty()))
				.andExpect(jsonPath("$.strokePaths", hasSize(2)));
	}

	@Test
	void unknownCharacterIsA404ProblemDetail() throws Exception {
		mockMvc.perform(get(KANJI + "/あ"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void radicalSearchRequiresAllRadicalsAndOrdersByStrokes() throws Exception {
		mockMvc.perform(get(KANJI).param("radicals", "化", "木"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].character", contains("休", "体")));
	}

	@Test
	void strokesAndJoyoFiltersCombine() throws Exception {
		// 猫 is the only 11-stroke kanji in the seed, and it is joyo.
		mockMvc.perform(get(KANJI).param("radicals", "田").param("strokes", "11").param("joyo", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].character", contains("猫")))
				.andExpect(jsonPath("$.content[*].strokeCount", everyItem(is(11))))
				.andExpect(jsonPath("$.content[*].joyo", everyItem(is(true))));
		// 木 matches 休(6) 体(7) 本(5) 校(10); strokes=6 keeps only 休.
		mockMvc.perform(get(KANJI).param("radicals", "木").param("strokes", "6"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].character", contains("休")));
		mockMvc.perform(get(KANJI).param("radicals", "田").param("strokes", "11").param("joyo", "false"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", empty()));
	}

	@Test
	void radicalsAreGroupedByStrokeCountWithKanjiCounts() throws Exception {
		mockMvc.perform(get("/api/v1/radicals"))
				.andExpect(status().isOk())
				// One group per stroke count among the 17 seed radicals, ascending.
				.andExpect(jsonPath("$[*].strokeCount", contains(1, 2, 3, 4, 5, 6, 7, 9)))
				.andExpect(jsonPath("$[0].radicals[*].character", contains("一")))
				.andExpect(jsonPath("$[1].radicals[*].character", contains("亠", "冖", "化")))
				.andExpect(jsonPath("$[3].radicals[*].character", contains("五", "日", "木", "父")))
				// Counts are the kanji rows in kanji_radical: 木 in 休 体 本 校, 一 in 体 本, 化 in 休 体, 日 in 日 書.
				.andExpect(jsonPath("$[?(@.strokeCount == 4)].radicals[?(@.character == '木')].kanjiCount",
						contains(contains(4))))
				.andExpect(jsonPath("$[0].radicals[0].kanjiCount").value(2))
				.andExpect(jsonPath("$[1].radicals[2].kanjiCount").value(2))
				.andExpect(jsonPath("$[3].radicals[1].kanjiCount").value(2))
				.andExpect(jsonPath("$[7].radicals[0].kanjiCount").value(1));
	}
}
