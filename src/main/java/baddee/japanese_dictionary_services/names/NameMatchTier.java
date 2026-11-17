package baddee.japanese_dictionary_services.names;

/**
 * How a name matched the query, best first (D90). English comparisons ignore case. The
 * {@code search} module ranks on this (D17).
 */
public enum NameMatchTier {

	/** The query equals a kanji writing, a reading, or a whole translation. */
	EXACT,

	/** A kanji writing, a reading, or a translation starts with the query. */
	PREFIX,

	/** A later word of a translation starts with the query ({@code kakuei} in "Tanaka Kakuei"). */
	PARTIAL
}
