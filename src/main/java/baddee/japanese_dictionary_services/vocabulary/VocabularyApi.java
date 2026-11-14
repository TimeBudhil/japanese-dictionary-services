package baddee.japanese_dictionary_services.vocabulary;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The vocabulary module's public API (D37). Other modules (mainly {@code search}) use only
 * this interface and the types next to it, never the module's sub-packages.
 */
public interface VocabularyApi {

	/** One entry by JMdict id, or empty if there is no such entry. */
	Optional<VocabEntryDetail> findByEntSeq(long entSeq);

	/**
	 * Full entries for the given ids, in the <b>same order</b> as {@code entSeqs}. Unknown
	 * ids are skipped; repeated ids appear once (first position).
	 */
	List<VocabEntryDetail> findAllByEntSeq(List<Long> entSeqs);

	/**
	 * Search candidates for {@code query}, each with its {@link MatchTier}, ordered best first
	 * (see D36). A blank query returns an empty page. Does not touch Postgres.
	 */
	Page<VocabCandidate> findCandidates(String query, Pageable pageable);

	/**
	 * {@link #findCandidates} with each candidate loaded as a full entry, same order and same
	 * paging. Used by {@code GET /api/v1/vocabulary?q=}.
	 */
	Page<VocabEntryDetail> searchEntries(String query, Pageable pageable);
}
