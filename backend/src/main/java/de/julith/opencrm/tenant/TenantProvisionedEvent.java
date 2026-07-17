package de.julith.opencrm.tenant;

import java.util.UUID;

/**
 * Wird nach erfolgreicher Tenant-Provisionierung publiziert; andere Module
 * (z. B. sales für die Default-Pipeline) hängen sich entkoppelt daran.
 */
public record TenantProvisionedEvent(UUID tenantId) {
}
