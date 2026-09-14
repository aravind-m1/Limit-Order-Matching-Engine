package com.matchforge;

import com.matchforge.testharness.TestRunner;
import com.matchforge.tests.MatchingLogicTest;
import com.matchforge.tests.OrderBookTest;
import com.matchforge.tests.ReplayDeterminismTest;
import com.matchforge.tests.SequencerConcurrencyStressTest;
import com.matchforge.tests.WalRecoveryTest;

public final class RunTests {
    public static void main(String[] args) {
        boolean allPassed = TestRunner.runAll(
                OrderBookTest.class,
                MatchingLogicTest.class,
                WalRecoveryTest.class,
                SequencerConcurrencyStressTest.class,
                ReplayDeterminismTest.class
        );
        System.exit(allPassed ? 0 : 1);
    }
}
