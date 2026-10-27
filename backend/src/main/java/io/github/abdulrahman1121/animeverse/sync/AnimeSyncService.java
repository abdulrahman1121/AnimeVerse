package io.github.abdulrahman1121.animeverse.sync;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Pulls the shows V1 displays from AniList and saves them. Episodes and scheduling come later.
 *
 * Deliberately NOT @Transactional: it only fetches over HTTP and splits the work. Each page is
 * saved in its own short transaction by AnimePageSaver, so a database error costs at most one
 * page, and no transaction is ever held open while waiting on the network.
 */
@Service
public class AnimeSyncService {

	private static final Logger log = LoggerFactory.getLogger(AnimeSyncService.class);

	/** V1 is "This Year in Anime", starting here. */
	static final LocalDate YEAR_START = LocalDate.of(2026, 1, 1);
	/** "Upcoming" means roughly the next season. */
	static final int UPCOMING_MONTHS = 3;

	public record SyncReport(int fetched, int saved, int skipped, int failedPages) {
	}

	private final AniListClient client;
	private final AnimePageSaver pageSaver;

	public AnimeSyncService(AniListClient client, AnimePageSaver pageSaver) {
		this.client = client;
		this.pageSaver = pageSaver;
	}

	public SyncReport syncAnime() {
		return syncAnime(Instant.now());
	}

	/** Takes "now" as a parameter so tests control the date windows. */
	SyncReport syncAnime(Instant now) {
		LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();

		// The AniList date filters are exclusive, so step one day outward to include the boundary day.
		List<AniListMedia> releasing = client.fetchReleasing();
		List<AniListMedia> finished = client.fetchFinishedAfter(YEAR_START.minusDays(1));
		List<AniListMedia> upcoming = client.fetchUpcomingBetween(today.minusDays(1),
				today.plusMonths(UPCOMING_MONTHS));

		// Merge in priority order. The same show can show up in two lists, and the first copy wins.
		Map<Integer, AniListMedia> byId = new LinkedHashMap<>();
		for (AniListMedia m : releasing) {
			addIfNew(byId, m);
		}
		for (AniListMedia m : finished) {
			addIfNew(byId, m);
		}
		for (AniListMedia m : upcoming) {
			if (m != null && (m.startDate() == null || m.startDate().year() == null)) {
				continue; // safety net: an "upcoming" show with no date can't be placed in the feed
			}
			addIfNew(byId, m);
		}
		List<AniListMedia> all = new ArrayList<>(byId.values());

		int saved = 0;
		int skipped = 0;
		int failedPages = 0;
		for (int from = 0; from < all.size(); from += AniListClient.PER_PAGE) {
			List<AniListMedia> page = all.subList(from, Math.min(from + AniListClient.PER_PAGE, all.size()));
			try {
				AnimePageSaver.PageResult result = pageSaver.save(page, now); // one transaction
				saved += result.saved();
				skipped += result.skipped();
			} catch (RuntimeException e) {
				// Only this page was rolled back. Log it and continue with the next page.
				failedPages++;
				log.error("Failed to save a page of {} shows; it was rolled back", page.size(), e);
			}
		}
		SyncReport report = new SyncReport(all.size(), saved, skipped, failedPages);
		log.info("Anime sync finished: {}", report);
		return report;
	}

	private static void addIfNew(Map<Integer, AniListMedia> byId, AniListMedia media) {
		if (media != null && media.id() != null) {
			byId.putIfAbsent(media.id(), media);
		}
	}
}
