package io.github.yesh.paycore.ledger.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
@ConditionalOnClass(KafkaAdmin.class)
public class KafkaTopicConfig {

  @Bean
  public NewTopic ledgerDomainEventsTopic(
      @Value("${paycore.ledger.outbox.topic:ledger.domain-events}") String topic) {
    return TopicBuilder.name(topic).partitions(6).replicas(1).build();
  }

  @Bean
  public NewTopic ledgerDomainEventsDlqTopic(
      @Value("${paycore.settlement.kafka-dlq-topic:ledger.domain-events.dlq}") String dlqTopic) {
    return TopicBuilder.name(dlqTopic).partitions(6).replicas(1).build();
  }
}
