package com.imyme.mine.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Firebase Admin SDK 초기화
 * - FIREBASE_SERVICE_ACCOUNT_KEY: Base64로 인코딩된 서비스 계정 JSON
 * - 환경변수 미설정 시 FCM 비활성화 (로컬 개발용)
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account-key:}")
    private String serviceAccountKey;

    @PostConstruct
    public void initialize() {
        if (serviceAccountKey == null || serviceAccountKey.isBlank()) {
            log.warn("[Firebase] FIREBASE_SERVICE_ACCOUNT_KEY 미설정 — FCM 비활성화");
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("[Firebase] FirebaseApp 이미 초기화됨");
            return;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(serviceAccountKey);
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("[Firebase] FirebaseApp 초기화 완료");
        } catch (Exception e) {
            log.error("[Firebase] FirebaseApp 초기화 실패 - {}", e.getMessage(), e);
        }
    }
}