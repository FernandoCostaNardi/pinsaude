package br.com.pinsaude.onboarding.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CnpjValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "11.222.333/0001-81",
        "11222333000181",
        "22.333.444/0001-81",
        "22333444000181"
    })
    void cnpjValido_retornaTrue(String cnpj) {
        assertThat(CnpjValidator.isValid(cnpj)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "11.222.333/0001-44",
        "00000000000000",
        "11111111111111",
        "12345678000100",
        "1234",
        ""
    })
    void cnpjInvalido_retornaFalse(String cnpj) {
        assertThat(CnpjValidator.isValid(cnpj)).isFalse();
    }

    @Test
    void cnpjNulo_retornaFalse() {
        assertThat(CnpjValidator.isValid(null)).isFalse();
    }

    @Test
    void cnpjTodosDigitosIguais_retornaFalse() {
        assertThat(CnpjValidator.isValid("99999999999999")).isFalse();
    }

    @Test
    void cnpjComMascara_retornaMesmoResultadoSemMascara() {
        assertThat(CnpjValidator.isValid("11.222.333/0001-81"))
            .isEqualTo(CnpjValidator.isValid("11222333000181"));
    }
}
