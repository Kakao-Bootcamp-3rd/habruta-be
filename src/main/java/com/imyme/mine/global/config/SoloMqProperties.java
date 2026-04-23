package com.imyme.mine.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Solo MQ 설정
 * - feature flag로 MQ / HTTP 폴링 경로 전환
 * - Exchange, Queue, Routing Key 이름 관리
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "solo.mq")
public class SoloMqProperties {

    private boolean enabled = false;

    private String exchange = "solo.direct";

    private Queue queue = new Queue();

    private Routing routing = new Routing();

    @Getter
    @Setter
    public static class Queue {
        private String sttResponse = "solo.stt.response";
        private String feedbackResponse = "solo.feedback.response";
    }

    @Getter
    @Setter
    public static class Routing {
        private String sttRequest = "solo.stt.request";
        private String feedbackRequest = "solo.feedback.request";
    }
}