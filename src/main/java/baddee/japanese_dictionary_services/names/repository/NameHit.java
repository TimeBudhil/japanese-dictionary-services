package baddee.japanese_dictionary_services.names.repository;

/** One row of {@link NameRepository#findActiveHits}: tier 0 exact, 1 prefix, 2 partial. */
public interface NameHit {

	long getEntSeq();

	int getTier();
}
