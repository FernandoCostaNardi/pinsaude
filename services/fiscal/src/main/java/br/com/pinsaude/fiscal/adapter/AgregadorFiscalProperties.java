package br.com.pinsaude.fiscal.adapter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais e configuração do agregador fiscal (PlugNotas / Nuvem Fiscal).
 * Valores injetados do Vault via spring.config.import=optional:vault://
 * — nunca hardcoded em application.yml.
 */
@ConfigurationProperties(prefix = "nfse")
public class AgregadorFiscalProperties {

    private boolean enabled = false;
    private String apiToken = "";
    private String baseUrl = "https://api.plugnotas.com.br";
    private int timeoutSegundos = 30;

    public boolean isEnabled()                      { return enabled; }
    public void setEnabled(boolean v)               { this.enabled = v; }
    public String getApiToken()                     { return apiToken; }
    public void setApiToken(String v)               { this.apiToken = v; }
    public String getBaseUrl()                      { return baseUrl; }
    public void setBaseUrl(String v)                { this.baseUrl = v; }
    public int getTimeoutSegundos()                 { return timeoutSegundos; }
    public void setTimeoutSegundos(int v)           { this.timeoutSegundos = v; }
}
