package br.com.pinsaude.onboarding.config;

/**
 * Mantém o CNPJ do tenant ativo no thread corrente durante uma requisição HTTP.
 * String vazia significa "gestão — sem filtro de tenant (ver todos os dados)".
 * Deve ser limpo no finally do TenantFilter para evitar vazamento entre threads.
 */
public final class TenantContext {

    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String cnpj) {
        TENANT.set(cnpj != null ? cnpj : "");
    }

    public static String get() {
        return TENANT.get();
    }

    public static void clear() {
        TENANT.remove();
    }
}
