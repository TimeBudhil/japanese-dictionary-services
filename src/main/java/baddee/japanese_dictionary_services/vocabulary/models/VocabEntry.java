package baddee.japanese_dictionary_services.vocabulary.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;

/**
 * Read-only copy of the updater's JMdict entry ({@code vocab_entry}), keyed by the JMdict
 * sequence id (ent_seq). The updater owns the table (D3); this class only mirrors it (D2).
 *
 * <p>The children are mapped one-way ({@code @JoinColumn} on the parent, no back-reference),
 * because this service only reads. Both lists are ordered by id, which follows JMdict
 * document order since the updater inserts them in that order with identity ids.
 *
 * <p>No N+1: readings and senses are lazy and load in batches of up to {@link #BATCH_SIZE}
 * entries ({@link BatchSize}), which covers the largest page in one query each. The
 * {@link Sense} collections use subselect fetching. A page of up to 100 entries costs 6
 * queries: entries, readings, senses, glosses, POS, misc. Nothing is join-fetched, because two
 * {@code List}s in one join fetch fail in Hibernate (MultipleBagFetchException) and multiply
 * the rows. Read them inside a transaction ({@code open-in-view=false}).
 *
 * <p>{@code common} is nullable in the schema. It is mapped as {@code Boolean}, and null reads as
 * false, so one bad row cannot fail a whole page.
 */
@Entity
@Immutable
@Table(name = "vocab_entry")
public class VocabEntry {

	/** Covers the largest page (size max 100, D28) in one batch. */
	private static final int BATCH_SIZE = 100;

	@Id
	@Column(name = "ent_seq")
	private Long entSeq;

	@Column(name = "common")
	private Boolean common;

	@Immutable
	@OneToMany
	@JoinColumn(name = "ent_seq")
	@OrderBy("id")
	@BatchSize(size = BATCH_SIZE)
	private List<Reading> readings = new ArrayList<>();

	@Immutable
	@OneToMany
	@JoinColumn(name = "ent_seq")
	@OrderBy("id")
	@BatchSize(size = BATCH_SIZE)
	private List<Sense> senses = new ArrayList<>();

	/** For JPA only. */
	protected VocabEntry() {
	}

	public Long getEntSeq() {
		return entSeq;
	}

	/** False when the column is null. */
	public boolean isCommon() {
		return Boolean.TRUE.equals(common);
	}

	/** Kanji writings and kana readings together; split them with {@link Reading#isKanji()}. */
	public List<Reading> getReadings() {
		return Collections.unmodifiableList(readings);
	}

	public List<Sense> getSenses() {
		return Collections.unmodifiableList(senses);
	}
}
