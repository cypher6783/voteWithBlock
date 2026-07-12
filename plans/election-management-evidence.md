# Election Management System — Repository Evidence

This document captures the current state of the Votetrust Ledger repository before implementing Election and Candidate Management.

## 1. Frontend Evidence

### 1.1 Hardcoded Ballot Data
**File:** `frontend ui/src/components/BallotScreen.tsx`
**Evidence:**
```typescript
const CANDIDATES: Candidate[] = [
  { id: "cand-1", name: "Dr. Clara Vance", party: "Progressive Alliance", ... },
  { id: "cand-2", name: "Marcus Sterling", party: "Conservative Coalition", ... }
];
```
```typescript
const payload = {
  nationalId: voterId,
  electionId: "elect-2026",
  candidateId: selectedCandidateId,
  token: anonToken,
  anonymousToken: anonToken,
};
```
**Explanation:** The frontend currently hardcodes both the active `electionId` and the list of `CANDIDATES`. There is no API integration to fetch live elections or candidate metadata.

### 1.2 Routing & UI Structure
**File:** `frontend ui/src/app/page.tsx`
**Evidence:**
```typescript
const [currentStep, setCurrentStep] = useState<1 | 2 | 3 | 4>(1);
```
**Explanation:** The frontend relies on a step-based flow within a single page rather than a formal router structure. The Next.js `/app` router is available but not deeply utilized yet.

## 2. Gateway & API Evidence

### 2.1 Vote Casting Architecture
**File:** `Core Gateway/src/main/java/com/votewithblock/gateway/controller/VoteController.java`
**Evidence:**
```java
inputValidationService.requireValidBallot(request.getElectionId(), request.getCandidateId(), request.getToken());
```
```java
if (voter.isHasVoted()) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Double voting attempt detected.");
}
```
**Explanation:** The backend validates ballots using `InputValidationService` (which is likely hardcoded via config `app.validation.allowed-election-ids`) and relies on `voter_accounts.has_voted` for off-chain replay protection.

### 2.2 Blockchain Ledger Integration
**File:** `Core Gateway/src/main/java/com/votewithblock/gateway/service/VotingLedgerService.java`
**Evidence:**
```java
byte[] txResult = contract.submitTransaction("CastVote", token, electionId, candidateId);
```
**Explanation:** The Hyperledger Fabric network acts as the final destination for vote payloads. The blockchain chaincode explicitly records the anonymous token, election ID, and candidate ID.

### 2.3 Existing Configuration
**File:** `Core Gateway/src/main/resources/application.yml`
**Evidence:**
```yaml
app:
  validation:
    allowed-election-ids: ${APP_ALLOWED_ELECTION_IDS:elect-2026}
    allowed-candidate-ids: ${APP_ALLOWED_CANDIDATE_IDS:cand-1,cand-2,cand-3,cand-4}
```
**Explanation:** Valid elections and candidates are currently hardcoded in the Spring Boot configuration properties.

## 3. Administrative Architecture Evidence

### 3.1 Security Context
**File:** `Core Gateway/src/main/java/com/votewithblock/gateway/config/SecurityConfig.java`
**Evidence:**
```java
.requestMatchers("/api/admin/**").authenticated()
```
**Explanation:** Spring Security is active. All `/api/admin/**` routes are protected by default and expect a valid `AdminSession`.

### 3.2 Role System
**File:** `Core Gateway/src/main/java/com/votewithblock/gateway/model/AdminRole.java`
**Evidence:**
```java
public enum AdminRole {
    SUPER_ADMIN,
    ELECTION_ADMIN,
    AUDITOR
}
```
**Explanation:** A hierarchical role system exists and is mapped to `AdminAccount` entities.

### 3.3 Audit Logging
**File:** `Core Gateway/src/main/java/com/votewithblock/gateway/model/AdminActionLog.java`
**Evidence:**
```java
private String action;
private String targetEntityType;
private String targetEntityIdHash;
```
**Explanation:** A comprehensive, hash-based audit log table exists for recording administrative actions.
