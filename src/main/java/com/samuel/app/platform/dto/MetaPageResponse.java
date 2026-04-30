package com.samuel.app.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Internal DTO mapping Meta Graph API /me/accounts page list response.
 * Used to retrieve Facebook pages linked to user account.
 */
public record MetaPageResponse(
    @JsonProperty("data") List<Page> data
) {
    /**
     * Facebook page information.
     */
    public record Page(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("access_token") String pageAccessToken
    ) {}
}