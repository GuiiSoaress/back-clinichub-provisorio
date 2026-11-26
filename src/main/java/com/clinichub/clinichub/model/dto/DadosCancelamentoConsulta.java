package com.clinichub.clinichub.model.dto;

import com.clinichub.clinichub.enums.MotivoCancelamento;
import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotNull;

public record DadosCancelamentoConsulta(
    @NotNull @JsonAlias({"id", "id_consulta", "consultaId"}) Long idConsulta,
    @NotNull @JsonAlias({"motivo_cancelamento", "motivoCancelamento"}) MotivoCancelamento motivo
) {
}
