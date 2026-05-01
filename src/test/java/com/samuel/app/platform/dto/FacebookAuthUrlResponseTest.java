package com.samuel.app.platform.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FacebookAuthUrlResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void should_create_facebook_auth_url_response() {
        String authUrl = "https://www.facebook.com/v18.0/dialog/oauth?client_id=test&response_type=code&scope=pages_show_list";
        
        FacebookAuthUrlResponse response = new FacebookAuthUrlResponse(authUrl);
        
        assertThat(response.authorizationUrl()).isEqualTo(authUrl);
    }

    @Test
    public void should_serialize_to_json() throws Exception {
        String authUrl = "https://www.facebook.com/v18.0/dialog/oauth?client_id=test&response_type=code";
        FacebookAuthUrlResponse response = new FacebookAuthUrlResponse(authUrl);
        
        String json = objectMapper.writeValueAsString(response);
        
        assertThat(json).contains("\"authorizationUrl\"");
        assertThat(json).contains(authUrl);
    }

    @Test
    public void should_deserialize_from_json() throws Exception {
        String authUrl = "https://www.facebook.com/v18.0/dialog/oauth?client_id=test";
        String json = "{\"authorizationUrl\":\"" + authUrl + "\"}";
        
        FacebookAuthUrlResponse response = objectMapper.readValue(json, FacebookAuthUrlResponse.class);
        
        assertThat(response.authorizationUrl()).isEqualTo(authUrl);
    }
}