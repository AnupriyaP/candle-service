package com.candleservice.ingestion.simulator;

import com.candleservice.domain.BidAskEvent;
import com.candleservice.ingestion.kafka.BidAskProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "candle.simulator.enabled", havingValue = "true")
public class BidAskSimulator {

    private static final Logger log = LoggerFactory.getLogger(BidAskSimulator.class);

    private final BidAskProducer producer;

    @Value("${candle.symbols}")
    private List<String> symbols;

    private static final Map<String, Double> BASE_PRICES = Map.of(
            "BTC-USD", 65000.0,
            "ETH-USD",  3500.0,
            "SOL-USD",   150.0,
            "BNB-USD",   600.0
    );

    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public BidAskSimulator(BidAskProducer producer) {
        this.producer = producer;
    }

    @Scheduled(fixedRateString = "${candle.simulator.rate-ms:500}")
    public void simulate() {
        long now = System.currentTimeMillis();

        for (String symbol : symbols) {
            double mid = nextPrice(symbol);
            double spread = mid * 0.0001;
            double bid = mid - spread / 2;
            double ask = mid + spread / 2;

            BidAskEvent event = new BidAskEvent(symbol, bid, ask, now);
            producer.publish(event);

            log.debug("Simulated event symbol={} bid={} ask={}",
                    symbol,
                    String.format("%.2f", bid),
                    String.format("%.2f", ask));
        }
    }

    private double nextPrice(String symbol) {
        double base = BASE_PRICES.getOrDefault(symbol, 100.0);
        double last = lastPrices.getOrDefault(symbol, base);
        double change = last * (random.nextDouble() * 0.002 - 0.001);
        double next = last + change;
        double maxDrift = base * 0.05;
        next = Math.max(base - maxDrift, Math.min(base + maxDrift, next));
        lastPrices.put(symbol, next);
        return next;
    }
}