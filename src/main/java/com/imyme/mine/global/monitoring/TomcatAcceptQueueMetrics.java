package com.imyme.mine.global.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TomcatAcceptQueueMetrics {

    private static final String TCP_LISTEN_STATE = "0A";
    private static final int TCP_LOCAL_ADDRESS_INDEX = 1;
    private static final int TCP_STATE_INDEX = 3;
    private static final int TCP_QUEUE_INDEX = 4;
    private static final int TCP_QUEUE_RADIX = 16;

    private final int serverPort;
    private final List<Path> tcpTables;
    private final AtomicInteger scrapeError = new AtomicInteger();

    @Autowired
    public TomcatAcceptQueueMetrics(
            MeterRegistry meterRegistry,
            @Value("${server.port:8080}") int serverPort
    ) {
        this(
                meterRegistry,
                serverPort,
                List.of(Path.of("/proc/net/tcp"), Path.of("/proc/net/tcp6"))
        );
    }

    TomcatAcceptQueueMetrics(MeterRegistry meterRegistry, int serverPort, List<Path> tcpTables) {
        this.serverPort = serverPort;
        this.tcpTables = tcpTables;

        Gauge.builder("tomcat.accept.queue.current.connections", this, TomcatAcceptQueueMetrics::readCurrentQueue)
                .description("Current Linux TCP accept queue length for the embedded Tomcat listen port")
                .tag("port", String.valueOf(serverPort))
                .register(meterRegistry);

        Gauge.builder("tomcat.accept.queue.scrape.error", scrapeError, AtomicInteger::get)
                .description("1 when the latest Tomcat accept queue scrape failed, otherwise 0")
                .tag("port", String.valueOf(serverPort))
                .register(meterRegistry);
    }

    double readCurrentQueue() {
        int total = 0;
        boolean readAnyTable = false;

        try {
            for (Path tcpTable : tcpTables) {
                if (!Files.isReadable(tcpTable)) {
                    continue;
                }

                readAnyTable = true;
                total += readCurrentQueue(tcpTable);
            }

            if (!readAnyTable) {
                scrapeError.set(1);
                return Double.NaN;
            }

            scrapeError.set(0);
            return total;
        } catch (IOException | RuntimeException exception) {
            scrapeError.set(1);
            return Double.NaN;
        }
    }

    private int readCurrentQueue(Path tcpTable) throws IOException {
        int total = 0;

        for (String line : Files.readAllLines(tcpTable)) {
            OptionalInt queueLength = parseListenQueueLength(line, serverPort);
            if (queueLength.isPresent()) {
                total += queueLength.getAsInt();
            }
        }

        return total;
    }

    static OptionalInt parseListenQueueLength(String line, int serverPort) {
        String[] columns = line.trim().split("\\s+");
        if (columns.length <= TCP_QUEUE_INDEX || !TCP_LISTEN_STATE.equals(columns[TCP_STATE_INDEX])) {
            return OptionalInt.empty();
        }

        int localPort = parseLocalPort(columns[TCP_LOCAL_ADDRESS_INDEX]);
        if (localPort != serverPort) {
            return OptionalInt.empty();
        }

        return parseReceiveQueue(columns[TCP_QUEUE_INDEX]);
    }

    private static int parseLocalPort(String localAddress) {
        int portSeparator = localAddress.lastIndexOf(':');
        if (portSeparator < 0 || portSeparator == localAddress.length() - 1) {
            return -1;
        }

        return parseHexInt(localAddress.substring(portSeparator + 1));
    }

    private static OptionalInt parseReceiveQueue(String queueColumn) {
        int queueSeparator = queueColumn.indexOf(':');
        if (queueSeparator < 0) {
            return OptionalInt.empty();
        }

        int receiveQueue = parseHexInt(queueColumn.substring(queueSeparator + 1));
        if (receiveQueue < 0) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(receiveQueue);
    }

    private static int parseHexInt(String value) {
        try {
            return Integer.parseUnsignedInt(value.toUpperCase(Locale.ROOT), TCP_QUEUE_RADIX);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}
