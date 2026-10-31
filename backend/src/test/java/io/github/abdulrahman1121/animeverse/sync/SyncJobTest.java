package io.github.abdulrahman1121.animeverse.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Unit test with hand-written fakes: no Spring, no timer, no database, no network. */
class SyncJobTest {

	static class FakeAnimeSync extends AnimeSyncService {
		final List<String> calls;
		boolean fail;

		FakeAnimeSync(List<String> calls) {
			super(null, null);
			this.calls = calls;
		}

		@Override public SyncReport syncAnime() {
			calls.add("shows");
			if (fail) {
				throw new AniListException("simulated AniList outage");
			}
			return new SyncReport(0, 0, 0, 0);
		}
	}

	static class FakeEpisodeSync extends EpisodeSyncService {
		final List<String> calls;
		boolean fail;

		FakeEpisodeSync(List<String> calls) {
			super(null, null);
			this.calls = calls;
		}

		@Override public SyncReport syncEpisodes() {
			calls.add("episodes");
			if (fail) {
				throw new IllegalStateException("simulated failure");
			}
			return new SyncReport(0, 0, 0, 0, 0);
		}
	}

	@Test
	void runsShowsThenEpisodes() {
		List<String> calls = new ArrayList<>();
		new SyncJob(new FakeAnimeSync(calls), new FakeEpisodeSync(calls)).run();
		assertEquals(List.of("shows", "episodes"), calls);
	}

	@Test
	void skipsEpisodesWhenTheShowSyncFails() {
		List<String> calls = new ArrayList<>();
		FakeAnimeSync shows = new FakeAnimeSync(calls);
		shows.fail = true;
		new SyncJob(shows, new FakeEpisodeSync(calls)).run(); // must not throw
		assertEquals(List.of("shows"), calls);
	}

	@Test
	void anEpisodeSyncFailureDoesNotEscapeTheJob() {
		List<String> calls = new ArrayList<>();
		FakeEpisodeSync episodes = new FakeEpisodeSync(calls);
		episodes.fail = true;
		new SyncJob(new FakeAnimeSync(calls), episodes).run(); // must not throw
		assertEquals(List.of("shows", "episodes"), calls);
	}
}
