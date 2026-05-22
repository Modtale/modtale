package net.modtale.model.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GitRepositoryDTO(
        String name,
        String url,
        String description,
        @JsonProperty("private") boolean isPrivate
) {}
