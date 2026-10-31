package io.github.abdulrahman1121.animeverse.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the two syncs on a timer: first the shows, then their episodes.
 *
 * - fixedDelay: the next run starts 3 hours after the previous one FINISHED, so two runs can
 *   never overlap. The first run starts as soon as the app has started (the default initial
 *   delay is 0), on Spring's scheduler thread, so it doesn't hold up the web server.
 * - @ConditionalOnProperty: the whole bean (and so the timer) exists only when
 *   animeverse.sync.enabled=true. Tests switch it off, otherwise every @SpringBootTest would
 *   call the real AniList and write to the real database.
 * - @EnableScheduling lives here, so scheduling is switched on only when this job exists.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "animeverse.sync.enabled", havingValue = "true")
public class SyncJob {

	private static final Logger log = LoggerFactory.getLogger(SyncJob.class);

	private final AnimeSyncService animeSync;
	private final EpisodeSyncService episodeSync;

	public SyncJob(AnimeSyncService animeSync, EpisodeSyncService episodeSync) {
		this.animeSync = animeSync;
		this.episodeSync = episodeSync;
	}

	// "PT3H" is an ISO-8601 duration: Period, Time, 3 Hours. Configurable in application.properties.
	@Scheduled(fixedDelayString = "${animeverse.sync.interval:PT3H}")
	public void run() {
		try {
			animeSync.syncAnime();
		} catch (RuntimeException e) {
			// An episode can only be saved if its show exists, so with no fresh shows there is
			// nothing safe to do. Skip the episode sync; the next run catches up.
			log.error("Show sync failed; skipping the episode sync this run", e);
			return;
		}
		try {
			episodeSync.syncEpisodes();
		} catch (RuntimeException e) {
			log.error("Episode sync failed; it will be retried next run", e);
		}
	}
}
