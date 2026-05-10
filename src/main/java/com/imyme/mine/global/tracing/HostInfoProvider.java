package com.imyme.mine.global.tracing;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Component
public class HostInfoProvider {

    @Getter
    private final String hostIp;

    public HostInfoProvider() {
        this.hostIp = fetchEc2Ip();
    }

    private String fetchEc2Ip() {
        String token = fetchMetadataToken();
        String publicIp = fetchMetadataValue("public-ipv4", token);
        if (!publicIp.isBlank()) {
            return publicIp;
        }
        String privateIp = fetchMetadataValue("local-ipv4", token);
        if (!privateIp.isBlank()) {
            return privateIp;
        }
        return "unknown";
    }

    private String fetchMetadataToken() {
        try {
            URL url = URI.create("http://169.254.169.254/latest/api/token").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("X-aws-ec2-metadata-token-ttl-seconds", "60");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            return new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String fetchMetadataValue(String path, String token) {
        try {
            URL url = URI.create("http://169.254.169.254/latest/meta-data/" + path).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (!token.isBlank()) {
                conn.setRequestProperty("X-aws-ec2-metadata-token", token);
            }
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            return new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }
}
