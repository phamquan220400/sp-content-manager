package com.samuel.app.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Internal DTO mapping Meta Graph API page Instagram Business Account response.
 * Maps GET /{page-id}?fields=instagram_business_account response.
 */
public record MetaIgAccountResponse(
    @JsonProperty("instagram_business_account") IgAccount instagramBusinessAccount
) {
    /**
     * Instagram Business Account information.
     */
    public record IgAccount(
        @JsonProperty("id") String id
    ) {}
}