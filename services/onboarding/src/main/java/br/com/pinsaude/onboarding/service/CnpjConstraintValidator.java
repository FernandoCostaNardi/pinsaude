package br.com.pinsaude.onboarding.service;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CnpjConstraintValidator implements ConstraintValidator<ValidCnpj, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;
        return CnpjValidator.isValid(value);
    }
}
