package com.ledgerbridge.common;

import java.util.UUID;

/**
 * Fixed UUIDs for the 5 fraud-scenario seed customers and their accounts.
 * Matches the values in V7__seed_demo_data.sql and RISK_ENGINE_TEST_MATRIX.md.
 */
public final class TestScenarioIds {

    private TestScenarioIds() {}

    // User IDs
    public static final UUID ALICE_USER_ID  = UUID.fromString("a0000001-0000-0000-0000-000000000001");
    public static final UUID BOB_USER_ID    = UUID.fromString("b0000002-0000-0000-0000-000000000002");
    public static final UUID CAROL_USER_ID  = UUID.fromString("c0000003-0000-0000-0000-000000000003");
    public static final UUID DAVE_USER_ID   = UUID.fromString("d0000004-0000-0000-0000-000000000004");
    public static final UUID EVE_USER_ID    = UUID.fromString("e0000005-0000-0000-0000-000000000005");

    // Account IDs
    public static final UUID ALICE_ACCOUNT  = UUID.fromString("a0000001-0001-0000-0000-000000000001");
    public static final UUID BOB_ACCOUNT    = UUID.fromString("b0000002-0001-0000-0000-000000000002");
    public static final UUID CAROL_ACCOUNT  = UUID.fromString("c0000003-0001-0000-0000-000000000003");
    public static final UUID DAVE_ACCOUNT   = UUID.fromString("d0000004-0001-0000-0000-000000000004");
    public static final UUID EVE_ACCOUNT    = UUID.fromString("e0000005-0001-0000-0000-000000000005");

    // Risk profile IDs
    public static final UUID ALICE_PROFILE  = UUID.fromString("a0000001-0002-0000-0000-000000000001");
    public static final UUID BOB_PROFILE    = UUID.fromString("b0000002-0002-0000-0000-000000000002");
    public static final UUID CAROL_PROFILE  = UUID.fromString("c0000003-0002-0000-0000-000000000003");
    public static final UUID DAVE_PROFILE   = UUID.fromString("d0000004-0002-0000-0000-000000000004");
    public static final UUID EVE_PROFILE    = UUID.fromString("e0000005-0002-0000-0000-000000000005");
}
