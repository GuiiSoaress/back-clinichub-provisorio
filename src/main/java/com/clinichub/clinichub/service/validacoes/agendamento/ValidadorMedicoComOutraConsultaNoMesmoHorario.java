package com.clinichub.clinichub.service.validacoes.agendamento;

import org.springframework.stereotype.Component;

import com.clinichub.clinichub.infra.exception.ValidacaoException;
import com.clinichub.clinichub.model.dto.DadosAgendamentoConsulta;
import com.clinichub.clinichub.repository.ConsultaRepository;

@Component
public class ValidadorMedicoComOutraConsultaNoMesmoHorario implements ValidadorAgendamentoDeConsulta {

    private final ConsultaRepository consultaRepository;

    ValidadorMedicoComOutraConsultaNoMesmoHorario(ConsultaRepository consultaRepository) {
        this.consultaRepository = consultaRepository;
    }

    @Override
    public void validar(DadosAgendamentoConsulta dados) {
        if (dados.idMedico() == null) {
            return;
        }

        var medicoPossuiOutraConsultaNoMesmoHorario = consultaRepository
                .existsByMedicoIdAndDataGreaterThanAndDataLessThanAndMotivoCancelamentoIsNull(
                        dados.idMedico(),
                        dados.data().minusHours(1),
                        dados.data().plusHours(1)
                );

        if (medicoPossuiOutraConsultaNoMesmoHorario) {
            throw new ValidacaoException("Médico já possui outra consulta agendada nesse mesmo horário!");
        }
    }

}
