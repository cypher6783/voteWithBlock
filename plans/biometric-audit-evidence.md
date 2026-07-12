# Biometric Subsystem Audit Evidence

## Frontend Evidence

### LoginScreen.tsx
- Path: `frontend ui/src/components/LoginScreen.tsx`
- Lines 39-47: Shows authentication flow where login returns `biometricEnrolled` flag
- Line 43-46: If biometricEnrolled is false, error message: "Your account is not enrolled for biometric verification. Please contact an administrator."
- Lines 47: `onProceed(response.data.nationalId, response.data.preAuthToken);` - passes preAuthToken to next step

### BiometricScreen.tsx
- Path: `frontend ui/src/components/BiometricScreen.tsx`
- Lines 16-23: Defines blink detection constants (BLINK_EAR_THRESHOLD = 0.25, BLINK_CONSEC_FRAMES = 2)
- Lines 29-35: Computes Eye Aspect Ratio (EAR) for blink detection
- Lines 304-344: Blink detection logic - increments counter when EAR < threshold, triggers after BLINK_CONSEC_FRAMES consecutive low EAR frames
- Lines 311-313: Captures frame data right after blink detection: `const frameData = captureFrame(); setCapturedFaceData(frameData);`
- Lines 315-316: Sets scanState to "blink-detected" and livenessStatus to "success"
- Lines 319-339: Simulates vector generation progress bar after blink detected
- Lines 442-474: `handleServerVerify` function that calls biometric challenge and confirm endpoints
- Lines 456-459: Sends `base64FaceImage: capturedFaceData` to biometric-confirm endpoint
- Line 462: Calls `onProceed(confirmRes.data.sessionToken);` with session token from server

### BallotScreen.tsx
- Path: `frontend ui/src/components/BallotScreen.tsx`
- Lines 103-110: Vote casting payload includes `nationalId: voterId` and `token: anonToken` (anonymous ballot token)
- Lines 113-116: POST request to VOTE_CAST_URL with sessionToken in Authorization header
- Line 119: `onProceed(candidate.name, response.data.transactionId);` on successful vote

### AuditScreen.tsx
- Path: `frontend ui/src/components/AuditScreen.tsx`
- Lines 50-66: Simulates real-time block streaming with new blocks every 6 seconds
- Lines 71-75: Copy transaction ID functionality

### page.tsx
- Path: `frontend ui/src/app/page.tsx`
- Lines 16-20: Session state variables for voterId, preAuthToken, sessionToken, selectedCandidate, transactionId
- Lines 22-26: handleLoginProceed sets voterId and preAuthToken, advances to step 2
- Lines 28-31: handleBiometricProceed sets sessionToken, advances to step 3
- Lines 33-36: handleBallotProceed sets selectedCandidate and transactionId, advances to step 4
- Lines 39-46: handleResetFlow clears all session state and returns to step 1

### api.ts
- Path: `frontend ui/src/lib/api.ts`
- Lines 1-5: Defines API endpoints:
  - AUTH_LOGIN_URL = `${CORE_GATEWAY_URL}/api/auth/login`
  - AUTH_BIOMETRIC_CHALLENGE_URL = `${CORE_GATEWAY_URL}/api/auth/biometric-challenge`
  - AUTH_BIOMETRIC_CONFIRM_URL = `${CORE_GATEWAY_URL}/api/auth/biometric-confirm`
  - VOTE_CAST_URL = `${CORE_GATEWAY_URL}/api/vote/cast`

## Gateway Evidence

### AuthController.java
- Path: `Core Gateway/src/main/java/com/votewithblock/gateway/controller/AuthController.java`
- Lines 62-91: `/login` endpoint:
  - Line 65: Rate limiting on login attempts
  - Lines 67-86: Validates credentials against VoterAccount, handles bootstrap password enrollment
  - Line 88: Issues preAuthToken via preAuthTokenService
  - Line 90: Returns LoginResponse with biometricEnrolled flag based on voter.getBiometricStatus()
- Lines 93-98: `/biometric-challenge` endpoint:
  - Line 95: Extracts nationalId from preAuthToken
  - Line 96: Creates and returns BiometricChallenge
- Lines 100-132: `/biometric-confirm` endpoint:
  - Line 102: Extracts nationalId from preAuthToken
  - Line 104: Validates and consumes challenge via challengeService
  - Line 109: Rate limiting on biometric verification
  - Line 111: Calls biometricVerificationService.verifyIdentity()
  - Lines 113-131: Handles verification results with appropriate HTTP status codes

### VoteController.java
- Path: `Core Gateway/src/main/java/com/votewithblock/gateway/controller/VoteController.java`
- Lines 57-62: `/cast` endpoint with rate limiting
- Line 65: `sessionTokenService.requireBiometricClaim(authorization)` - enforces biometric verified claim
- Lines 67-69: Validates session nationalId matches request nationalId
- Lines 76-85: Checks if voter has already voted (replay protection), throws BAD_REQUEST if true
- Lines 88-95: Updates voter.hasVoted = true in database after vote attempt
- Lines 98-107: Submits ballot to ledger via votingLedgerService
- Lines 109-122: Rollback recovery if blockchain submission fails (reverts hasVoted to false)

