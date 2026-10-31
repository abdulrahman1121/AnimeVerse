package io.github.abdulrahman1121.animeverse.sync;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import io.github.abdulrahman1121.animeverse.sync.AniListResponses.AiringPage;
import io.github.abdulrahman1121.animeverse.sync.AniListResponses.AiringResponse;
import io.github.abdulrahman1121.animeverse.sync.AniListResponses.GraphQlError;
import io.github.abdulrahman1121.animeverse.sync.AniListResponses.MediaPage;
import io.github.abdulrahman1121.animeverse.sync.AniListResponses.MediaResponse;
import io.github.abdulrahman1121.animeverse.sync.AniListResponses.PageInfo;

/**
 * Knows how to talk to AniList's GraphQL API and nothing else: it builds the HTTP request,
 * walks through every page of results, and waits and retries when AniList says "slow down".
 * Turning the results into our entities is the mapper's job; saving them is the sync job's.
 */
@Component
public class AniListClient {

	static final int PER_PAGE = 50;                          // AniList's maximum page size
	static final int MAX_PAGES = 200;                        // safety stop so a bug can't loop forever
	static final int MAX_ATTEMPTS = 3;                       // tries per request when rate limited
	static final Duration DEFAULT_WAIT = Duration.ofSeconds(60);  // if AniList sends no Retry-After
	static final Duration MAX_WAIT = Duration.ofSeconds(120);     // never sleep longer than this
	private static final int MAX_BODY_IN_MESSAGE = 500;          // how much of an error response goes into the exception message
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

	private static final String MEDIA_FIELDS = """
			id title { romaji english } format status season seasonYear episodes averageScore genres
			startDate { year month day } endDate { year month day } coverImage { large }""";

	// %s #1 = the section-specific filters, %s #2 = MEDIA_FIELDS. Sorting by ID keeps pages stable
	// between requests. $page and $perPage are GraphQL variables, sent separately from the text.
	private static final String MEDIA_QUERY = """
			query ($page: Int, $perPage: Int) {
			  Page(page: $page, perPage: $perPage) {
			    pageInfo { hasNextPage }
			    media(type: ANIME, isAdult: false, format_not: MUSIC, %s, sort: ID) { %s }
			  }
			}""";

	private static final String AIRING_QUERY = """
			query ($page: Int, $perPage: Int, $from: Int, $to: Int) {
			  Page(page: $page, perPage: $perPage) {
			    pageInfo { hasNextPage }
			    airingSchedules(airingAt_greater: $from, airingAt_lesser: $to, sort: [TIME, ID]) {
			      episode airingAt mediaId
			    }
			  }
			}""";

	/** Waiting is injected so tests can record "would have waited 7s" instead of really waiting. */
	@FunctionalInterface
	interface Sleeper {
		void sleep(Duration duration) throws InterruptedException;
	}

	private final RestClient restClient;
	private final String url;
	private final Sleeper sleeper;

	/** The constructor Spring uses: real HTTP, real waiting. */
	@Autowired
	public AniListClient(@Value("${animeverse.anilist.url}") String url) {
		this(defaultBuilder(), url, duration -> Thread.sleep(duration));
	}

	/** The constructor tests use: they pass in a builder wired to a fake server, and a fake sleeper. */
	AniListClient(RestClient.Builder builder, String url, Sleeper sleeper) {
		this.restClient = builder.build();
		this.url = url;
		this.sleeper = sleeper;
	}

	private static RestClient.Builder defaultBuilder() {
		// Without timeouts, a hung AniList connection would block the sync job forever.
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
		factory.setReadTimeout(READ_TIMEOUT);
		return RestClient.builder().requestFactory(factory);
	}

	// ---------------------------------------------------------------------------------------
	// Public API: one method per kind of data the sync job needs
	// ---------------------------------------------------------------------------------------

	/** Everything currently airing, whatever year it started. */
	public List<AniListMedia> fetchReleasing() {
		return fetchMedia("status: RELEASING");
	}

	/** Finished shows whose end date is strictly after the given date. */
	public List<AniListMedia> fetchFinishedAfter(LocalDate date) {
		return fetchMedia("status: FINISHED, endDate_greater: " + fuzzyInt(date));
	}

	/**
	 * Not-yet-released shows whose start date is strictly between the two dates. The lower bound
	 * matters: a real response showed that startDate_lesser alone also matches shows with no
	 * announced date, and requiring a date after the lower bound filters those out on AniList's side.
	 */
	public List<AniListMedia> fetchUpcomingBetween(LocalDate after, LocalDate before) {
		return fetchMedia("status: NOT_YET_RELEASED, startDate_greater: " + fuzzyInt(after)
				+ ", startDate_lesser: " + fuzzyInt(before));
	}

