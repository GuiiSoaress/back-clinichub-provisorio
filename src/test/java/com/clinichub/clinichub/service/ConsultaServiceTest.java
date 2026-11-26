package com.clinichub.clinichub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.clinichub.clinichub.enums.Especialidade;
import com.clinichub.clinichub.enums.MotivoCancelamento;
import com.clinichub.clinichub.infra.exception.ValidacaoException;
import com.clinichub.clinichub.model.dto.DadosAgendamentoConsulta;
import com.clinichub.clinichub.model.dto.DadosCancelamentoConsulta;
import com.clinichub.clinichub.model.entity.Consulta;
import com.clinichub.clinichub.model.entity.Endereco;
import com.clinichub.clinichub.model.entity.Medico;
import com.clinichub.clinichub.model.entity.Paciente;
import com.clinichub.clinichub.repository.ConsultaRepository;
import com.clinichub.clinichub.repository.MedicoRepository;
import com.clinichub.clinichub.repository.PacienteRepository;
import com.clinichub.clinichub.service.validacoes.agendamento.ValidadorHorarioAntecedenciaAgendamento;
import com.clinichub.clinichub.service.validacoes.agendamento.ValidadorHorarioFuncionamentoClinica;
import com.clinichub.clinichub.service.validacoes.agendamento.ValidadorMedicoAtivo;
import com.clinichub.clinichub.service.validacoes.agendamento.ValidadorMedicoComOutraConsultaNoMesmoHorario;
import com.clinichub.clinichub.service.validacoes.agendamento.ValidadorPacienteAtivo;
import com.clinichub.clinichub.service.validacoes.agendamento.ValidadorPacienteSemOutraConsultaNoDia;
import com.clinichub.clinichub.service.validacoes.cancelamento.ValidadorHorarioAntecedenciaCancelamento;

@ExtendWith(MockitoExtension.class)
class ConsultaServiceTest {

    @Mock
    private ConsultaRepository consultaRepository;

    @Mock
    private MedicoRepository medicoRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private ValidadorHorarioFuncionamentoClinica validadorHorarioFuncionamento;

    @InjectMocks
    private ValidadorHorarioAntecedenciaAgendamento validadorHorarioAntecedenciaAgendamento;

    @InjectMocks
    private ValidadorPacienteAtivo validadorPacienteAtivo;

    @InjectMocks
    private ValidadorMedicoAtivo validadorMedicoAtivo;

    @InjectMocks
    private ValidadorPacienteSemOutraConsultaNoDia validadorPacienteSemOutraConsultaNoDia;

    @InjectMocks
    private ValidadorMedicoComOutraConsultaNoMesmoHorario validadorMedicoComOutraConsultaNoMesmoHorario;

    @InjectMocks
    private ValidadorHorarioAntecedenciaCancelamento validadorHorarioAntecedenciaCancelamento;

    private ConsultaService consultaService;

    @BeforeEach
    void setUp() {
        consultaService = new ConsultaService(
                consultaRepository,
                medicoRepository,
                pacienteRepository,
                List.of(
                        validadorHorarioFuncionamento,
                        validadorHorarioAntecedenciaAgendamento,
                        validadorPacienteAtivo,
                        validadorMedicoAtivo,
                        validadorPacienteSemOutraConsultaNoDia,
                        validadorMedicoComOutraConsultaNoMesmoHorario
                ),
                List.of(validadorHorarioAntecedenciaCancelamento)
        );
    }

    private LocalDateTime proximaSegundaAs(int hora, int minuto) {
        return LocalDate.now()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atTime(LocalTime.of(hora, minuto));
    }

    private Medico medico(Long id, Especialidade especialidade) {
        return new Medico(id, "Dr. João", "joao@clinica.com", "11999999999", "123456", especialidade, new Endereco(), true);
    }

    private Paciente paciente(Long id) {
        return new Paciente(id, "Maria", "maria@email.com", "11988888888", "12345678901", new Endereco(), true);
    }

    private void mockBaseValidaAgendamento(Long idPaciente, Long idMedico) {
        when(pacienteRepository.existsById(idPaciente)).thenReturn(true);
        when(pacienteRepository.findAtivoById(idPaciente)).thenReturn(true);
        when(consultaRepository.existsByPacienteIdAndDataBetweenAndMotivoCancelamentoIsNull(eq(idPaciente), any(), any()))
                .thenReturn(false);

        if (idMedico != null) {
            when(medicoRepository.existsById(idMedico)).thenReturn(true);
            when(medicoRepository.findAtivoById(idMedico)).thenReturn(true);
            when(consultaRepository.existsByMedicoIdAndDataGreaterThanAndDataLessThanAndMotivoCancelamentoIsNull(
                    eq(idMedico), any(), any())).thenReturn(false);
        }
    }

    @Test
    void naoDeveAgendarNoDomingo() {
        var domingo = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).atTime(10, 0);
        var dados = new DadosAgendamentoConsulta(1L, 1L, domingo, Especialidade.CARDIOLOGIA);

