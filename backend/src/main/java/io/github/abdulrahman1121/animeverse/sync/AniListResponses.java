package io.github.abdulrahman1121.animeverse.sync;

import java.util.List;

/**
 * The envelopes AniList wraps around the data we ask for. A response looks like
 * {"data": {"Page": {"pageInfo": {...}, "media": [...]}}} and, when something is wrong with
 * the query, {"data": null, "errors": [{"message": "..."}]}.
 *
 * The component named "Page" is capitalised on purpose: it must match AniList's JSON key.
 */
final class AniListResponses {

	private AniListResponses() {
	}

	record PageInfo(Boolean hasNextPage) {
	}

	record GraphQlError(String message) {
	}

	record MediaResponse(MediaData data, List<GraphQlError> errors) {
	}

	record MediaData(MediaPage Page) {
	}

	record MediaPage(PageInfo pageInfo, List<AniListMedia> media) {
	}

	record AiringResponse(AiringData data, List<GraphQlError> errors) {
	}

	record AiringData(AiringPage Page) {
	}

	record AiringPage(PageInfo pageInfo, List<AniListAiringSchedule> airingSchedules) {
	}
}
