package baddee.japanese_dictionary_services.vocabulary;

/**
 * One vocabulary search hit, before the full entry is loaded. {@code common} is included so
 * the {@code search} module can rank without loading entries (D17).
 *
 * @param entSeq the JMdict entry id
 * @param tier the best way this entry matched the query
 * @param common whether JMdict marks the entry as common
 */
public record VocabCandidate(long entSeq, MatchTier tier, boolean common) {
}
