package com.clinichub.clinichub.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinichub.clinichub.model.dto.DadosCadastroMedico;
import com.clinichub.clinichub.repository.MedicoRepository;

@RestController 
@RequestMapping("medicos")
public class MedicoController {

    private final MedicoRepository medicoRepository;

    MedicoController(MedicoRepository medicoRepository) {
        this.medicoRepository = medicoRepository;
    }

    @PostMapping
    public void cadastrar(@RequestBody DadosCadastroMedico dados){
        System.out.println(dados);
    }

}
