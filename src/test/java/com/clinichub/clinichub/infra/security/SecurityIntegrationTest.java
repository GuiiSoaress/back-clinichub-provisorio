package com.clinichub.clinichub.infra.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveRetornar401ParaRotasProtegidasSemToken() throws Exception {
        mockMvc.perform(get("/medicos"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/pacientes"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/consultas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornar401ComMensagemParaTokenInvalido() throws Exception {
        mockMvc.perform(get("/medicos")
                        .header("Authorization", "Bearer token_invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Token JWT inválido ou expirado!"));
    }

    @Test
    void deveRetornar401ParaLoginComCredenciaisInvalidas() throws Exception {
        var json = """
                {
                    "login": "admin@clinichub.com",
                    "senha": "senha_errada"
                }
                """;

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Login ou senha inválidos"));
    }

    @Test
    void deveAutenticarComUsuarioDaMigrationELiberarRotasComTokenValido() throws Exception {
        var loginJson = """
                {
                    "login": "admin@clinichub.com",
                    "senha": "123456"
                }
                """;

        var body = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(body, "$.token");

        mockMvc.perform(get("/medicos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pacientes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/consultas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

}
