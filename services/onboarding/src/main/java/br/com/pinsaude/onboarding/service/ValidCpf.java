package br.com.pinsaude.onboarding.service;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = CpfConstraintValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCpf {
    String message() default "CPF inválido";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
