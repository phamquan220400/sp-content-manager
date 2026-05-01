package com.samuel.app.platform.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FacebookPageDetailsResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void should_create_facebook_page_details_response() {
        FacebookPageDetailsResponse response = new FacebookPageDetailsResponse(
            "page-123",
            "Test Page",
            50000L,
            "Brand"
        );
        
        assertThat(response.id()).isEqualTo("page-123");
        assertThat(response.name()).isEqualTo("Test Page");
        assertThat(response.fanCount()).isEqualTo(50000L);
        assertThat(response.category()).isEqualTo("Brand");
    }

    @Test
    public void should_serialize_to_json() throws Exception {
        FacebookPageDetailsResponse response = new FacebookPageDetailsResponse(
            "page-456",
            "My Business Page",
            75000L,
            "Company"
        );
        
        String json = objectMapper.writeValueAsString(response);
        
        assertThat(json).contains("\"id\":\"page-456\"");
        assertThat(json).contains("\"name\":\"My Business Page\"");
        assertThat(json).contains("\"fan_count\":75000");
        assertThat(json).contains("\"category\":\"Company\"");
    }

    @Test
    public void should_deserialize_from_json() throws Exception {
        String json = "{\"id\":\"page-789\",\"name\":\"Another Page\",\"fan_count\":25000,\"category\":\"Entertainment\"}";
        
        FacebookPageDetailsResponse response = objectMapper.readValue(json, FacebookPageDetailsResponse.class);
        
        assertThat(response.id()).isEqualTo("page-789");
        assertThat(response.name()).isEqualTo("Another Page");
        assertThat(response.fanCount()).isEqualTo(25000L);
        assertThat(response.category()).isEqualTo("Entertainment");
    }
}