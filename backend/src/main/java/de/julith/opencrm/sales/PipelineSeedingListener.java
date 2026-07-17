package de.julith.opencrm.sales;

import de.julith.opencrm.shared.tenancy.TenantScopedExecutor;
import de.julith.opencrm.tenant.TenantProvisionedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PipelineSeedingListener {

    private final PipelineProvisioningService pipelineProvisioningService;
    private final TenantScopedExecutor tenantScopedExecutor;

    public PipelineSeedingListener(PipelineProvisioningService pipelineProvisioningService,
                                   TenantScopedExecutor tenantScopedExecutor) {
        this.pipelineProvisioningService = pipelineProvisioningService;
        this.tenantScopedExecutor = tenantScopedExecutor;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TenantProvisionedEvent event) {
        tenantScopedExecutor.runAs(event.tenantId(),
                () -> pipelineProvisioningService.seedDefaultPipeline(event.tenantId()));
    }
}
