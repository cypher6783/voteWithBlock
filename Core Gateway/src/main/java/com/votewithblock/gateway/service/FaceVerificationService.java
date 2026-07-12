package com.votewithblock.gateway.service;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FaceVerificationService {

    private static final Logger log = LoggerFactory.getLogger(FaceVerificationService.class);

    private final RestTemplate restTemplate;
    private final String aiBaseUrl;

    public FaceVerificationService(
            @Value("${services.python-ai.url:http://localhost:8000}") String verifyUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);
        // Extract base url just in case
        if (verifyUrl.endsWith("/verify-liveness")) {
            this.aiBaseUrl = verifyUrl.replace("/verify-liveness", "");
        } else {
            this.aiBaseUrl = verifyUrl.endsWith("/") ? verifyUrl.substring(0, verifyUrl.length() - 1) : verifyUrl;
        }
    }

    public BiometricAiResponse requestEnrollment(String base64Image) {
        return callAiService("/enroll", base64Image);
    }

    public BiometricAiResponse requestVerification(String base64Image) {
        return callAiService("/verify-identity", base64Image);
    }

    public boolean verifyLivenessBase64(String base64Image) {
        BiometricAiResponse res = callAiService("/verify-liveness", base64Image);
        return res != null && res.isLivenessPassed();
    }

    private BiometricAiResponse callAiService(String path, String base64Image) {
        String url = aiBaseUrl + path;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("image", base64Image);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<BiometricAiResponse> response = restTemplate.postForEntity(url, request, BiometricAiResponse.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            log.warn("AI service {} failed with status {}", path, response.getStatusCode());
            return null;
        } catch (Exception e) {
            log.error("Error communicating with AI service at " + url, e);
            return null;
        }
    }

    @Data
    public static class BiometricAiResponse {
        private boolean liveness_passed;
        private List<Float> embedding;
        private String model_version;
        private int face_count;
        private float confidence;
        private String message;
        // fallback for older endpoints
        private Boolean verified; 
        
        public boolean isLivenessPassed() {
            if (verified != null) return verified;
            return liveness_passed;
        }
    }
}
