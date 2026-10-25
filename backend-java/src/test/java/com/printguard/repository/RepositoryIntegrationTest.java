package com.printguard.repository;

import com.printguard.BaseIntegrationTest;
import com.printguard.auth.UserEntity;
import com.printguard.auth.UserRepository;
import com.printguard.jobs.PrintJobEntity;
import com.printguard.jobs.PrintJobRepository;
import com.printguard.logs.ErrorLogEntity;
import com.printguard.logs.ErrorLogRepository;
import com.printguard.models.GeneratedModelEntity;
import com.printguard.models.GeneratedModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@Transactional
class RepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GeneratedModelRepository modelRepository;

    @Autowired
    private PrintJobRepository jobRepository;

    @Autowired
    private ErrorLogRepository errorLogRepository;

    @Test
    @DisplayName("Verify User entity persistence, uniqueness, and retrieval")
    void testUserPersistence() {
        String uniqueUsername = "engineer_" + UUID.randomUUID().toString().substring(0, 8);
        UserEntity user = new UserEntity();
        user.setUsername(uniqueUsername);
        user.setEmail(uniqueUsername + "@printguard.io");
        user.setPassword("argon2$hashed_secure_pass");
        user.setIsActive(true);

        UserEntity saved = userRepository.save(user);
        assertNotNull(saved.getId());

        UserEntity fetched = userRepository.findByUsername(uniqueUsername).orElse(null);
        assertNotNull(fetched);
        assertEquals(user.getEmail(), fetched.getEmail());
    }

    @Test
    @DisplayName("Verify GeneratedModel relationship, user scoping, and cascade")
    void testModelPersistenceAndUserScoping() {
        String username = "cad_designer_" + UUID.randomUUID().toString().substring(0, 8);
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(username + "@printguard.io");
        user.setPassword("hashed_pass");
        UserEntity savedUser = userRepository.save(user);

        GeneratedModelEntity model = new GeneratedModelEntity();
        model.setUser(savedUser);
        model.setPrompt("Parametric Planetary Gear Set");
        model.setFilePath("models/planetary_gear.obj");
        model.setStatus("ready");
        model.setSourceType("procedural");
        model.setSteps(64);
        GeneratedModelEntity savedModel = modelRepository.save(model);

        assertNotNull(savedModel.getId());
        List<GeneratedModelEntity> userModels = modelRepository.findByUserIdOrderByCreatedAtDesc(savedUser.getId());
        assertFalse(userModels.isEmpty());
        assertEquals(savedModel.getId(), userModels.get(0).getId());
    }

    @Test
    @DisplayName("Verify PrintJob and ErrorLog foreign key constraints and lifecycle")
    void testPrintJobAndErrorLogRelations() {
        String username = "operator_" + UUID.randomUUID().toString().substring(0, 8);
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(username + "@printguard.io");
        user.setPassword("hashed_pass");
        UserEntity savedUser = userRepository.save(user);

        GeneratedModelEntity model = new GeneratedModelEntity();
        model.setUser(savedUser);
        model.setPrompt("Nozzle Mount Bracket");
        model.setFilePath("models/bracket.stl");
        model.setStatus("ready");
        GeneratedModelEntity savedModel = modelRepository.save(model);

        PrintJobEntity job = new PrintJobEntity();
        job.setModel(savedModel);
        job.setStatus("printing");
        job.setProgress(45);
        job.setAutoPauseEnabled(true);
        job.setStartTime(OffsetDateTime.now());
        PrintJobEntity savedJob = jobRepository.save(job);

        assertNotNull(savedJob.getId());

        ErrorLogEntity log = new ErrorLogEntity();
        log.setPrintJob(savedJob);
        log.setErrorType("spaghetti");
        log.setConfidence(0.89);
        log.setActionTaken("alerted");
        ErrorLogEntity savedLog = errorLogRepository.save(log);

        assertNotNull(savedLog.getId());
        assertEquals(savedJob.getId(), savedLog.getPrintJob().getId());
    }
}
