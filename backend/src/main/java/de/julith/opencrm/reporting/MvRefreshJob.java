package de.julith.opencrm.reporting;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refresh der Dashboard-MV alle 15 Minuten, genau eine Instanz (ShedLock, E-43).
 * CONCURRENTLY läuft ausserhalb einer Transaktion (Autocommit) und blockiert Leser nicht.
 */
@Component
public class MvRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(MvRefreshJob.class);

    private final JdbcTemplate jdbcTemplate;

    public MvRefreshJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    @SchedulerLock(name = "mv_sales_kpis_daily_refresh", lockAtMostFor = "14m")
    public void refresh() {
        long start = System.currentTimeMillis();
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_sales_kpis_daily");
        log.info("mv_sales_kpis_daily aktualisiert in {} ms", System.currentTimeMillis() - start);
    }
}
