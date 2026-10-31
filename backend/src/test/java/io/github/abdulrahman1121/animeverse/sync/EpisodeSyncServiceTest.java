package io.github.abdulrahman1121.animeverse.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/** Unit test with hand-written fakes: no Spring, no database, no network. */
class EpisodeSyncServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
	// Jan 1 to Nov 9 12:00 is 312.5 days, so 30-day windows give 11 of them
	private static final int WINDOWS = 11;

	static class FakeClient extends AniListClient {
		final List<Instant[]> calls = new ArrayList<>();            // {from, to} for each fetch
		final Map<Integer, List<AniListAiringSchedule>> rowsByCall = new HashMap<>(); // 0-based call number
		int failOnCall = -1;                                         // 0-based call that should throw

		FakeClient() {
			super("http://unused.invalid");
		}

		@Override public List<AniListAiringSchedule> fetchAiringBetween(Instant from, Instant to) {
			int call = calls.size();
			calls.add(new Instant[] { from, to });
			if (call == failOnCall) {
				throw new AniListException("simulated AniList failure");
			}
			return rowsByCall.getOrDefault(call, List.of());
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
	void windowsCoverTheYearWithoutGapsOrOverlap() {
		FakeClient client = new FakeClient();
		new EpisodeSyncService(client, new FakeSaver()).syncEpisodes(NOW);

		assertEquals(WINDOWS, client.calls.size());
		// the first window starts one second before Jan 1 (AniList's bound is exclusive)
		assertEquals(Instant.parse("2025-12-31T23:59:59Z"), client.calls.get(0)[0]);
		// the last window ends at now + 14 days
		assertEquals(Instant.parse("2026-11-09T12:00:00Z"), client.calls.get(WINDOWS - 1)[1]);
		for (int i = 1; i < WINDOWS; i++) {
			// each window starts (minus the one-second nudge) exactly where the previous one ended
			assertEquals(client.calls.get(i - 1)[1], client.calls.get(i)[0].plusSeconds(1));
			// and no window is longer than 30 days (plus the nudge)
			assertEquals(true, client.calls.get(i)[1].isBefore(client.calls.get(i)[0].plusSeconds(30L * 86400 + 2)));
		}
	}

	@Test
	void eachWindowIsSavedInPagesOfFifty() {
		FakeClient client = new FakeClient();
		FakeSaver saver = new FakeSaver();
		client.rowsByCall.put(0, rows(120));
		client.rowsByCall.put(1, rows(10));

		EpisodeSyncService.SyncReport report = new EpisodeSyncService(client, saver).syncEpisodes(NOW);

		assertEquals(List.of(50, 50, 20, 10), saver.pageSizes);
		assertEquals(new EpisodeSyncService.SyncReport(130, 126, 4, 0, 0), report);
	}

	@Test
	void aFailedPageIsCountedAndTheNextPageStillRuns() {
		FakeClient client = new FakeClient();
		FakeSaver saver = new FakeSaver();
		saver.failOnCall = 2;
		client.rowsByCall.put(0, rows(120));

		EpisodeSyncService.SyncReport report = new EpisodeSyncService(client, saver).syncEpisodes(NOW);

		assertEquals(List.of(50, 50, 20), saver.pageSizes);
		assertEquals(new EpisodeSyncService.SyncReport(120, 68, 2, 1, 0), report);
	}

	@Test
	void aFailedWindowDownloadIsCountedAndTheOtherWindowsStillRun() {
		FakeClient client = new FakeClient();
		FakeSaver saver = new FakeSaver();
		client.failOnCall = 2;               // the third window's download fails
		client.rowsByCall.put(0, rows(10));
		client.rowsByCall.put(5, rows(10));

		EpisodeSyncService.SyncReport report = new EpisodeSyncService(client, saver).syncEpisodes(NOW);

		assertEquals(WINDOWS, client.calls.size());             // every window was still attempted
		assertEquals(new EpisodeSyncService.SyncReport(20, 18, 2, 0, 1), report);
	}
}