### BiometricVerificationService.java
- Path: `Core Gateway/src/main/java/com/votewithblock/gateway/service/BiometricVerificationService.java`
- Lines 34-103: `verifyIdentity` method:
  - Lines 39-48: Lockout check - fails if >=5 failed attempts within last 15 minutes
  - Lines 52-53: Status check - returns FAIL_NOT_ENROLLED or FAIL_DISABLED based on biometricStatus
  - Lines 56-57: Loads VoterBiometric entity, returns FAIL_NOT_ENROLLED if not found
  - Lines 60-65: Calls AI service for verification, checks liveness, returns FAIL_LIVENESS if failed
  - Lines 68-97: Decrypts stored embedding, compares with fresh embedding using cosine similarity
  - Lines 74-84: If similarity >= threshold (0.82): resets failed attempts, updates lastVerifiedAt, logs attempt, returns PASS
  - Lines 86-91: If similarity >= 0.75 but < threshold: sets status to PENDING_REENROLLMENT, handles failure, returns FAIL_PENDING_REENROLLMENT
  - Lines 93-97: If similarity < 0.75: handles failure, returns FAIL_SIMILARITY
  - Lines 99-102: Exception handling returns FAIL_ERROR
- Lines 105-109: `handleFailure` increments failedBiometricAttempts and sets lastFailedBiometricAt
- Lines 111-123: `logAttempt` creates BiometricAttemptLog entry with details
- Lines 125-135: Helper methods for cosine similarity and float array conversion
- Lines 143-152: VerificationResult enum with all possible outcomes

### FaceVerificationService.java
- Path: `Core Gateway/src/main/java/com/votewithblock/gateway/service/FaceVerificationService.java`
- Lines 37-48: Methods for calling AI service:
  - Line 37-39: requestEnrollment calls /enroll endpoint
  - Line 41-43: requestVerification calls /verify-identity endpoint
  - Line 45-48: verifyLivenessBase64 calls /verify-liveness endpoint
  - Lines 50-71: callAiService handles HTTP communication with AI service using RestTemplate
- Lines 73-88: BiometricAiResponse DTO with liveness_passed, embedding, model_version, etc.
- Lines 84-87: isLivenessPassed() method handles legacy verified field

### VoterAccount.java
- Path: `Core Gateway/src/main/java/com/votewithblock/gateway/model/VoterAccount.java`
- Lines 10-26: VoterAccount entity:
  - Line 17: @Id private String nationalId;
  - Line 19: private boolean hasVoted;
  - Line 20: private String faceEmbeddingReference; (legacy reference)
  - Line 21-22: private String passwordSalt; private String passwordHash;
  - Line 23: private String biometricStatus = "NOT_ENROLLED";
  - Line 24: private int failedBiometricAttempts = 0;
  - Line 25: private java.time.OffsetDateTime lastFailedBiometricAt;

### VoterBiometric.java
- Path: `Core Gateway/src/main/java/com/votewithblock/gateway/model/VoterBiometric.java`
- Lines 10-45: VoterBiometric entity:
  - Line 20: @Column(unique = true, nullable = false) private String nationalId;
  - Line 25: @Column(nullable = false) private byte[] embeddingCiphertext;
  - Line 28: @Column(nullable = false) private byte[] embeddingIv;
  - Line 30: @Column(nullable = false) private byte[] embeddingTag;
  - Line 33: @Column(nullable = false) private String modelVersion;
  - Line 36: @Column(nullable = false) private OffsetDateTime enrolledAt;
  - Line 39: private String enrollmentIpHash;
  - Line 41: private OffsetDateTime lastVerifiedAt;
  - Line 43: @Version private int version;

### VoterBiometricRepository.java
- Path: `Core Gateway/src/main/java/com/votewithblock/gateway/repository/VoterBiometricRepository.java`
- Lines 9-12: Spring Data JPA repository with findByNationalId method

### BiometricChallenge.java (inferred from usage)
- Referenced in AuthController line 96: challengeService.createChallenge(nationalId)
- Referenced in AuthController line 104: challengeService.validateAndConsumeChallenge

### BiometricAttemptLog.java (inferred from usage)
- Referenced in BiometricVerificationService line 112-121: BiometricAttemptLog.builder() usage

### VoterRepository.java (inferred from usage)
- Referenced in AuthController line 32: private final VoterRepository voterRepository;
- Referenced in AuthController line 67: voterRepository.findByNationalId
- Referenced in VoteController line 38: private final VoterRepository voterRepository;
- Referenced in VoteController line 76: voterRepository.findByNationalId