	/** Every episode airing between the two moments (exclusive), across all shows, in air-time order. */
	public List<AniListAiringSchedule> fetchAiringBetween(Instant from, Instant to) {
		return fetchAllPages(page -> {
			AiringResponse response = post(AIRING_QUERY, Map.of(
					"page", page,
					"perPage", PER_PAGE,
					"from", (int) from.getEpochSecond(),
					"to", (int) to.getEpochSecond()), AiringResponse.class);
			failOnErrors(response == null ? null : response.errors());
			if (response == null || response.data() == null || response.data().Page() == null) {
				throw new AniListException("AniList returned an empty airing schedule response");
			}
			AiringPage p = response.data().Page();
			return new Page<>(p.airingSchedules(), hasNext(p.pageInfo()));
		});
	}

	// ---------------------------------------------------------------------------------------
	// Internals
	// ---------------------------------------------------------------------------------------

	private List<AniListMedia> fetchMedia(String filters) {
		String query = MEDIA_QUERY.formatted(filters, MEDIA_FIELDS);
		return fetchAllPages(page -> {
			MediaResponse response = post(query, Map.of("page", page, "perPage", PER_PAGE), MediaResponse.class);
			failOnErrors(response == null ? null : response.errors());
			if (response == null || response.data() == null || response.data().Page() == null) {
				throw new AniListException("AniList returned an empty media response");
			}
			MediaPage p = response.data().Page();
			return new Page<>(p.media(), hasNext(p.pageInfo()));
		});
	}

	private record Page<T>(List<T> items, boolean hasNext) {
	}

	/** Asks for page 1, 2, 3... until AniList says there is no next page. */
	private <T> List<T> fetchAllPages(IntFunction<Page<T>> fetchPage) {
		List<T> all = new ArrayList<>();
		for (int page = 1; page <= MAX_PAGES; page++) {
			Page<T> result = fetchPage.apply(page);
			all.addAll(result.items());
			if (!result.hasNext()) {
				return all;
			}
		}
		throw new AniListException("Still more results after " + MAX_PAGES + " pages; stopping to be safe");
	}

	/**
	 * POSTs one GraphQL request. On HTTP 429 (rate limited) it waits as long as AniList asks, then retries.
	 * Any other HTTP error (4xx or 5xx) is turned into an AniListException that carries AniList's own
	 * explanation, so callers only ever deal with one exception type.
	 */
	private <R> R post(String query, Map<String, Object> variables, Class<R> responseType) {
		Map<String, Object> body = Map.of("query", query, "variables", variables);
		for (int attempt = 1;; attempt++) {
			try {
				return restClient.post()
						.uri(url)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.body(body)
						.retrieve()
						.body(responseType);
			}
			catch (HttpClientErrorException.TooManyRequests e) {
				if (attempt == MAX_ATTEMPTS) {
					throw new AniListException("AniList rate limit hit " + MAX_ATTEMPTS + " times in a row", e);
				}
				try {
					sleeper.sleep(retryAfter(e));
				}
				catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt(); // keep the "stop" signal alive for whoever interrupted us
					throw new AniListException("Interrupted while waiting out the AniList rate limit", interrupted);
				}
			}
			// Must come after the 429 handler: Java picks the first catch that matches, and
			// TooManyRequests is also a RestClientResponseException. This covers every other
			// 4xx (like 400 "page depth exceeds") and every 5xx (AniList is having a bad day).
			catch (RestClientResponseException e) {
				throw new AniListException(describe(e), e);
			}
		}
	}

	/** "AniList returned HTTP 400: ..." with the start of AniList's response body, which holds its reason. */
	private static String describe(RestClientResponseException e) {
		String message = "AniList returned HTTP " + e.getStatusCode().value();
		String body = e.getResponseBodyAsString();
		if (body == null || body.isBlank()) {
			return message;
		}
		String shown = body.length() > MAX_BODY_IN_MESSAGE ? body.substring(0, MAX_BODY_IN_MESSAGE) + "..." : body;
		return message + ": " + shown;
	}

	/** AniList sends "Retry-After: 60" (seconds) with a 429. Missing or unreadable: use the default. */
	private static Duration retryAfter(HttpClientErrorException e) {
		String header = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
		if (header == null) {
			return DEFAULT_WAIT;
		}
		try {
			Duration asked = Duration.ofSeconds(Long.parseLong(header.trim()));
			return asked.compareTo(MAX_WAIT) > 0 ? MAX_WAIT : asked;
		}
		catch (NumberFormatException notANumber) {
			return DEFAULT_WAIT;
		}
	}

	/** Some GraphQL mistakes come back as HTTP 200 with an "errors" list, so the status code alone isn't enough. */
	private static void failOnErrors(List<GraphQlError> errors) {
		if (errors != null && !errors.isEmpty()) {
			throw new AniListException("AniList returned errors: " + errors.stream().map(GraphQlError::message).toList());
		}
	}

	private static boolean hasNext(PageInfo info) {
		return info != null && Boolean.TRUE.equals(info.hasNextPage());
	}

	/** AniList's date filters are plain numbers shaped YYYYMMDD: 2027-04-01 becomes 20270401. */
	static int fuzzyInt(LocalDate date) {
		return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
	}
}
