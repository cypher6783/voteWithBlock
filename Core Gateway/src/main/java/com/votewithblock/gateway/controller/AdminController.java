package com.votewithblock.gateway.controller;

import com.votewithblock.gateway.service.BiometricEnrollmentService;
import com.votewithblock.gateway.util.SafeLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/admin/biometrics")
public class AdminController {

    private final BiometricEnrollmentService enrollmentService;

    public AdminController(BiometricEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping("/enroll")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<EnrollmentResponse> enrollVoter(@Valid @RequestBody EnrollmentRequest request, HttpServletRequest servletRequest) {

        String ip = clientIp(servletRequest);
        log.info("Admin enrollment request for voter {}", SafeLog.id(request.getNationalId()));

        BiometricEnrollmentService.EnrollmentResult result = enrollmentService.enrollVoter(request.getNationalId(), request.getBase64FaceImage(), ip);

        switch (result) {
            case SUCCESS:
                log.info("Successfully enrolled biometrics for voter {}", SafeLog.id(request.getNationalId()));
                return ResponseEntity.ok(new EnrollmentResponse(true, "Biometric profile enrolled successfully."));
            case FAIL_NOT_FOUND:
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voter account not found.");
            case FAIL_QUALITY:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image quality validation failed. Ensure adequate lighting and single face.");
            default:
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error occurred during biometric enrollment.");
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnrollmentRequest {
        @NotBlank
        private String nationalId;
        @NotBlank
        private String base64FaceImage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnrollmentResponse {
        private boolean success;
        private String message;
    }
}
