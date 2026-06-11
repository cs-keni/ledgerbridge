package com.ledgerbridge.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerbridge.account.model.Account;
import com.ledgerbridge.account.model.AccountStatus;
import com.ledgerbridge.account.model.AccountType;
import com.ledgerbridge.account.repository.AccountRepository;
import com.ledgerbridge.common.KafkaIntegrationTest;
import com.ledgerbridge.common.TestScenarioIds;
import com.ledgerbridge.common.config.KafkaConfig;
import com.ledgerbridge.transaction.dto.TransactionRequest;
import com.ledgerbridge.transaction.service.TransactionService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionIntegrationTest extends KafkaIntegrationTest {

    @Autowired TransactionService transactionService;
    @Autowired AccountRepository accountRepository;
    @Autowired ObjectMapper objectMapper;
    @Autowired EmbeddedKafkaBroker embeddedKafkaBroker;

    // Use a seeded user from V7 migration to satisfy account.user_id FK
    private static final java.util.UUID TEST_USER_ID = TestScenarioIds.ALICE_USER_ID;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUpConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "it-group-" + System.nanoTime(), "false", embeddedKafkaBroker);
        // KafkaTestUtils defaults key deserializer to Integer — override to String
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // auto.offset.reset=latest: once the consumer has assignment, it starts at the
        // CURRENT end — so we poll here (before producing) to anchor the position,
        // and subsequent polls in the test only see messages produced after this point.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumer = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        consumer.subscribe(List.of(KafkaConfig.TRANSACTION_EVENTS_TOPIC));
        // Drive the coordinator loop until partitions are assigned
        long deadline = System.currentTimeMillis() + 5_000;
        while (consumer.assignment().isEmpty() && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(200));
        }
    }

    @AfterEach
    void closeConsumer() {
        consumer.close();
    }

    @Test
    void deposit_publishesEventToKafka() throws Exception {
        Account account = buildAccount("888" + System.nanoTime() % 1_000_000_000L,
                new BigDecimal("1000.00"));
        account = accountRepository.save(account);

        transactionService.deposit(TEST_USER_ID,
                new TransactionRequest(account.getId(), new BigDecimal("250.00"), "FOOD", "Grocery run"),
                "corr-integ-test-01");

        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                consumer, KafkaConfig.TRANSACTION_EVENTS_TOPIC, Duration.ofSeconds(10));

        assertThat(record.key()).isEqualTo(TEST_USER_ID.toString());

        JsonNode payload = objectMapper.readTree(record.value());
        assertThat(payload.get("type").asText()).isEqualTo("DEPOSIT");
        assertThat(new BigDecimal(payload.get("amount").asText())).isEqualByComparingTo("250.00");
        assertThat(payload.get("correlationId").asText()).isEqualTo("corr-integ-test-01");
        assertThat(payload.get("currency").asText()).isEqualTo("USD");

        Account updated = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo("1250.00");
    }

    @Test
    void withdraw_publishesEventToKafka() throws Exception {
        Account account = buildAccount("777" + System.nanoTime() % 1_000_000_000L,
                new BigDecimal("500.00"));
        account = accountRepository.save(account);

        transactionService.withdraw(TEST_USER_ID,
                new TransactionRequest(account.getId(), new BigDecimal("100.00"), null, "ATM"), null);

        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                consumer, KafkaConfig.TRANSACTION_EVENTS_TOPIC, Duration.ofSeconds(10));

        JsonNode payload = objectMapper.readTree(record.value());
        assertThat(payload.get("type").asText()).isEqualTo("WITHDRAWAL");
        assertThat(new BigDecimal(payload.get("amount").asText())).isEqualByComparingTo("100.00");

        Account updated = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo("400.00");
    }

    private Account buildAccount(String accountNumber, BigDecimal balance) {
        Account a = new Account();
        a.setAccountNumber(accountNumber.length() > 20 ? accountNumber.substring(0, 20) : accountNumber);
        a.setUserId(TEST_USER_ID);
        a.setType(AccountType.CHECKING);
        a.setBalance(balance);
        a.setCurrency("USD");
        a.setStatus(AccountStatus.ACTIVE);
        return a;
    }
}
