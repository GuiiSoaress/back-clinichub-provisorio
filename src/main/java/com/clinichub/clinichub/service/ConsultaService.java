package com.clinichub.clinichub.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.clinichub.clinichub.infra.exception.ValidacaoException;
import com.clinichub.clinichub.model.dto.DadosAgendamentoConsulta;
import com.clinichub.clinichub.model.dto.DadosCancelamentoConsulta;
import com.clinichub.clinichub.model.dto.DadosDetalhamentoConsulta;
import com.clinichub.clinichub.model.entity.Consulta;
import com.clinichub.clinichub.model.entity.Medico;
import com.clinichub.clinichub.repository.ConsultaRepository;
import com.clinichub.clinichub.repository.MedicoRepository;
import com.clinichub.clinichub.repository.PacienteRepository;
import com.clinichub.clinichub.service.validacoes.agendamento.ValidadorAgendamentoDeConsulta;
import com.clinichub.clinichub.service.validacoes.cancelamento.ValidadorCancelamentoDeConsulta;

@Service
public class ConsultaService {

    private final ConsultaRepository consultaRepository;
    private final MedicoRepository medicoRepository;
    private final PacienteRepository pacienteRepository;
    private final List<ValidadorAgendamentoDeConsulta> validadoresAgendamento;
    private final List<ValidadorCancelamentoDeConsulta> validadoresCancelamento;

    ConsultaService(
            ConsultaRepository consultaRepository,
            MedicoRepository medicoRepository,
            PacienteRepository pacienteRepository,
            List<ValidadorAgendamentoDeConsulta> validadoresAgendamento,
            List<ValidadorCancelamentoDeConsulta> validadoresCancelamento
    ) {
        this.consultaRepository = consultaRepository;
        this.medicoRepository = medicoRepository;
        this.pacienteRepository = pacienteRepository;
        this.validadoresAgendamento = validadoresAgendamento;
        this.validadoresCancelamento = validadoresCancelamento;
    }

    public DadosDetalhamentoConsulta agendar(DadosAgendamentoConsulta dados) {
        if (!pacienteRepository.existsById(dados.idPaciente())) {
            throw new ValidacaoException("Id do paciente informado não existe!");
        }
        if (dados.idMedico() != null && !medicoRepository.existsById(dados.idMedico())) {
            throw new ValidacaoException("Id do médico informado não existe!");
        }

        validadoresAgendamento.forEach(v -> v.validar(dados));

        var paciente = pacienteRepository.getReferenceById(dados.idPaciente());
        var medico = escolherMedico(dados);

        var consulta = new Consulta(medico, paciente, dados.data());
        consultaRepository.save(consulta);

        return new DadosDetalhamentoConsulta(consulta);
    }

    public Page<DadosDetalhamentoConsulta> listar(Pageable paginacao) {
        return consultaRepository.findAll(paginacao)
                .map(DadosDetalhamentoConsulta::new);
    }

    public DadosDetalhamentoConsulta detalhar(Long id) {
        var consulta = consultaRepository.getReferenceById(id);
        return new DadosDetalhamentoConsulta(consulta);
    }

    public void cancelar(DadosCancelamentoConsulta dados) {
        if (!consultaRepository.existsById(dados.idConsulta())) {
            throw new ValidacaoException("Id da consulta informado não existe!");
        }

        var consulta = consultaRepository.getReferenceById(dados.idConsulta());
        if (consulta.getMotivoCancelamento() != null) {
            throw new ValidacaoException("Consulta já foi cancelada!");
        }

        validadoresCancelamento.forEach(v -> v.validar(dados));

        consulta.cancelar(dados.motivo());
    }

    private Medico escolherMedico(DadosAgendamentoConsulta dados) {
        if (dados.idMedico() != null) {
            return medicoRepository.getReferenceById(dados.idMedico());
        }

        var medicosDisponiveis = medicoRepository.buscarMedicosDisponiveisNaData(
                dados.especialidade(),
                dados.data().minusHours(1),
                dados.data().plusHours(1)
        );

        if (medicosDisponiveis.isEmpty()) {
            throw new ValidacaoException("Não existe médico disponível nessa data!");
        }

        var indiceAleatorio = ThreadLocalRandom.current().nextInt(medicosDisponiveis.size());
        return medicosDisponiveis.get(indiceAleatorio);
    }

}
