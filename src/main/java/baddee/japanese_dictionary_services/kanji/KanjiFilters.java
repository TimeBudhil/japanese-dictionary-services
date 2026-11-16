package baddee.japanese_dictionary_services.kanji;

/**
 * Optional narrowing for {@link KanjiApi#findByRadicals}. A null field means "any"; set fields
 * combine with AND.
 *
 * @param strokeCount exact stroke count
 * @param grade KanjiDic2 grade (see {@link KanjiDetail#grade()})
 * @param joyo true for jōyō kanji only, false for non-jōyō only
 */
public record KanjiFilters(Integer strokeCount, Integer grade, Boolean joyo) {

	public static final KanjiFilters NONE = new KanjiFilters(null, null, null);
}
