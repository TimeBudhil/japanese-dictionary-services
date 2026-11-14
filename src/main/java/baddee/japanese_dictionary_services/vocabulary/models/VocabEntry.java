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
 * <p>No N+1: the collections are lazy and load in batches ({@link BatchSize}) across all
 * entries in the persistence context. Loading a page of entries costs a fixed number of
 * queries (entries, readings, senses, glosses, POS, misc), whatever the page size. They are
 * not join-fetched, because two {@code List}s in one join fetch fail in Hibernate
 * (MultipleBagFetchException) and multiply the rows. Read them inside a transaction
 * ({@code open-in-view=false}).
 */
@Entity
@Immutable
@Table(name = "vocab_entry")
public class VocabEntry {

	/** Covers the largest page (size max 100, D28) in one batch. */
	public static final int BATCH_SIZE = 100;

	@Id
	@Column(name = "ent_seq")
	private Long entSeq;

	@Column(name = "common")
	private boolean common;

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

	public boolean isCommon() {
		return common;
	}

	/** Kanji writings and kana readings together; split them with {@link Reading#isKanji()}. */
	public List<Reading> getReadings() {
		return Collections.unmodifiableList(readings);
	}

	public List<Sense> getSenses() {
		return Collections.unmodifiableList(senses);
	}
}
