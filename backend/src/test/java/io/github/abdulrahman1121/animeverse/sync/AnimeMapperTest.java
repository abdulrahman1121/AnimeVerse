package io.github.abdulrahman1121.animeverse.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.abdulrahman1121.animeverse.anime.Anime;
import io.github.abdulrahman1121.animeverse.anime.AnimeFormat;
import io.github.abdulrahman1121.animeverse.anime.AnimeStatus;
import io.github.abdulrahman1121.animeverse.anime.DatePrecision;
import io.github.abdulrahman1121.animeverse.anime.Season;
import io.github.abdulrahman1121.animeverse.sync.AniListMedia.CoverImage;
import io.github.abdulrahman1121.animeverse.sync.AniListMedia.FuzzyDate;
import io.github.abdulrahman1121.animeverse.sync.AniListMedia.Title;

/** Pure unit tests: no Spring, no database, no Docker needed. */
class AnimeMapperTest {

	private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

	private static AniListMedia media(Integer id, Title title, String format, String status, String season,
			Integer episodes, Integer score, FuzzyDate start, FuzzyDate end) {
		return new AniListMedia(id, title, format, status, season, 2026, episodes, score,
				List.of("Comedy", "Slice of Life"), start, end, new CoverImage("https://img/cover.jpg"));
	}

	private static AniListMedia valid() {
		return media(1, new Title("Gelpiyo", "Gel Piyo"), "TV", "RELEASING", "FALL", 12, 74,
				new FuzzyDate(2026, 10, 4), new FuzzyDate(null, null, null));
	}

	@Test
	void mapsAFullShow() {
		Anime a = AnimeMapper.map(valid(), null, NOW);
		assertEquals(1, a.getAnilistId());
		assertEquals("Gelpiyo", a.getTitleRomaji());
		assertEquals("Gel Piyo", a.getTitleEnglish());
		assertEquals(AnimeFormat.TV, a.getFormat());
		assertEquals(AnimeStatus.RELEASING, a.getStatus());
		assertEquals(Season.FALL, a.getSeason());
		assertEquals((short) 2026, a.getSeasonYear());
		assertEquals((short) 12, a.getEpisodes());
		assertEquals((short) 74, a.getAverageScore());
		assertEquals(LocalDate.of(2026, 10, 4), a.getStartDate());
		assertEquals(DatePrecision.DAY, a.getStartPrecision());
		assertNull(a.getEndDate());
		assertNull(a.getEndPrecision());
		assertEquals("https://img/cover.jpg", a.getCoverImageUrl());
		assertEquals(NOW, a.getSyncedAt());
	}

	@Test
	void updatesExistingInPlace() {
		Anime existing = AnimeMapper.map(valid(), null, NOW);
		Instant later = NOW.plusSeconds(3 * 3600);
		AniListMedia changed = media(1, new Title("Gelpiyo", null), "TV", "FINISHED", "FALL", 12, 80,
				new FuzzyDate(2026, 10, 4), new FuzzyDate(2026, 12, 20));
		Anime result = AnimeMapper.map(changed, existing, later);
		assertSame(existing, result);
		assertEquals(AnimeStatus.FINISHED, result.getStatus());
		assertNull(result.getTitleEnglish());
		assertEquals((short) 80, result.getAverageScore());
		assertEquals(LocalDate.of(2026, 12, 20), result.getEndDate());
		assertEquals(later, result.getSyncedAt());
	}

	@Test
	void upcomingShowKeepsMonthPrecision() {
		AniListMedia m = media(2, new Title("Next", null), "TV", "NOT_YET_RELEASED", "WINTER", null, null,
				new FuzzyDate(2027, 1, null), null);
		Anime a = AnimeMapper.map(m, null, NOW);
		assertEquals(LocalDate.of(2027, 1, 1), a.getStartDate());
		assertEquals(DatePrecision.MONTH, a.getStartPrecision());
		assertNull(a.getEpisodes());
		assertNull(a.getAverageScore());
	}

	@Test
	void unknownFormatIsUnmappable() {
		AniListMedia m = media(3, new Title("X", null), "HOLOGRAM", "RELEASING", null, 1, 50, null, null);
		UnmappableMediaException e = assertThrows(UnmappableMediaException.class,
				() -> AnimeMapper.map(m, null, NOW));
		assertEquals(3, e.getAnilistId());
	}

	@Test
	void missingTitleOrFormatIsUnmappable() {
		assertThrows(UnmappableMediaException.class, () -> AnimeMapper.map(
				media(4, new Title(" ", "Eng"), "TV", "RELEASING", null, 1, 50, null, null), null, NOW));
		assertThrows(UnmappableMediaException.class, () -> AnimeMapper.map(
				media(5, null, "TV", "RELEASING", null, 1, 50, null, null), null, NOW));
		assertThrows(UnmappableMediaException.class, () -> AnimeMapper.map(
				media(6, new Title("X", null), null, "RELEASING", null, 1, 50, null, null), null, NOW));
	}

	@Test
	void zeroEpisodesAndZeroScoreBecomeNull() {
		Anime a = AnimeMapper.map(
				media(7, new Title("X", null), "TV", "RELEASING", null, 0, 0, null, null), null, NOW);
		assertNull(a.getEpisodes());
		assertNull(a.getAverageScore());
	}

	@Test
	void scoreOutOfRangeIsUnmappable() {
		assertThrows(UnmappableMediaException.class, () -> AnimeMapper.map(
				media(8, new Title("X", null), "TV", "RELEASING", null, 1, 140, null, null), null, NOW));
	}

	@Test
	void mismatchedExistingAnimeIsRejected() {
		Anime other = AnimeMapper.map(valid(), null, NOW);
		AniListMedia m = media(99, new Title("X", null), "TV", "RELEASING", null, 1, 50, null, null);
		assertThrows(IllegalArgumentException.class, () -> AnimeMapper.map(m, other, NOW));
	}

	@Test
	void genreNamesAreTrimmedDedupedAndNullSafe() {
		AniListMedia m = new AniListMedia(9, new Title("X", null), "TV", "RELEASING", null, null, null, null,
				java.util.Arrays.asList(" Comedy ", "Comedy", "", null, "Drama"), null, null, null);
		assertEquals(Set.of("Comedy", "Drama"), AnimeMapper.genreNames(m));
		AniListMedia none = new AniListMedia(10, new Title("X", null), "TV", "RELEASING", null, null, null,
				null, null, null, null, null);
		assertEquals(Set.of(), AnimeMapper.genreNames(none));
	}
}
