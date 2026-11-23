package baddee.japanese_dictionary_services.vocabulary;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import baddee.japanese_dictionary_services.support.IntegrationTest;

/** Checks R1.1 to R1.3 over HTTP against the seed data (D99). */
class VocabularyModuleIT extends IntegrationTest {

	private static final String BASE = "/api/v1/vocabulary";

	@Test
	void entryReturnsReadingsAndSensesInJmdictOrder() throws Exception {
		mockMvc.perform(get(BASE + "/1358280"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entSeq").value(1358280))
				.andExpect(jsonPath("$.kanji", hasItems("食べる", "喰べる")))
				.andExpect(jsonPath("$.readings", hasItem("たべる")))
				.andExpect(jsonPath("$.senses[0].glosses", contains("to eat")))
				.andExpect(jsonPath("$.senses[1].glosses",
						contains("to live on (e.g. a salary)", "to live off", "to subsist on")));
	}

	@Test
	void unknownEntryIsA404ProblemDetail() throws Exception {
		mockMvc.perform(get(BASE + "/999999999"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void exactMatchRanksBeforeLongerMatches() throws Exception {
		mockMvc.perform(get(BASE).param("q", "日本"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].kanji", hasItem("日本")))
				.andExpect(jsonPath("$.content[?(@.kanji[0] == '日本語')]").exists());
	}

	@Test
	void pageBeyondTenThousandHitsIsRejected() throws Exception {
		mockMvc.perform(get(BASE).param("q", "日本").param("page", "600").param("size", "20"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}
}