        when(pacienteRepository.existsById(1L)).thenReturn(true);
        when(medicoRepository.existsById(1L)).thenReturn(true);

        assertThrows(ValidacaoException.class, () -> consultaService.agendar(dados));
    }

    @Test
    void naoDeveAgendarAntesDasSeteHoras() {
        var dados = new DadosAgendamentoConsulta(1L, 1L, proximaSegundaAs(6, 59), Especialidade.CARDIOLOGIA);

        when(pacienteRepository.existsById(1L)).thenReturn(true);
        when(medicoRepository.existsById(1L)).thenReturn(true);

        assertThrows(ValidacaoException.class, () -> consultaService.agendar(dados));
    }

    @Test
    void naoDeveAgendarDepoisDasDezoitoHoras() {
        var dados1830 = new DadosAgendamentoConsulta(1L, 1L, proximaSegundaAs(18, 30), Especialidade.CARDIOLOGIA);
        var dados1900 = new DadosAgendamentoConsulta(1L, 1L, proximaSegundaAs(19, 0), Especialidade.CARDIOLOGIA);

        when(pacienteRepository.existsById(1L)).thenReturn(true);
        when(medicoRepository.existsById(1L)).thenReturn(true);

        assertThrows(ValidacaoException.class, () -> consultaService.agendar(dados1830));
        assertThrows(ValidacaoException.class, () -> consultaService.agendar(dados1900));
    }

    @Test
    void devePermitirAgendarAsSeteEAsDezoitoHoras() {
        var horario7 = proximaSegundaAs(7, 0);
        var horario18 = proximaSegundaAs(18, 0);

        mockBaseValidaAgendamento(1L, 1L);
        when(pacienteRepository.getReferenceById(1L)).thenReturn(paciente(1L));
        when(medicoRepository.getReferenceById(1L)).thenReturn(medico(1L, Especialidade.CARDIOLOGIA));

        var detalhes7 = consultaService.agendar(new DadosAgendamentoConsulta(1L, 1L, horario7, Especialidade.CARDIOLOGIA));
        var detalhes18 = consultaService.agendar(new DadosAgendamentoConsulta(1L, 1L, horario18, Especialidade.CARDIOLOGIA));

        assertEquals(horario7, detalhes7.data());
        assertEquals(horario18, detalhes18.data());
    }

    @Test
    void naoDeveAgendarComMenosDeTrintaMinutosDeAntecedencia() {
        var daquiVinteMinutos = LocalDateTime.now().plusMinutes(20);
        var dados = new DadosAgendamentoConsulta(1L, 1L, daquiVinteMinutos, null);

        assertThrows(ValidacaoException.class, () -> validadorHorarioAntecedenciaAgendamento.validar(dados));
    }

    @Test
    void devePermitirAgendarComMaisDeTrintaMinutosDeAntecedencia() {
        var dataValida = proximaSegundaAs(10, 0);
        validadorHorarioAntecedenciaAgendamento.validar(new DadosAgendamentoConsulta(1L, 1L, dataValida, null));
    }

    @Test
    void naoDeveAgendarComPacienteInativo() {
        var dados = new DadosAgendamentoConsulta(1L, 1L, proximaSegundaAs(10, 0), null);
        when(pacienteRepository.existsById(1L)).thenReturn(true);
        when(medicoRepository.existsById(1L)).thenReturn(true);
        when(pacienteRepository.findAtivoById(1L)).thenReturn(false);

        assertThrows(ValidacaoException.class, () -> consultaService.agendar(dados));
    }

    @Test
    void naoDeveAgendarComMedicoInativo() {
        var dados = new DadosAgendamentoConsulta(1L, 1L, proximaSegundaAs(10, 0), null);
        when(pacienteRepository.existsById(1L)).thenReturn(true);
        when(medicoRepository.existsById(1L)).thenReturn(true);
        when(pacienteRepository.findAtivoById(1L)).thenReturn(true);
        when(medicoRepository.findAtivoById(1L)).thenReturn(false);

        assertThrows(ValidacaoException.class, () -> consultaService.agendar(dados));
    }

    @Test
    void naoDeveAgendarMaisDeUmaConsultaNoMesmoDiaParaMesmoPaciente() {
        var dados = new DadosAgendamentoConsulta(1L, 1L, proximaSegundaAs(14, 0), null);
        when(pacienteRepository.existsById(1L)).thenReturn(true);
        when(medicoRepository.existsById(1L)).thenReturn(true);
        when(pacienteRepository.findAtivoById(1L)).thenReturn(true);
        when(medicoRepository.findAtivoById(1L)).thenReturn(true);
        when(consultaRepository.existsByPacienteIdAndDataBetweenAndMotivoCancelamentoIsNull(eq(1L), any(), any()))
                .thenReturn(true);

        assertThrows(ValidacaoException.class, () -> consultaService.agendar(dados));
    }

    @Test
    void naoDeveAgendarComMedicoOcupadoNoMesmoHorario() {
        var dados = new DadosAgendamentoConsulta(1L, 1L, proximaSegundaAs(15, 0), null);
        when(pacienteRepository.existsById(1L)).thenReturn(true);
        when(medicoRepository.existsById(1L)).thenReturn(true);
        when(pacienteRepository.findAtivoById(1L)).thenReturn(true);
        when(medicoRepository.findAtivoById(1L)).thenReturn(true);
        when(consultaRepository.existsByPacienteIdAndDataBetweenAndMotivoCancelamentoIsNull(eq(1L), any(), any()))
                .thenReturn(false);
        when(consultaRepository.existsByMedicoIdAndDataGreaterThanAndDataLessThanAndMotivoCancelamentoIsNull(
                eq(1L), any(), any())).thenReturn(true);

        assertThrows(ValidacaoException.class, () -> consultaService.agendar(dados));
    }

    @Test
    void deveEscolherMedicoAleatorioQuandoIdMedicoNaoInformado() {
        var data = proximaSegundaAs(10, 0);
        var dadosSemEspecialidade = new DadosAgendamentoConsulta(null, 1L, data, null);
        var dadosComEspecialidade = new DadosAgendamentoConsulta(null, 1L, data, Especialidade.ORTOPEDIA);

        mockBaseValidaAgendamento(1L, null);
        when(pacienteRepository.getReferenceById(1L)).thenReturn(paciente(1L));
        when(medicoRepository.buscarMedicosDisponiveisNaData(eq(null), any(), any()))
                .thenReturn(List.of(medico(10L, Especialidade.CARDIOLOGIA)));
        when(medicoRepository.buscarMedicosDisponiveisNaData(eq(Especialidade.ORTOPEDIA), any(), any()))
                .thenReturn(List.of(medico(20L, Especialidade.ORTOPEDIA)));

        var resultado1 = consultaService.agendar(dadosSemEspecialidade);
        var resultado2 = consultaService.agendar(dadosComEspecialidade);

        assertEquals(10L, resultado1.idMedico());
        assertEquals(20L, resultado2.idMedico());
        verify(consultaRepository, org.mockito.Mockito.times(2)).save(any(Consulta.class));
    }

    @Test
    void deveLancarExcecaoQuandoNaoHouverMedicoDisponivelNaData() {
        var data = proximaSegundaAs(10, 0);
        var dados = new DadosAgendamentoConsulta(null, 1L, data, Especialidade.DERMATOLOGIA);

        mockBaseValidaAgendamento(1L, null);
        when(pacienteRepository.getReferenceById(1L)).thenReturn(paciente(1L));
        when(medicoRepository.buscarMedicosDisponiveisNaData(eq(Especialidade.DERMATOLOGIA), any(), any()))
                .thenReturn(List.of());

        assertThrows(ValidacaoException.class, () -> consultaService.agendar(dados));
    }

    @Test
    void naoDeveCancelarConsultaComMenosDe24HorasDeAntecedencia() {
        var consulta = new Consulta(1L, medico(1L, Especialidade.CARDIOLOGIA), paciente(1L), LocalDateTime.now().plusHours(10), null);
        var dados = new DadosCancelamentoConsulta(1L, MotivoCancelamento.PACIENTE_DESISTIU);

        when(consultaRepository.existsById(1L)).thenReturn(true);
        when(consultaRepository.getReferenceById(1L)).thenReturn(consulta);

        assertThrows(ValidacaoException.class, () -> consultaService.cancelar(dados));
    }

    @Test
    void deveCancelarConsultaComMaisDe24HorasDeAntecedencia() {
        var consulta = new Consulta(1L, medico(1L, Especialidade.CARDIOLOGIA), paciente(1L), LocalDateTime.now().plusHours(48), null);
        var dados = new DadosCancelamentoConsulta(1L, MotivoCancelamento.MEDICO_CANCELOU);

        when(consultaRepository.existsById(1L)).thenReturn(true);
        when(consultaRepository.getReferenceById(1L)).thenReturn(consulta);

        consultaService.cancelar(dados);

        assertNotNull(consulta.getMotivoCancelamento());
        assertEquals(MotivoCancelamento.MEDICO_CANCELOU, consulta.getMotivoCancelamento());
    }

    @Test
    void naoDeveCancelarConsultaJaCanceladaOuInexistente() {
        var consultaJaCancelada = new Consulta(1L, medico(1L, Especialidade.CARDIOLOGIA), paciente(1L), LocalDateTime.now().plusHours(48), MotivoCancelamento.OUTROS);

        when(consultaRepository.existsById(99L)).thenReturn(false);
        assertThrows(ValidacaoException.class,
                () -> consultaService.cancelar(new DadosCancelamentoConsulta(99L, MotivoCancelamento.OUTROS)));

        when(consultaRepository.existsById(1L)).thenReturn(true);
        when(consultaRepository.getReferenceById(1L)).thenReturn(consultaJaCancelada);
        assertThrows(ValidacaoException.class,
                () -> consultaService.cancelar(new DadosCancelamentoConsulta(1L, MotivoCancelamento.PACIENTE_DESISTIU)));
    }

}
