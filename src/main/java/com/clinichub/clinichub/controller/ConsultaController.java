package com.clinichub.clinichub.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.clinichub.clinichub.model.dto.DadosAgendamentoConsulta;
import com.clinichub.clinichub.model.dto.DadosCancelamentoConsulta;
import com.clinichub.clinichub.model.dto.DadosDetalhamentoConsulta;
import com.clinichub.clinichub.service.ConsultaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("consultas")
public class ConsultaController {

    private final ConsultaService consultaService;

    ConsultaController(ConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<DadosDetalhamentoConsulta> agendar(
            @RequestBody @Valid DadosAgendamentoConsulta dados,
            UriComponentsBuilder uriBuilder) {
        var dto = consultaService.agendar(dados);
        var uri = uriBuilder.path("/consultas/{id}").buildAndExpand(dto.id()).toUri();
        return ResponseEntity.created(uri).body(dto);
    }

    @GetMapping
    public ResponseEntity<Page<DadosDetalhamentoConsulta>> listar(
            @PageableDefault(size = 10, sort = {"data"}) Pageable paginacao) {
        var page = consultaService.listar(paginacao);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DadosDetalhamentoConsulta> detalhar(@PathVariable Long id) {
        return ResponseEntity.ok(consultaService.detalhar(id));
    }

    @DeleteMapping
    @Transactional
    public ResponseEntity<Void> cancelar(@RequestBody @Valid DadosCancelamentoConsulta dados) {
        consultaService.cancelar(dados);
        return ResponseEntity.noContent().build();
    }

}
