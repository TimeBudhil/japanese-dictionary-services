package baddee.japanese_dictionary_services.vocabulary.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;

/**
 * Read-only copy of the updater's {@code sense} row: one meaning of a {@link VocabEntry},
 * with English glosses, part-of-speech tags and misc tags.
 *
 * <p>JMdict {@code <field>} (subject) and {@code <dial>} (dialect) tags are not stored by
 * the updater yet (X3), so they are not here.
 *
 * <p>Glosses and tags keep the JMdict order through their order columns
 * ({@code gloss_order}, {@code pos_order}, {@code misc_order}). The updater adds these in
 * X8 (D34); this class already expects them.
 *
 * <p>The three collections are loaded lazily, in batches across many senses
 * ({@link BatchSize}), so a page of entries costs a fixed number of queries, not one per sense.
 */
@Entity
@Immutable
@Table(name = "sense")
public class Sense {

	@Id
	@Column(name = "id")
	private Long id;

	@Immutable
	@ElementCollection
	@CollectionTable(name = "sense_gloss", joinColumns = @JoinColumn(name = "sense_id"))
	@OrderColumn(name = "gloss_order")
	@Column(name = "gloss")
	@BatchSize(size = VocabEntry.BATCH_SIZE)
	private List<String> glosses = new ArrayList<>();

	@Immutable
	@ElementCollection
	@CollectionTable(name = "sense_pos", joinColumns = @JoinColumn(name = "sense_id"))
	@OrderColumn(name = "pos_order")
	@Column(name = "pos")
	@BatchSize(size = VocabEntry.BATCH_SIZE)
	private List<String> partsOfSpeech = new ArrayList<>();

	@Immutable
	@ElementCollection
	@CollectionTable(name = "sense_misc", joinColumns = @JoinColumn(name = "sense_id"))
	@OrderColumn(name = "misc_order")
	@Column(name = "misc")
	@BatchSize(size = VocabEntry.BATCH_SIZE)
	private List<String> miscTags = new ArrayList<>();

	/** For JPA only. */
	protected Sense() {
	}

	public Long getId() {
		return id;
	}

	public List<String> getGlosses() {
		return Collections.unmodifiableList(glosses);
	}

	public List<String> getPartsOfSpeech() {
		return Collections.unmodifiableList(partsOfSpeech);
	}

	public List<String> getMiscTags() {
		return Collections.unmodifiableList(miscTags);
	}
}
