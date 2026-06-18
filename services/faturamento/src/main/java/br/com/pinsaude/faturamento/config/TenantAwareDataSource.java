package br.com.pinsaude.faturamento.config;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource delegate) {
        super(delegate);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = super.getConnection();
        applyTenant(conn);
        return conn;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection conn = super.getConnection(username, password);
        applyTenant(conn);
        return conn;
    }

    private void applyTenant(Connection conn) {
        String cnpj = TenantContext.get();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, false)")) {
            ps.setString(1, cnpj != null ? cnpj : "");
            ps.execute();
        } catch (Exception ignored) {
            // set_config é específico do PostgreSQL; ignorado silenciosamente em H2 (testes)
        }
    }
}
