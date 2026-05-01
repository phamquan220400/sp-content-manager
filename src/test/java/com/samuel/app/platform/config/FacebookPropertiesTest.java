package com.samuel.app.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = FacebookProperties.class)
@EnableConfigurationProperties(FacebookProperties.class)
@TestPropertySource(properties = {
    "facebook.client-id=test-facebook-client-id",
    "facebook.client-secret=test-facebook-client-secret",
    "facebook.redirect-uri=http://localhost:8080/api/v1/platforms/facebook/callback"
})
public class FacebookPropertiesTest {

    @Autowired
    private FacebookProperties facebookProperties;

    @Test
    public void should_load_facebook_client_id_from_properties() {
        assertThat(facebookProperties.getClientId()).isEqualTo("test-facebook-client-id");
    }

    @Test
    public void should_load_facebook_client_secret_from_properties() {
        assertThat(facebookProperties.getClientSecret()).isEqualTo("test-facebook-client-secret");
    }

    @Test
    public void should_load_facebook_redirect_uri_from_properties() {
        assertThat(facebookProperties.getRedirectUri()).isEqualTo("http://localhost:8080/api/v1/platforms/facebook/callback");
    }
}