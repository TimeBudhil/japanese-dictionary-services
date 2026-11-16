package baddee.japanese_dictionary_services.kanji.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Read-only copy of the updater's {@code radical} row: one RADKFILE radical. About 20 of them
 * are a stand-in kanji that contains the real component (犯 for 犭, 艾 for 艹), exactly as
 * RADKFILE writes them; this service returns them unchanged.
 */
@Entity
@Immutable
@Table(name = "radical")
public class Radical {

	@Id
	@Column(name = "character")
	private String character;

	@Column(name = "stroke_count")
	private Integer strokeCount;

	/** For JPA only. */
	protected Radical() {
	}

	public String getCharacter() {
		return character;
	}

	public Integer getStrokeCount() {
		return strokeCount;
	}
}
