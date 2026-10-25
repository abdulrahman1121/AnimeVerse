package io.github.abdulrahman1121.animeverse.anime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs against the real local Postgres (docker compose up -d first).
 * @Transactional rolls every test back when it ends, so nothing is left in the database.
 */
@SpringBootTest
@Transactional
class AnimeRepositoryTest {

	@Autowired AnimeRepository animeRepository;
	@Autowired GenreRepository genreRepository;
	@Autowired AiringEpisodeRepository episodeRepository;
	@Autowired EntityManager entityManager;

	@Test
	void savesAnimeWithGenresAndCountsAiredEpisodes() {
		Instant now = Instant.now();
		Genre action = genreRepository.save(new Genre("Test Action"));
		Genre fantasy = genreRepository.save(new Genre("Test Fantasy"));

		Anime anime = new Anime(999_001, "Test Starfall Courier", AnimeFormat.TV, AnimeStatus.RELEASING, now);
		anime.setStart(LocalDate.of(2026, 10, 4), DatePrecision.DAY);
		anime.addGenre(action);
		anime.addGenre(fantasy);
		animeRepository.save(anime);

		// 3 episodes already aired, 1 in the future
		for (short ep = 1; ep <= 3; ep++) {
			episodeRepository.save(new AiringEpisode(anime, ep, now.minus(Duration.ofDays(7L * (4 - ep)))));
		}
		episodeRepository.save(new AiringEpisode(anime, (short) 4, now.plus(Duration.ofDays(7))));

		// Send the SQL to Postgres and forget the cached objects, so the reads below hit the DB for real.
		entityManager.flush();
		entityManager.clear();

		Anime loaded = animeRepository.findByAnilistId(999_001).orElseThrow();
		Set<String> genreNames = loaded.getGenres().stream().map(Genre::getName).collect(Collectors.toSet());
		assertEquals(Set.of("Test Action", "Test Fantasy"), genreNames);
		assertEquals(3, episodeRepository.countByAnimeIdAndAiringAtLessThanEqual(loaded.getId(), now));
	}

	@Test
	void deletingAnimeRemovesItsEpisodesAndGenreLinksButKeepsGenres() {
		Genre action = genreRepository.save(new Genre("Test Action"));
		Anime anime = new Anime(999_002, "Test Iron Lotus", AnimeFormat.TV, AnimeStatus.FINISHED, Instant.now());
		anime.addGenre(action);
		animeRepository.save(anime);
		episodeRepository.save(new AiringEpisode(anime, (short) 1, Instant.now()));
		entityManager.flush();
		entityManager.clear();

		Anime loaded = animeRepository.findByAnilistId(999_002).orElseThrow();
		Long animeId = loaded.getId();
		animeRepository.delete(loaded);
		entityManager.flush();

		assertTrue(animeRepository.findByAnilistId(999_002).isEmpty());
		// ON DELETE CASCADE removed the episode (count only this show's rows, so real synced data can't interfere)
		assertEquals(0, episodeRepository.countByAnimeIdAndAiringAtLessThanEqual(animeId, Instant.parse("2100-01-01T00:00:00Z")));
		assertTrue(genreRepository.findByName("Test Action").isPresent()); // the genre itself stays
	}
}
