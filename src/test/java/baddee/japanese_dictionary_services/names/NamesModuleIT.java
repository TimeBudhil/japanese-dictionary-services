package baddee.japanese_dictionary_services.names;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import baddee.japanese_dictionary_services.support.IntegrationTest;

/** Checks R3.1 and R3.2 over HTTP against the seed data (D99). */
class NamesModuleIT extends IntegrationTest {

	private static final String BASE = "/api/v1/names";

	@Test
	void entryReturnsWritingsReadingsTypesAndTranslations() throws Exception {
		mockMvc.perform(get(BASE + "/5543705"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entSeq").value(5543705))
				.andExpect(jsonPath("$.kanji", contains("田中")))
				.andExpect(jsonPath("$.readings", contains("たなか")))
				.andExpect(jsonPath("$.nameTypes", contains("family or surname", "place name")))
				.andExpect(jsonPath("$.translations", contains("Tanaka")));
	}

	@Test
	void unknownEntryIsA404ProblemDetail() throws Exception {
		mockMvc.perform(get(BASE + "/999999999"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void kanjiQueryReturnsTheNameFirst() throws Exception {
		mockMvc.perform(get(BASE).param("q", "田中"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].entSeq").value(5543705));
	}

	@Test
	void readingQueryFindsTheName() throws Exception {
		mockMvc.perform(get(BASE).param("q", "たなか"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].entSeq", hasItem(5543705)));
	}

	@Test
	void translationQueryFindsTheNameIgnoringCase() throws Exception {
		mockMvc.perform(get(BASE).param("q", "Tanaka"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].entSeq", hasItem(5543705)));
	}
}
