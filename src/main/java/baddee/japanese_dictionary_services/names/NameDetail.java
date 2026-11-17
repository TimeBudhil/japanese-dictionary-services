package baddee.japanese_dictionary_services.names;

import java.util.List;
import java.util.Objects;

/**
 * A JMnedict name, as returned by {@code GET /api/v1/names/{entSeq}} and in name search
 * results. Lists are in JMnedict order; null elements are dropped.
 *
 * <p>JMnedict links some readings to some writings and groups translations per name type;
 * the updater stores flat lists, so those links are not available.
 *
 * @param entSeq the JMnedict entry id
 * @param kanji kanji writings; empty for kana-only names such as さくら
 * @param readings kana readings
 * @param nameTypes name types as expanded text, e.g. {@code family or surname}, each once
 * @param translations romanized or English forms, e.g. {@code Tanaka}
 */
public record NameDetail(
		long entSeq,
		List<String> kanji,
		List<String> readings,
		List<String> nameTypes,
		List<String> translations) {

	public NameDetail {
		kanji = withoutNulls(kanji);
		readings = withoutNulls(readings);
		nameTypes = withoutNulls(nameTypes);
		translations = withoutNulls(translations);
	}

	private static List<String> withoutNulls(List<String> values) {
		return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
	}
}
