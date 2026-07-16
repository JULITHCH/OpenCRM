package de.julith.opencrm.shared.tenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getIdentifier();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
