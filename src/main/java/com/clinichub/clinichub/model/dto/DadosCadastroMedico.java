package com.clinichub.clinichub.model.dto;

import com.clinichub.clinichub.enums.Especialidade;

public record DadosCadastroMedico(String nome, 
    String email,
    String crm,
    Especialidade especialidade,
    DadosEndereco endereco

) {
    
}
