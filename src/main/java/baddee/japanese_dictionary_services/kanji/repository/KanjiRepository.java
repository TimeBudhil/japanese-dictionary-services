package baddee.japanese_dictionary_services.kanji.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import baddee.japanese_dictionary_services.kanji.models.Kanji;

/**
 * Read-only access to kanji in Postgres. It extends {@link Repository}, not
 * {@code JpaRepository}, so no {@code save}/{@code delete} exists (the updater is the only
 * writer).
 *
 * <p>"Active" means not retired and not a bare row ({@code codepoint} is null only on bare
 * rows). Searches return active kanji only (D76, D84).
 */
public interface KanjiRepository extends Repository<Kanji, String> {

	/** Any row, retired or bare included (D85). */
	Optional<Kanji> findByCharacter(String character);

	/** Active kanji among {@code characters}, in no particular order. */
	@Query("""
			select k from Kanji k
			where k.character in :characters and k.retiredAt is null and k.codepoint is not null""")
	List<Kanji> findActiveByCharacterIn(@Param("characters") Collection<String> characters);

	/**
	 * Active kanji that contain all of {@code radicals} (which must have no duplicates, size
	 * {@code radicalCount}), with optional exact filters (null = any). Order: D84. Two queries
	 * per page: this one and the count.
	 */
	@Query(value = """
			select k from Kanji k
			where k.retiredAt is null and k.codepoint is not null
			  and (:strokeCount is null or k.strokeCount = :strokeCount)
			  and (:grade is null or k.grade = :grade)
			  and (:joyo is null or coalesce(k.joyo, false) = :joyo)
			  and k.character in (
			    select k2.character from Kanji k2 join k2.radicals r
			    where r.character in :radicals
			    group by k2.character
			    having count(r) = :radicalCount)
			order by k.strokeCount asc nulls last, k.frequency asc nulls last, k.character asc""",
			countQuery = """
			select count(k) from Kanji k
			where k.retiredAt is null and k.codepoint is not null
			  and (:strokeCount is null or k.strokeCount = :strokeCount)
			  and (:grade is null or k.grade = :grade)
			  and (:joyo is null or coalesce(k.joyo, false) = :joyo)
			  and k.character in (
			    select k2.character from Kanji k2 join k2.radicals r
			    where r.character in :radicals
			    group by k2.character
			    having count(r) = :radicalCount)""")
	Page<Kanji> findActiveByAllRadicals(
			@Param("radicals") Collection<String> radicals,
			@Param("radicalCount") long radicalCount,
			@Param("strokeCount") Integer strokeCount,
			@Param("grade") Integer grade,
			@Param("joyo") Boolean joyo,
			Pageable pageable);

	/**
	 * Every radical with its count of active kanji, ordered by stroke count.
	 * One query; radicals no active kanji contains have count 0.
	 */
	@Query("""
			select new baddee.japanese_dictionary_services.kanji.repository.RadicalCount(
			  r.character, r.strokeCount,
			  (select count(k) from Kanji k join k.radicals kr
			   where kr = r and k.retiredAt is null and k.codepoint is not null))
			from Radical r
			order by r.strokeCount asc nulls last, r.character asc""")
	List<RadicalCount> findAllRadicalCounts();
}
