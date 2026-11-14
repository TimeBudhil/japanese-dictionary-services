package baddee.japanese_dictionary_services.vocabulary.integration;

import java.util.List;
import java.util.Set;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import baddee.japanese_dictionary_services.vocabulary.MatchTier;
import baddee.japanese_dictionary_services.vocabulary.VocabCandidate;

/**
 * Searches the {@code vocab} index for candidates and tags each with a {@link MatchTier}.
 * Read-only: it only calls {@code search}.
 *
 * <p>One {@code bool} query with three named {@code should} clauses:
 * <ul>
 * <li>{@code exact}: {@code term} on {@code kanji} or {@code readings}, constant score 1000.</li>
 * <li>{@code prefix}: {@code prefix} on {@code kanji} or {@code readings}, constant score 100.</li>
 * <li>{@code partial}: {@code match} on {@code glosses} (all words), normal BM25 score.</li>
 * </ul>
 * The tier comes from the hit's matched query names (best one wins), so it is always right.
 * The <b>order</b> is by score, then {@code common}: the large constant scores keep exact
 * before prefix before gloss hits in practice, but a very high gloss score could in theory pass
 * a prefix hit. The {@code search} module re-ranks by tier anyway (D17).
 *
 * <p>Japanese fields are {@code keyword} today, so matching is exact characters only. Better
 * Japanese matching waits for kuromoji/ICU fields in the updater (X2, D14).
 */
@Component
public class VocabSearchClient {

	static final String EXACT = "exact";
	static final String PREFIX = "prefix";
	static final String PARTIAL = "partial";

	private static final float EXACT_BOOST = 1000f;
	private static final float PREFIX_BOOST = 100f;

	/** Elasticsearch's default {@code index.max_result_window}; the updater does not change it. */
	static final int MAX_RESULT_WINDOW = 10_000;

	private final ElasticsearchOperations operations;

	public VocabSearchClient(ElasticsearchOperations operations) {
		this.operations = operations;
	}

	/**
	 * Candidates for {@code query}, one page at a time. A blank query returns an empty page.
	 *
	 * @throws VocabPageTooDeepException if the page ends past the first 10,000 hits
	 */
	public Page<VocabCandidate> findCandidates(String query, Pageable pageable) {
		if (pageable.getOffset() + pageable.getPageSize() > MAX_RESULT_WINDOW) {
			throw new VocabPageTooDeepException(MAX_RESULT_WINDOW);
		}
		String text = query == null ? "" : query.strip();
		if (text.isEmpty()) {
			return Page.empty(pageable);
		}

		NativeQuery request = NativeQuery.builder()
				.withQuery(candidateQuery(text))
				.withSort(
						SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))),
						SortOptions.of(s -> s.field(f -> f.field(VocabDocument.COMMON).order(SortOrder.Desc))))
				.withPageable(pageable)
				.withTrackTotalHits(true)
				.build();

		SearchHits<VocabDocument> hits = operations.search(request, VocabDocument.class);
		List<VocabCandidate> candidates = hits.getSearchHits().stream()
				.map(VocabSearchClient::toCandidate)
				.toList();
		return new PageImpl<>(candidates, pageable, hits.getTotalHits());
	}

	static Query candidateQuery(String text) {
		return Query.of(q -> q.bool(b -> b
				.should(s -> s.constantScore(c -> c
						.queryName(EXACT)
						.boost(EXACT_BOOST)
						.filter(f -> f.bool(o -> o
								.should(t -> t.term(term -> term.field(VocabDocument.KANJI).value(text)))
								.should(t -> t.term(term -> term.field(VocabDocument.READINGS).value(text)))))))
				.should(s -> s.constantScore(c -> c
						.queryName(PREFIX)
						.boost(PREFIX_BOOST)
						.filter(f -> f.bool(o -> o
								.should(p -> p.prefix(pre -> pre.field(VocabDocument.KANJI).value(text)))
								.should(p -> p.prefix(pre -> pre.field(VocabDocument.READINGS).value(text)))))))
				.should(s -> s.match(m -> m
						.queryName(PARTIAL)
						.field(VocabDocument.GLOSSES)
						.query(text)
						.operator(Operator.And)))
				.minimumShouldMatch("1")));
	}

	private static VocabCandidate toCandidate(SearchHit<VocabDocument> hit) {
		return new VocabCandidate(
				Long.parseLong(hit.getId()),
				tierOf(hit.getMatchedQueries().keySet()),
				hit.getContent().isCommon());
	}

	/** The best tier among the named clauses a hit matched. */
	static MatchTier tierOf(Set<String> matchedQueries) {
		if (matchedQueries.contains(EXACT)) {
			return MatchTier.EXACT;
		}
		if (matchedQueries.contains(PREFIX)) {
			return MatchTier.PREFIX;
		}
		return MatchTier.PARTIAL;
	}
}
