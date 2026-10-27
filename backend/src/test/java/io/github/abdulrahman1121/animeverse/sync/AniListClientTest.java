package io.github.abdulrahman1121.animeverse.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

/**
 * Tests the client against a fake AniList: MockRestServiceServer intercepts the HTTP call, checks
 * what we sent, and replies with the JSON we script. No network, no Spring context, no waiting.
 */
class AniListClientTest {

	private static final String URL = "https://anilist.test/graphql";

	private final List<Duration> sleeps = new ArrayList<>();
	private MockRestServiceServer server;
	private AniListClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new AniListClient(builder, URL, sleeps::add); // "sleeping" just records the duration
	}

	@Test
	void walksThroughEveryPageAndMapsFields() {
		server.expect(requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(bodyContains("\"page\":1"))
				.andExpect(bodyContains("status: RELEASING"))
				.andExpect(bodyContains("isAdult: false"))
				.andExpect(bodyContains("format_not: MUSIC"))
				.andRespond(withSuccess(mediaPage(true, media(1), media(2)), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL))
				.andExpect(bodyContains("\"page\":2"))
				.andRespond(withSuccess(mediaPage(false, media(3)), MediaType.APPLICATION_JSON));

		List<AniListMedia> result = client.fetchReleasing();

		assertEquals(List.of(1, 2, 3), result.stream().map(AniListMedia::id).toList());
		AniListMedia first = result.get(0);
		assertEquals("Show 1", first.title().romaji());
		assertNull(first.title().english());
		assertNull(first.episodes());
		assertNull(first.averageScore());
		assertEquals("FALL", first.season());
		assertEquals(2026, first.startDate().year());
		assertEquals(List.of("Comedy"), first.genres());
		server.verify();
	}

	@Test
	void sendsDateFiltersForFinishedAndUpcoming() {
		server.expect(requestTo(URL))
				.andExpect(bodyContains("status: FINISHED, endDate_greater: 20251231"))
				.andRespond(withSuccess(mediaPage(false), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL))
				.andExpect(bodyContains("status: NOT_YET_RELEASED, startDate_greater: 20251231, startDate_lesser: 20270401"))
				.andRespond(withSuccess(mediaPage(false), MediaType.APPLICATION_JSON));

		assertTrue(client.fetchFinishedAfter(LocalDate.of(2025, 12, 31)).isEmpty());
		assertTrue(client.fetchUpcomingBetween(LocalDate.of(2025, 12, 31), LocalDate.of(2027, 4, 1)).isEmpty());
		server.verify();
	}

	@Test
	void waitsAsLongAsAniListAsksThenRetries() {
		server.expect(requestTo(URL))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "7"));
		server.expect(requestTo(URL))
				.andRespond(withSuccess(mediaPage(false, media(1)), MediaType.APPLICATION_JSON));

		assertEquals(1, client.fetchReleasing().size());
		assertEquals(List.of(Duration.ofSeconds(7)), sleeps);
		server.verify();
	}

	@Test
	void usesDefaultWaitWhenNoRetryAfterHeader() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		server.expect(requestTo(URL))
				.andRespond(withSuccess(mediaPage(false, media(1)), MediaType.APPLICATION_JSON));

		client.fetchReleasing();

		assertEquals(List.of(AniListClient.DEFAULT_WAIT), sleeps);
	}

	@Test
	void givesUpAfterMaxAttempts() {
		for (int i = 0; i < AniListClient.MAX_ATTEMPTS; i++) {
			server.expect(requestTo(URL))
					.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "1"));
		}

		assertThrows(AniListException.class, client::fetchReleasing);
		// It waits between attempts, not after the last one.
		assertEquals(AniListClient.MAX_ATTEMPTS - 1, sleeps.size());
		server.verify();
	}

	@Test
	void turnsGraphQlErrorsIntoAnException() {
		server.expect(requestTo(URL)).andRespond(withSuccess(
				"{\"data\":null,\"errors\":[{\"message\":\"Unknown argument\"}]}", MediaType.APPLICATION_JSON));

		AniListException e = assertThrows(AniListException.class, client::fetchReleasing);
		assertTrue(e.getMessage().contains("Unknown argument"), e.getMessage());
	}

	@Test
	void readsAiringScheduleIncludingLongRunningShows() {
		String json = """
				{"data":{"Page":{"pageInfo":{"hasNextPage":false},"airingSchedules":[
				  {"episode":389,"airingAt":1792918800,"mediaId":118123}]}}}""";
		server.expect(requestTo(URL))
				.andExpect(bodyContains("\"from\":1792900000"))
				.andExpect(bodyContains("\"to\":1792990000"))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

		List<AniListAiringSchedule> result = client.fetchAiringBetween(
				Instant.ofEpochSecond(1792900000L), Instant.ofEpochSecond(1792990000L));

		assertEquals(1, result.size());
		assertEquals(389, result.get(0).episode());
		assertEquals(1792918800L, result.get(0).airingAt());
		assertEquals(118123, result.get(0).mediaId());
	}

	@Test
	void fuzzyIntIsYyyyMmDd() {
		assertEquals(20270401, AniListClient.fuzzyInt(LocalDate.of(2027, 4, 1)));
	}

	// --- helpers ---

	/** Checks that the JSON body we sent contains the given text. */
	private static RequestMatcher bodyContains(String text) {
		return request -> {
			String body = ((MockClientHttpRequest) request).getBodyAsString();
			assertTrue(body.contains(text), "request body was: " + body);
		};
	}

	private static String media(int id) {
		return """
				{"id":%d,"title":{"romaji":"Show %d","english":null},"format":"TV","status":"RELEASING",
				 "season":"FALL","seasonYear":2026,"episodes":null,"averageScore":null,"genres":["Comedy"],
				 "startDate":{"year":2026,"month":10,"day":4},"endDate":{"year":null,"month":null,"day":null},
				 "coverImage":{"large":"https://img.test/%d.jpg"}}""".formatted(id, id, id);
	}

	private static String mediaPage(boolean hasNext, String... items) {
		return "{\"data\":{\"Page\":{\"pageInfo\":{\"hasNextPage\":" + hasNext + "},\"media\":["
				+ String.join(",", items) + "]}}}";
	}
}
