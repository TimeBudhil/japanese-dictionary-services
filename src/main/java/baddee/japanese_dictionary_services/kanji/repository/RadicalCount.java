package baddee.japanese_dictionary_services.kanji.repository;

/** One row of {@link KanjiRepository#findAllRadicalCounts()}. */
public record RadicalCount(String character, Integer strokeCount, long kanjiCount) {
}
