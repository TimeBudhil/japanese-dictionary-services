package baddee.japanese_dictionary_services.vocabulary;

import java.util.List;

/**
 * A full vocabulary entry, as returned by {@link VocabularyApi}. Readings are split into
 * kanji writings and kana readings. All lists keep JMdict order.
 *
 * @param entSeq the JMdict entry id
 * @param common whether JMdict marks the entry as common
 * @param kanji kanji writings (JMdict {@code <keb>}), e.g. {@code 食べる}; empty for kana-only words
 * @param readings kana readings (JMdict {@code <reb>}), e.g. {@code たべる}
 * @param senses meanings, in JMdict order
 */
public record VocabEntryDetail(
		long entSeq,
		boolean common,
		List<String> kanji,
		List<String> readings,
		List<VocabSense> senses) {

	public VocabEntryDetail {
		kanji = List.copyOf(kanji);
		readings = List.copyOf(readings);
		senses = List.copyOf(senses);
	}
}
