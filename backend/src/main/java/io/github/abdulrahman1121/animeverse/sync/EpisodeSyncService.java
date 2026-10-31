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
 * AniList refuses to serve more than 5,000 entries for a single query (page 101 of 50 fails with
 * HTTP 400), and the schedule for a whole year is bigger than that. So we walk the year in
 * smaller time windows and fetch each one separately: the 5,000 limit applies per query, and
 * every window is far below it.
 *
 * Like AnimeSyncService it has no transaction of its own: one short transaction per page.
 */
@Service
public class EpisodeSyncService {

	private static final Logger log = LoggerFactory.getLogger(EpisodeSyncService.class);

	/** Look ahead this far so upcoming episodes (V2's notifications) are known in advance. */
	static final Duration LOOK_AHEAD = Duration.ofDays(14);

	/** Size of one fetch. A busy month is well under 5,000 rows, so a window can't hit the cap. */
	static final Duration WINDOW = Duration.ofDays(30);

	public record SyncReport(int fetched, int saved, int skipped, int failedPages, int failedWindows) {
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
		Instant start = AnimeSyncService.YEAR_START.atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant end = now.plus(LOOK_AHEAD);

		int fetched = 0;
		int saved = 0;
		int skipped = 0;
		int failedPages = 0;
		int failedWindows = 0;

		// Windows are half-open: [from, to). The next window starts exactly where this one ends.
		for (Instant from = start; from.isBefore(end); from = from.plus(WINDOW)) {
			Instant to = from.plus(WINDOW).isBefore(end) ? from.plus(WINDOW) : end;

			List<AniListAiringSchedule> rows;
			try {
				// AniList's bounds are exclusive on both sides, so asking from one second early
				// makes this window include an episode airing exactly at "from". The previous
				// window excluded it (its upper bound is exclusive), so nothing is missed or doubled.
				rows = client.fetchAiringBetween(from.minusSeconds(1), to);
			} catch (RuntimeException e) {
				// The windows are independent, so one failed download doesn't stop the others.
				failedWindows++;
				log.error("Failed to download the airing schedule for {} to {}; skipping this window", from, to, e);
				continue;
			}
			fetched += rows.size();

			for (int i = 0; i < rows.size(); i += AniListClient.PER_PAGE) {
				List<AniListAiringSchedule> page = rows.subList(i, Math.min(i + AniListClient.PER_PAGE, rows.size()));
				try {
					AiringEpisodePageSaver.PageResult result = pageSaver.save(page); // one transaction
					saved += result.saved();
					skipped += result.skipped();
				} catch (RuntimeException e) {
					failedPages++;
					log.error("Failed to save a page of {} episodes; it was rolled back", page.size(), e);
				}
			}
		}
		SyncReport report = new SyncReport(fetched, saved, skipped, failedPages, failedWindows);
		log.info("Episode sync finished: {}", report);
		return report;
	}
}
