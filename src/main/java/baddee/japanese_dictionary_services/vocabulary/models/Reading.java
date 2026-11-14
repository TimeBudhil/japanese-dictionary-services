package baddee.japanese_dictionary_services.vocabulary.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Read-only copy of the updater's {@code reading} row: one written form of a
 * {@link VocabEntry}. Covers both kanji writings ({@code <keb>}, kanji=true) and kana
 * readings ({@code <reb>}, kanji=false).
 *
 * <p>The {@code ent_seq} column is mapped by {@link VocabEntry#getReadings()}, so there is
 * no back-reference here.
 */
@Entity
@Immutable
@Table(name = "reading")
public class Reading {

	@Id
	@Column(name = "id")
	private Long id;

	@Column(name = "text", nullable = false)
	private String text;

	/** True for a kanji writing (keb), false for a kana reading (reb). Nullable in the schema. */
	@Column(name = "kanji")
	private Boolean kanji;

	/** For JPA only. */
	protected Reading() {
	}

	public Long getId() {
		return id;
	}

	public String getText() {
		return text;
	}

	/** False (kana reading) when the column is null. */
	public boolean isKanji() {
		return Boolean.TRUE.equals(kanji);
	}
}
