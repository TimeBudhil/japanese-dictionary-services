package baddee.japanese_dictionary_services.vocabulary;

/**
 * How a vocabulary candidate matched the query, best first. The {@code search} module ranks
 * on this (D17) and adds its own {@code DEINFLECTED} tier for queries it deinflects first, so
 * that tier is not here.
 */
public enum MatchTier {

	/** The query equals a kanji writing or a kana reading. */
	EXACT,

	/** A kanji writing or a kana reading starts with the query. */
	PREFIX,

	/** Full-text match on the English glosses (all query words must appear). */
	PARTIAL
}
