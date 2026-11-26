package com.clinichub.clinichub.model.dto;

import java.time.LocalDateTime;

import com.clinichub.clinichub.model.entity.Consulta;

public record DadosDetalhamentoConsulta(
    Long id,
    Long idMedico,
    Long idPaciente,
    LocalDateTime data
) {
    public DadosDetalhamentoConsulta(Consulta consulta) {
        this(
            consulta.getId(),
            consulta.getMedico().getId(),
            consulta.getPaciente().getId(),
            consulta.getData()
        );
    }
}
