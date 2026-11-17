package baddee.japanese_dictionary_services.names.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import baddee.japanese_dictionary_services.names.models.NameEntry;

/**
 * Read-only access to names in Postgres. It extends {@link Repository}, not
 * {@code JpaRepository}, so no {@code save}/{@code delete} exists.
 */
public interface NameRepository extends Repository<NameEntry, Long> {

	/** Any row, retired included (D76). */
	Optional<NameEntry> findByEntSeq(long entSeq);

	/** Rows among {@code entSeqs}, retired included, in no particular order. */
	List<NameEntry> findByEntSeqIn(Collection<Long> entSeqs);

	/*
	 * The WHERE clause is the index-served candidate set: name_entry_jp_text and
	 * name_entry_en_text are the updater's trigram-indexed functions (X5, D91). "% q%" is a
	 * word-start match on the space-joined values. The CASE then sets the exact tier per value;
	 * rows that matched only after a space inside a Japanese value get no tier and drop out.
	 */
	String TIERED = """
			select n.ent_seq as ent_seq,
			  case
			    when :q = any(n.kanji) or :q = any(n.readings)
			      or exists (select 1 from unnest(n.translations) t where lower(t) = lower(:q)) then 0
			    when exists (select 1 from unnest(n.kanji || n.readings) v where starts_with(v, :q))
			      or exists (select 1 from unnest(n.translations) t where starts_with(lower(t), lower(:q))) then 1
			    when name_entry_en_text(n.translations) like lower(:pattern) escape '\\' then 2
			  end as tier
			from name_entry n
			where n.retired_at is null
			  and (name_entry_jp_text(n.kanji, n.readings) like :pattern escape '\\'
			    or name_entry_en_text(n.translations) like lower(:pattern) escape '\\')""";

	/**
	 * Active names matching {@code q}, best tier first (0 exact, 1 prefix, 2 partial), then
	 * by id (D90). {@code pattern} is {@code "% " + q + "%"} with {@code q}'s LIKE wildcards
	 * escaped by a backslash. Two queries per page: this one and the count.
	 */
	@NativeQuery(value = "select ent_seq as \"entSeq\", tier from (" + TIERED + ") c "
			+ "where c.tier is not null order by c.tier, c.ent_seq",
			countQuery = "select count(*) from (" + TIERED + ") c where c.tier is not null")
	Page<NameHit> findActiveHits(@Param("q") String q, @Param("pattern") String pattern, Pageable pageable);
}
