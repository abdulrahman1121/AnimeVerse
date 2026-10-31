package io.github.abdulrahman1121.animeverse.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/** Unit test with hand-written fakes: no Spring, no database, no network. */
class EpisodeSyncServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

	static class FakeClient extends AniListClient {
		List<AniListAiringSchedule> rows = List.of();
		Instant from;
		Instant to;

		FakeClient() {
			super("http://unused.invalid");
		}

		@Override public List<AniListAiringSchedule> fetchAiringBetween(Instant from, Instant to) {
			this.from = from;
			this.to = to;
			return rows;
		}
	}

	static class FakeSaver extends AiringEpisodePageSaver {
		final List<Integer> pageSizes = new ArrayList<>();
		int failOnCall = -1; // 1-based

		FakeSaver() {
			super(null, null);
		}

		@Override public PageResult save(List<AniListAiringSchedule> page) {
			pageSizes.add(page.size());
			if (pageSizes.size() == failOnCall) {
				throw new IllegalStateException("simulated database failure");
			}
			return new PageResult(page.size() - 1, 1); // pretend one row per page is an untracked show
		}
	}

	private static List<AniListAiringSchedule> rows(int n) {
		return IntStream.rangeClosed(1, n).mapToObj(i -> new AniListAiringSchedule(i, 1_700_000_000L + i, 100)).toList();
	}

	@Test
	void fetchesTheWholeYearPlusTwoWeeksAndSavesInPagesOfFifty() {
		FakeClient client = new FakeClient();
		FakeSaver saver = new FakeSaver();
		client.rows = rows(120);

		EpisodeSyncService.SyncReport report = new EpisodeSyncService(client, saver).syncEpisodes(NOW);

		assertEquals(Instant.parse("2025-12-31T23:59:59Z"), client.from);
		assertEquals(Instant.parse("2026-11-09T12:00:00Z"), client.to);
		assertEquals(List.of(50, 50, 20), saver.pageSizes);
		assertEquals(new EpisodeSyncService.SyncReport(120, 117, 3, 0), report);
	}

	@Test
	void aFailedPageIsCountedAndTheNextPageStillRuns() {
		FakeClient client = new FakeClient();
		FakeSaver saver = new FakeSaver();
		saver.failOnCall = 2;
		client.rows = rows(120);

		EpisodeSyncService.SyncReport report = new EpisodeSyncService(client, saver).syncEpisodes(NOW);

		assertEquals(List.of(50, 50, 20), saver.pageSizes);
		assertEquals(new EpisodeSyncService.SyncReport(120, 68, 2, 1), report);
	}
}
