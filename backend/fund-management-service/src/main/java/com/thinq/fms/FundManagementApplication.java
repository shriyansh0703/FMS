package com.thinq.fms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Fund Management System service.
 *
 * <p>A module inside the estate's existing Spring Boot monolith rather than a new deployable — the
 * HLD's modular-monolith constraint. It is bootable on its own for local work and for generating
 * the API description, which is what makes the Swagger gate checkable.
 *
 * <p>Note what is deliberately absent: no {@code @EnableScheduling}. The end-of-day payout run is
 * scheduled, and a service that starts scheduling on every developer's laptop would instruct real
 * payouts from a local process. Scheduling is enabled by the deployment that owns the single-runner
 * arrangement, not by the application class.
 */
@SpringBootApplication
public class FundManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(FundManagementApplication.class, args);
    }
}
