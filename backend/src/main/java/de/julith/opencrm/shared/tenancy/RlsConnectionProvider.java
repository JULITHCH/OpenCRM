package de.julith.opencrm.shared.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

/**
 * Setzt beim Verleih einer Pool-Connection die Session-Variable {@code app.current_tenant},
 * auf die die RLS-Policies vergleichen, und setzt sie bei Rückgabe garantiert zurück
 * (Connection-Reuse durch HikariCP darf nie einen fremden Tenant-Kontext erben).
 * Ohne gesetzten Kontext liefern die Policies keine Zeilen — fail-closed.
 */
@Component
public class RlsConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    public RlsConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        if (!TenantContext.NONE.equals(tenantIdentifier)) {
            // UUID-Parse verhindert, dass je ein nicht-validierter Wert die Session erreicht
            UUID tenantId = UUID.fromString(tenantIdentifier);
            try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
                ps.setString(1, tenantId.toString());
                ps.execute();
            }
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("RESET app.current_tenant");
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return unwrapType.isInstance(this);
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return unwrapType.cast(this);
    }
}
