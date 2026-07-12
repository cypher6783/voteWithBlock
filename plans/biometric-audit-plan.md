# Biometric Subsystem Audit Plan

## Executive Summary

This audit examines the biometric subsystem of the Votetrust Ledger voting system. The implementation features a multi-factor authentication system combining password-based login with facial recognition liveness detection. The system uses a client-side facial scanning component (BiometricScreen.tsx) that performs blink-based liveness detection locally, then sends captured facial data to a Spring Boot gateway for verification against stored encrypted biometric templates via an AI microservice. Evidence shows the system implements enrollment, verification, liveness checking, and secure token management with appropriate security controls including rate limiting, challenge-response mechanisms, and replay protection.

## Current Architecture

### Authentication Flow
1. **Login**: User enters national ID and password → AuthController validates credentials → Issues preAuthToken → Returns biometricEnrolled flag
2. **Biometric Verification**: 
   - Frontend BiometricScreen performs local liveness detection (blink detection via Eye Aspect Ratio)
   - After blink detected, captures face image and sends to /api/auth/biometric-challenge with preAuthToken
   - Gateway validates preAuthToken, creates challenge, returns challengeId
   - Frontend sends challengeId and base64 face image to /api/auth/biometric-confirm
   - Gateway validates challenge, calls BiometricVerificationService which:
     - Checks lockout/status
     - Loads encrypted biometric template from voter_biometrics table
     - Calls AI service (/verify-identity) for liveness and embedding extraction
     - Compares fresh embedding with stored template using cosine similarity
     - Returns appropriate VerificationResult
   - On PASS: Gateway issues sessionToken via SessionTokenService
3. **Voting**: 
   - Frontend BallotScreen uses sessionToken to call /api/vote/cast
   - Gateway validates session has biometric claim, checks hasVoted flag, records vote on blockchain
   - Updates voter.hasVoted = true for replay protection
   - Includes rollback mechanism if blockchain submission fails

### Biometric Flow
- **Enrollment**: Not directly visible in code but inferred from service methods (FaceVerificationService.requestEnrollment, BiometricEnrollmentService)
- **Verification**: As described above, uses InsightFace model in AI service for embedding extraction and liveness checks
- **Liveness Detection**: Dual approach:
  - Client-side: Eye Aspect Ratio (EAR) blink detection in BiometricScreen.tsx
  - Server-side: AI service liveness check via InsightFace (verify-liveness endpoint)
- **Storage**: Encrypted embeddings stored in voter_biometrics table with AES-GCM encryption (IV, ciphertext, tag)

### AI Verification Architecture
- **Service**: FastAPI application in ai-verification/src/main.py
- **Model**: InsightFace with buffalo_l model (ArcFace 512-d embeddings)
- **Endpoints**:
  - /enroll: For biometric template creation
  - /verify-identity: Strict quality checks + embedding extraction
  - /verify-liveness: Backward compatibility liveness-only check
  - /health: Model status check
- **Quality Validation**: Resolution ≥224x224, single face, face area ≥10%, blur <80 Laplacian variance, brightness 40-240, head pose <30° yaw/pitch

### Database Support
- **Tables**:
  - voter_accounts: Stores credentials, biometricStatus, failed attempts, hasVoted flag
  - voter_biometrics: Stores encrypted embeddings with IV/tag, modelVersion, timestamps
  - biometric_challenges: Stores challenge IDs with expiration and usage tracking
  - biometric_attempt_log: Audit log of verification attempts
- **Relationships**: voter_biometrics.national_id → voter_accounts.national_id (FK)

### Token Architecture
- **preAuthToken**: Short-term token issued after password login, used for biometric verification
- **challengeId**: UUID used in biometric challenge-response to prevent replay
- **sessionToken**: Issued after successful biometric validation, contains biometric verified claim
- **anonymousToken**: Random token used in vote casting for unlinkability
- All tokens managed by dedicated services (PreAuthTokenService, SessionTokenService) with TTL configuration

