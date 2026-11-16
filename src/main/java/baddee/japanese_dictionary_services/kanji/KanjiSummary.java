package baddee.japanese_dictionary_services.kanji;

import java.util.List;

/**
 * A kanji in a list or search result (D86): {@link KanjiDetail} without nanori, radicals and
 * stroke paths, which can be several KB per kanji. The fields mean the same as there.
 */
public record KanjiSummary(
		String character,
		List<String> onyomi,
		List<String> kunyomi,
		List<String> meanings,
		Integer strokeCount,
		Integer grade,
		boolean joyo,
		Integer frequency) {

	public KanjiSummary {
		onyomi = KanjiDetail.withoutNulls(onyomi);
		kunyomi = KanjiDetail.withoutNulls(kunyomi);
		meanings = KanjiDetail.withoutNulls(meanings);
	}
}
