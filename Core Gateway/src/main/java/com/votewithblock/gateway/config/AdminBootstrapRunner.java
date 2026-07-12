package com.votewithblock.gateway.config;

import com.votewithblock.gateway.model.AdminAccount;
import com.votewithblock.gateway.model.AdminActionLog;
import com.votewithblock.gateway.model.AdminRole;
import com.votewithblock.gateway.repository.AdminAccountRepository;
import com.votewithblock.gateway.repository.AdminActionLogRepository;
import com.votewithblock.gateway.service.PasswordService;
import com.votewithblock.gateway.util.HashUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@Slf4j
public class AdminBootstrapRunner implements CommandLineRunner {

    private final AdminAccountRepository adminAccountRepository;
    private final AdminActionLogRepository adminActionLogRepository;
    private final PasswordService passwordService;
    
    private final boolean bootstrapEnabled;
    private final String bootstrapUsername;
    private final String bootstrapPassword;

    public AdminBootstrapRunner(AdminAccountRepository adminAccountRepository,
                                AdminActionLogRepository adminActionLogRepository,
                                PasswordService passwordService,
                                @Value("${app.admin.bootstrap.enabled:false}") boolean bootstrapEnabled,
                                @Value("${app.admin.bootstrap.username:}") String bootstrapUsername,
                                @Value("${app.admin.bootstrap.password:}") String bootstrapPassword) {
        this.adminAccountRepository = adminAccountRepository;
        this.adminActionLogRepository = adminActionLogRepository;
        this.passwordService = passwordService;
        this.bootstrapEnabled = bootstrapEnabled;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!bootstrapEnabled) {
            return;
        }

        if (bootstrapUsername == null || bootstrapUsername.isBlank() || 
            bootstrapPassword == null || bootstrapPassword.isBlank()) {
            log.error("CRITICAL: Admin bootstrap is enabled but username/password is missing.");
            throw new IllegalStateException("Admin bootstrap is enabled but credentials are missing. Failing startup.");
        }

        if (adminAccountRepository.findByUsername(bootstrapUsername).isEmpty()) {
            String salt = passwordService.newSalt();
            String hash = passwordService.hashPassword(bootstrapPassword, salt);

            AdminAccount account = AdminAccount.builder()
                    .username(bootstrapUsername)
                    .fullName("Bootstrap Administrator")
                    .role(AdminRole.SUPER_ADMIN)
                    .passwordSalt(salt)
                    .passwordHash(hash)
                    .isActive(true)
                    .createdAt(OffsetDateTime.now())
                    .createdBy("SYSTEM")
                    .build();

            adminAccountRepository.save(account);

            AdminActionLog logEntry = AdminActionLog.builder()
                    .adminUsernameHash(HashUtil.sha256Hex("SYSTEM"))
                    .action("BOOTSTRAP_ADMIN_CREATED")
                    .targetEntityType("ADMIN")
                    .targetEntityIdHash(HashUtil.sha256Hex(bootstrapUsername))
                    .outcome("SUCCESS")
                    .ipHash(HashUtil.sha256Hex("localhost"))
                    .performedAt(OffsetDateTime.now())
                    .metadata("System bootstrap created default SUPER_ADMIN")
                    .build();
            adminActionLogRepository.save(logEntry);

            log.info("Successfully provisioned bootstrap SUPER_ADMIN account: {}", bootstrapUsername);
        } else {
            log.info("Bootstrap SUPER_ADMIN account {} already exists. Skipping creation.", bootstrapUsername);
        }
    }
}
