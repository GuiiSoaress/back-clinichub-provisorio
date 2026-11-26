package com.clinichub.clinichub.model.dto;

import java.time.LocalDateTime;

import com.clinichub.clinichub.enums.Especialidade;
import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

public record DadosAgendamentoConsulta(
    @JsonAlias({"id_medico", "medicoId"}) Long idMedico,
    @NotNull @JsonAlias({"id_paciente", "pacienteId"}) Long idPaciente,
    @NotNull @Future LocalDateTime data,
    Especialidade especialidade
) {
}
