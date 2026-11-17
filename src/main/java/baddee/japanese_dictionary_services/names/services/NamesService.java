package baddee.japanese_dictionary_services.names.services;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import baddee.japanese_dictionary_services.names.NameCandidate;
import baddee.japanese_dictionary_services.names.NameDetail;
import baddee.japanese_dictionary_services.names.NameMatchTier;
import baddee.japanese_dictionary_services.names.NamesApi;
import baddee.japanese_dictionary_services.names.models.NameEntry;
import baddee.japanese_dictionary_services.names.repository.NameHit;
import baddee.japanese_dictionary_services.names.repository.NameRepository;

/** Implements {@link NamesApi} from Postgres only (D18), one read-only transaction per call. */
@Service
@Transactional(readOnly = true)
public class NamesService implements NamesApi {

	private static final NameMatchTier[] TIERS = NameMatchTier.values();

	private final NameRepository repository;

	public NamesService(NameRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<NameDetail> findByEntSeq(long entSeq) {
		return repository.findByEntSeq(entSeq).map(NamesService::toDetail);
	}

	@Override
	public List<NameDetail> findAllByEntSeq(List<Long> entSeqs) {
		LinkedHashSet<Long> distinct = new LinkedHashSet<>(entSeqs);
		if (distinct.isEmpty()) {
			return List.of();
		}
		Map<Long, NameEntry> byId = repository.findByEntSeqIn(distinct).stream()
				.collect(Collectors.toMap(NameEntry::getEntSeq, Function.identity()));
		return distinct.stream().map(byId::get).filter(Objects::nonNull).map(NamesService::toDetail).toList();
	}

	@Override
	public Page<NameCandidate> findCandidates(String query, Pageable pageable) {
		// Page number and size only: the query's own order is the contract (D90).
		Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
		String q = query == null ? "" : query.strip();
		if (q.isEmpty()) {
			return Page.empty(page);
		}
		return repository.findActiveHits(q, wordStartPattern(q), page)
				.map(hit -> new NameCandidate(hit.getEntSeq(), TIERS[hit.getTier()]));
	}

	@Override
	public Page<NameDetail> searchEntries(String query, Pageable pageable) {
		Page<NameCandidate> candidates = findCandidates(query, pageable);
		List<Long> ids = candidates.getContent().stream().map(NameCandidate::entSeq).toList();
		return new PageImpl<>(findAllByEntSeq(ids), candidates.getPageable(), candidates.getTotalElements());
	}

	/** {@code "% " + q + "%"} for LIKE, with backslash, {@code %} and {@code _} in q escaped. */
	static String wordStartPattern(String q) {
		return "% " + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
	}

	static NameDetail toDetail(NameEntry entry) {
		return new NameDetail(entry.getEntSeq(), entry.getKanji(), entry.getReadings(),
				entry.getNameTypes(), entry.getTranslations());
	}
}
