package io.github.abdulrahman1121.animeverse.feed;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The three feed endpoints, one per section. They are separate on purpose: each is a small
 * query, they can load in parallel, and one failing doesn't affect the others.
 *
 * @RestController means each returned object is written straight into the HTTP response as JSON
 * (Spring uses Jackson to turn the AnimeCard records into JSON). The controller does no logic of
 * its own: it only maps a URL to the matching FeedService call.
 */
@RestController
@RequestMapping("/api/anime")
public class FeedController {

	private final FeedService feedService;

	public FeedController(FeedService feedService) {
		this.feedService = feedService;
	}

	/** GET /api/anime/airing */
	@GetMapping("/airing")
	public List<AnimeCard> airing() {
		return feedService.airing();
	}

	/** GET /api/anime/finished */
	@GetMapping("/finished")
	public List<AnimeCard> finished() {
		return feedService.finished();
	}

	/** GET /api/anime/upcoming */
	@GetMapping("/upcoming")
	public List<AnimeCard> upcoming() {
		return feedService.upcoming();
	}
}
