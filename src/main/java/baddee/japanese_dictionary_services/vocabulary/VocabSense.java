package baddee.japanese_dictionary_services.vocabulary;

import java.util.List;

/**
 * One meaning of a vocabulary entry. All lists keep the JMdict order (X8, D34).
 *
 * @param glosses English translations, most important first
 * @param partsOfSpeech JMdict part-of-speech tags, e.g. {@code v1}, {@code n}
 * @param miscTags JMdict misc tags, e.g. {@code uk} (usually written in kana), {@code sl} (slang)
 */
public record VocabSense(List<String> glosses, List<String> partsOfSpeech, List<String> miscTags) {

	public VocabSense {
		glosses = List.copyOf(glosses);
		partsOfSpeech = List.copyOf(partsOfSpeech);
		miscTags = List.copyOf(miscTags);
	}
}
