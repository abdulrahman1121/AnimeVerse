package io.github.abdulrahman1121.animeverse.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.abdulrahman1121.animeverse.anime.Anime;
import io.github.abdulrahman1121.animeverse.anime.AnimeRepository;
import io.github.abdulrahman1121.animeverse.anime.AnimeStatus;
import io.github.abdulrahman1121.animeverse.anime.Genre;
import io.github.abdulrahman1121.animeverse.anime.GenreRepository;
import io.github.abdulrahman1121.animeverse.sync.AniListMedia.FuzzyDate;
import io.github.abdulrahman1121.animeverse.sync.AniListMedia.Title;

/**
 * Integration test: the real saver against the real local Postgres (docker compose up -d first).
 * The test's own transaction is rolled back at the end, so nothing is left in the database.
 * Ids 999_1xx are far from real AniList ids, so real synced data can't collide.
 */
@SpringBootTest
@Transactional
class AnimePageSaverTest {

	private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

	@Autowired AnimePageSaver saver;
	@Autowired AnimeRepository animeRepository;
	@Autowired GenreRepository genreRepository;
	@Autowired EntityManager entityManager;

	private static AniListMedia show(int id, String format, String status, Integer score, List<String> genres) {
		return new AniListMedia(id, new Title("Test Show " + id, null), format, status, "FALL", 2026, 12, score,
				genres, new FuzzyDate(2026, 10, 4), null, null);
	}

	private static Set<String> genreNames(Anime anime) {
		return anime.getGenres().stream().map(Genre::getName).collect(Collectors.toSet());
	}

	private void flushAndForget() {
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	void insertsNewShowsWithTheirGenres() {
		AnimePageSaver.PageResult result = saver.save(List.of(
				show(999_101, "TV", "RELEASING", 80, List.of("Test Action", "Test Comedy")),
				show(999_102, "TV", "RELEASING", null, List.of("Test Comedy"))), NOW);
		flushAndForget();

		assertEquals(new AnimePageSaver.PageResult(2, 0), result);
		Anime first = animeRepository.findByAnilistId(999_101).orElseThrow();
		assertEquals(Set.of("Test Action", "Test Comedy"), genreNames(first));
		assertEquals(NOW, first.getSyncedAt());
		// the shared genre exists once, not once per show
		assertTrue(genreRepository.findByName("Test Comedy").isPresent());
	}

	@Test
	void secondSyncUpdatesInPlaceAndMirrorsGenres() {
		saver.save(List.of(show(999_103, "TV", "RELEASING", null, List.of("Test A", "Test B"))), NOW);
		flushAndForget();
		Long firstId = animeRepository.findByAnilistId(999_103).orElseThrow().getId();

		Instant later = NOW.plusSeconds(3 * 3600);
		saver.save(List.of(show(999_103, "TV", "FINISHED", 77, List.of("Test B", "Test C"))), later);
		flushAndForget();

		Anime updated = animeRepository.findByAnilistId(999_103).orElseThrow();
		assertEquals(firstId, updated.getId());                       // same row, not a duplicate
		assertEquals(AnimeStatus.FINISHED, updated.getStatus());
		assertEquals((short) 77, updated.getAverageScore());
		assertEquals(later, updated.getSyncedAt());
		assertEquals(Set.of("Test B", "Test C"), genreNames(updated)); // A unlinked, C added
		assertTrue(genreRepository.findByName("Test A").isPresent());  // the genre row itself stays
	}

	@Test
	void skipsUnmappableShowsAndSavesTheRest() {
		AnimePageSaver.PageResult result = saver.save(List.of(
				show(999_104, "HOLOGRAM", "RELEASING", 50, List.of("Test A")),
				show(999_105, "TV", "RELEASING", 50, List.of("Test A"))), NOW);
		flushAndForget();

		assertEquals(new AnimePageSaver.PageResult(1, 1), result);
		assertTrue(animeRepository.findByAnilistId(999_104).isEmpty());
		assertTrue(animeRepository.findByAnilistId(999_105).isPresent());
	}
}
