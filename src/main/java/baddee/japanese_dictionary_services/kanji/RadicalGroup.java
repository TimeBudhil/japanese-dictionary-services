package baddee.japanese_dictionary_services.kanji;

import java.util.List;

/**
 * The radicals with one stroke count, as returned by {@code GET /api/v1/radicals} (D87).
 *
 * @param strokeCount the radicals' stroke count
 * @param radicals the radicals, in code point order
 */
public record RadicalGroup(Integer strokeCount, List<Radical> radicals) {

	public RadicalGroup {
		radicals = List.copyOf(radicals);
	}

	/**
	 * One RADKFILE radical. Some are a stand-in kanji for the real component, e.g. {@code 犯}
	 * for 犭, exactly as RADKFILE writes them.
	 *
	 * @param character the radical, as used in {@code ?radicals=}
	 * @param kanjiCount how many kanji a radical search for it alone returns
	 */
	public record Radical(String character, long kanjiCount) {
	}
}
