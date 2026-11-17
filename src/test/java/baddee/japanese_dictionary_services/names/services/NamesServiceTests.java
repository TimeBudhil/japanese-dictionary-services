package baddee.japanese_dictionary_services.names.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import baddee.japanese_dictionary_services.names.NameCandidate;
import baddee.japanese_dictionary_services.names.NameMatchTier;
import baddee.japanese_dictionary_services.names.repository.NameHit;
import baddee.japanese_dictionary_services.names.repository.NameRepository;

/** Plain unit tests for what {@link NamesService} adds around the repository's query. */
class NamesServiceTests {

	private final NameRepository repository = mock(NameRepository.class);
	private final NamesService service = new NamesService(repository);

	@Test
	void patternIsWordStartWithLikeWildcardsEscaped() {
		assertThat(NamesService.wordStartPattern("田中")).isEqualTo("% 田中%");
		assertThat(NamesService.wordStartPattern("a%b_c\\d")).isEqualTo("% a\\%b\\_c\\\\d%");
	}

	@Test
	void candidatesMapTierNumbersAndStripTheQueryAndIgnoreSort() {
		given(repository.findActiveHits(anyString(), anyString(), any())).willReturn(new PageImpl<>(
				List.of(hit(5543705, 0), hit(5543762, 1), hit(5000001, 2)), PageRequest.of(1, 3), 9));

		var page = service.findCandidates("  田中 ", PageRequest.of(1, 3, Sort.by("entSeq")));

		verify(repository).findActiveHits("田中", "% 田中%", PageRequest.of(1, 3));
		assertThat(page.getContent()).containsExactly(
				new NameCandidate(5543705, NameMatchTier.EXACT),
				new NameCandidate(5543762, NameMatchTier.PREFIX),
				new NameCandidate(5000001, NameMatchTier.PARTIAL));
		assertThat(page.getTotalElements()).isEqualTo(9);
	}

	@Test
	void blankQueryIsEmptyWithoutQuerying() {
		assertThat(service.findCandidates(" ", PageRequest.of(0, 20))).isEmpty();
		assertThat(service.findCandidates(null, PageRequest.of(0, 20))).isEmpty();
		verifyNoInteractions(repository);
	}

	private static NameHit hit(long entSeq, int tier) {
		return new NameHit() {
			@Override
			public long getEntSeq() {
				return entSeq;
			}

			@Override
			public int getTier() {
				return tier;
			}
		};
	}
}
