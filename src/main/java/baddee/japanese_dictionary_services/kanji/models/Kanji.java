package baddee.japanese_dictionary_services.kanji.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Read-only copy of the updater's {@code kanji} row (D2, D3), keyed by the character.
 *
 * <p>The arrays are Postgres {@code text[]} columns in KanjiDic2 / KanjiVG order. A
 * <b>bare row</b> (a radical form such as ⺉) has only radical data: no codepoint, readings,
 * meanings or grade. {@code codepoint IS NULL} is how queries tell them apart.
 *
 * <p>Radicals are lazy and batch-loaded (D35); only the detail endpoint reads them.
 */
@Entity
@Immutable
@Table(name = "kanji")
public class Kanji {

	private static final int BATCH_SIZE = 100;

	@Id
	@Column(name = "character")
	private String character;

	/** Unicode code point as lowercase hex, e.g. {@code 65e5}. Null on bare rows. */
	@Column(name = "codepoint")
	private String codepoint;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "onyomi", columnDefinition = "text[]")
	private List<String> onyomi;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "kunyomi", columnDefinition = "text[]")
	private List<String> kunyomi;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "nanori", columnDefinition = "text[]")
	private List<String> nanori;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "meanings", columnDefinition = "text[]")
	private List<String> meanings;

	@Column(name = "stroke_count")
	private Integer strokeCount;

	@Column(name = "grade")
	private Integer grade;

	@Column(name = "joyo")
	private Boolean joyo;

	/** The old (pre-2010) 1–4 JLPT scale. */
	@Column(name = "jlpt")
	private Integer jlpt;

	@Column(name = "frequency")
	private Integer frequency;

	@Column(name = "classical_radical")
	private Integer classicalRadical;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "stroke_paths", columnDefinition = "text[]")
	private List<String> strokePaths;

	@Column(name = "retired_at")
	private Instant retiredAt;

	@Immutable
	@ManyToMany
	@JoinTable(name = "kanji_radical",
			joinColumns = @JoinColumn(name = "kanji"),
			inverseJoinColumns = @JoinColumn(name = "radical"))
	@OrderBy("strokeCount, character")
	@BatchSize(size = BATCH_SIZE)
	private List<Radical> radicals = new ArrayList<>();

	/** For JPA only. */
	protected Kanji() {
	}

	public String getCharacter() {
		return character;
	}

	public String getCodepoint() {
		return codepoint;
	}

	public List<String> getOnyomi() {
		return orEmpty(onyomi);
	}

	public List<String> getKunyomi() {
		return orEmpty(kunyomi);
	}

	public List<String> getNanori() {
		return orEmpty(nanori);
	}

	public List<String> getMeanings() {
		return orEmpty(meanings);
	}

	public Integer getStrokeCount() {
		return strokeCount;
	}

	public Integer getGrade() {
		return grade;
	}

	/** False when the column is null. */
	public boolean isJoyo() {
		return Boolean.TRUE.equals(joyo);
	}

	public Integer getJlpt() {
		return jlpt;
	}

	public Integer getFrequency() {
		return frequency;
	}

	public Integer getClassicalRadical() {
		return classicalRadical;
	}

	public List<String> getStrokePaths() {
		return orEmpty(strokePaths);
	}

	public Instant getRetiredAt() {
		return retiredAt;
	}

	public List<Radical> getRadicals() {
		return Collections.unmodifiableList(radicals);
	}

	private static List<String> orEmpty(List<String> values) {
		return values == null ? List.of() : Collections.unmodifiableList(values);
	}
}