### Security Controls
- **Rate Limiting**: Applied to login, biometric verification, and voting endpoints via RateLimitService
- **Challenge-Response**: Biometric verification uses nonce-based challenges to prevent replay
- **Lockout Mechanism**: 5 failed attempts → 15 minute lockout with exponential backoff implied
- **Secure Storage**: Biometric templates encrypted with AES-GCM (IV stored separately)
- **Liveness Detection**: Dual client-side (blink) and server-side (AI) verification
- **Session Binding**: Session token requires biometric claim for voting access
- **Audit Logging**: BiometricAttemptLog table records all verification attempts
- **Input Validation**: Validation services for nationalId format, ballot parameters
- **Error Handling**: Generic error messages to prevent information leakage

### Rate Limiting Controls
- Implemented via RateLimitService with requireAllowed() method
- Applied to:
  - Login: "login:" + IP + ":" + nationalId
  - Biometric verify: "biometric:verify:" + nationalId + ":" + IP
  - Voting: "vote:" + IP + ":" + nationalId
- Prevents brute force attacks on credentials and biometric verification

### Audit Logging Controls
- BiometricAttemptLog table captures:
  - nationalIdHash (hashed identifier)
  - attemptType (VERIFICATION)
  - outcome (PASS/FAIL_* enum values)
  - similarityScore (when applicable)
  - thresholdUsed
  - ipHash (hashed IP address)
  - attemptedAt timestamp
  - modelVersion
- Enables forensic analysis of biometric system usage and attack patterns

### Production Readiness Assessment
- **Strengths**:
  - Multi-layered liveness detection (client blink + server AI)
  - Encrypted biometric storage with proper key management (IV/tag)
  - Challenge-response prevents replay attacks
  - Rate limiting on all critical endpoints
  - Account lockout after failed attempts
  - Rollback mechanism for blockchain/database consistency
  - Comprehensive audit logging
  - Input validation and sanitization
  - Separation of concerns (frontend liveness, gateway orchestration, AI service)
- **Weaknesses/Gaps**:
  - Client-side liveness (blink detection) could be spoofed with video replay
  - No explicit mention of secure key management for encryption service
  - AI service appears to lack model versioning/signing for robustness
  - No evidence of biometric template re-enrollment UI/flow
  - Limited evidence of disaster recovery/backup procedures for biometric database
  - No explicit HTTPS enforcement shown in configuration (though implied by localhost URLs)
  - No evidence of biometric data minimization (templates stored indefinitely)

## Findings

Based on evidence review:

### A. Facial Enrollment
- **Stored biometric identity**: EVIDENCE FOUND - VoterBiometric table stores national_id as unique identifier with embeddingCiphertext, embeddingIv, embeddingTag
- **Face embeddings**: EVIDENCE FOUND - AI service extracts 512-d normalized embeddings via InsightFace normed_embedding
- **Identity matching**: EVIDENCE FOUND - Cosine similarity comparison between stored and fresh embeddings with threshold 0.82
- **Re-enrollment**: PARTIAL EVIDENCE - BiometricVerificationService sets status to "PENDING_REENROLLMENT" when similarity 0.75-0.82, but no explicit re-enrollment flow found in evidence

### B. Authentication
- **Password validation**: EVIDENCE FOUND - AuthController uses PasswordService to hash and match credentials with salt
- **Challenge-response flow**: EVIDENCE FOUND - Biometric verification uses challengeId nonce validated and consumed by challengeService
- **Session security**: EVIDENCE FOUND - SessionTokenService issues tokens with biometric claim required for voting; tokens have TTL

### C. Voting Security
- **Vote replay protection**: EVIDENCE FOUND - VoteController checks voter.hasVoted flag before and after blockchain submission with rollback on failure
- **Double-vote prevention**: EVIDENCE FOUND - Same mechanism as replay protection; hasVoted flag prevents multiple votes
- **Session enforcement**: EVIDENCE FOUND - VoteController requires session token with biometric verified claim matching nationalId

### D. AI Security
- **Spoof resistance**: PARTIAL EVIDENCE - AI service includes quality checks (resolution, face count, area, blur, brightness, head pose) but no explicit anti-spoofing (texture/depth analysis) mentioned
- **Liveness checks**: EVIDENCE FOUND - Dual approach: client-side blink detection (EAR) and server-side AI liveness check
- **Model robustness**: PARTIAL EVIDENCE - Model loads at startup with version tracking, but no evidence of model signing, validation, or anomaly detection

