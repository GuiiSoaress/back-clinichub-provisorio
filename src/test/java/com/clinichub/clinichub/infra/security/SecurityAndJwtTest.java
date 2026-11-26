package com.clinichub.clinichub.infra.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.clinichub.clinichub.model.entity.Usuario;
import com.clinichub.clinichub.repository.UsuarioRepository;

import jakarta.servlet.FilterChain;

class SecurityAndJwtTest {

    private final TokenService tokenService = new TokenService("12345678");

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveGerarEValidarTokenJwtRetornandoSubject() {
        var usuario = new Usuario(1L, "admin@clinichub.com", "hash");
        var token = tokenService.gerarToken(usuario);

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals("admin@clinichub.com", tokenService.getSubject(token));
    }

    @Test
    void deveLancarExcecaoParaTokenAdulteradoOuInvalido() {
        var usuario = new Usuario(1L, "admin@clinichub.com", "hash");
        var tokenValido = tokenService.gerarToken(usuario);
        var tokenAdulterado = tokenValido.substring(0, tokenValido.length() - 2) + "xx";

        var ex = assertThrows(RuntimeException.class, () -> tokenService.getSubject(tokenAdulterado));
        assertEquals("Token JWT inválido ou expirado!", ex.getMessage());
    }

    @Test
    void securityFilterDeveAutenticarQuandoTokenForValido() throws Exception {
        var usuarioRepository = mock(UsuarioRepository.class);
        var usuario = new Usuario(1L, "admin@clinichub.com", "hash");
        when(usuarioRepository.findByLogin("admin@clinichub.com")).thenReturn(usuario);

        var filter = new SecurityFilter(tokenService, usuarioRepository);
        var token = tokenService.gerarToken(usuario);

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(usuario, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void securityFilterDeveRetornar401QuandoTokenForInvalido() throws Exception {
        var usuarioRepository = mock(UsuarioRepository.class);
        var filter = new SecurityFilter(tokenService, usuarioRepository);

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token.invalido.aqui");
        var response = new MockHttpServletResponse();
        var filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertEquals("{\"mensagem\":\"Token JWT inválido ou expirado!\"}", response.getContentAsString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(request, response);
    }

}
