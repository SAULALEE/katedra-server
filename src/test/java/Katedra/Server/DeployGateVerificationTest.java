package Katedra.Server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * TEMPORARY. Deliberately failing test used once to prove that the CI deploy gate blocks a red
 * build: the deploy job must show as skipped and production must keep serving the previous commit.
 * Reverted immediately after the run.
 */
class DeployGateVerificationTest {

    @Test
    void deployMustNotHappenWhenTheSuiteIsRed() {
        fail("Deliberate failure verifying the CI deploy gate.");
    }
}
