package io.github.abdulrahman1121.animeverse.anime;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One episode and its exact air time. The link is one-directional: an episode knows its
 * Anime, but Anime has no episode list ("Ep 3 / 12" comes from a count query instead).
 */
@Entity
@Table(name = "airing_episode")
public class AiringEpisode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Many episodes belong to one anime. This field IS the anime_id foreign key.
	// LAZY: loading an episode doesn't also load its whole Anime row until we ask for it.
	// (Single-object links default to EAGER in JPA, so we opt out explicitly.)
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "anime_id", nullable = false)
	private Anime anime;

	@Column(nullable = false)
	private Short episode;

	@Column(name = "airing_at", nullable = false)
	private Instant airingAt;

	/** Required by JPA. */
	protected AiringEpisode() {
	}

	public AiringEpisode(Anime anime, Short episode, Instant airingAt) {
		this.anime = anime;
		this.episode = episode;
		this.airingAt = airingAt;
	}

	public Long getId() { return id; }
	public Anime getAnime() { return anime; }
	public Short getEpisode() { return episode; }
	public Instant getAiringAt() { return airingAt; }

	/** AniList sometimes reschedules an episode; the sync job updates the time. */
	public void setAiringAt(Instant airingAt) { this.airingAt = airingAt; }
}
