package baddee.japanese_dictionary_services.vocabulary.controller;

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

import baddee.japanese_dictionary_services.vocabulary.VocabEntryDetail;
import baddee.japanese_dictionary_services.vocabulary.VocabularyApi;

/**
 * Read-only vocabulary endpoints. Depends only on {@link VocabularyApi}, the same API other
 * modules use (D37).
 *
 * <p>Bad parameters ({@code @Min}/{@code @Max}/{@code @NotBlank}/{@code @Size}, or a
 * non-numeric id) become 400 ProblemDetails through Spring's built-in method validation and
 * Boot's ProblemDetail handler (D28, D29).
 */
@RestController
@RequestMapping("/api/v1/vocabulary")
public class VocabularyController {

	/** Longest accepted query. Dictionary lookups are short; this bounds the ES term/prefix work. */
	static final int MAX_QUERY_LENGTH = 100;

	private final VocabularyApi vocabulary;

	public VocabularyController(VocabularyApi vocabulary) {
		this.vocabulary = vocabulary;
	}

	/** One entry by JMdict id. 404 if unknown. */
	@GetMapping("/{entSeq}")
	public VocabEntryDetail getEntry(@PathVariable long entSeq) {
		return vocabulary.findByEntSeq(entSeq)
				.orElseThrow(() -> new VocabEntryNotFoundException(entSeq));
	}

	/**
	 * Entries matching {@code q} (exact, then prefix on kanji/kana, then English glosses),
	 * as a page (D28). A page past the first 10,000 hits is a 400.
	 */
	@GetMapping
	public Page<VocabEntryDetail> search(
			@RequestParam @NotBlank @Size(max = MAX_QUERY_LENGTH) String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return vocabulary.searchEntries(q, PageRequest.of(page, size));
	}
}
