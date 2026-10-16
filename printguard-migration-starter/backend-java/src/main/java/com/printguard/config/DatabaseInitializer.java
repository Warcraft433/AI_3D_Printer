package com.printguard.config;

import com.printguard.auth.UserEntity;
import com.printguard.auth.UserRepository;
import com.printguard.jobs.PrintJobEntity;
import com.printguard.jobs.PrintJobRepository;
import com.printguard.logs.ErrorLogEntity;
import com.printguard.logs.ErrorLogRepository;
import com.printguard.models.GeneratedModelEntity;
import com.printguard.models.GeneratedModelRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final GeneratedModelRepository modelRepository;
    private final PrintJobRepository jobRepository;
    private final ErrorLogRepository errorLogRepository;
    private final com.printguard.auth.PasswordService passwordService;

    public DatabaseInitializer(
            UserRepository userRepository,
            GeneratedModelRepository modelRepository,
            PrintJobRepository jobRepository,
            ErrorLogRepository errorLogRepository,
            com.printguard.auth.PasswordService passwordService) {
        this.userRepository = userRepository;
        this.modelRepository = modelRepository;
        this.jobRepository = jobRepository;
        this.errorLogRepository = errorLogRepository;
        this.passwordService = passwordService;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            System.out.println("[PrintGuard] Seeding initial preserved baseline records...");

            UserEntity admin = new UserEntity();
            admin.setUsername("admin");
            admin.setEmail("admin@ai3dprinter.local");
            admin.setPassword(passwordService.encode("adminpassword123"));
            admin.setIsSuperuser(true);
            admin.setIsStaff(true);
            admin = userRepository.save(admin);

            GeneratedModelEntity model3 = new GeneratedModelEntity();
            model3.setUser(admin);
            model3.setPrompt("3D Printer Simulation — Spaghetti Error Demo");
            model3.setStatus("ready");
            model3.setGenerationTime(0.0);
            model3.setFilePath("");
            model3 = modelRepository.save(model3);

            GeneratedModelEntity model4 = new GeneratedModelEntity();
            model4.setUser(admin);
            model4.setPrompt("cup");
            model4.setStatus("failed");
            model4.setErrorDetail("Traceback: ModuleNotFoundError: No module named 'torch'");
            modelRepository.save(model4);

            PrintJobEntity job2 = new PrintJobEntity();
            job2.setModel(model3);
            job2.setStatus("ready_to_print");
            job2.setGcodePath("demo/demo_print.gcode");
            job2.setEstimatedTime(600);
            job2.setProgress(0);
            job2.setAutoPauseEnabled(true);
            job2 = jobRepository.save(job2);

            ErrorLogEntity log1 = new ErrorLogEntity();
            log1.setPrintJob(job2);
            log1.setErrorType("spaghetti");
            log1.setConfidence(0.927);
            log1.setActionTaken("alerted");
            log1.setFrameImage("detections/defect_1.jpg");
            errorLogRepository.save(log1);

            ErrorLogEntity log2 = new ErrorLogEntity();
            log2.setPrintJob(job2);
            log2.setErrorType("spaghetti");
            log2.setConfidence(0.952);
            log2.setActionTaken("alerted");
            log2.setFrameImage("detections/defect_2.jpg");
            errorLogRepository.save(log2);

            ErrorLogEntity log3 = new ErrorLogEntity();
            log3.setPrintJob(job2);
            log3.setErrorType("spaghetti");
            log3.setConfidence(0.848);
            log3.setActionTaken("alerted");
            log3.setFrameImage("detections/defect_3.jpg");
            errorLogRepository.save(log3);

            System.out.println("[PrintGuard] Seeding complete! Preserved 1 user, 2 models, 1 print job, 3 error logs.");
        }
    }
}
