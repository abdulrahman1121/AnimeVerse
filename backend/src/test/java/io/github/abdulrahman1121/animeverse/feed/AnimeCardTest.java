package io.github.abdulrahman1121.animeverse.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.abdulrahman1121.animeverse.anime.Anime;
import io.github.abdulrahman1121.animeverse.anime.AnimeFormat;
import io.github.abdulrahman1121.animeverse.anime.AnimeStatus;
import io.github.abdulrahman1121.animeverse.anime.DatePrecision;
import io.github.abdulrahman1121.animeverse.anime.Genre;
import io.github.abdulrahman1121.animeverse.anime.Season;

/** Pure unit test: builds an Anime in memory and checks the card made from it. */
class AnimeCardTest {

	/** The id is database-generated and has no setter, so a test sets it by reflection. */
	private static Anime withId(Anime anime, long id) {
		try {
			Field field = Anime.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(anime, id);
			return anime;
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void buildsACardFromAnAiringShow() {
		Anime anime = withId(new Anime(189123, "Gelpiyo", AnimeFormat.TV, AnimeStatus.RELEASING, Instant.now()), 412L);
		anime.setTitleEnglish("Gel Piyo");
		anime.setCoverImageUrl("https://img/cover.jpg");
		anime.setSeason(Season.FALL, (short) 2026);
		anime.setEpisodes((short) 12);
		anime.setAverageScore((short) 74);
		anime.setStart(LocalDate.of(2026, 10, 4), DatePrecision.DAY);
		anime.addGenre(new Genre("Slice of Life"));
		anime.addGenre(new Genre("Comedy"));

		AnimeCard card = AnimeCard.from(anime, (short) 4);

		assertEquals(new AnimeCard(412L, "Gelpiyo", "Gel Piyo", "https://img/cover.jpg", "TV", "Fall 2026",
				List.of("Comedy", "Slice of Life"), "Ep 4 / 12", 74, "Oct 4, 2026", null), card);
	}

	@Test
	void unratedUpcomingShowHasNullsWhereThereIsNothingToShow() {
		Anime anime = withId(new Anime(2, "Next Show", AnimeFormat.MOVIE, AnimeStatus.NOT_YET_RELEASED, Instant.now()), 7L);
		anime.setStart(LocalDate.of(2027, 1, 1), DatePrecision.MONTH);

		AnimeCard card = AnimeCard.from(anime, null);

		assertNull(card.averageScore());     // React shows NEW
		assertNull(card.episodes());
		assertNull(card.season());
		assertNull(card.endDate());
		assertEquals("January 2027", card.startDate());
		assertEquals(List.of(), card.genres());
	}
}
