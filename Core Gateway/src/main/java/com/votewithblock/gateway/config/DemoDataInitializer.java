package com.votewithblock.gateway.config;

import com.votewithblock.gateway.model.VoterAccount;
import com.votewithblock.gateway.repository.VoterRepository;
import com.votewithblock.gateway.service.PasswordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DemoDataInitializer implements CommandLineRunner {

    private final VoterRepository voterRepository;
    private final PasswordService passwordService;
    private final boolean seedDemoVoter;
    private final String demoNationalId;
    private final String demoPassword;

    public DemoDataInitializer(VoterRepository voterRepository,
                               PasswordService passwordService,
                               @Value("${app.demo.seed-voter:false}") boolean seedDemoVoter,
                               @Value("${app.demo.national-id:US-98420-A}") String demoNationalId,
                               @Value("${app.demo.password:#julianisabella#}") String demoPassword) {
        this.voterRepository = voterRepository;
        this.passwordService = passwordService;
        this.seedDemoVoter = seedDemoVoter;
        this.demoNationalId = demoNationalId;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(String... args) {
        if (!seedDemoVoter || demoPassword.isBlank()) {
            return;
        }
        VoterAccount voter = voterRepository.findById(demoNationalId).orElseGet(VoterAccount::new);
        voter.setNationalId(demoNationalId);
        voter.setFullName("Demo Voter");
        voter.setHasVoted(false);
        voter.setFaceEmbeddingReference("demo-reference");
        voter.setBiometricStatus("ENROLLED");
        String salt = passwordService.newSalt();
        voter.setPasswordSalt(salt);
        voter.setPasswordHash(passwordService.hashPassword(demoPassword, salt));
        voterRepository.save(voter);
        log.info("Demo voter seeded for local development");
    }
}
