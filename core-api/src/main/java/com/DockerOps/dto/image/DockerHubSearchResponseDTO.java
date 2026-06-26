package com.DockerOps.dto.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DockerHubSearchResponseDTO(
        List<Result> results
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("repo_name") String repoName,
            @JsonProperty("short_description") String shortDescription,
            @JsonProperty("star_count") long starCount,
            @JsonProperty("is_official") boolean official,
            @JsonProperty("is_automated") boolean automated
    ) {}
}
