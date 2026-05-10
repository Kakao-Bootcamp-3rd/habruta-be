package com.imyme.mine.global.tracing;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

@Component
public class HostInfoProvider {

    @Getter
    private final String privateIp;

    public HostInfoProvider() {
        this.privateIp = fetchEc2PrivateIp();
    }

    private String fetchEc2PrivateIp() {
        try {
            URL url = URI.create("http://169.254.169.254/latest/meta-data/local-ipv4").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            return new String(conn.getInputStream().readAllBytes()).trim();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
