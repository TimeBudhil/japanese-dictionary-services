package baddee.japanese_dictionary_services.kanji;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The kanji module's public API (D37). Other modules (mainly {@code search}) use only this
 * interface and the types next to it, never the module's sub-packages.
 *
 * <p>Lookups by character return any row that exists, retired kanji and bare rows included
 * (D76, D85). Searches never return either.
 */
public interface KanjiApi {

	/** One kanji (or bare radical form) by its character, or empty if there is no such row. */
	Optional<KanjiDetail> findByCharacter(String character);

	/**
	 * The kanji that appear in {@code text}, in the order they first appear, each once.
	 * Characters with no active kanji row (kana, Latin, bare rows, retired kanji) are skipped.
	 */
	List<KanjiSummary> findInText(String text);

	/**
	 * Kanji containing <b>every</b> radical in {@code radicals}, narrowed by {@code filters},
	 * ordered by stroke count, then frequency (unranked last), then character (D84). Repeated
	 * radicals count once. An empty collection returns an empty page. Any {@code Sort} on
	 * {@code pageable} is ignored.
	 */
	Page<KanjiSummary> findByRadicals(Collection<String> radicals, KanjiFilters filters, Pageable pageable);

	/**
	 * All radicals grouped by stroke count (fewest first), each with the number of kanji that
	 * {@link #findByRadicals} would return for it alone.
	 */
	List<RadicalGroup> findRadicalGroups();
}
