package br.com.pinsaude.onboarding.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Envolve o DataSource auto-configurado pelo Spring Boot com TenantAwareDataSource,
 * garantindo que app.current_tenant seja definido em cada conexão emprestada do pool.
 * Não usa @Autowired (BPPs são instanciados antes dos beans comuns).
 */
@Component
public class TenantDataSourcePostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource ds && !(bean instanceof TenantAwareDataSource)) {
            return new TenantAwareDataSource(ds);
        }
        return bean;
    }
}
