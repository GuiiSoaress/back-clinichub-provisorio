package com.clinichub.clinichub.infra.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.persistence.EntityNotFoundException;

@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Void> tratarErro404() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<List<DadosErroValidacao>> tratarErro400(MethodArgumentNotValidException e) {
        var erros = e.getFieldErrors();
        return ResponseEntity.badRequest().body(erros.stream().map(DadosErroValidacao::new).toList());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<DadosErro> tratarErro400(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new DadosErro(ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<DadosErro> tratarErro401() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new DadosErro("Login ou senha inválidos"));
    }

    @ExceptionHandler(ValidacaoException.class)
    public ResponseEntity<DadosErro> tratarErroRegraDeNegocio(ValidacaoException ex) {
        return ResponseEntity.badRequest().body(new DadosErro(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<DadosErro> tratarErroArgumentoInvalido(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new DadosErro(ex.getMessage()));
    }

    private record DadosErroValidacao(String campo, String mensagem) {
        public DadosErroValidacao(FieldError erro) {
            this(erro.getField(), erro.getDefaultMessage());
        }
    }

    private record DadosErro(String mensagem) {
    }
}
