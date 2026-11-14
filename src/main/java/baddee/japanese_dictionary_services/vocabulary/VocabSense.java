package baddee.japanese_dictionary_services.vocabulary;

import java.util.List;
import java.util.Objects;

/**
 * One meaning of a vocabulary entry. All lists keep the JMdict order (X8, D34).
 *
 * <p>Null elements are dropped. They can come from a NULL value column, or from a gap in an
 * order column, which Hibernate fills with null. Dropping them keeps one bad row from failing
 * the whole entry.
 *
 * @param glosses English translations, most important first
 * @param partsOfSpeech JMdict part-of-speech tags, e.g. {@code v1}, {@code n}
 * @param miscTags JMdict misc tags, e.g. {@code uk} (usually written in kana), {@code sl} (slang)
 */
public record VocabSense(List<String> glosses, List<String> partsOfSpeech, List<String> miscTags) {

	public VocabSense {
		glosses = withoutNulls(glosses);
		partsOfSpeech = withoutNulls(partsOfSpeech);
		miscTags = withoutNulls(miscTags);
	}

	private static List<String> withoutNulls(List<String> values) {
		return values.stream().filter(Objects::nonNull).toList();
	}
}
