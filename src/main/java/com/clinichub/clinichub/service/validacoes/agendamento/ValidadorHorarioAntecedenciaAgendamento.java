package com.clinichub.clinichub.service.validacoes.agendamento;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.clinichub.clinichub.infra.exception.ValidacaoException;
import com.clinichub.clinichub.model.dto.DadosAgendamentoConsulta;

@Component
public class ValidadorHorarioAntecedenciaAgendamento implements ValidadorAgendamentoDeConsulta {

    @Override
    public void validar(DadosAgendamentoConsulta dados) {
        var dataConsulta = dados.data();
        var agora = LocalDateTime.now();
        var diferencaEmMinutos = Duration.between(agora, dataConsulta).toMinutes();

        if (diferencaEmMinutos < 30) {
            throw new ValidacaoException("Consulta deve ser agendada com antecedência mínima de 30 minutos!");
        }
    }

}
