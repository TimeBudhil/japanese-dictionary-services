package baddee.japanese_dictionary_services.names.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import baddee.japanese_dictionary_services.names.NameDetail;
import baddee.japanese_dictionary_services.names.NamesApi;

/**
 * Read-only name endpoints. Depends only on {@link NamesApi} (D37). Bad parameters become 400
 * ProblemDetails through method validation (D28, D29).
 */
@RestController
@RequestMapping("/api/v1/names")
public class NamesController {

	/** Longest accepted query (D79). */
	static final int MAX_QUERY_LENGTH = 100;

	private final NamesApi names;

	public NamesController(NamesApi names) {
		this.names = names;
	}

	/** One name by JMnedict id, retired names included (D76). 404 if unknown. */
	@GetMapping("/{entSeq}")
	public NameDetail getName(@PathVariable long entSeq) {
		return names.findByEntSeq(entSeq).orElseThrow(() -> new NameNotFoundException(entSeq));
	}

	/**
	 * Names matching {@code q} on kanji, readings or translations: exact, then prefix, then a
	 * later translation word starting with {@code q} (D90), as a page.
	 */
	@GetMapping
	public Page<NameDetail> search(
			@RequestParam @NotBlank @Size(max = MAX_QUERY_LENGTH) String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return names.searchEntries(q, PageRequest.of(page, size));
	}
}
