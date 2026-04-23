package com.imyme.mine.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Knowledge 배치 작업 설정
 * - application.yml에서 값 주입
 * - 매일 자정 실행되는 Knowledge 업데이트 배치 제어
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "knowledge.batch")
public class KnowledgeProperties {

    /**
     * 배치 작업 활성화 여부
     * - false로 설정하면 스케줄러가 실행되지 않음
     */
    private boolean enabled = true;

    /**
     * 한 번에 처리할 피드백 배치 크기
     * - AI 서버 제한: 최대 100개
     */
    private int batchSize = 50;

    /**
     * 키워드 간 처리 지연 시간 (밀리초)
     * - AI 서버 부하 방지
     */
    private long keywordDelayMs = 1000;

    /**
     * 배치 간 처리 지연 시간 (밀리초)
     * - OpenAI API rate limit 방지
     */
    private long batchDelayMs = 2000;

    /**
     * 활성화된 키워드 ID 목록
     * - 빈 리스트면 모든 키워드 처리
     * - 값이 있으면 해당 키워드만 처리
     */
    private List<Long> enabledKeywords = new ArrayList<>();

    /**
     * 유사도 검색 시 가져올 최대 개수
     * - AI 서버 제한: 최대 10개
     */
    private int maxSimilarCount = 5;

    /**
     * 유사도 임계값 (0.0 ~ 1.0)
     * - 이 값보다 높은 유사도를 가진 지식만 가져옴
     */
    private double similarityThreshold = 0.7;

    /**
     * 처리할 피드백이 없을 때 대기 시간 (밀리초)
     * - 기본값: 120000ms (2분)
     */
    private long idleSleepMs = 120000;
}