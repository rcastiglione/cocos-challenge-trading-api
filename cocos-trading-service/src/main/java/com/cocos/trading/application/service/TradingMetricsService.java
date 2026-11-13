package com.cocos.trading.application.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Dedicated telemetry service that encapsulates Micrometer metric definitions, tag keys,
 * and recording operations to keep core application services decoupled from observability boilerplate.
 *
 * <p><b>Thread-Safety &amp; Concurrency Guarantees:</b>
 * <ul>
 *   <li><b>Stateless Singleton:</b> Holds only immutable {@code static final} constants and a {@code final}
 *       reference to the thread-safe {@link MeterRegistry}. Contains zero mutable instance or static state.</li>
 *   <li><b>Stack-Confined Latency Samples:</b> {@link #startOrderTimer()} returns a new, immutable
 *       {@link Timer.Sample} per invocation capturing {@code System.nanoTime()}. Callers confine the sample
 *       to their local thread stack frame, guaranteeing zero cross-thread interference under concurrent requests.</li>
 *   <li><b>Lock-Free Atomic Accumulation:</b> Meter lookups in {@link MeterRegistry} are backed by a concurrent map
 *       keyed by {@code Meter.Id}, and increments/recordings use lock-free striped adders ({@code LongAdder} /
 *       {@code DoubleAdder}) safe for high-throughput multi-threaded execution and horizontal replica scaling.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TradingMetricsService {

    static final String ORDERS_PROCESSED_METRIC = "cocos.orders.processed.total";
    static final String ORDER_EXECUTION_LATENCY_METRIC = "cocos.orders.execution.latency";

    private static final String TAG_STATUS = "status";
    private static final String TAG_TYPE = "type";
    private static final String TAG_SIDE = "side";

    private final MeterRegistry meterRegistry;

    /**
     * Starts a Micrometer {@link Timer.Sample} to measure order execution latency.
     *
     * @return the started timer sample
     */
    public Timer.Sample startOrderTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Stops the given {@link Timer.Sample} and records the order execution latency tagged by status, type, and side.
     *
     * @param sample the active timer sample started via {@link #startOrderTimer()}
     * @param status the resulting order status (e.g. FILLED, REJECTED, NEW)
     * @param type   the order type (MARKET, LIMIT)
     * @param side   the order side (BUY, SELL, CASH_IN, CASH_OUT)
     */
    public void stopOrderTimer(Timer.Sample sample, String status, String type, String side) {
        if (sample == null) {
            return;
        }
        sample.stop(
                Timer.builder(ORDER_EXECUTION_LATENCY_METRIC)
                        .description("Time taken to evaluate and persist a trading or transfer order")
                        .tag(TAG_STATUS, status)
                        .tag(TAG_TYPE, type)
                        .tag(TAG_SIDE, side)
                        .publishPercentileHistogram()
                        .register(meterRegistry)
        );
    }

    /**
     * Increments the processed orders counter tagged by status, type, and side.
     *
     * @param status the order status (e.g. FILLED, REJECTED, NEW, CANCELLED)
     * @param type   the order type (MARKET, LIMIT)
     * @param side   the order side (BUY, SELL, CASH_IN, CASH_OUT)
     */
    public void recordOrderProcessed(String status, String type, String side) {
        Counter.builder(ORDERS_PROCESSED_METRIC)
                .description("Total number of orders processed tagged by status, type, and side")
                .tag(TAG_STATUS, status)
                .tag(TAG_TYPE, type)
                .tag(TAG_SIDE, side)
                .register(meterRegistry)
                .increment();
    }
}

