package com.thinq.backoffice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The FMS back-office gateway: TechExcel's own request and response shapes, served either from a
 * built-in mock or proxied to the real back office, decided by one flag.
 *
 * <p><b>ONLY THE ENDPOINTS THE FMS MAPPING ASSIGNS TO THE FUND MANAGEMENT SERVICE LIVE HERE.</b>
 * The vendor's back office serves far more, and the mapping splits them by owning service — the
 * four here are the ones whose responsibility is funds, charges and the running account. The
 * others in the same document ({@code new_segment_enable},
 * {@code client_active_inactive_status_update}, {@code add_brokerage}, {@code portfolio_insert})
 * belong to the Order Management Service and are deliberately absent: two services answering the
 * same write is worse than one service missing a read.
 *
 * <p><b>The layout is the vendor's category column, not ours.</b> Each API lives in the package
 * for the category it belongs to — {@code funds}, {@code brokerage}, {@code accounting} — so
 * "where does ledger live" has one answer and the next endpoint has an obvious home. The two
 * cross-cutting mechanisms sit apart from all of them: {@code scheduler} keeps the upstream token
 * alive, {@code ratelimit} caps what one caller may spend. Both are configuration-driven and
 * neither belongs to any one category.
 *
 * <p>{@code platform} is what every category package shares: the vendor's envelope, its
 * validation vocabulary, and the single place a call leaves this process.
 *
 * <p>{@code @EnableScheduling} IS THE WHOLE OF THE BACKGROUND MECHANISM here, and it is declared
 * here rather than beside the job so there is one place to look for the answer to "does this
 * process run crons at all". Removing it silently disables
 * {@code scheduler.TokenRefresher} — the managed TechExcel login — and nothing else would say so.
 *
 * <p>THERE IS NO DATABASE. The back office is the record. The only in-memory state is the mock's
 * token map and the rate limiter's buckets, and both state their ceilings where they are defined.
 */
@SpringBootApplication
@EnableScheduling
public class BackofficeApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackofficeApplication.class, args);
	}
}
