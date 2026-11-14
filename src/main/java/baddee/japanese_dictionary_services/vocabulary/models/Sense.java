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

import org.hibernate.Length;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
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
 * <p>The three collections load lazily with {@link FetchMode#SUBSELECT}: touching one sense's
 * glosses loads the glosses of <b>every</b> sense from the same query in one SQL statement. So
 * each collection costs one query per page, however many senses the page has. (A
 * {@code @BatchSize} would count senses, not entries: a page of 100 entries has 200–300 senses.)
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
	@Column(name = "gloss", length = Length.LONG32) // `text` in the updater schema
	@Fetch(FetchMode.SUBSELECT)
	private List<String> glosses = new ArrayList<>();

	@Immutable
	@ElementCollection
	@CollectionTable(name = "sense_pos", joinColumns = @JoinColumn(name = "sense_id"))
	@OrderColumn(name = "pos_order")
	@Column(name = "pos")
	@Fetch(FetchMode.SUBSELECT)
	private List<String> partsOfSpeech = new ArrayList<>();

	@Immutable
	@ElementCollection
	@CollectionTable(name = "sense_misc", joinColumns = @JoinColumn(name = "sense_id"))
	@OrderColumn(name = "misc_order")
	@Column(name = "misc")
	@Fetch(FetchMode.SUBSELECT)
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
