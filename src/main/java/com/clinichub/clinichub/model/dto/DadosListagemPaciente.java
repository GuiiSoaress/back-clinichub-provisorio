package com.clinichub.clinichub.model.dto;

import com.clinichub.clinichub.model.entity.Paciente;

public record DadosListagemPaciente(
    Long id,
    String nome,
    String email,
    String cpf
) {
    public DadosListagemPaciente(Paciente paciente) {
        this(paciente.getId(), paciente.getNome(), paciente.getEmail(), paciente.getCpf());
    }
}
