package com.votewithblock.controller.admin;

import com.votewithblock.service.BiometricStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/biometrics")
public class BiometricEnrollmentController {

    private final BiometricStorageService biometricStorageService;

    public BiometricEnrollmentController(BiometricStorageService biometricStorageService) {
        this.biometricStorageService = biometricStorageService;
    }

    /**
     * Enrolls biometric data for a user.
     * Only accessible by users with ADMIN role.
     *
     * @param userId The ID of the user to enroll biometrics for
     * @param encryptedEmbedding The encrypted biometric template (base64 encoded)
     * @return 200 OK on success
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/enroll")
    public ResponseEntity<Void> enrollBiometrics(
            @RequestParam String userId,
            @RequestBody String encryptedEmbedding) {
        // Store the encrypted biometric embedding
        // In production, validate the userId and embedding format
        biometricStorageService.storeBiometric(userId, encryptedEmbedding);
        return ResponseEntity.ok().build();
    }
}