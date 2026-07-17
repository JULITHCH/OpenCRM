package de.julith.opencrm.shared.tenancy;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Führt Arbeit unter einem explizit gesetzten Tenant-Kontext in einer NEUEN Transaktion aus.
 * Nötig überall dort, wo der Kontext nicht aus dem Request-JWT stammt: Tenant-Provisionierung
 * (Seeding für den frisch angelegten Mandanten) und Hintergrundjobs (Spring Batch).
 * Die neue Transaktion erzwingt eine frische Connection, die den Kontext über den
 * RlsConnectionProvider erhält; der vorherige Kontext wird danach wiederhergestellt.
 */
@Component
public class TenantScopedExecutor {

    private final TransactionTemplate requiresNew;

    public TenantScopedExecutor(PlatformTransactionManager transactionManager) {
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T callAs(UUID tenantId, Supplier<T> work) {
        UUID previous = TenantContext.get();
        try {
            TenantContext.set(tenantId);
            return requiresNew.execute(status -> work.get());
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }

    public void runAs(UUID tenantId, Runnable work) {
        callAs(tenantId, () -> {
            work.run();
            return null;
        });
    }
}
