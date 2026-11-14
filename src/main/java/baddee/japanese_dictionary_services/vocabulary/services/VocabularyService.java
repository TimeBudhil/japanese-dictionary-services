package baddee.japanese_dictionary_services.vocabulary.services;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import baddee.japanese_dictionary_services.vocabulary.VocabCandidate;
import baddee.japanese_dictionary_services.vocabulary.VocabEntryDetail;
import baddee.japanese_dictionary_services.vocabulary.VocabularyApi;
import baddee.japanese_dictionary_services.vocabulary.integration.VocabSearchClient;

/**
 * Implements {@link VocabularyApi}: Elasticsearch for candidates ({@link VocabSearchClient}),
 * Postgres for full entries ({@link VocabEntryReader}).
 */
@Service
public class VocabularyService implements VocabularyApi {

	private final VocabEntryReader reader;
	private final VocabSearchClient searchClient;

	public VocabularyService(VocabEntryReader reader, VocabSearchClient searchClient) {
		this.reader = reader;
		this.searchClient = searchClient;
	}

	@Override
	public Optional<VocabEntryDetail> findByEntSeq(long entSeq) {
		return reader.findByEntSeq(entSeq);
	}

	@Override
	public List<VocabEntryDetail> findAllByEntSeq(List<Long> entSeqs) {
		return reader.findAllByEntSeq(entSeqs);
	}

	@Override
	public Page<VocabCandidate> findCandidates(String query, Pageable pageable) {
		return searchClient.findCandidates(query, pageable);
	}

	/**
	 * The total comes from Elasticsearch. If an id is in ES but not in Postgres (the updater
	 * writes them separately), that entry is skipped, so a page can be shorter than its size.
	 */
	@Override
	public Page<VocabEntryDetail> searchEntries(String query, Pageable pageable) {
		Page<VocabCandidate> candidates = searchClient.findCandidates(query, pageable);
		List<Long> ids = candidates.getContent().stream().map(VocabCandidate::entSeq).toList();
		return new PageImpl<>(reader.findAllByEntSeq(ids), pageable, candidates.getTotalElements());
	}
}
