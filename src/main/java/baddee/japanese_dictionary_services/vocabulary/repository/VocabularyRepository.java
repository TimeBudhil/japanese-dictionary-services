package baddee.japanese_dictionary_services.vocabulary.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

import baddee.japanese_dictionary_services.vocabulary.models.VocabEntry;

/**
 * Read-only access to vocabulary entries in Postgres. It extends {@link Repository}, not
 * {@code JpaRepository}, so no {@code save}/{@code delete} exists (the updater is the only
 * writer).
 *
 * <p>Readings, senses and their glosses/tags are lazy and batch-loaded (see
 * {@link VocabEntry}), so call these inside a read-only transaction and map to DTOs there.
 */
public interface VocabularyRepository extends Repository<VocabEntry, Long> {

	/** One entry for the detail endpoint. */
	Optional<VocabEntry> findByEntSeq(Long entSeq);

	/**
	 * Entries for a set of ids, e.g. the ES candidates of one page. The result order is
	 * <b>not</b> the order of {@code entSeqs}; the caller reorders. Missing ids are skipped.
	 */
	List<VocabEntry> findByEntSeqIn(Collection<Long> entSeqs);
}
