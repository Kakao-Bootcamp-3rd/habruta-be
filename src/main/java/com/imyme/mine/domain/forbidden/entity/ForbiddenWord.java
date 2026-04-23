package com.imyme.mine.domain.forbidden.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * 금지어 엔티티
 * - 닉네임 등에서 사용할 수 없는 단어 목록
 * - Master Data 성격이 강하므로 캐싱 권장
 */
@Entity
@Table(
    name = "forbidden_words",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_forbidden_word",
            columnNames = {"word"}
        )
    },
    indexes = {
        @Index(name = "idx_forbidden_type", columnList = "type")
    }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class ForbiddenWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 금지어 (중복 불가)
    @Column(name = "word", nullable = false, unique = true, length = 50)
    private String word;

    // 금지어 유형 (적용 범위)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    @ColumnDefault("'COMMON'")
    private ForbiddenWordType type;

    // 생성 일시
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.type == null) {
            this.type = ForbiddenWordType.COMMON;
        }
    }

    // 편의 메서드: 해당 타입에 적용되는지 확인
    public boolean isApplicableTo(ForbiddenWordType targetType) {
        return this.type == ForbiddenWordType.COMMON || this.type == targetType;
    }
}
