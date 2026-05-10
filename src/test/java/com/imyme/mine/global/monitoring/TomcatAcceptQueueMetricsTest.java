package com.imyme.mine.global.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TomcatAcceptQueueMetricsTest {

    @TempDir
    Path tempDir;

    @Test
    void parseListenQueueLength_returnsReceiveQueueForMatchingListenPort() {
        String line = "0: 00000000:1F90 00000000:0000 0A 00000000:00000003 00:00000000 00000000 0 0 1 1";

        OptionalInt queueLength = TomcatAcceptQueueMetrics.parseListenQueueLength(line, 8080);

        assertThat(queueLength).hasValue(3);
    }

    @Test
    void parseListenQueueLength_ignoresDifferentPort() {
        String line = "0: 00000000:1F91 00000000:0000 0A 00000000:00000003 00:00000000 00000000 0 0 1 1";

        OptionalInt queueLength = TomcatAcceptQueueMetrics.parseListenQueueLength(line, 8080);

        assertThat(queueLength).isEmpty();
    }

    @Test
    void parseListenQueueLength_ignoresNonListenSocket() {
        String line = "0: 00000000:1F90 00000000:0000 01 00000000:00000003 00:00000000 00000000 0 0 1 1";

        OptionalInt queueLength = TomcatAcceptQueueMetrics.parseListenQueueLength(line, 8080);

        assertThat(queueLength).isEmpty();
    }

    @Test
    void readCurrentQueue_sumsReadableTcpTables() throws IOException {
        Path tcp = tempDir.resolve("tcp");
        Path tcp6 = tempDir.resolve("tcp6");
        Files.writeString(tcp, """
                sl  local_address rem_address   st tx_queue:rx_queue tr tm->when retrnsmt   uid  timeout inode
                 0: 00000000:1F90 00000000:0000 0A 00000000:00000002 00:00000000 00000000 0 0 1 1
                """);
        Files.writeString(tcp6, """
                sl  local_address rem_address   st tx_queue:rx_queue tr tm->when retrnsmt   uid  timeout inode
                 0: 00000000000000000000000000000000:1F90 00000000000000000000000000000000:0000 0A 00000000:00000004 00:00000000 00000000 0 0 1 1
                """);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TomcatAcceptQueueMetrics metrics = new TomcatAcceptQueueMetrics(meterRegistry, 8080, List.of(tcp, tcp6));

        assertThat(metrics.readCurrentQueue()).isEqualTo(6.0);
        assertThat(meterRegistry.get("tomcat.accept.queue.current.connections").gauge().value())
                .isEqualTo(6.0);
        assertThat(meterRegistry.get("tomcat.accept.queue.scrape.error").gauge().value())
                .isZero();
    }
}
