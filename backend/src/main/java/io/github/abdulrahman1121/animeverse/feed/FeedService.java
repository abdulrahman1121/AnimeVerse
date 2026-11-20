package io.github.abdulrahman1121.animeverse.feed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.abdulrahman1121.animeverse.anime.AiringEpisodeRepository;
import io.github.abdulrahman1121.animeverse.anime.Anime;
import io.github.abdulrahman1121.animeverse.anime.AnimeRepository;
import io.github.abdulrahman1121.animeverse.anime.MaxAiredEpisode;
import io.github.abdulrahman1121.animeverse.sync.AnimeSyncService;

/**
 * Builds the three feed sections. Each section costs exactly two queries however many cards it
 * has: one for the shows with their genres, and one for the latest aired episode of all of them.
 *
 * readOnly = true tells Spring and Hibernate this method never writes, so Hibernate can skip
 * its change tracking. Controllers call the public methods; the Instant overloads let tests pick "now".
 */
@Service
@Transactional(readOnly = true)
public class FeedService {

	private final AnimeRepository animeRepository;
	private final AiringEpisodeRepository episodeRepository;

	public FeedService(AnimeRepository animeRepository, AiringEpisodeRepository episodeRepository) {
		this.animeRepository = animeRepository;
		this.episodeRepository = episodeRepository;
	}

	public List<AnimeCard> airing() {
		return airing(Instant.now());
	}

	List<AnimeCard> airing(Instant now) {
		return toCards(animeRepository.findAiring(), now);
	}

	public List<AnimeCard> finished() {
		return finished(Instant.now());
	}

	List<AnimeCard> finished(Instant now) {
		return toCards(animeRepository.findFinishedSince(AnimeSyncService.YEAR_START), now);
	}

	public List<AnimeCard> upcoming() {
		return upcoming(Instant.now());
	}

	List<AnimeCard> upcoming(Instant now) {
		LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
		return toCards(animeRepository.findUpcoming(today, today.plusMonths(AnimeSyncService.UPCOMING_MONTHS)), now);
	}

	private List<AnimeCard> toCards(List<Anime> animes, Instant now) {
		if (animes.isEmpty()) {
			return List.of(); // skip the second query: an empty "in ()" list is pointless
		}
		List<Long> ids = animes.stream().map(Anime::getId).toList();
		Map<Long, Short> aired = episodeRepository.findMaxAiredEpisodes(ids, now).stream()
				.collect(Collectors.toMap(MaxAiredEpisode::animeId, MaxAiredEpisode::episode));
		// aired.get(...) is null for a show with no aired episode, which the card turns into "nothing to show"
		return animes.stream().map(anime -> AnimeCard.from(anime, aired.get(anime.getId()))).toList();
	}
}
