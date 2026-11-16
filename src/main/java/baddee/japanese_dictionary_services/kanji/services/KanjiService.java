package baddee.japanese_dictionary_services.kanji.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import baddee.japanese_dictionary_services.kanji.KanjiApi;
import baddee.japanese_dictionary_services.kanji.KanjiDetail;
import baddee.japanese_dictionary_services.kanji.KanjiFilters;
import baddee.japanese_dictionary_services.kanji.KanjiSummary;
import baddee.japanese_dictionary_services.kanji.RadicalGroup;
import baddee.japanese_dictionary_services.kanji.models.Kanji;
import baddee.japanese_dictionary_services.kanji.models.Radical;
import baddee.japanese_dictionary_services.kanji.repository.KanjiRepository;
import baddee.japanese_dictionary_services.kanji.repository.RadicalCount;

/**
 * Implements {@link KanjiApi} from Postgres only. Unlike vocabulary there is no Elasticsearch
 * call to keep outside the transaction, so the whole service is one read-only transaction per
 * call, and the lazy radicals load inside it ({@code open-in-view=false}).
 */
@Service
@Transactional(readOnly = true)
public class KanjiService implements KanjiApi {

	private final KanjiRepository repository;

	public KanjiService(KanjiRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<KanjiDetail> findByCharacter(String character) {
		return repository.findByCharacter(character).map(KanjiService::toDetail);
	}

	@Override
	public List<KanjiSummary> findInText(String text) {
		if (text == null || text.isEmpty()) {
			return List.of();
		}
		// Code points, not chars: some kanji (e.g. 𠮟) are outside the BMP.
		Set<String> characters = text.codePoints()
				.mapToObj(Character::toString)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		Map<String, Kanji> byCharacter = repository.findActiveByCharacterIn(characters).stream()
				.collect(Collectors.toMap(Kanji::getCharacter, Function.identity()));
		return characters.stream()
				.map(byCharacter::get)
				.filter(Objects::nonNull)
				.map(KanjiService::toSummary)
				.toList();
	}

	@Override
	public Page<KanjiSummary> findByRadicals(Collection<String> radicals, KanjiFilters filters, Pageable pageable) {
		// Page number and size only: the query's own order is the contract (D84).
		Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
		Set<String> distinct = new LinkedHashSet<>(radicals);
		if (distinct.isEmpty()) {
			return Page.empty(page);
		}
		KanjiFilters f = filters == null ? KanjiFilters.NONE : filters;
		return repository.findActiveByAllRadicals(distinct, distinct.size(), f.strokeCount(), f.grade(), f.joyo(), page)
				.map(KanjiService::toSummary);
	}

	@Override
	public List<RadicalGroup> findRadicalGroups() {
		// The query is ordered by stroke count, so groups come out fewest strokes first.
		Map<Integer, List<RadicalGroup.Radical>> byStrokes = new LinkedHashMap<>();
		for (RadicalCount row : repository.findAllRadicalCounts()) {
			byStrokes.computeIfAbsent(row.strokeCount(), strokes -> new ArrayList<>())
					.add(new RadicalGroup.Radical(row.character(), row.kanjiCount()));
		}
		// Sorted here, not in SQL: the database's collation order differs between environments.
		return byStrokes.entrySet().stream()
				.map(group -> new RadicalGroup(group.getKey(), group.getValue().stream()
						.sorted(Comparator.comparingInt((RadicalGroup.Radical r) -> r.character().codePointAt(0)))
						.toList()))
				.toList();
	}

	static KanjiDetail toDetail(Kanji kanji) {
		return new KanjiDetail(
				kanji.getCharacter(),
				kanji.getCodepoint(),
				kanji.getOnyomi(),
				kanji.getKunyomi(),
				kanji.getNanori(),
				kanji.getMeanings(),
				kanji.getStrokeCount(),
				kanji.getGrade(),
				kanji.isJoyo(),
				kanji.getJlpt(),
				kanji.getFrequency(),
				kanji.getClassicalRadical(),
				kanji.getRadicals().stream().map(Radical::getCharacter).toList(),
				kanji.getStrokePaths());
	}

	static KanjiSummary toSummary(Kanji kanji) {
		return new KanjiSummary(
				kanji.getCharacter(),
				kanji.getOnyomi(),
				kanji.getKunyomi(),
				kanji.getMeanings(),
				kanji.getStrokeCount(),
				kanji.getGrade(),
				kanji.isJoyo(),
				kanji.getFrequency());
	}
}
