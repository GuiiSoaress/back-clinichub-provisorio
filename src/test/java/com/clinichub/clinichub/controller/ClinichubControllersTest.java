package com.clinichub.clinichub.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.clinichub.clinichub.enums.Especialidade;
import com.clinichub.clinichub.enums.MotivoCancelamento;
import com.clinichub.clinichub.infra.exception.TratadorDeErros;
import com.clinichub.clinichub.infra.exception.ValidacaoException;
import com.clinichub.clinichub.infra.security.TokenService;
import com.clinichub.clinichub.model.dto.DadosAgendamentoConsulta;
import com.clinichub.clinichub.model.dto.DadosCancelamentoConsulta;
import com.clinichub.clinichub.model.dto.DadosDetalhamentoConsulta;
import com.clinichub.clinichub.model.entity.Endereco;
import com.clinichub.clinichub.model.entity.Medico;
import com.clinichub.clinichub.model.entity.Paciente;
import com.clinichub.clinichub.model.entity.Usuario;
import com.clinichub.clinichub.repository.MedicoRepository;
import com.clinichub.clinichub.repository.PacienteRepository;
import com.clinichub.clinichub.service.ConsultaService;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class ClinichubControllersTest {

    @Mock
    private MedicoRepository medicoRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private ConsultaService consultaService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenService tokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var medicoController = new MedicoController(medicoRepository);
        var pacienteController = new PacienteController(pacienteRepository);
        var consultaController = new ConsultaController(consultaService);
        var autenticacaoController = new AutenticacaoController(authenticationManager, tokenService);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        medicoController,
                        pacienteController,
                        consultaController,
                        autenticacaoController
                )
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new TratadorDeErros())
                .build();
    }

    private Endereco enderecoExemplo() {
        return new Endereco("Rua A", "Centro", "12345678", "São Paulo", "SP", "100", "Sala 1");
    }

    @Test
    void deveUsarPaginacaoPadraoDeDezRegistrosOrdenadosPorNomeEmMedicos() throws Exception {
        when(medicoRepository.findAllByAtivoTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/medicos"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(Pageable.class);
        verify(medicoRepository).findAllByAtivoTrue(captor.capture());

        var pageable = captor.getValue();
        assertEquals(10, pageable.getPageSize());
        assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("nome").getDirection());
    }

    @Test
    void deveCadastrarDetalharAtualizarEExcluirMedicoComCodigosHttpCorretos() throws Exception {
        when(medicoRepository.save(any(Medico.class))).thenAnswer(inv -> {
            var m = inv.getArgument(0, Medico.class);
            ReflectionTestUtils.setField(m, "id", 1L);
            return m;
        });

        var cadastroJson = """
                {
                    "nome": "Dra. Ana",
                    "email": "ana@clinichub.com",
                    "telefone": "11999999999",
                    "crm": "123456",
                    "especialidade": "CARDIOLOGIA",
                    "endereco": {
                        "logradouro": "Rua A",
                        "bairro": "Centro",
                        "cep": "12345678",
                        "cidade": "São Paulo",
                        "uf": "SP",
                        "numero": "100",
                        "complemento": "Sala 1"
                    }
                }
                """;

        mockMvc.perform(post("/medicos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastroJson))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/medicos/1")))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.nome").value("Dra. Ana"))
                .andExpect(jsonPath("$.crm").value("123456"));

        var medico = new Medico(1L, "Dra. Ana", "ana@clinichub.com", "11999999999", "123456",
                Especialidade.CARDIOLOGIA, enderecoExemplo(), true);
        when(medicoRepository.getReferenceById(1L)).thenReturn(medico);

        mockMvc.perform(get("/medicos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("ana@clinichub.com"));

        var atualizacaoJson = """
                {
                    "id": 1,
                    "nome": "Dra. Ana Paula",
                    "telefone": "11988887777"
                }
                """;

        mockMvc.perform(put("/medicos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizacaoJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Dra. Ana Paula"))
                .andExpect(jsonPath("$.telefone").value("11988887777"));

        mockMvc.perform(delete("/medicos/1"))
                .andExpect(status().isNoContent());

        when(medicoRepository.getReferenceById(99L)).thenThrow(new EntityNotFoundException());
        mockMvc.perform(get("/medicos/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveUsarPaginacaoPadraoDeDezRegistrosOrdenadosPorNomeEmPacientes() throws Exception {
        when(pacienteRepository.findAllByAtivoTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/pacientes"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(Pageable.class);
        verify(pacienteRepository).findAllByAtivoTrue(captor.capture());

        var pageable = captor.getValue();
        assertEquals(10, pageable.getPageSize());
        assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("nome").getDirection());
    }

    @Test
    void deveCadastrarDetalharAtualizarEExcluirPacienteComCodigosHttpCorretos() throws Exception {
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(inv -> {
            var p = inv.getArgument(0, Paciente.class);
            ReflectionTestUtils.setField(p, "id", 2L);
            return p;
        });

        var cadastroJson = """
                {
                    "nome": "Carlos Silva",
                    "email": "carlos@email.com",
                    "telefone": "11977776666",
                    "cpf": "123.456.789-01",
                    "endereco": {
                        "logradouro": "Rua B",
                        "bairro": "Jardins",
                        "cep": "01234567",
                        "cidade": "São Paulo",
                        "uf": "SP"
                    }
                }
                """;

        mockMvc.perform(post("/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastroJson))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/pacientes/2")))
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.cpf").value("123.456.789-01"));

        var paciente = new Paciente(2L, "Carlos Silva", "carlos@email.com", "11977776666",
                "123.456.789-01", enderecoExemplo(), true);
        when(pacienteRepository.getReferenceById(2L)).thenReturn(paciente);

        mockMvc.perform(get("/pacientes/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.nome").value("Carlos Silva"));

        var atualizacaoJson = """
                {
                    "id": 2,
                    "nome": "Carlos Eduardo Silva"
                }
                """;

        mockMvc.perform(put("/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizacaoJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Carlos Eduardo Silva"));

        mockMvc.perform(delete("/pacientes/2"))
                .andExpect(status().isNoContent());

        when(pacienteRepository.getReferenceById(99L)).thenThrow(new EntityNotFoundException());
        mockMvc.perform(get("/pacientes/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornarListaCampoEMensagemQuandoValidacaoFalhar() throws Exception {
        mockMvc.perform(post("/medicos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].campo").exists())
                .andExpect(jsonPath("$[0].mensagem").exists());
    }

    @Test
    void deveAgendarConsultaComSucessoERetornar200() throws Exception {
        var data = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        when(consultaService.agendar(any(DadosAgendamentoConsulta.class)))
                .thenReturn(new DadosDetalhamentoConsulta(1L, 2L, 3L, data));

        var json = """
                {
                    "idMedico": 2,
                    "idPaciente": 3,
                    "data": "%s"
                }
                """.formatted(data);

        mockMvc.perform(post("/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.idMedico").value(2L))
                .andExpect(jsonPath("$.idPaciente").value(3L));
    }

    @Test
    void deveRetornar400QuandoAgendamentoViolarRegraDeNegocio() throws Exception {
        var data = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        when(consultaService.agendar(any(DadosAgendamentoConsulta.class)))
                .thenThrow(new ValidacaoException("Consulta fora do horário de funcionamento da clínica!"));

        var json = """
                {
                    "idMedico": 2,
                    "idPaciente": 3,
                    "data": "%s"
                }
                """.formatted(data);

        mockMvc.perform(post("/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Consulta fora do horário de funcionamento da clínica!"));
    }

    @Test
    void deveCancelarConsultaComSucessoERetornar204() throws Exception {
        doNothing().when(consultaService).cancelar(any(DadosCancelamentoConsulta.class));

        var json = """
                {
                    "idConsulta": 1,
                    "motivo": "PACIENTE_DESISTIU"
                }
                """;

        mockMvc.perform(delete("/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNoContent());

        verify(consultaService).cancelar(new DadosCancelamentoConsulta(1L, MotivoCancelamento.PACIENTE_DESISTIU));
    }

    @Test
    void deveRetornar400QuandoCancelarSemMotivo() throws Exception {
        var json = """
                {
                    "idConsulta": 1
                }
                """;

        mockMvc.perform(delete("/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveEfetuarLoginERetornarTokenJwt() throws Exception {
        var usuario = new Usuario(1L, "admin@clinichub.com", "hash");
        var auth = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(tokenService.gerarToken(usuario)).thenReturn("token.jwt.gerado");

        var json = """
                {
                    "login": "admin@clinichub.com",
                    "senha": "123456"
                }
                """;

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token.jwt.gerado"));
    }

    @Test
    void deveRetornar401QuandoCredenciaisDeLoginForemInvalidas() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        var json = """
                {
                    "login": "admin@clinichub.com",
                    "senha": "errada"
                }
                """;

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Login ou senha inválidos"));
    }

}
