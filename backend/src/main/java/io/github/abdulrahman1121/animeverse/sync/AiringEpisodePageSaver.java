package io.github.abdulrahman1121.animeverse.sync;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.abdulrahman1121.animeverse.anime.AiringEpisode;
import io.github.abdulrahman1121.animeverse.anime.AiringEpisodeRepository;
import io.github.abdulrahman1121.animeverse.anime.Anime;
import io.github.abdulrahman1121.animeverse.anime.AnimeRepository;

/**
 * Saves ONE page of airing-schedule rows in ONE transaction. Its own bean for the same reason
 * as AnimePageSaver: @Transactional only works when the call comes through Spring's proxy.
 *
 * AniList's schedule covers every anime it knows, but we only keep episodes of shows already in
 * our anime table, so rows whose show we don't have are skipped.
 */
@Component
public class AiringEpisodePageSaver {

	public record PageResult(int saved, int skipped) {
	}

	private final AnimeRepository animeRepository;
	private final AiringEpisodeRepository episodeRepository;

	public AiringEpisodePageSaver(AnimeRepository animeRepository, AiringEpisodeRepository episodeRepository) {
		this.animeRepository = animeRepository;
		this.episodeRepository = episodeRepository;
	}

	@Transactional
	public PageResult save(List<AniListAiringSchedule> page) {
		// One lookup per show per page, not per row. Optional.empty() remembers "not one of ours".
		Map<Integer, Optional<Anime>> animeCache = new HashMap<>();
		int saved = 0;
		int skipped = 0;
		for (AniListAiringSchedule row : page) {
			if (row == null || row.mediaId() == null || row.episode() == null || row.airingAt() == null
					|| row.episode() <= 0 || row.episode() > Short.MAX_VALUE) {
				skipped++;
				continue;
			}
			Optional<Anime> anime = animeCache.computeIfAbsent(row.mediaId(),
					id -> animeRepository.findByAnilistId(id));
			if (anime.isEmpty()) {
				skipped++;
				continue;
			}
			Short episode = row.episode().shortValue();
			Instant airingAt = Instant.ofEpochSecond(row.airingAt());
			AiringEpisode existing = episodeRepository.findByAnimeAndEpisode(anime.get(), episode).orElse(null);
			if (existing == null) {
				episodeRepository.save(new AiringEpisode(anime.get(), episode, airingAt));
			} else {
				existing.setAiringAt(airingAt); // AniList rescheduled it; Hibernate sees the change and UPDATEs
			}
			saved++;
		}
		return new PageResult(saved, skipped);
	}
}
