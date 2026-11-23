package br.com.pinsaude.faturamento.previa;

import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.domain.TipoTomador;
import br.com.pinsaude.faturamento.domain.Tomador;
import br.com.pinsaude.faturamento.domain.TomadorAliquota;
import br.com.pinsaude.faturamento.dto.PreviewCalculoRequest;
import br.com.pinsaude.faturamento.dto.PreviewCalculoResponse;
import br.com.pinsaude.faturamento.port.MotorFiscalPort;
import br.com.pinsaude.faturamento.port.MotorFiscalPort.MotorFiscalException;
import br.com.pinsaude.faturamento.port.MotorFiscalPort.PedidoCalculo;
import br.com.pinsaude.faturamento.port.MotorFiscalPort.ResultadoCalculo;
import br.com.pinsaude.faturamento.repository.ServicoRepository;
import br.com.pinsaude.faturamento.repository.TomadorAliquotaRepository;
import br.com.pinsaude.faturamento.repository.TomadorRepository;
import br.com.pinsaude.faturamento.service.PreviaTributosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CBS-30 — a prévia da produção vem do motor fiscal, não de um cálculo próprio do faturamento. */
@ExtendWith(MockitoExtension.class)
class PreviaTributosServiceTest {

    @Mock TomadorRepository tomadorRepo;
    @Mock ServicoRepository servicoRepo;
    @Mock TomadorAliquotaRepository aliquotaRepo;
    @Mock MotorFiscalPort motor;

    PreviaTributosService service;
    Tomador tomador;
    Servico servico;

    @BeforeEach
    void setUp() {
        service = new PreviaTributosService(tomadorRepo, servicoRepo, aliquotaRepo, motor);

        tomador = new Tomador();
        tomador.setId(UUID.randomUUID());
        tomador.setCnpjIdTenant("12345678000190");
        tomador.setTipo(TipoTomador.HOSPITAL);
        tomador.setIndicadorRetencaoFederal(true);
        tomador.setIndicadorRetencaoIss(false);

        servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setAliquotaIss(new BigDecimal("5.0000"));
        servico.setAliquotaIr(new BigDecimal("1.5000"));
        servico.setAliquotaCsll(new BigDecimal("1.0000"));
        servico.setAliquotaPis(new BigDecimal("0.6500"));
        servico.setAliquotaCofins(new BigDecimal("3.0000"));
        servico.setCstIbsCbs("200");
        servico.setCClassTrib("200029");
        servico.setReducaoIbsCbs(new BigDecimal("0.6000"));

        when(tomadorRepo.findById(tomador.getId())).thenReturn(Optional.of(tomador));
        when(servicoRepo.findById(servico.getId())).thenReturn(Optional.of(servico));
    }

    private ResultadoCalculo resultadoIbsCbs() {
        // Motor (regime novo, 2027, cenário A, dentro do preço) para bruto de R$ 10.000,00
        return new ResultadoCalculo(
            1_000_000L, 150_000L,
            48_000L, 14_400L, 9_600L, 0L, 0L,
            24_000L, 129_000L, 21_000L,
            "A", "2027-01", "IBS_CBS",
            43_000L, 14_000L,
            new BigDecimal("0.0448"), new BigDecimal("0.0004"), BigDecimal.ONE,
            "200", "200029", false, 960_000L, 1_000_000L,
            List.of("IR", "CSLL"));
    }

    @Test
    void montaPedidoComAliquotasEmFracaoEClassificacaoDoServico() {
        TomadorAliquota issDoTomador = new TomadorAliquota();
        issDoTomador.setTipoTributo("ISS");
        issDoTomador.setValorAliquota(new BigDecimal("2.0000"));
        when(aliquotaRepo.findByTomadorId(tomador.getId())).thenReturn(List.of(issDoTomador));
        when(motor.calcular(any(), any())).thenReturn(resultadoIbsCbs());

        service.calcular(new PreviewCalculoRequest(servico.getId(), tomador.getId(), 1_000_000L, null, "2027-01"));

        ArgumentCaptor<PedidoCalculo> captor = ArgumentCaptor.forClass(PedidoCalculo.class);
        verify(motor).calcular(eq("12345678000190"), captor.capture());
        PedidoCalculo p = captor.getValue();
        assertThat(p.valorBruto()).isEqualTo(1_000_000L);
        assertThat(p.competencia()).isEqualTo("2027-01");
        assertThat(p.tomadorPj()).isTrue();
        assertThat(p.indicadorRetencaoFederal()).isTrue();
        assertThat(p.indicadorRetencaoIss()).isFalse();
        assertThat(p.equiparacaoHospitalar()).isFalse();
        // tomador sobrepõe o serviço; percentual → fração
        assertThat(p.aliquotasOverride().get("ISS")).isEqualByComparingTo("0.02");
        assertThat(p.aliquotasOverride().get("IR")).isEqualByComparingTo("0.015");
        assertThat(p.aliquotasOverride().get("PIS")).isEqualByComparingTo("0.0065");
        assertThat(p.aliquotasOverride().get("COFINS")).isEqualByComparingTo("0.03");
        assertThat(p.reducaoIbsCbs()).isEqualByComparingTo("0.6");
        assertThat(p.cstIbsCbs()).isEqualTo("200");
        assertThat(p.cClassTrib()).isEqualTo("200029");
        assertThat(p.cbsIbsPorFora()).isNull();
    }

