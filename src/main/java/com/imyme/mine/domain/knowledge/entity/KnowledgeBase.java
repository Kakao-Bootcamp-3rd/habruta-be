package com.imyme.mine.domain.knowledge.entity;

import com.imyme.mine.domain.keyword.entity.Keyword;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 지식 베이스 엔티티
 * - 사용자 업로드 지식 콘텐츠 저장
 * - RAG용 벡터 임베딩 저장 (1024차원)
 * - SHA-256 콘텐츠 해시로 중복 방지
 */
@Entity
@Table(
    name = "knowledge_base",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_kb_content_hash",
            columnNames = {"content_hash"}
        )
    },
    indexes = {
        @Index(name = "idx_kb_keyword_active", columnList = "keyword_id"), // Partial Index 조건은 JPA로 표현 불가하므로 이름만 명시
        @Index(name = "idx_kb_created_at", columnList = "created_at DESC")
    }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 연관 키워드 (삭제 시 SET NULL 정책은 DB 레벨에서 처리됨)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id")
    private Keyword keyword;

    // 지식 콘텐츠 원문
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 벡터 임베딩: qwen3-embedding-0.6b 모델 기준 1024차원
    // PostgreSQL vector 타입으로 명시적 형변환(Casting) 추가
    @Column(name = "embedding", columnDefinition = "vector")
    @ColumnTransformer(write = "?::vector")
    private String embedding;

    // 콘텐츠 해시: SHA-256 해시값 (64자)
    @Column(name = "content_hash", nullable = false, length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String contentHash;

    // 활성화 여부
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("TRUE")
    @Builder.Default
    private Boolean isActive = true;

    // 타임스탬프
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // --- 비즈니스 로직 ---

    // 콘텐츠 업데이트 (임베딩 초기화)
    public void updateContent(String newContent, String newContentHash) {
        this.content = newContent;
        this.contentHash = newContentHash;
        this.embedding = null; // 재발급 필요 상태로 변경
    }

    public void updateEmbedding(String embeddingVector) {
        this.embedding = embeddingVector;
    }

    public void toggleActive() {
        this.isActive = !this.isActive;
    }

    public boolean hasEmbedding() {
        return this.embedding != null && !this.embedding.isBlank();
    }
}
