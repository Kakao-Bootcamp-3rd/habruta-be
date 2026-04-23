package com.imyme.mine.domain.card.service;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.card.dto.CardCreateRequest;
import com.imyme.mine.domain.card.dto.CardDetailResponse;
import com.imyme.mine.domain.card.dto.CardListResponse;
import com.imyme.mine.domain.card.dto.CardResponse;
import com.imyme.mine.domain.card.dto.CardUpdateRequest;
import com.imyme.mine.domain.card.dto.CardUpdateResponse;
import com.imyme.mine.domain.card.entity.Card;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.card.repository.CardRepository;
import com.imyme.mine.domain.category.entity.Category;
import com.imyme.mine.domain.category.repository.CategoryRepository;
import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import com.imyme.mine.domain.notification.entity.NotificationType;
import com.imyme.mine.domain.notification.service.NotificationCreatorService;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardService {

    private final CardRepository cardRepository;
    private final CardAttemptRepository cardAttemptRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final KeywordRepository keywordRepository;
    private final NotificationCreatorService notificationCreatorService;

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    @Transactional
    public CardResponse createCard(Long userId, CardCreateRequest request) {
        log.debug("카드 생성 시작 - userId: {}, categoryId: {}, keywordId: {}",
            userId, request.categoryId(), request.keywordId());

        if (!keywordRepository.existsByIdAndCategoryId(request.keywordId(), request.categoryId())) {
            throw new BusinessException(ErrorCode.KEYWORD_NOT_FOUND);
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Category category = categoryRepository.getReferenceById(request.categoryId());
        Keyword keyword = keywordRepository.getReferenceById(request.keywordId());

        Card card = Card.builder()
            .user(user)
            .category(category)
            .keyword(keyword)
            .title(request.title())
            .build();

        Card savedCard = cardRepository.save(card);

        int prevLevel = user.getLevel();
        user.incrementTotalCardCount();

        log.info("카드 생성 완료 - cardId: {}, userId: {}", savedCard.getId(), userId);

        if (user.getLevel() > prevLevel) {
            notificationCreatorService.create(
                userId,
                NotificationType.LEVEL_UP,
                "레벨업!",
                "Lv." + user.getLevel() + " 달성! 계속 성장하고 있어요.",
                null,
                null
            );
        }

        return CardResponse.from(savedCard);
    }

    @Transactional
    public CardUpdateResponse updateCardTitle(Long userId, Long cardId, CardUpdateRequest request) {
        log.debug("카드 제목 수정 시작 - userId: {}, cardId: {}", userId, cardId);

        Card card = cardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

        card.updateTitle(request.title());

        log.info("카드 제목 수정 완료 - cardId: {}, newTitle: {}", cardId, request.title());

        return CardUpdateResponse.from(card);
    }

    @Transactional
    public void deleteCard(Long userId, Long cardId) {
        log.debug("카드 삭제 시작 - userId: {}, cardId: {}", userId, cardId);

        Card card = cardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

        cardRepository.delete(card);

        if (card.getAttemptCount() > 0) {
            card.getUser().decrementActiveCardCount();
        }

        log.info("카드 삭제 완료 - cardId: {}, userId: {}", cardId, userId);
    }

    public CardListResponse getCards(
        Long userId,
        Long categoryId,
        List<Long> keywordIds,
        boolean excludeGhost,
        String sort,
        String cursor,
        Integer limit
    ) {
        log.debug("카드 목록 조회 - userId: {}, categoryId: {}, keywordIds: {}, excludeGhost: {}, sort: {}, cursor: {}",
            userId, categoryId, keywordIds, excludeGhost, sort, cursor);

        // Pagination 공격 방지: 최대값 제한
        int pageSize = (limit != null && limit > 0) ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        boolean isRecentFirst = !"oldest".equalsIgnoreCase(sort);
        PageRequest pageRequest = PageRequest.of(0, pageSize + 1);

        List<Card> cards;

        if (cursor == null || cursor.isEmpty()) {
            cards = isRecentFirst
                ? cardRepository.findCardsRecentFirst(userId, categoryId, keywordIds, excludeGhost, pageRequest)
                : cardRepository.findCardsOldestFirst(userId, categoryId, keywordIds, excludeGhost, pageRequest);
        } else {
            CursorInfo cursorInfo = decodeCursor(cursor);
            cards = isRecentFirst
                ? cardRepository.findCardsRecentAfterCursor(userId, categoryId, keywordIds, excludeGhost,
                    cursorInfo.createdAt(), cursorInfo.id(), pageRequest)
                : cardRepository.findCardsOldestAfterCursor(userId, categoryId, keywordIds, excludeGhost,
                    cursorInfo.createdAt(), cursorInfo.id(), pageRequest);
        }

        log.info("카드 목록 조회 완료 - userId: {}, 조회 건수: {}", userId, Math.min(cards.size(), pageSize));

        return CardListResponse.of(cards, pageSize);
    }

    public CardDetailResponse getCardDetail(Long userId, Long cardId) {
        log.debug("카드 상세 조회 - userId: {}, cardId: {}", userId, cardId);

        Card card = cardRepository.findByIdAndUserIdWithRelations(cardId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

        List<CardAttempt> attempts = cardAttemptRepository.findByCardIdOrderByAttemptNoDesc(cardId);

        log.info("카드 상세 조회 완료 - cardId: {}, 시도 수: {}", cardId, attempts.size());

        return CardDetailResponse.of(card, attempts);
    }

    private record CursorInfo(LocalDateTime createdAt, Long id) {}

    private CursorInfo decodeCursor(String cursor) {
        try {
            String decoded = new String(
                java.util.Base64.getUrlDecoder().decode(cursor)
            );

            int lastUnderscore = decoded.lastIndexOf('_');
            if (lastUnderscore == -1) {
                log.warn("Invalid cursor format: missing underscore - cursor: {}", cursor);
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }

            String createdAtStr = decoded.substring(0, lastUnderscore);
            String idStr = decoded.substring(lastUnderscore + 1);

            // DateTimeFormatter를 명시적으로 사용하여 파싱
            LocalDateTime createdAt = LocalDateTime.parse(
                createdAtStr,
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
            );
            Long id = Long.parseLong(idStr);

            return new CursorInfo(createdAt, id);

        } catch (DateTimeParseException e) {
            log.warn("Invalid cursor format: invalid datetime - cursor: {}", cursor, e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        } catch (NumberFormatException e) {
            log.warn("Invalid cursor format: invalid id - cursor: {}", cursor, e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid cursor format: invalid base64 - cursor: {}", cursor, e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        } catch (Exception e) {
            log.warn("Cursor decoding failed - cursor: {}", cursor, e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
