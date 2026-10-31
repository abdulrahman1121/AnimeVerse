package io.github.abdulrahman1121.animeverse.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.EntityManager;

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
 * Integration test against the real local Postgres (docker compose up -d first). Your database
 * also holds real synced shows, so each test only looks at its own shows (titles start with
 * "Test Feed") and checks their order relative to each other. The transaction is rolled back at
 * the end, so nothing is left behind.
 */
@SpringBootTest
@Transactional
class FeedServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
	private static final String PREFIX = "Test Feed ";

	@Autowired FeedService feedService;
	@Autowired AnimeRepository animeRepository;
	@Autowired GenreRepository genreRepository;
	@Autowired AiringEpisodeRepository episodeRepository;
	@Autowired EntityManager entityManager;

	private Anime show(int anilistId, String name, AnimeStatus status) {
		return animeRepository.save(new Anime(anilistId, PREFIX + name, AnimeFormat.TV, status, NOW));
	}

	private void flushAndForget() {
		entityManager.flush();
		entityManager.clear();
	}

	private static List<AnimeCard> ours(List<AnimeCard> cards) {
		return cards.stream().filter(c -> c.titleRomaji().startsWith(PREFIX)).toList();
	}

	private static List<String> names(List<AnimeCard> cards) {
		return cards.stream().map(c -> c.titleRomaji().substring(PREFIX.length())).toList();
	}

	@Test
	void airingIsNewestPremiereFirstWithUndatedLastAndNoOtherStatus() {
		Anime older = show(999_301, "Older", AnimeStatus.RELEASING);
		older.setStart(LocalDate.of(2026, 10, 1), DatePrecision.DAY);
		Anime newer = show(999_302, "Newer", AnimeStatus.RELEASING);
		newer.setStart(LocalDate.of(2026, 10, 15), DatePrecision.DAY);
		newer.setEpisodes((short) 12);
		newer.setAverageScore((short) 80);
		newer.addGenre(genreRepository.save(new Genre(PREFIX + "Zeta")));
		newer.addGenre(genreRepository.save(new Genre(PREFIX + "Alpha")));
		show(999_303, "Undated", AnimeStatus.RELEASING);
		Anime paused = show(999_304, "Paused", AnimeStatus.HIATUS);
		paused.setStart(LocalDate.of(2026, 10, 20), DatePrecision.DAY);
		// Newer has aired episodes 1 and 2; episode 3 is still in the future
		episodeRepository.save(new AiringEpisode(newer, (short) 1, NOW.minusSeconds(14 * 86400)));
		episodeRepository.save(new AiringEpisode(newer, (short) 2, NOW.minusSeconds(7 * 86400)));
		episodeRepository.save(new AiringEpisode(newer, (short) 3, NOW.plusSeconds(7 * 86400)));
		flushAndForget();

		List<AnimeCard> cards = ours(feedService.airing(NOW));

		// Hiatus is hidden. Newest first. Undated last. Each show appears once, even with 2 genres.
		assertEquals(List.of("Newer", "Older", "Undated"), names(cards));
		AnimeCard newerCard = cards.get(0);
		assertEquals(List.of(PREFIX + "Alpha", PREFIX + "Zeta"), newerCard.genres());
		assertEquals("Ep 2 / 12", newerCard.episodes());
		assertEquals(80, newerCard.averageScore());
		assertEquals("Oct 15, 2026", newerCard.startDate());
		assertEquals(null, cards.get(1).episodes()); // Older: nothing aired, total unknown
	}

	@Test
	void finishedThisYearIsMostRecentlyEndedFirst() {
		Anime march = show(999_311, "March", AnimeStatus.FINISHED);
		march.setEnd(LocalDate.of(2026, 3, 1), DatePrecision.DAY);
		Anime may = show(999_312, "May", AnimeStatus.FINISHED);
		may.setEnd(LocalDate.of(2026, 5, 10), DatePrecision.DAY);
		Anime lastYear = show(999_313, "LastYear", AnimeStatus.FINISHED);
		lastYear.setEnd(LocalDate.of(2025, 12, 31), DatePrecision.DAY);
		show(999_314, "NoEndDate", AnimeStatus.FINISHED);
		flushAndForget();

		List<AnimeCard> cards = ours(feedService.finished(NOW));

		assertEquals(List.of("May", "March"), names(cards)); // 2025 and undated shows are left out
		assertEquals("May 10, 2026", cards.get(0).endDate());
	}

	@Test
	void upcomingIsSoonestFirstInsideTheNextThreeMonths() {
		// "now" is 2026-10-26, so the window is [2026-10-26, 2027-01-26)
		Anime later = show(999_321, "Later", AnimeStatus.NOT_YET_RELEASED);
		later.setStart(LocalDate.of(2026, 12, 1), DatePrecision.MONTH);
		Anime soon = show(999_322, "Soon", AnimeStatus.NOT_YET_RELEASED);
		soon.setStart(LocalDate.of(2026, 11, 1), DatePrecision.DAY);
		Anime tooFar = show(999_323, "TooFar", AnimeStatus.NOT_YET_RELEASED);
		tooFar.setStart(LocalDate.of(2027, 2, 1), DatePrecision.MONTH);
		Anime past = show(999_324, "Past", AnimeStatus.NOT_YET_RELEASED);
		past.setStart(LocalDate.of(2026, 10, 1), DatePrecision.DAY);
		show(999_325, "Undated", AnimeStatus.NOT_YET_RELEASED);
		Anime airing = show(999_326, "AlreadyAiring", AnimeStatus.RELEASING);
		airing.setStart(LocalDate.of(2026, 11, 5), DatePrecision.DAY);
		flushAndForget();

		List<AnimeCard> cards = ours(feedService.upcoming(NOW));

		assertEquals(List.of("Soon", "Later"), names(cards));
		assertEquals("Nov 1, 2026", cards.get(0).startDate());
		assertEquals("December 2026", cards.get(1).startDate());
	}
}