### E. Operational Stability
- **Failure handling**: EVIDENCE FOUND - Comprehensive error handling with specific failure types (FAIL_LIVENESS, FAIL_SIMILARITY, etc.) and lockout mechanisms
- **Scalability concerns**: NO EVIDENCE FOUND - No evidence of load balancing, caching, or horizontal scaling considerations for AI service or gateway
- **24-hour election operation**: PARTIAL EVIDENCE - Session tokens have TTL configuration, but no explicit evidence of long-duration stability testing or memory leak prevention

## Security Risks

1. **Client-Side Liveness Bypass Risk**: The blink detection (EAR) in BiometricScreen.tsx could potentially be circumvented by playing a pre-recorded video of a blink on a screen held up to the camera.

2. **Biometric Template Protection Gap**: While templates are encrypted (AES-GCM), evidence does not show key management practices (rotation, storage in HSM/KMS, separation of duties).

3. **AI Service Integrity Risk**: No evidence of model signing or integrity verification for the InsightFace model, potentially allowing tampered model deployment.

4. **Insufficient Liveness Depth**: Quality checks in AI service focus on image quality but lack advanced spoof detection (e.g., texture analysis, motion analysis, 3D depth).

5. **Biometric Data Retention**: No evidence of automated biometric template expiration or user-controlled deletion mechanisms.

6. **Side Channel Potential**: Error messages differentiate between FAIL_LIVENESS, FAIL_SIMILARITY, etc., which could aid attackers in tuning spoof attempts.

## Architectural Gaps

1. **Explicit Enrollment Flow**: No frontend component or API endpoint clearly dedicated to biometric enrollment found in evidence.

2. **Biometric Update Mechanism**: While PENDING_REENROLLMENT status exists, no evidence of UI/flow to guide users through re-enrollment.

3. **Cross-Service Trust**: No evidence of mutual TLS or service-to-service authentication between gateway and AI service.

4. **Emergency Override**: No evidence of administrative bypass for biometric verification failures during elections.

5. **Template Migration Strategy**: No evidence of handling biometric template format changes when updating models.

## Production Readiness Assessment

**Overall**: The biometric subsystem demonstrates strong security foundations with multiple layers of protection, but requires enhancements for high-assurance production deployment.

**Ready for**: Controlled environment testing, pilot deployments with monitoring.

**Not ready for**: Nationwide election deployment without addressing identified gaps.

**Key Production Requirements Met**:
- ✅ Multi-factor authentication (password + biometric)
- ✅ Liveness detection (dual client/server)
- ✅ Encrypted biometric storage
- ✅ Replay protection (challenge-response + state flags)
- ✅ Rate limiting and lockout
- ✅ Audit logging
- ✅ Input validation
- ✅ Error handling without information leakage

**Key Production Requirements Needing Improvement**:
- ❌ Client liveness robustness (video replay vulnerability)
- ❌ Key management transparency
- ❌ AI model integrity verification
- ❌ Advanced anti-spoofing
- ❌ Biometric enrollment/update UI
- ❌ Service-to-service authentication
- ❌ Long-term stability evidence

## Prioritized Remediation Roadmap

### Priority 1 (Immediate - Before Production)
1. **Enhance Client Liveness**: Add random challenge to client-side liveness (e.g., prompt for specific head movement or expression) to prevent video replay attacks
2. **Implement Key Management**: Document and implement secure key storage (AWS KMS, HashiCorp Vault, or HSM) for encryption keys with rotation capability
3. **Add AI Model Integrity**: Implement model signing/verification at startup to prevent tampered model deployment
4. **Create Enrollment UI**: Develop BiometricEnrollmentScreen component and associated API endpoints for initial enrollment and re-enrollment

