package io.github.abdulrahman1121.animeverse.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.abdulrahman1121.animeverse.anime.AiringEpisode;
import io.github.abdulrahman1121.animeverse.anime.AiringEpisodeRepository;
import io.github.abdulrahman1121.animeverse.anime.Anime;
import io.github.abdulrahman1121.animeverse.anime.AnimeFormat;
import io.github.abdulrahman1121.animeverse.anime.AnimeRepository;
import io.github.abdulrahman1121.animeverse.anime.AnimeStatus;
import io.github.abdulrahman1121.animeverse.anime.DatePrecision;
import io.github.abdulrahman1121.animeverse.anime.Genre;
import io.github.abdulrahman1121.animeverse.anime.GenreRepository;

/**
 * Guards the batching decision: each feed section must cost exactly 2 SQL statements however many
 * cards it has. Hibernate counts every statement it sends to Postgres when "generate_statistics"
 * is on, so an accidental N+1 (one extra query per card) would push the count far above 2.
 *
 * The last test is a deliberate N+1 that must be counted. A guard you've never seen fail may not
 * be measuring anything, so this proves the counter really notices.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class FeedQueryCountTest {

	private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
	private static final int SHOWS_PER_SECTION = 30;

	@Autowired FeedService feedService;
	@Autowired AnimeRepository animeRepository;
	@Autowired GenreRepository genreRepository;
	@Autowired AiringEpisodeRepository episodeRepository;
	@Autowired EntityManager entityManager;

	private Statistics statistics;
	private final List<Integer> airingIds = new ArrayList<>();

	@BeforeEach
	void seed() {
		statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		List<Genre> genres = List.of(
				genreRepository.save(new Genre("Test Count A")), genreRepository.save(new Genre("Test Count B")),
				genreRepository.save(new Genre("Test Count C")), genreRepository.save(new Genre("Test Count D")));
		for (int i = 0; i < SHOWS_PER_SECTION; i++) {
			// Each show has 3 genres and 2 aired episodes, so both queries have real work to do.
			Anime airing = show(999_400 + i, AnimeStatus.RELEASING, genres);
			airing.setStart(LocalDate.of(2026, 10, 1), DatePrecision.DAY);
			airingIds.add(999_400 + i);
			Anime finished = show(999_430 + i, AnimeStatus.FINISHED, genres);
			finished.setEnd(LocalDate.of(2026, 6, 1), DatePrecision.DAY);
			Anime upcoming = show(999_460 + i, AnimeStatus.NOT_YET_RELEASED, genres);
			upcoming.setStart(LocalDate.of(2026, 11, 1), DatePrecision.DAY);
		}
		entityManager.flush();
		entityManager.clear(); // so the service reads from the database, not from memory
	}

	private Anime show(int anilistId, AnimeStatus status, List<Genre> genres) {
		Anime anime = animeRepository.save(new Anime(anilistId, "Test Count " + anilistId, AnimeFormat.TV, status, NOW));
		for (int g = 0; g < 3; g++) {
			anime.addGenre(genres.get((anilistId + g) % genres.size()));
		}
		episodeRepository.save(new AiringEpisode(anime, (short) 1, NOW.minusSeconds(14 * 86400)));
		episodeRepository.save(new AiringEpisode(anime, (short) 2, NOW.minusSeconds(7 * 86400)));
		return anime;
	}

	@Test
	void airingCostsTwoStatements() {
		statistics.clear();
		List<AnimeCard> cards = feedService.airing(NOW);
		assertTrue(cards.size() >= SHOWS_PER_SECTION, "expected at least our own shows, got " + cards.size());
		assertEquals(2L, statistics.getPrepareStatementCount());
	}

	@Test
	void finishedCostsTwoStatements() {
		statistics.clear();
		List<AnimeCard> cards = feedService.finished(NOW);
		assertTrue(cards.size() >= SHOWS_PER_SECTION, "expected at least our own shows, got " + cards.size());
		assertEquals(2L, statistics.getPrepareStatementCount());
	}

	@Test
	void upcomingCostsTwoStatements() {
		statistics.clear();
		List<AnimeCard> cards = feedService.upcoming(NOW);
		assertTrue(cards.size() >= SHOWS_PER_SECTION, "expected at least our own shows, got " + cards.size());
		assertEquals(2L, statistics.getPrepareStatementCount());
	}

	@Test
	void theCounterWouldCatchAnNPlusOne() {
		statistics.clear();
		// The per-card way: look up each show, then touch its genres. That is 1 + 1 statements per show.
		for (Integer anilistId : airingIds) {
			animeRepository.findByAnilistId(anilistId).orElseThrow().getGenres().size();
		}
		assertTrue(statistics.getPrepareStatementCount() >= 2L * SHOWS_PER_SECTION,
				"the per-card approach should be counted, got " + statistics.getPrepareStatementCount());
	}
}
