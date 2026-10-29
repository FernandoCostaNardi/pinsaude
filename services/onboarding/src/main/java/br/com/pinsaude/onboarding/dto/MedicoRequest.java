package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.service.ValidCpf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * CPF e CRMs são opcionais enquanto o médico não está ATIVO (sócios importados da planilha
 * podem chegar sem eles); MedicoService exige os dois para médico ativo.
 * pisNit: null = não altera; "" = remove.
 */
public record MedicoRequest(
    @ValidCpf String cpf,
    @NotBlank @Size(max = 200) String nome,
    @Valid List<CrmRequest> crms,
    List<UUID> especialidadeIds,
    @Size(max = 200) String email,
    @Size(max = 20) String telefone,
    @Size(max = 20) String pisNit,
    UUID empresaId,
    @DecimalMin(value = "0.0100", message = "Taxa Pin mínima é 1%")
    @DecimalMax(value = "0.5000", message = "Taxa Pin máxima é 50%")
    BigDecimal taxaPinPct
) {}
