package br.com.pinsaude.onboarding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keycloak.admin")
public record KeycloakAdminProperties(
    String serverUrl,
    String realm,
    String username,
    String password
) {}