### Services used in AuthController (inferred from constructor)
- PasswordService (line 33)
- PreAuthTokenService (line 34)
- SessionTokenService (line 35)
- RateLimitService (line 36)
- BiometricChallengeService (line 37)
- BiometricVerificationService (line 38)

## AI Service Evidence

### main.py
- Path: `ai-verification/src/main.py`
- Lines 10-15: Configuration constants:
  - MAX_IMAGE_LENGTH = 3_000_000
  - ENABLE_DOCS = os.getenv("AI_ENABLE_DOCS", "false").lower() == "true"
  - MODEL_PACK_NAME = os.getenv("AI_MODEL_PACK", "buffalo_l")
- Lines 21-25: App state for model loading
- Lines 27-42: Lifespan context manager for loading InsightFace model
- Lines 44-52: FastAPI app configuration
- Lines 54-59: ImageRequest Pydantic model with base64 image and max length
- Lines 61-67: BiometricResponse Pydantic model
- Lines 69-73: OldFaceVerificationResponse Pydantic model (backward compatibility)
- Lines 74-80: Health check endpoint
- Lines 82-90: decode_image function - handles base64 decoding with fallback for data URLs
- Lines 92-135: validate_image_quality function with checks for:
  - Resolution (min 224x224) - lines 96-97
  - Face count (exactly 1 required) - lines 99-102
  - Face area (min 10% of image) - lines 106-111
  - Blur check (Laplacian variance > 80.0) - lines 113-119
  - Brightness check (40-240 range) - lines 121-128
  - Head pose check (yaw/pitch < 30 degrees) - lines 129-133
- Lines 136-173: extract_biometric_data function:
  - Lines 138: Checks if model loaded
  - Lines 141-142: Decodes image and gets faces from model
  - Lines 144-154: Validates image quality, returns BiometricResponse with liveness_passed=False if failed
  - Lines 156-158: Extracts normalized 512-d embedding from face.normed_embedding
  - Lines 160-167: Returns BiometricResponse with liveness_passed=True and embedding
- Lines 175-178: /enroll endpoint - uses extract_biometric_data with require_strict=True
- Lines 180-184: /verify-identity endpoint - uses extract_biometric_data with require_strict=True
- Lines 186-212: /verify-liveness endpoint - backward compatible, returns OldFaceVerificationResponse
  - Lines 194-199: Returns verified=True if at least one face detected
  - Lines 201-206: Returns verified=False with message if no face detected
  - Lines 207-212: Exception handler returns verified=False
- Lines 214-216: Main entry point running uvicorn on host="0.0.0.0", port=8000

## Database Evidence

### database_migrations.sql
- Path: `database_migrations.sql`
- Lines 4-8: ALTER TABLE voter_accounts to add biometric columns:
  - biometric_status VARCHAR(32) DEFAULT 'NOT_ENROLLED'
  - failed_biometric_attempts INT DEFAULT 0
  - last_failed_biometric_at TIMESTAMPTZ
- Lines 10-23: CREATE TABLE voter_biometrics:
  - id BIGSERIAL PRIMARY KEY
  - national_id VARCHAR(64) UNIQUE NOT NULL
  - embedding_ciphertext BYTEA NOT NULL
  - embedding_iv BYTEA NOT NULL
  - embedding_tag BYTEA NOT NULL
  - model_version VARCHAR(32) NOT NULL
  - enrolled_at TIMESTAMPTZ NOT NULL
  - enrollment_ip_hash VARCHAR(64)
  - last_verified_at TIMESTAMPTZ
  - version INT NOT NULL
  - FOREIGN KEY (national_id) REFERENCES voter_accounts(national_id)
- Lines 25-32: CREATE TABLE biometric_challenges:
  - id BIGSERIAL PRIMARY KEY
  - challenge_id UUID UNIQUE NOT NULL
  - national_id VARCHAR(64) NOT NULL
  - expires_at TIMESTAMPTZ NOT NULL
  - used BOOLEAN DEFAULT FALSE
- Lines 34-45: CREATE TABLE biometric_attempt_log:
  - id BIGSERIAL PRIMARY KEY
  - national_id_hash VARCHAR(64)
  - attempt_type VARCHAR(16)
  - outcome VARCHAR(16)
  - similarity_score FLOAT
  - threshold_used FLOAT
  - ip_hash VARCHAR(64)
  - attempted_at TIMESTAMPTZ
  - model_version VARCHAR(32)

## Infrastructure Evidence

### docker-compose.yml
- Path: `docker-compose.yml` (referenced in file listing)
- Contains service definitions for frontend, gateway, ai-verification, and blockchain network

### challenge_implementation.txt
- Path: `challenge_implementation.txt` (referenced in file listing)
- Contains implementation details (exact content not shown in grep output but file exists)

### pom.xml
- Path: `pom.xml` (referenced in file listing)
- Maven configuration for gateway service

### .env.example
- Path: `.env.example` (referenced in file listing)
- Example environment variables