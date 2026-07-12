package com.votewithblock.gateway.integration;

import com.votewithblock.gateway.model.VoterAccount;
import com.votewithblock.gateway.service.PasswordService;
import com.votewithblock.gateway.service.PreAuthTokenService;
import com.votewithblock.gateway.service.SessionTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthControllerIntegrationTest {

    private PasswordService passwordService;
    private PreAuthTokenService preAuthTokenService;
    private VoterAccount testVoter;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
        testVoter = new VoterAccount("US-98420-A", "Test Voter", false, null, null, null, "ACTIVE", 0, null);
    }

    @Test
    void passwordService_shouldHashAndVerify() {
        String salt = passwordService.newSalt();
        String hash = passwordService.hashPassword("testpassword", salt);

        assertNotNull(salt);
        assertNotNull(hash);
        assertTrue(passwordService.matches("testpassword", salt, hash));
        assertFalse(passwordService.matches("wrongpassword", salt, hash));
    }

    @Test
    void passwordService_shouldGenerateUniqueSalts() {
        String salt1 = passwordService.newSalt();
        String salt2 = passwordService.newSalt();

        assertNotEquals(salt1, salt2);
    }

    @Test
    void voterAccount_shouldHaveCorrectDefaults() {
        VoterAccount voter = new VoterAccount();

        assertEquals("NOT_ENROLLED", voter.getBiometricStatus());
        assertEquals(0, voter.getFailedBiometricAttempts());
        assertFalse(voter.isHasVoted());
    }

    @Test
    void voterAccount_shouldStoreCredentials() {
        String salt = passwordService.newSalt();
        String hash = passwordService.hashPassword("password123", salt);

        testVoter.setPasswordSalt(salt);
        testVoter.setPasswordHash(hash);

        assertEquals(salt, testVoter.getPasswordSalt());
        assertEquals(hash, testVoter.getPasswordHash());
        assertTrue(passwordService.matches("password123", testVoter.getPasswordSalt(), testVoter.getPasswordHash()));
    }

    @Test
    void voterAccount_shouldTrackBiometricStatus() {
        testVoter.setBiometricStatus("ACTIVE");
        assertEquals("ACTIVE", testVoter.getBiometricStatus());

        testVoter.setBiometricStatus("NOT_ENROLLED");
        assertEquals("NOT_ENROLLED", testVoter.getBiometricStatus());
    }

    @Test
    void voterAccount_shouldTrackVotingState() {
        assertFalse(testVoter.isHasVoted());

        testVoter.setHasVoted(true);
        assertTrue(testVoter.isHasVoted());
    }

    @Test
    void voterAccount_shouldTrackFailedAttempts() {
        assertEquals(0, testVoter.getFailedBiometricAttempts());

        testVoter.setFailedBiometricAttempts(5);
        assertEquals(5, testVoter.getFailedBiometricAttempts());
    }
}
