package io.github.abdulrahman1121.animeverse.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.abdulrahman1121.animeverse.anime.AiringEpisodeRepository;
import io.github.abdulrahman1121.animeverse.anime.Anime;
import io.github.abdulrahman1121.animeverse.anime.AnimeFormat;
import io.github.abdulrahman1121.animeverse.anime.AnimeRepository;
import io.github.abdulrahman1121.animeverse.anime.AnimeStatus;

/**
 * Integration test against the real local Postgres (docker compose up -d first). The test's
 * transaction is rolled back at the end. Ids 999_2xx can't collide with real AniList data.
 */
@SpringBootTest
@Transactional
class AiringEpisodePageSaverTest {

	private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
	private static final long WEEK = 7 * 24 * 3600L;

	@Autowired AiringEpisodePageSaver saver;
	@Autowired AnimeRepository animeRepository;
	@Autowired AiringEpisodeRepository episodeRepository;
	@Autowired EntityManager entityManager;

	private Anime trackedShow(int anilistId) {
		return animeRepository.save(new Anime(anilistId, "Test Show " + anilistId, AnimeFormat.TV,
				AnimeStatus.RELEASING, NOW));
	}

	private void flushAndForget() {
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	void savesEpisodesOfTrackedShowsAndSkipsOthers() {
		Anime show = trackedShow(999_201);
		long t = NOW.getEpochSecond();

		AiringEpisodePageSaver.PageResult result = saver.save(List.of(
				new AniListAiringSchedule(1, t - 2 * WEEK, 999_201),
				new AniListAiringSchedule(2, t - WEEK, 999_201),
				new AniListAiringSchedule(3, t + WEEK, 999_201),          // not aired yet
				new AniListAiringSchedule(1, t - WEEK, 999_299),          // a show we don't track
				new AniListAiringSchedule(null, t, 999_201)));            // incomplete row
		flushAndForget();

		assertEquals(new AiringEpisodePageSaver.PageResult(3, 2), result);
		// "Ep X" = highest episode that has aired by now: 2, even though episode 3 is stored
		assertEquals((short) 2, episodeRepository.findMaxAiredEpisode(show.getId(), NOW).orElseThrow());
	}

	@Test
	void aRescheduledEpisodeIsUpdatedNotDuplicated() {
		Anime show = trackedShow(999_202);
		long t = NOW.getEpochSecond();
		saver.save(List.of(new AniListAiringSchedule(5, t + WEEK, 999_202)));
		flushAndForget();

		// AniList moves episode 5 a week earlier, so by now it has aired
		saver.save(List.of(new AniListAiringSchedule(5, t - 60, 999_202)));
		flushAndForget();

		Anime reloaded = animeRepository.findByAnilistId(999_202).orElseThrow();
		assertEquals(Instant.ofEpochSecond(t - 60),
				episodeRepository.findByAnimeAndEpisode(reloaded, (short) 5).orElseThrow().getAiringAt());
		assertEquals((short) 5, episodeRepository.findMaxAiredEpisode(show.getId(), NOW).orElseThrow());
	}

	@Test
	void noAiredEpisodeMeansNoMax() {
		Anime show = trackedShow(999_203);
		saver.save(List.of(new AniListAiringSchedule(1, NOW.getEpochSecond() + WEEK, 999_203)));
		flushAndForget();

		assertTrue(episodeRepository.findMaxAiredEpisode(show.getId(), NOW).isEmpty());
	}
}
