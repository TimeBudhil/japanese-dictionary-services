package baddee.japanese_dictionary_services.names;

/**
 * One name search hit, before the full name is loaded. Names have no "common" flag or
 * frequency, so the tier is all {@code search} has to rank them by.
 *
 * @param entSeq the JMnedict entry id
 * @param tier the best way this name matched the query
 */
public record NameCandidate(long entSeq, NameMatchTier tier) {
}
