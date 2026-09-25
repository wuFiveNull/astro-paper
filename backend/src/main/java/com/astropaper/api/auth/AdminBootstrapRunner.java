package com.astropaper.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapService bootstrapService;
    private final boolean enabled;
    private final String username;
    private final String email;
    private final String displayName;
    private final String password;

    public AdminBootstrapRunner(
        AdminBootstrapService bootstrapService,
        @Value("${app.bootstrap-admin.enabled:false}") boolean enabled,
        @Value("${app.bootstrap-admin.username:}") String username,
        @Value("${app.bootstrap-admin.email:}") String email,
        @Value("${app.bootstrap-admin.display-name:}") String displayName,
        @Value("${app.bootstrap-admin.password:}") String password
    ) {
        this.bootstrapService = bootstrapService;
        this.enabled = enabled;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        boolean created = bootstrapService.createFirstAdmin(username, email, displayName, password);
        if (created) {
            logger.info("One-time administrator bootstrap completed. Remove ADMIN_BOOTSTRAP_* values from the deployment environment.");
        } else {
            logger.info("Administrator bootstrap skipped because at least one account already exists.");
        }
    }
}
