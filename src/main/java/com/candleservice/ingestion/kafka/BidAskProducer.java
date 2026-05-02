package com.candleservice.ingestion.kafka;

import com.candleservice.config.KafkaTopicConfig;
import com.candleservice.domain.BidAskEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class BidAskProducer {

    private static final Logger log = LoggerFactory.getLogger(BidAskProducer.class);

    private final KafkaTemplate<String, BidAskEvent> kafkaTemplate;

    public BidAskProducer(KafkaTemplate<String, BidAskEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(BidAskEvent event) {
        CompletableFuture<SendResult<String, BidAskEvent>> future =
                kafkaTemplate.send(
                        KafkaTopicConfig.TOPIC_BID_ASK,
                        event.symbol(),
                        event
                );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event for symbol={} error={}",
                        event.symbol(), ex.getMessage());
            } else {
                log.debug("Published event symbol={} partition={} offset={}",
                        event.symbol(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}