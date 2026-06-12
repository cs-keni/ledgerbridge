package com.ledgerbridge;

import com.ledgerbridge.account.repository.AccountRepository;
import com.ledgerbridge.auth.repository.UserRepository;
import com.ledgerbridge.common.AbstractIntegrationTest;
import com.ledgerbridge.common.TestScenarioIds;
import com.ledgerbridge.risk.repository.CustomerRiskProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Flyway migrations run clean and seed data is present.
 * Replaces the Phase 0 smoke test (LedgerBridgeApplicationTests).
 */
class SchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRiskProfileRepository profileRepository;

    @Test
    void migrations_run_clean_and_context_loads() {
        // If this test passes, Flyway ran all V1–V7 migrations without error
        // and Hibernate validated the schema against entity definitions.
        assertThat(userRepository).isNotNull();
    }

    @Test
    void seed_data_has_five_scenario_users() {
        // V12 adds a 6th user (DEMO_ACTOR — demo@ledgerbridge.io) for Phase 6 local testing
        assertThat(userRepository.count()).isEqualTo(6);
        assertThat(userRepository.findById(TestScenarioIds.ALICE_USER_ID)).isPresent();
        assertThat(userRepository.findById(TestScenarioIds.BOB_USER_ID)).isPresent();
        assertThat(userRepository.findById(TestScenarioIds.CAROL_USER_ID)).isPresent();
        assertThat(userRepository.findById(TestScenarioIds.DAVE_USER_ID)).isPresent();
        assertThat(userRepository.findById(TestScenarioIds.EVE_USER_ID)).isPresent();
    }

    @Test
    void seed_data_has_five_accounts() {
        assertThat(accountRepository.count()).isEqualTo(5);
        assertThat(accountRepository.findById(TestScenarioIds.ALICE_ACCOUNT)).isPresent();
    }

    @Test
    void seed_data_has_five_risk_profiles_with_precomputed_stats() {
        assertThat(profileRepository.count()).isEqualTo(5);

        var aliceProfile = profileRepository.findByUserId(TestScenarioIds.ALICE_USER_ID);
        assertThat(aliceProfile).isPresent();
        assertThat(aliceProfile.get().getTransactionCount()).isEqualTo(30);
        assertThat(aliceProfile.get().getAmountMean()).isNotNull();

        var eveProfile = profileRepository.findByUserId(TestScenarioIds.EVE_USER_ID);
        assertThat(eveProfile).isPresent();
        assertThat(eveProfile.get().getTransactionCount()).isEqualTo(5);
        assertThat(eveProfile.get().getTypicalCounterparties()).isNotEmpty();
    }
}
