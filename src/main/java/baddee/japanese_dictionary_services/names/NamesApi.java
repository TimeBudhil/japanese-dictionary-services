package baddee.japanese_dictionary_services.names;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The names module's public API (D37). Other modules (mainly {@code search}) use only this
 * interface and the types next to it, never the module's sub-packages.
 *
 * <p>Lookups by id return retired names unchanged (D76); searches never return them.
 */
public interface NamesApi {

	/** One name by JMnedict id, or empty if there is no such entry. */
	Optional<NameDetail> findByEntSeq(long entSeq);

	/**
	 * Names for the given ids, in the <b>same order</b> as {@code entSeqs}. Unknown ids are
	 * skipped; repeated ids appear once (first position).
	 */
	List<NameDetail> findAllByEntSeq(List<Long> entSeqs);

	/**
	 * Search candidates for {@code query}, each with its {@link NameMatchTier}, ordered by tier,
	 * then id (D90). The query is stripped first; a blank query returns an empty page. Any
	 * {@code Sort} on {@code pageable} is ignored.
	 */
	Page<NameCandidate> findCandidates(String query, Pageable pageable);

	/**
	 * {@link #findCandidates} with each candidate loaded as a full name, same order and same
	 * paging. Used by {@code GET /api/v1/names?q=}.
	 */
	Page<NameDetail> searchEntries(String query, Pageable pageable);
}
