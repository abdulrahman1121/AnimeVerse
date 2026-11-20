package io.github.abdulrahman1121.animeverse.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import io.github.abdulrahman1121.animeverse.anime.AnimeFormat;
import io.github.abdulrahman1121.animeverse.anime.DatePrecision;
import io.github.abdulrahman1121.animeverse.anime.Season;

/** Pure unit tests: no Spring, no database. */
class CardLabelsTest {

	@Test
	void formatsReadNaturally() {
		assertEquals("TV", CardLabels.format(AnimeFormat.TV));
		assertEquals("TV Short", CardLabels.format(AnimeFormat.TV_SHORT));
		assertEquals("Movie", CardLabels.format(AnimeFormat.MOVIE));
		assertNull(CardLabels.format(null));
	}

	@Test
	void seasonLabelIncludesTheYearWhenKnown() {
		assertEquals("Fall 2026", CardLabels.season(Season.FALL, (short) 2026));
		assertEquals("Winter", CardLabels.season(Season.WINTER, null));
		assertNull(CardLabels.season(null, (short) 2026));
	}

	@Test
	void dateLabelMatchesThePrecision() {
		assertEquals("Oct 4, 2026", CardLabels.date(LocalDate.of(2026, 10, 4), DatePrecision.DAY));
		assertEquals("April 2027", CardLabels.date(LocalDate.of(2027, 4, 1), DatePrecision.MONTH));
		assertEquals("2027", CardLabels.date(LocalDate.of(2027, 1, 1), DatePrecision.YEAR));
		assertNull(CardLabels.date(null, null));
	}

	@Test
	void episodeLabelWithAndWithoutATotal() {
		assertEquals("Ep 4 / 12", CardLabels.episodes((short) 4, (short) 12));
		assertEquals("Ep 1168 / ?", CardLabels.episodes((short) 1168, null));
	}

	@Test
	void episodeLabelWhenNothingHasAiredYet() {
		assertEquals("12 episodes", CardLabels.episodes(null, (short) 12));
		assertNull(CardLabels.episodes(null, null));
	}

	@Test
	void airedCountIsCappedAtTheTotal() {
		assertEquals("Ep 12 / 12", CardLabels.episodes((short) 13, (short) 12));
	}
}
