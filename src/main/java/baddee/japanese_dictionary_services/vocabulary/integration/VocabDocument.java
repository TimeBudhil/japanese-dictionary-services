package baddee.japanese_dictionary_services.vocabulary.integration;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Read-only copy of the updater's {@code vocab} Elasticsearch document (D2). The updater
 * writes it; this service only searches it for candidates and then loads the full entry
 * from Postgres.
 *
 * <p>{@code createIndex = false}: the index and its mapping belong to the updater
 * ({@code docker/elasticsearch/mappings/vocab.json} locally), so this service never creates
 * or changes them.
 */
@Document(indexName = VocabDocument.INDEX, createIndex = false)
public class VocabDocument {

	public static final String INDEX = "vocab";

	/** Field names, as written by the updater. */
	static final String KANJI = "kanji";
	static final String READINGS = "readings";
	static final String GLOSSES = "glosses";
	static final String COMMON = "common";
	/**
	 * Keyword sub-field of the {@code id} (= ent_seq) source field. The updater's
	 * mapping has no {@code id} field, so ES maps it dynamically as {@code text} +
	 * {@code keyword}; this repo's dev mapping declares the same. Unique, so it is the
	 * last sort key for stable paging ({@code _id} cannot be sorted on in ES 8+).
	 */
	static final String ID_KEYWORD = "id.keyword";

	/** The JMdict ent_seq, as a string. */
	@Id
	private String id;

	@Field(type = FieldType.Keyword)
	private List<String> kanji;

	@Field(type = FieldType.Keyword)
	private List<String> readings;

	@Field(type = FieldType.Text)
	private List<String> glosses;

	@Field(type = FieldType.Boolean)
	private boolean common;

	/** For Spring Data only. */
	protected VocabDocument() {
	}

	public String getId() {
		return id;
	}

	public List<String> getKanji() {
		return kanji == null ? List.of() : List.copyOf(kanji);
	}

	public List<String> getReadings() {
		return readings == null ? List.of() : List.copyOf(readings);
	}

	public List<String> getGlosses() {
		return glosses == null ? List.of() : List.copyOf(glosses);
	}

	public boolean isCommon() {
		return common;
	}
}
