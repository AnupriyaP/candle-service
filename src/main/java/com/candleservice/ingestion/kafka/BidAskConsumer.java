package com.candleservice.ingestion.kafka;

import com.candleservice.aggregation.AggregationDispatcher;
import com.candleservice.config.KafkaTopicConfig;
import com.candleservice.domain.BidAskEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class BidAskConsumer {

    private static final Logger log = LoggerFactory.getLogger(BidAskConsumer.class);

    private final AggregationDispatcher dispatcher;

    public BidAskConsumer(AggregationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_BID_ASK,
            groupId = "candle-aggregator",
            concurrency = "3",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            BidAskEvent event,
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.debug("Received event symbol={} partition={} offset={}",
                event.symbol(), partition, offset);

        try {
            dispatcher.dispatch(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process event symbol={} partition={} offset={} error={}",
                    event.symbol(), partition, offset, e.getMessage(), e);
        }
    }
}