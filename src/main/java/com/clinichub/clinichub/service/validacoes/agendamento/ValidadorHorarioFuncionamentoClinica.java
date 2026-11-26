package com.clinichub.clinichub.service.validacoes.agendamento;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.springframework.stereotype.Component;

import com.clinichub.clinichub.infra.exception.ValidacaoException;
import com.clinichub.clinichub.model.dto.DadosAgendamentoConsulta;

@Component
public class ValidadorHorarioFuncionamentoClinica implements ValidadorAgendamentoDeConsulta {

    private static final LocalTime ABERTURA = LocalTime.of(7, 0);
    private static final LocalTime ULTIMO_HORARIO_INICIO = LocalTime.of(18, 0);

    @Override
    public void validar(DadosAgendamentoConsulta dados) {
        var dataConsulta = dados.data();
        var domingo = dataConsulta.getDayOfWeek().equals(DayOfWeek.SUNDAY);
        var horario = dataConsulta.toLocalTime();
        var antesDaAbertura = horario.isBefore(ABERTURA);
        var depoisDoEncerramento = horario.isAfter(ULTIMO_HORARIO_INICIO);

        if (domingo || antesDaAbertura || depoisDoEncerramento) {
            throw new ValidacaoException("Consulta fora do horário de funcionamento da clínica!");
        }
    }

}
