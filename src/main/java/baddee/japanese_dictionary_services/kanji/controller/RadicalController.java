package baddee.japanese_dictionary_services.kanji.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import baddee.japanese_dictionary_services.kanji.KanjiApi;
import baddee.japanese_dictionary_services.kanji.RadicalGroup;

/** The radicals list for a radical picker. Part of the kanji module (D87). */
@RestController
@RequestMapping("/api/v1/radicals")
public class RadicalController {

	private final KanjiApi kanji;

	public RadicalController(KanjiApi kanji) {
		this.kanji = kanji;
	}

	/** All radicals grouped by stroke count, with kanji counts. Not paged: there are ~250. */
	@GetMapping
	public List<RadicalGroup> listRadicals() {
		return kanji.findRadicalGroups();
	}
}
