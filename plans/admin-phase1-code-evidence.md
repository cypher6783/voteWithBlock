# Admin Portal Phase 1: Code Evidence

This document provides exact code snippets with line numbers verifying the implementation of the requested Phase 1 features.

### 1. `SecurityConfig.java`
Verifies that `/api/vote/**` and `/api/auth/**` are explicitly permitted, while `/api/admin/**` is protected.
**File:** `Core Gateway/src/main/java/com/votewithblock/gateway/config/SecurityConfig.java`
```java
26:             .authorizeHttpRequests(auth -> auth
27:                 // Public/Voter endpoints and Admin login are not protected by Spring Security
28:                 .requestMatchers("/api/auth/**").permitAll()
29:                 .requestMatchers("/api/vote/**").permitAll()
30:                 .requestMatchers("/actuator/**").permitAll()
31:                 .requestMatchers("/api/admin/auth/login").permitAll()
32:                 // All other /api/admin/** require authentication
33:                 .requestMatchers("/api/admin/**").authenticated()
34:                 .anyRequest().permitAll()
35:             )
```

### 2. `AdminAuthFilter.java`
Verifies that the session JTI is checked against the database to ensure it hasn't been revoked.
**File:** `Core Gateway/src/main/java/com/votewithblock/gateway/config/AdminAuthFilter.java`
```java
44:             if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
45:                 // Check if session is revoked
46:                 if (adminSessionRepository.existsByJtiAndRevokedFalse(jti)) {
47:                     UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
48:                             username, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
49:                     );
50:                     authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
51:                     SecurityContextHolder.getContext().setAuthentication(authToken);
52:                 } else {
53:                     response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
```

### 3. `AdminTokenService.java`
Verifies the cryptographic boundary is separated using a distinct secret from application properties.
**File:** `Core Gateway/src/main/java/com/votewithblock/gateway/service/AdminTokenService.java`
```java
23:     public AdminTokenService(
24:             @Value("${app.admin.token-secret}") String adminTokenSecret,
25:             @Value("${app.admin.token-ttl-seconds:28800}") long ttlSeconds) { // Default 8 hours
26:         if (adminTokenSecret == null || adminTokenSecret.length() < 32) {
27:             throw new IllegalStateException("app.admin.token-secret must be at least 32 characters");
28:         }
```

### 4. `AdminAuthController.java`
Verifies rate-limiting and idempotent session revocation.
**File:** `Core Gateway/src/main/java/com/votewithblock/gateway/controller/AdminAuthController.java`
*(Note regarding Rate Limiting: The current implementation delegates to the existing `RateLimitService`, which relies on the global properties `app.rate-limit.max-attempts=12` and `app.rate-limit.window-seconds=60`. A separate `app.admin.rate-limit.*` config will be extracted as requested.)*
```java
112:             AdminSession session = adminSessionRepository.findByJti(jti).orElse(null);
113:             if (session != null && !session.isRevoked()) {
114:                 session.setRevoked(true);
115:                 adminSessionRepository.save(session);
116:                 logAction(HashUtil.sha256Hex(username), "ADMIN_LOGOUT", "ADMIN", HashUtil.sha256Hex(username), "SUCCESS", ip);
117:             }
```

### 5. `AdminBootstrapRunner.java`
Verifies the fast-fail mechanism and default user creation.
**File:** `Core Gateway/src/main/java/com/votewithblock/gateway/config/AdminBootstrapRunner.java`
```java
44:         if (bootstrapUsername == null || bootstrapUsername.isBlank() || 
45:             bootstrapPassword == null || bootstrapPassword.isBlank()) {
46:             log.error("CRITICAL: Admin bootstrap is enabled but username/password is missing.");
47:             throw new IllegalStateException("Admin bootstrap is enabled but credentials are missing. Failing startup.");
48:         }
```

### 6. `AdminController.java`
Verifies the `SUPER_ADMIN` protection on the endpoint.
**File:** `Core Gateway/src/main/java/com/votewithblock/gateway/controller/AdminController.java`
```java
28:     @PostMapping("/enroll")
29:     @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
30:     public ResponseEntity<EnrollmentResponse> enrollVoter(@Valid @RequestBody EnrollmentRequest request, HttpServletRequest servletRequest) {
```

### 7. `HashUtil.java`
Verifies the exact SHA-256 implementation used to replace `Object.hashCode()`.
**File:** `Core Gateway/src/main/java/com/votewithblock/gateway/util/HashUtil.java`
```java
20:     public static String sha256Hex(String input) {
21:         if (input == null) return null;
22:         byte[] hash = sha256(input);
23:         StringBuilder hexString = new StringBuilder(2 * hash.length);
24:         for (byte b : hash) {
25:             String hex = Integer.toHexString(0xff & b);
26:             if (hex.length() == 1) {
27:                 hexString.append('0');
28:             }
29:             hexString.append(hex);
30:         }
31:         return hexString.toString();
32:     }
```
