package com.votewithblock.gateway.integration;

import com.votewithblock.gateway.service.PasswordService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationValidationTest {

    @Test
    void applicationYml_shouldExist() {
        Resource resource = new ClassPathResource("application.yml");
        assertTrue(resource.exists(), "application.yml should exist");
    }

    @Test
    void applicationYml_shouldContainDatabaseConfig() {
        Resource resource = new ClassPathResource("application.yml");
        try (Scanner scanner = new Scanner(resource.getInputStream())) {
            String content = scanner.useDelimiter("\\A").next();
            assertTrue(content.contains("datasource"), "Should contain datasource config");
            assertTrue(content.contains("url"), "Should contain URL config");
            assertTrue(content.contains("username"), "Should contain username config");
        } catch (Exception e) {
            fail("Failed to read application.yml: " + e.getMessage());
        }
    }

    @Test
    void applicationYml_shouldContainHealthEndpoint() {
        Resource resource = new ClassPathResource("application.yml");
        try (Scanner scanner = new Scanner(resource.getInputStream())) {
            String content = scanner.useDelimiter("\\A").next();
            assertTrue(content.contains("health"), "Should expose health endpoint");
            assertTrue(content.contains("management"), "Should contain management config");
        } catch (Exception e) {
            fail("Failed to read application.yml: " + e.getMessage());
        }
    }

    @Test
    void applicationYml_shouldContainSessionConfig() {
        Resource resource = new ClassPathResource("application.yml");
        try (Scanner scanner = new Scanner(resource.getInputStream())) {
            String content = scanner.useDelimiter("\\A").next();
            assertTrue(content.contains("session-secret"), "Should contain session secret config");
            assertTrue(content.contains("session-ttl"), "Should contain session TTL config");
        } catch (Exception e) {
            fail("Failed to read application.yml: " + e.getMessage());
        }
    }

    @Test
    void applicationYml_shouldContainAdminConfig() {
        Resource resource = new ClassPathResource("application.yml");
        try (Scanner scanner = new Scanner(resource.getInputStream())) {
            String content = scanner.useDelimiter("\\A").next();
            assertTrue(content.contains("token-secret"), "Should contain token secret config");
            assertTrue(content.contains("admin"), "Should contain admin config");
        } catch (Exception e) {
            fail("Failed to read application.yml: " + e.getMessage());
        }
    }

    @Test
    void applicationYml_shouldContainFabricConfig() {
        Resource resource = new ClassPathResource("application.yml");
        try (Scanner scanner = new Scanner(resource.getInputStream())) {
            String content = scanner.useDelimiter("\\A").next();
            assertTrue(content.contains("fabric"), "Should contain Fabric config");
            assertTrue(content.contains("channel-name"), "Should contain channel name");
        } catch (Exception e) {
            fail("Failed to read application.yml: " + e.getMessage());
        }
    }

    @Test
    void applicationYml_shouldContainAIConfig() {
        Resource resource = new ClassPathResource("application.yml");
        try (Scanner scanner = new Scanner(resource.getInputStream())) {
            String content = scanner.useDelimiter("\\A").next();
            assertTrue(content.contains("python-ai"), "Should contain AI service config");
            assertTrue(content.contains("url"), "Should contain URL config");
        } catch (Exception e) {
            fail("Failed to read application.yml: " + e.getMessage());
        }
    }

    @Test
    void passwordService_shouldGenerateValidHash() {
        PasswordService passwordService = new PasswordService();
        String salt = passwordService.newSalt();
        String hash = passwordService.hashPassword("testpassword", salt);

        assertNotNull(salt);
        assertNotNull(hash);
        assertTrue(passwordService.matches("testpassword", salt, hash));
        assertFalse(passwordService.matches("wrongpassword", salt, hash));
    }

    @Test
    void networkConfig_shouldExist() {
        Resource resource = new ClassPathResource("network-config.json");
        assertTrue(resource.exists(), "network-config.json should exist");
    }
}
