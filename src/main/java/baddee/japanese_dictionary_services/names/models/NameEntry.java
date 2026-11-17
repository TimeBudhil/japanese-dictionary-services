package baddee.japanese_dictionary_services.names.models;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Read-only copy of the updater's {@code name_entry} row (D2, D3, D18). The arrays are Postgres
 * {@code text[]} columns in JMnedict order; {@code name_types} holds each type once.
 */
@Entity
@Immutable
@Table(name = "name_entry")
public class NameEntry {

	@Id
	@Column(name = "ent_seq")
	private Long entSeq;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "kanji", columnDefinition = "text[]")
	private List<String> kanji;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "readings", columnDefinition = "text[]")
	private List<String> readings;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "name_types", columnDefinition = "text[]")
	private List<String> nameTypes;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "translations", columnDefinition = "text[]")
	private List<String> translations;

	@Column(name = "retired_at")
	private Instant retiredAt;

	/** For JPA only. */
	protected NameEntry() {
	}

	public long getEntSeq() {
		return entSeq;
	}

	public List<String> getKanji() {
		return orEmpty(kanji);
	}

	public List<String> getReadings() {
		return orEmpty(readings);
	}

	public List<String> getNameTypes() {
		return orEmpty(nameTypes);
	}

	public List<String> getTranslations() {
		return orEmpty(translations);
	}

	public Instant getRetiredAt() {
		return retiredAt;
	}

	private static List<String> orEmpty(List<String> values) {
		return values == null ? List.of() : Collections.unmodifiableList(values);
	}
}
