package io.github.abdulrahman1121.animeverse.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import io.github.abdulrahman1121.animeverse.anime.DatePrecision;
import io.github.abdulrahman1121.animeverse.sync.AniListMedia.FuzzyDate;

/**
 * A plain unit test: no Spring, no database, no network. It runs in milliseconds,
 * so pure logic like this should always be tested this way.
 */
class PartialDateTest {

	@Test
	void fullDateHasDayPrecision() {
		PartialDate result = PartialDate.from(new FuzzyDate(2026, 10, 4));
		assertEquals(LocalDate.of(2026, 10, 4), result.date());
		assertEquals(DatePrecision.DAY, result.precision());
	}

	@Test
	void missingDayRoundsToFirstOfMonth() {
		PartialDate result = PartialDate.from(new FuzzyDate(2027, 4, null));
		assertEquals(LocalDate.of(2027, 4, 1), result.date());
		assertEquals(DatePrecision.MONTH, result.precision());
	}

	@Test
	void yearOnlyRoundsToJanuaryFirst() {
		PartialDate result = PartialDate.from(new FuzzyDate(2027, null, null));
		assertEquals(LocalDate.of(2027, 1, 1), result.date());
		assertEquals(DatePrecision.YEAR, result.precision());
	}

	@Test
	void unknownYearMeansNoDate() {
		assertNull(PartialDate.from(new FuzzyDate(null, null, null)));
		assertNull(PartialDate.from(null));
	}
}
