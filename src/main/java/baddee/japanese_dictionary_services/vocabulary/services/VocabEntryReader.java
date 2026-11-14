package baddee.japanese_dictionary_services.vocabulary.services;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import baddee.japanese_dictionary_services.vocabulary.VocabEntryDetail;
import baddee.japanese_dictionary_services.vocabulary.VocabSense;
import baddee.japanese_dictionary_services.vocabulary.models.Reading;
import baddee.japanese_dictionary_services.vocabulary.models.Sense;
import baddee.japanese_dictionary_services.vocabulary.models.VocabEntry;
import baddee.japanese_dictionary_services.vocabulary.repository.VocabularyRepository;

/**
 * Loads entries from Postgres and maps them to {@link VocabEntryDetail} inside one read-only
 * transaction, so the lazy, batch-loaded collections work with {@code open-in-view=false}.
 *
 * <p>It is a separate bean (not methods on {@link VocabularyService}) so the transaction
 * proxy applies, and so the Elasticsearch call in {@code searchEntries} runs outside the
 * transaction and does not hold a database connection.
 */
@Component
public class VocabEntryReader {

	private final VocabularyRepository repository;

	public VocabEntryReader(VocabularyRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public Optional<VocabEntryDetail> findByEntSeq(long entSeq) {
		return repository.findByEntSeq(entSeq).map(VocabEntryReader::toDetail);
	}

	/** Same order as {@code entSeqs}; unknown ids skipped, duplicates kept once. */
	@Transactional(readOnly = true)
	public List<VocabEntryDetail> findAllByEntSeq(List<Long> entSeqs) {
		if (entSeqs.isEmpty()) {
			return List.of();
		}
		LinkedHashSet<Long> ordered = new LinkedHashSet<>(entSeqs);
		Map<Long, VocabEntry> byId = repository.findByEntSeqIn(ordered).stream()
				.collect(Collectors.toMap(VocabEntry::getEntSeq, Function.identity()));
		return ordered.stream()
				.map(byId::get)
				.filter(entry -> entry != null)
				.map(VocabEntryReader::toDetail)
				.toList();
	}

	static VocabEntryDetail toDetail(VocabEntry entry) {
		List<Reading> readings = entry.getReadings();
		return new VocabEntryDetail(
				entry.getEntSeq(),
				entry.isCommon(),
				readings.stream().filter(Reading::isKanji).map(Reading::getText).toList(),
				readings.stream().filter(reading -> !reading.isKanji()).map(Reading::getText).toList(),
				entry.getSenses().stream().map(VocabEntryReader::toSense).toList());
	}

	private static VocabSense toSense(Sense sense) {
		return new VocabSense(sense.getGlosses(), sense.getPartsOfSpeech(), sense.getMiscTags());
	}
}
