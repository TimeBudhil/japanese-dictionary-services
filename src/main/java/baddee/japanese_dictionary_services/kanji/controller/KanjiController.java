package baddee.japanese_dictionary_services.kanji.controller;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import baddee.japanese_dictionary_services.kanji.KanjiApi;
import baddee.japanese_dictionary_services.kanji.KanjiDetail;
import baddee.japanese_dictionary_services.kanji.KanjiFilters;
import baddee.japanese_dictionary_services.kanji.KanjiSummary;

/**
 * Read-only kanji endpoints. Depends only on {@link KanjiApi} (D37). Bad parameters become 400
 * ProblemDetails through method validation (D28, D29).
 */
@RestController
@RequestMapping("/api/v1/kanji")
public class KanjiController {

	/** No kanji has more than 12 radicals; this only bounds the input. */
	static final int MAX_RADICALS = 20;

	private final KanjiApi kanji;

	public KanjiController(KanjiApi kanji) {
		this.kanji = kanji;
	}

	/** One kanji by its character, bare rows and retired kanji included (D85). 404 if unknown. */
	@GetMapping("/{character}")
	public KanjiDetail getKanji(@PathVariable String character) {
		return kanji.findByCharacter(character)
				.orElseThrow(() -> new KanjiNotFoundException(character));
	}

	/**
	 * Kanji containing every given radical ({@code radicals=日,木} or repeated), with optional
	 * exact filters, as a page (D84, D89).
	 */
	@GetMapping
	public Page<KanjiSummary> searchByRadicals(
			@RequestParam @NotEmpty @Size(max = MAX_RADICALS) List<@NotBlank String> radicals,
			@RequestParam(required = false) @Min(1) Integer strokes,
			@RequestParam(required = false) @Min(1) @Max(10) Integer grade,
			@RequestParam(required = false) Boolean joyo,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		List<String> stripped = radicals.stream().map(String::strip).toList();
		return kanji.findByRadicals(stripped, new KanjiFilters(strokes, grade, joyo), PageRequest.of(page, size));
	}
}
