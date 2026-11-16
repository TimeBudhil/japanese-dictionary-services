package baddee.japanese_dictionary_services.kanji;

import java.util.List;
import java.util.Objects;

/**
 * A full kanji, as returned by {@code GET /api/v1/kanji/{character}}. Lists keep KanjiDic2 /
 * KanjiVG order; null elements are dropped. Numbers are null when KanjiDic2 has no value. A bare
 * row (a radical form such as ⺉) has a null codepoint, empty readings and meanings, and only its
 * radicals filled in.
 *
 * @param character the kanji itself
 * @param codepoint Unicode code point as lowercase hex, e.g. {@code 65e5}; null on bare rows
 * @param onyomi on readings in katakana, e.g. {@code ニチ}
 * @param kunyomi kun readings in KanjiDic2 notation: {@code .} marks okurigana ({@code た.べる}),
 *        {@code -} a prefix or suffix ({@code -び})
 * @param nanori readings used only in names
 * @param meanings English meanings, most important first
 * @param strokeCount number of strokes
 * @param grade KanjiDic2 grade: 1–6 taught in primary school, 8 the rest of jōyō, 9–10 jinmeiyō
 * @param joyo whether it is a jōyō (common-use) kanji
 * @param oldJlpt the <b>old</b> (pre-2010) JLPT level, 4 easiest to 1 hardest
 * @param frequency newspaper frequency rank, 1 most frequent; only the top ~2,500 have one
 * @param classicalRadical classical (Kangxi) radical number, 1–214
 * @param radicals RADKFILE radicals it contains, fewest strokes first. Some are a stand-in
 *        kanji for the real component, e.g. {@code 犯} for 犭
 * @param strokePaths SVG path data, one per stroke in stroke order (KanjiVG); empty for about
 *        half of all kanji
 */
public record KanjiDetail(
		String character,
		String codepoint,
		List<String> onyomi,
		List<String> kunyomi,
		List<String> nanori,
		List<String> meanings,
		Integer strokeCount,
		Integer grade,
		boolean joyo,
		Integer oldJlpt,
		Integer frequency,
		Integer classicalRadical,
		List<String> radicals,
		List<String> strokePaths) {

	public KanjiDetail {
		onyomi = withoutNulls(onyomi);
		kunyomi = withoutNulls(kunyomi);
		nanori = withoutNulls(nanori);
		meanings = withoutNulls(meanings);
		radicals = withoutNulls(radicals);
		strokePaths = withoutNulls(strokePaths);
	}

	static List<String> withoutNulls(List<String> values) {
		return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
	}
}
