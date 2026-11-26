package com.clinichub.clinichub.service.validacoes.agendamento;

import org.springframework.stereotype.Component;

import com.clinichub.clinichub.infra.exception.ValidacaoException;
import com.clinichub.clinichub.model.dto.DadosAgendamentoConsulta;
import com.clinichub.clinichub.repository.MedicoRepository;

@Component
public class ValidadorMedicoAtivo implements ValidadorAgendamentoDeConsulta {

    private final MedicoRepository medicoRepository;

    ValidadorMedicoAtivo(MedicoRepository medicoRepository) {
        this.medicoRepository = medicoRepository;
    }

    @Override
    public void validar(DadosAgendamentoConsulta dados) {
        if (dados.idMedico() == null) {
            return;
        }

        var medicoEstaAtivo = medicoRepository.findAtivoById(dados.idMedico());
        if (!Boolean.TRUE.equals(medicoEstaAtivo)) {
            throw new ValidacaoException("Consulta não pode ser agendada com médico inativo!");
        }
    }

}