### Priority 2 (Short-Term - 1-4 Weeks)
1. **Advanced Spoof Detection**: Integrate texture-based or motion-based liveness checks in AI service to complement existing quality checks
2. **Service Authentication**: Implement mutual TLS or JWT-based authentication between gateway and AI service
3. **Biometric Data Policy**: Implement automated template expiration (e.g., 90 days) and user-initiated deletion
4. **Error Message Harmonization**: Review error messages to minimize information leakage while maintaining usability

### Priority 3 (Medium-Term - 1-3 Months)
1. **Comprehensive Monitoring**: Add Prometheus metrics for biometric system (verification rates, failure types, latency)
2. **Template Migration**: Implement versioned biometric templates with migration path for model updates
3. **Disaster Recovery**: Implement biometric database backup/restore procedures tested for RTO/RPO
4. **Administrative Override**: Implement secure emergency biometric bypass with multi-person approval and audit trail

### Priority 4 (Long-Term - 3-6 Months)
1. **Continuous Learning**: Implement feedback loop where verified biometrics improve liveness models (with privacy safeguards)
2. **Multi-Modal Biometrics**: Evaluate addition of voice or other modalities for multi-factor biometric authentication
3. **Zero-Knowledge Proofs**: Research ZKP-based biometric verification to minimize template storage requirements
4. **Formal Verification**: Conduct formal verification of critical security properties (replay resistance, template protection)

## Recommended Biometric Architecture

### Enhanced Client-Side
```
[BiometricScreen.tsx]
- Challenge-Response Liveness: Server sends random nonce (blink left/right/up/down)
- EAR + Pose Validation: Verify blink matches challenge + head position within tolerance
- Temporal Binding: Challenge must be completed within time window
- Secure Enclave Preparation: Design for future WebAuthn/platform authenticator integration
```

### Enhanced Gateway-Orchestration
```
[BiometricVerificationService]
- Template Versioning: Store modelVersion with each template, allow graceful migration
- Adaptive Thresholds: Dynamic similarity threshold based on historical success rates
- Consolidated Logging: Structured logging with correlation IDs for end-to-end tracing
- Fallback Mechanisms: Graceful degradation to higher security modes during attacks
```

### Enhanced AI Service
```
[main.py]
- Anti-Spoofing Ensemble: Combine texture analysis, eye movement, and 3D depth (if available)
- Model Attestations: Verify model integrity via signed hashes at startup
- Quality Tiers: Multiple validation levels with corresponding security assurances
- Batch Processing: Optimize for concurrent verification requests during peak voting
```

### Enhanced Data Management
```
[voter_biometrics table]
- key_rotation_id: Track which key version encrypted the template
- template_source: Track enrollment vs update vs recovery origin
- privacy_metrics: Store entropy/uniqueness measures for template quality monitoring
```

### Infrastructure Enhancements
```
[Deployment]
- Service Mesh: Istio/Linkerd for mutual TLS, traffic policies, observability
- Autoscaling: HPA based on queue depth and response times for AI service
- Geographic Distribution: Regional AI service instances for latency-sensitive liveness
- Chaos Engineering: Regular failure injection testing for resilience validation
```

## Implementation Strategy

### Phase 0: Foundation (Week 1)
- Document current architecture and data flows
- Establish security baselines and monitoring
- Create enrollment UI prototype
- Implement basic key management documentation

### Phase 1: Core Security (Weeks 2-3)
- Implement enhanced client liveness with challenges
- Deploy key management solution (KMS/Vault integration)
- Add model integrity verification to AI service
- Create enrollment/re-enrollment API endpoints

### Phase 2: Defense Depth (Weeks 4-5)
- Integrate advanced spoof detection in AI service
- Implement service-to-service authentication
- Add biometric data retention policies
- Harmonize error messages and audit logging

### Phase 3: Operational Excellence (Weeks 6-8)
- Deploy comprehensive monitoring and alerting
- Implement template versioning and migration
- Establish disaster recovery procedures
- Conduct penetration testing and red team exercises

### Phase 4: Maturity (Ongoing)
- Implement continuous learning feedback loops
- Evaluate multi-modal biometric options
- Research and prototype ZKP approaches
- Establish formal verification pipeline

This strategy ensures incremental improvement while maintaining system stability, with each phase delivering tangible security enhancements.