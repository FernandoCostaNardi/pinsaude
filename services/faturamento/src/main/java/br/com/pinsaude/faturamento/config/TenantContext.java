package br.com.pinsaude.faturamento.config;

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
