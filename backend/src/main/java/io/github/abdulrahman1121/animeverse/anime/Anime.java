package io.github.abdulrahman1121.animeverse.anime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/** One show, copied from AniList by the sync job. Maps to the anime table from V1__create_v1_schema.sql. */
@Entity
@Table(name = "anime")
public class Anime {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "anilist_id", nullable = false, unique = true)
	private Integer anilistId;

	@Column(name = "title_romaji", nullable = false)
	private String titleRomaji;

	@Column(name = "title_english")
	private String titleEnglish; // null: no official English title yet

	@Column(name = "cover_image_url")
	private String coverImageUrl;

	// STRING stores the enum's name ('TV'), never its position (0), so reordering
	// the enum can't silently change what old rows mean.
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AnimeFormat format;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AnimeStatus status;

	@Enumerated(EnumType.STRING)
	private Season season;

	// smallint columns map to Short in Java (2 bytes on both sides).
	@Column(name = "season_year")
	private Short seasonYear;

	private Short episodes; // null: total not announced

	@Column(name = "start_date")
	private LocalDate startDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "start_precision")
	private DatePrecision startPrecision;

	@Column(name = "end_date")
	private LocalDate endDate; // null while still airing

	@Enumerated(EnumType.STRING)
	@Column(name = "end_precision")
	private DatePrecision endPrecision;

	@Column(name = "average_score")
	private Short averageScore; // null: unrated, shown as "NEW"

	// Instant = an exact moment in UTC, the Java match for timestamptz.
	@Column(name = "synced_at", nullable = false)
	private Instant syncedAt;

	// Many-to-many through the anime_genre join table. Hibernate inserts and deletes
	// the link rows when this Set changes. Outside code changes it only through
	// addGenre / removeGenre; getGenres() hands out a read-only view.
	// LAZY (the default for collections): genres are loaded only when first used.
	@ManyToMany
	@JoinTable(
			name = "anime_genre",
			joinColumns = @JoinColumn(name = "anime_id"),
			inverseJoinColumns = @JoinColumn(name = "genre_id"))
	private Set<Genre> genres = new HashSet<>();

	/** Required by JPA. */
	protected Anime() {
	}

	public Anime(Integer anilistId, String titleRomaji, AnimeFormat format, AnimeStatus status, Instant syncedAt) {
		this.anilistId = anilistId;
		this.titleRomaji = titleRomaji;
		this.format = format;
		this.status = status;
		this.syncedAt = syncedAt;
	}

	// --- Dates come in pairs, so they're set together (mirrors the DB's both-or-neither CHECK) ---

	public void setStart(LocalDate date, DatePrecision precision) {
		requireBothOrNeither(date, precision);
		this.startDate = date;
		this.startPrecision = precision;
	}

	public void setEnd(LocalDate date, DatePrecision precision) {
		requireBothOrNeither(date, precision);
		this.endDate = date;
		this.endPrecision = precision;
	}

	private static void requireBothOrNeither(LocalDate date, DatePrecision precision) {
		if ((date == null) != (precision == null)) {
			throw new IllegalArgumentException("A date and its precision must both be set or both be null");
		}
	}

	// --- Getters ---

	public Long getId() { return id; }
	public Integer getAnilistId() { return anilistId; }
	public String getTitleRomaji() { return titleRomaji; }
	public String getTitleEnglish() { return titleEnglish; }
	public String getCoverImageUrl() { return coverImageUrl; }
	public AnimeFormat getFormat() { return format; }
	public AnimeStatus getStatus() { return status; }
	public Season getSeason() { return season; }
	public Short getSeasonYear() { return seasonYear; }
	public Short getEpisodes() { return episodes; }
	public LocalDate getStartDate() { return startDate; }
	public DatePrecision getStartPrecision() { return startPrecision; }
	public LocalDate getEndDate() { return endDate; }
	public DatePrecision getEndPrecision() { return endPrecision; }
	public Short getAverageScore() { return averageScore; }
	public Instant getSyncedAt() { return syncedAt; }

	/** Read-only view: callers can look, but must use addGenre / removeGenre to change it. */
	public Set<Genre> getGenres() { return Collections.unmodifiableSet(genres); }

	// --- Setters for fields the sync job updates ---

	public void setTitleRomaji(String titleRomaji) { this.titleRomaji = titleRomaji; }
	public void setTitleEnglish(String titleEnglish) { this.titleEnglish = titleEnglish; }
	public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
	public void setFormat(AnimeFormat format) { this.format = format; }
	public void setStatus(AnimeStatus status) { this.status = status; }
	public void setSeason(Season season, Short seasonYear) { this.season = season; this.seasonYear = seasonYear; }
	public void setEpisodes(Short episodes) { this.episodes = episodes; }
	public void setAverageScore(Short averageScore) { this.averageScore = averageScore; }
	public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }

	// --- Genre links ---

	/** Links a genre to this show. Returns false if it was already linked (a Set ignores duplicates). */
	public boolean addGenre(Genre genre) {
		return genres.add(genre);
	}

	/** Unlinks a genre from this show (the genre itself is not deleted). Returns false if it wasn't linked. */
	public boolean removeGenre(Genre genre) {
		return genres.remove(genre);
	}
}