    @Test
    void semCompetenciaUsaMesAtualEPacientePfNaoEhPj() {
        tomador.setTipo(TipoTomador.PACIENTE_PF);
        servico.setReducaoIbsCbs(BigDecimal.ZERO);
        servico.setCstIbsCbs(null);
        when(aliquotaRepo.findByTomadorId(tomador.getId())).thenReturn(List.of());
        when(motor.calcular(any(), any())).thenReturn(resultadoIbsCbs());

        service.calcular(new PreviewCalculoRequest(servico.getId(), tomador.getId(), 1_000_000L, null));

        ArgumentCaptor<PedidoCalculo> captor = ArgumentCaptor.forClass(PedidoCalculo.class);
        verify(motor).calcular(any(), captor.capture());
        assertThat(captor.getValue().competencia()).isEqualTo(YearMonth.now().toString());
        assertThat(captor.getValue().tomadorPj()).isFalse();
        assertThat(captor.getValue().reducaoIbsCbs()).isNull();
        assertThat(captor.getValue().cstIbsCbs()).isNull();
    }

    @Test
    void respostaReproduzOMotorComATaxaPinDoMedico() {
        when(aliquotaRepo.findByTomadorId(tomador.getId())).thenReturn(List.of());
        when(motor.calcular(any(), any())).thenReturn(resultadoIbsCbs());

        PreviewCalculoResponse r = service.calcular(new PreviewCalculoRequest(
            servico.getId(), tomador.getId(), 1_000_000L, new BigDecimal("0.1000"), "2027-01"));

        assertThat(r.regime()).isEqualTo("IBS_CBS");
        assertThat(r.cenario()).isEqualTo("A");
        assertThat(r.valorCbs()).isEqualTo(43_000L);
        assertThat(r.valorIbs()).isEqualTo(14_000L);
        assertThat(r.valorIss()).isEqualTo(48_000L);
        assertThat(r.valorPis()).isZero();
        // só o que o tomador retém
        assertThat(r.issRetido()).isZero();
        assertThat(r.irRetido()).isEqualTo(14_400L);
        assertThat(r.csllRetido()).isEqualTo(9_600L);
        assertThat(r.totalRetencoes()).isEqualTo(24_000L);
        // taxa Pin do médico (10%), não os 15% fixos do motor
        assertThat(r.taxaPin()).isEqualTo(100_000L);
        assertThat(r.valorLiquidoMedico()).isEqualTo(900_000L);
        // tributos da Pin no motor = 150.000 − 21.000 = 129.000 → 100.000 − 129.000
        assertThat(r.resultadoPin()).isEqualTo(-29_000L);
        assertThat(r.totalTributos()).isEqualTo(129_000L);
        assertThat(r.valorTotalNota()).isEqualTo(1_000_000L);
        assertThat(r.tributosRetidos()).containsExactly("IR", "CSLL");
    }

    @Test
    void recusaDoMotorViraErroComOMesmoStatus() {
        when(aliquotaRepo.findByTomadorId(tomador.getId())).thenReturn(List.of());
        when(motor.calcular(any(), any()))
            .thenThrow(new MotorFiscalException(422, "Cronograma da reforma sem alíquotas para 2034", null));

        assertThatThrownBy(() -> service.calcular(new PreviewCalculoRequest(
                servico.getId(), tomador.getId(), 1_000_000L, null, "2034-01")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                ResponseStatusException rse = (ResponseStatusException) e;
                assertThat(rse.getStatusCode().value()).isEqualTo(422);
                assertThat(rse.getReason()).contains("2034");
            });
    }
}
