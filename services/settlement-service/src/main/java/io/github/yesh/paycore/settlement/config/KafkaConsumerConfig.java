package io.github.yesh.paycore.settlement.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

  private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

  @Bean
  public Counter dlqCounter(MeterRegistry meterRegistry) {
    return Counter.builder("settlement_consumer_dlq_total")
        .description("Total number of poison pill messages routed to DLQ")
        .register(meterRegistry);
  }

  @Bean
  @ConditionalOnBean(KafkaOperations.class)
  public CommonErrorHandler kafkaErrorHandler(
      KafkaOperations<Object, Object> kafkaOperations,
      @Value("${paycore.settlement.kafka-dlq-topic:ledger.domain-events.dlq}") String dlqTopic,
      Counter dlqCounter) {

    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaOperations,
            (consumerRecord, exception) -> {
              log.error(
                  "Consumer failed after 3 retries for key={}, partition={}, offset={}. Routing to DLQ topic {}",
                  consumerRecord.key(),
                  consumerRecord.partition(),
                  consumerRecord.offset(),
                  dlqTopic,
                  exception);
              dlqCounter.increment();
              return new TopicPartition(dlqTopic, consumerRecord.partition());
            });

    FixedBackOff backOff = new FixedBackOff(1000L, 3);
    return new DefaultErrorHandler(recoverer, backOff);
  }

  @Bean
  @ConditionalOnBean(KafkaOperations.class)
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> consumerFactory, CommonErrorHandler kafkaErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(kafkaErrorHandler);
    return factory;
  }
}
