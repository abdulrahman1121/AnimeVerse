package io.github.abdulrahman1121.animeverse.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Pulls the airing schedule and saves the episodes of the shows we track. Run it AFTER
 * AnimeSyncService, because an episode can only be saved once its show exists.
 *
 * Like AnimeSyncService it has no transaction of its own: one short transaction per page.
 */
@Service
public class EpisodeSyncService {

	private static final Logger log = LoggerFactory.getLogger(EpisodeSyncService.class);

	/** Look ahead this far so upcoming episodes (V2's notifications) are known in advance. */
	static final Duration LOOK_AHEAD = Duration.ofDays(14);

	public record SyncReport(int fetched, int saved, int skipped, int failedPages) {
	}

	private final AniListClient client;
	private final AiringEpisodePageSaver pageSaver;

	public EpisodeSyncService(AniListClient client, AiringEpisodePageSaver pageSaver) {
		this.client = client;
		this.pageSaver = pageSaver;
	}

	public SyncReport syncEpisodes() {
		return syncEpisodes(Instant.now());
	}

	SyncReport syncEpisodes(Instant now) {
		// The whole year so far, plus two weeks ahead. AniList's bounds are exclusive, so start one second early.
		Instant from = AnimeSyncService.YEAR_START.atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(1);
		Instant to = now.plus(LOOK_AHEAD);
		List<AniListAiringSchedule> rows = client.fetchAiringBetween(from, to);

		int saved = 0;
		int skipped = 0;
		int failedPages = 0;
		for (int start = 0; start < rows.size(); start += AniListClient.PER_PAGE) {
			List<AniListAiringSchedule> page = rows.subList(start, Math.min(start + AniListClient.PER_PAGE, rows.size()));
			try {
				AiringEpisodePageSaver.PageResult result = pageSaver.save(page); // one transaction
				saved += result.saved();
				skipped += result.skipped();
			} catch (RuntimeException e) {
				failedPages++;
				log.error("Failed to save a page of {} episodes; it was rolled back", page.size(), e);
			}
		}
		SyncReport report = new SyncReport(rows.size(), saved, skipped, failedPages);
		log.info("Episode sync finished: {}", report);
		return report;
	}
}
