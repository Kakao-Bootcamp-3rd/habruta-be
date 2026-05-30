package com.imyme.mine.domain.card.service;

import com.imyme.mine.domain.notification.entity.NotificationType;
import com.imyme.mine.domain.notification.service.NotificationCreatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CardCountBatchService {

    private static final String UPDATE_CARD_COUNT_SQL = """
        WITH deltas AS (
            SELECT *
            FROM unnest(?::bigint[], ?::integer[]) AS d(user_id, delta)
        ),
        before_update AS (
            SELECT u.id, u.level AS old_level, d.delta
            FROM users u
            JOIN deltas d ON d.user_id = u.id
            WHERE u.deleted_at IS NULL
        ),
        updated_user AS (
            UPDATE users u
            SET total_card_count = u.total_card_count + b.delta,
                level = CAST(floor((sqrt(1 + 8.0 * (u.total_card_count + b.delta) / 5.0) - 1) / 2) + 1 AS integer),
                updated_at = now()
            FROM before_update b
            WHERE u.id = b.id
            RETURNING u.id AS user_id, b.old_level AS old_level, u.level AS new_level
        )
        SELECT user_id, old_level, new_level
        FROM updated_user
        """;

    private final JdbcTemplate jdbcTemplate;
    private final NotificationCreatorService notificationCreatorService;

    @Transactional
    public int applyDeltas(Map<Long, Integer> deltas) {
        if (deltas.isEmpty()) {
            return 0;
        }

        List<LevelUpdateResult> results = updateCardCounts(deltas);
        results.stream()
            .filter(result -> result.newLevel() > result.oldLevel())
            .forEach(result -> notificationCreatorService.create(
                result.userId(),
                NotificationType.LEVEL_UP,
                "레벨업!",
                "Lv." + result.newLevel() + " 달성! 계속 성장하고 있어요.",
                null,
                null
            ));

        return results.size();
    }

    private List<LevelUpdateResult> updateCardCounts(Map<Long, Integer> deltas) {
        Long[] userIds = deltas.keySet().toArray(Long[]::new);
        Integer[] counts = deltas.values().toArray(Integer[]::new);

        return jdbcTemplate.execute((ConnectionCallback<List<LevelUpdateResult>>) connection -> {
            Array userIdArray = connection.createArrayOf("bigint", userIds);
            Array countArray = connection.createArrayOf("integer", counts);

            try (PreparedStatement statement = connection.prepareStatement(UPDATE_CARD_COUNT_SQL)) {
                statement.setArray(1, userIdArray);
                statement.setArray(2, countArray);

                List<LevelUpdateResult> results = new ArrayList<>();
                try (var resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        results.add(new LevelUpdateResult(
                            resultSet.getLong("user_id"),
                            resultSet.getInt("old_level"),
                            resultSet.getInt("new_level")
                        ));
                    }
                }
                return results;
            } finally {
                userIdArray.free();
                countArray.free();
            }
        });
    }

    private record LevelUpdateResult(Long userId, int oldLevel, int newLevel) {
    }
}
