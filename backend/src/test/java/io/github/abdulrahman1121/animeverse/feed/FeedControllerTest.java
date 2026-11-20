package io.github.abdulrahman1121.animeverse.feed;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests the URLs and the JSON. MockMvc calls the controller the way a browser would, but with no
 * server, no Spring context and no database: the service is replaced by a hand-written fake.
 */
class FeedControllerTest {

	private static final AnimeCard AIRING = new AnimeCard(412L, "Gelpiyo", "Gel Piyo", "https://img/cover.jpg", "TV",
			"Fall 2026", List.of("Comedy", "Slice of Life"), "Ep 4 / 12", null, "Oct 4, 2026", null);
	private static final AnimeCard FINISHED = new AnimeCard(7L, "Done Show", null, null, "Movie",
			null, List.of(), "Ep 1 / 1", 81, "Mar 1, 2026", "May 10, 2026");
	private static final AnimeCard UPCOMING = new AnimeCard(9L, "Next Show", null, null, "ONA",
			"Winter 2027", List.of("Action"), null, null, "January 2027", null);

	/** Extends the real service but answers from memory. The repositories are never touched, so null is fine. */
	static class FakeFeedService extends FeedService {
		FakeFeedService() {
			super(null, null);
		}

		@Override public List<AnimeCard> airing() { return List.of(AIRING); }
		@Override public List<AnimeCard> finished() { return List.of(FINISHED); }
		@Override public List<AnimeCard> upcoming() { return List.of(UPCOMING); }
	}

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new FeedController(new FakeFeedService())).build();
	}

	@Test
	void airingReturnsTheCardsAsJson() throws Exception {
		mvc.perform(get("/api/anime/airing"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(412))
				.andExpect(jsonPath("$[0].titleRomaji").value("Gelpiyo"))
				.andExpect(jsonPath("$[0].genres[1]").value("Slice of Life"))
				.andExpect(jsonPath("$[0].episodes").value("Ep 4 / 12"))
				.andExpect(jsonPath("$[0].startDate").value("Oct 4, 2026"))
				.andExpect(jsonPath("$[0].averageScore").isEmpty()); // null: React shows NEW
	}

	@Test
	void finishedAndUpcomingHaveTheirOwnUrls() throws Exception {
		mvc.perform(get("/api/anime/finished"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].titleRomaji").value("Done Show"))
				.andExpect(jsonPath("$[0].endDate").value("May 10, 2026"))
				.andExpect(jsonPath("$[0].averageScore").value(81));
		mvc.perform(get("/api/anime/upcoming"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].titleRomaji").value("Next Show"))
				.andExpect(jsonPath("$[0].startDate").value("January 2027"));
	}

	@Test
	void internalFieldsNeverReachTheFrontend() throws Exception {
		mvc.perform(get("/api/anime/airing"))
				.andExpect(jsonPath("$[0].anilistId").doesNotExist())
				.andExpect(jsonPath("$[0].syncedAt").doesNotExist());
	}

	@Test
	void unknownUrlIsNotFound() throws Exception {
		mvc.perform(get("/api/anime/everything")).andExpect(status().isNotFound());
	}
}
