package com.clinichub.clinichub.model.dto;

import com.clinichub.clinichub.enums.Especialidade;
import com.clinichub.clinichub.model.entity.Endereco;
import com.clinichub.clinichub.model.entity.Medico;

public record DadosDetalhamentoMedico(
    Long id,
    String nome,
    String email,
    String telefone,
    String crm,
    Especialidade especialidade,
    Endereco endereco
) {
    public DadosDetalhamentoMedico(Medico medico) {
        this(
            medico.getId(),
            medico.getNome(),
            medico.getEmail(),
            medico.getTelefone(),
            medico.getCrm(),
            medico.getEspecialidade(),
            medico.getEndereco()
        );
    }
}
