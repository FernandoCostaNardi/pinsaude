package br.com.pinsaude.onboarding.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CryptoService {

    private final JdbcTemplate jdbc;

    @Value("${crypto.key:pinsaude-dev-key-change-in-prod}")
    private String cryptoKey;

    public CryptoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public byte[] encrypt(String plaintext) {
        if (plaintext == null) return null;
        return jdbc.queryForObject(
            "SELECT onboarding.encrypt_sensitive(?, ?)",
            byte[].class, plaintext, cryptoKey);
    }

    public String decrypt(byte[] ciphertext) {
        if (ciphertext == null) return null;
        return jdbc.queryForObject(
            "SELECT onboarding.decrypt_sensitive(?, ?)",
            String.class, ciphertext, cryptoKey);
    }
}
