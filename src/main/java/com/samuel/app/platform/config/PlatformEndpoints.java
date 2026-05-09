package com.samuel.app.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Centralised configuration for all platform API endpoint URLs and OAuth scopes.
 * Mapped from application.yml under the {@code platform.endpoints} prefix.
 *
 * <p>All platform-specific URLs (auth, token, resource endpoints) and OAuth scopes are
 * declared here so they can be overridden per-environment via environment variables or
 * application properties without recompiling the application.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "platform.endpoints")
public class PlatformEndpoints {

    private YouTube youtube = new YouTube();
    private TikTok tiktok = new TikTok();
    private Instagram instagram = new Instagram();
    private Facebook facebook = new Facebook();

    // ------- Getters / Setters -------

    public YouTube getYoutube() { return youtube; }
    public void setYoutube(YouTube youtube) { this.youtube = youtube; }

    public TikTok getTiktok() { return tiktok; }
    public void setTiktok(TikTok tiktok) { this.tiktok = tiktok; }

    public Instagram getInstagram() { return instagram; }
    public void setInstagram(Instagram instagram) { this.instagram = instagram; }

    public Facebook getFacebook() { return facebook; }
    public void setFacebook(Facebook facebook) { this.facebook = facebook; }

    // =========================================================
    //  YouTube
    // =========================================================
    public static class YouTube {
        private String oauthUrl;
        private String tokenUrl;
        private String channelUrl;
        private String uploadUrl;
        private String analyticsUrl;
        private String videoBaseUrl;
        private String scopes;

        public String getOauthUrl() { return oauthUrl; }
        public void setOauthUrl(String oauthUrl) { this.oauthUrl = oauthUrl; }

        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }

        public String getChannelUrl() { return channelUrl; }
        public void setChannelUrl(String channelUrl) { this.channelUrl = channelUrl; }

        public String getUploadUrl() { return uploadUrl; }
        public void setUploadUrl(String uploadUrl) { this.uploadUrl = uploadUrl; }

        public String getAnalyticsUrl() { return analyticsUrl; }
        public void setAnalyticsUrl(String analyticsUrl) { this.analyticsUrl = analyticsUrl; }

        public String getVideoBaseUrl() { return videoBaseUrl; }
        public void setVideoBaseUrl(String videoBaseUrl) { this.videoBaseUrl = videoBaseUrl; }

        public String getScopes() { return scopes; }
        public void setScopes(String scopes) { this.scopes = scopes; }
    }

    // =========================================================
    //  TikTok
    // =========================================================
    public static class TikTok {
        private String authUrl;
        private String tokenUrl;
        private String userInfoUrl;
        private String scopes;

        public String getAuthUrl() { return authUrl; }
        public void setAuthUrl(String authUrl) { this.authUrl = authUrl; }

        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }

        public String getUserInfoUrl() { return userInfoUrl; }
        public void setUserInfoUrl(String userInfoUrl) { this.userInfoUrl = userInfoUrl; }

        public String getScopes() { return scopes; }
        public void setScopes(String scopes) { this.scopes = scopes; }
    }

    // =========================================================
    //  Instagram  (uses Meta / Graph API)
    // =========================================================
    public static class Instagram {
        private String oauthUrl;
        private String tokenUrl;
        private String pagesUrl;
        private String graphUrlTemplate;
        private String userInfoUrl;
        private String scopes;

        public String getOauthUrl() { return oauthUrl; }
        public void setOauthUrl(String oauthUrl) { this.oauthUrl = oauthUrl; }

        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }

        public String getPagesUrl() { return pagesUrl; }
        public void setPagesUrl(String pagesUrl) { this.pagesUrl = pagesUrl; }

        public String getGraphUrlTemplate() { return graphUrlTemplate; }
        public void setGraphUrlTemplate(String graphUrlTemplate) { this.graphUrlTemplate = graphUrlTemplate; }

        public String getUserInfoUrl() { return userInfoUrl; }
        public void setUserInfoUrl(String userInfoUrl) { this.userInfoUrl = userInfoUrl; }

        public String getScopes() { return scopes; }
        public void setScopes(String scopes) { this.scopes = scopes; }
    }

    // =========================================================
    //  Facebook  (uses Meta / Graph API)
    // =========================================================
    public static class Facebook {
        private String oauthUrl;
        private String tokenUrl;
        private String pagesUrl;
        private String pageDetailUrlTemplate;
        private String pageInfoUrlTemplate;
        private String scopes;

        public String getOauthUrl() { return oauthUrl; }
        public void setOauthUrl(String oauthUrl) { this.oauthUrl = oauthUrl; }

        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }

        public String getPagesUrl() { return pagesUrl; }
        public void setPagesUrl(String pagesUrl) { this.pagesUrl = pagesUrl; }

        public String getPageDetailUrlTemplate() { return pageDetailUrlTemplate; }
        public void setPageDetailUrlTemplate(String t) { this.pageDetailUrlTemplate = t; }

        public String getPageInfoUrlTemplate() { return pageInfoUrlTemplate; }
        public void setPageInfoUrlTemplate(String t) { this.pageInfoUrlTemplate = t; }

        public String getScopes() { return scopes; }
        public void setScopes(String scopes) { this.scopes = scopes; }
    }
}
