package com.clinichub.clinichub.model.dto;

import com.clinichub.clinichub.enums.Especialidade;
import com.clinichub.clinichub.model.entity.Medico;

public record DadosListagemMedico(
    Long id,
    String nome,
    String email,
    String crm,
    Especialidade especialidade
) {
    public DadosListagemMedico(Medico medico) {
        this(medico.getId(), medico.getNome(), medico.getEmail(), medico.getCrm(), medico.getEspecialidade());
    }
}
