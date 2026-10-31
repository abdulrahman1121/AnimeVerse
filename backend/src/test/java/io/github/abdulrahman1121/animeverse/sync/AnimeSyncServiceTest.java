package io.github.abdulrahman1121.animeverse.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.abdulrahman1121.animeverse.sync.AniListMedia.FuzzyDate;
import io.github.abdulrahman1121.animeverse.sync.AniListMedia.Title;

/**
 * Unit test with hand-written fakes: no Spring, no database, no network. The fakes extend the
 * real classes and replace only the methods the service calls.
 */
class AnimeSyncServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

	private static AniListMedia show(int id, String status, FuzzyDate start) {
		return new AniListMedia(id, new Title("Show " + id, null), "TV", status, null, null, 12, 70,
				List.of("Action"), start, null, null);
	}

	static class FakeClient extends AniListClient {
		List<AniListMedia> releasing = List.of();
		List<AniListMedia> finished = List.of();
		List<AniListMedia> upcoming = List.of();
		LocalDate finishedAfter;
		LocalDate upcomingAfter;
		LocalDate upcomingBefore;

		FakeClient() {
			super("http://unused.invalid");
		}

		@Override public List<AniListMedia> fetchReleasing() { return releasing; }

		@Override public List<AniListMedia> fetchFinishedAfter(LocalDate date) {
			finishedAfter = date;
			return finished;
		}

		@Override public List<AniListMedia> fetchUpcomingBetween(LocalDate after, LocalDate before) {
			upcomingAfter = after;
			upcomingBefore = before;
			return upcoming;
		}
	}

	static class FakeSaver extends AnimePageSaver {
		final List<List<Integer>> pages = new ArrayList<>();
		int failOnCall = -1; // 1-based call number that should throw

		FakeSaver() {
			super(null, null);
		}

		@Override public PageResult save(List<AniListMedia> page, Instant syncedAt) {
			pages.add(page.stream().map(AniListMedia::id).toList());
			if (pages.size() == failOnCall) {
				throw new IllegalStateException("simulated database failure");
			}
			return new PageResult(page.size(), 0);
		}
	}

	@Test
	void splitsIntoPagesOfFiftyAndUsesTheRightDateWindows() {
		FakeClient client = new FakeClient();
		FakeSaver saver = new FakeSaver();
		client.releasing = IntStream.rangeClosed(1, 120).mapToObj(i -> show(i, "RELEASING", null)).toList();

		AnimeSyncService.SyncReport report = new AnimeSyncService(client, saver).syncAnime(NOW);

		assertEquals(List.of(50, 50, 20), saver.pages.stream().map(List::size).toList());
		assertEquals(new AnimeSyncService.SyncReport(120, 120, 0, 0), report);
		assertEquals(LocalDate.of(2025, 12, 31), client.finishedAfter);   // YEAR_START minus one day
		assertEquals(LocalDate.of(2026, 10, 25), client.upcomingAfter);   // yesterday
		assertEquals(LocalDate.of(2027, 1, 26), client.upcomingBefore);   // today plus 3 months
	}

	@Test
	void dedupesById_firstListWins_andDropsUndatedUpcoming() {
		FakeClient client = new FakeClient();
		FakeSaver saver = new FakeSaver();
		client.releasing = List.of(show(1, "RELEASING", null));
		client.finished = List.of(show(1, "FINISHED", null), show(2, "FINISHED", null));
		client.upcoming = List.of(
				show(3, "NOT_YET_RELEASED", new FuzzyDate(2027, 1, null)),
				show(4, "NOT_YET_RELEASED", new FuzzyDate(null, null, null)),
				show(5, "NOT_YET_RELEASED", null));

		AnimeSyncService.SyncReport report = new AnimeSyncService(client, saver).syncAnime(NOW);

		assertEquals(List.of(List.of(1, 2, 3)), saver.pages);
		assertEquals(3, report.fetched());
	}

	@Test
	void aFailedPageIsCountedAndTheNextPageStillRuns() {
		FakeClient client = new FakeClient();
		FakeSaver saver = new FakeSaver();
		saver.failOnCall = 1;
		client.releasing = IntStream.rangeClosed(1, 60).mapToObj(i -> show(i, "RELEASING", null)).toList();

		AnimeSyncService.SyncReport report = new AnimeSyncService(client, saver).syncAnime(NOW);

		assertEquals(2, saver.pages.size());
		assertEquals(new AnimeSyncService.SyncReport(60, 10, 0, 1), report);
	}
}
