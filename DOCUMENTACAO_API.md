# Documentação Completa da API — ClinicHub Backend

> **Propósito deste documento:** Especificação técnica exata de todos os endpoints, contratos JSON (request/response), autenticação, paginação, regras de negócio, tratamento de erros e tipos TypeScript do backend **ClinicHub**, pronta para ser utilizada por desenvolvedores ou outra IA na adequação do Front-end.

---

## 1. Visão Geral e Configuração Base

- **Stack:** Java 25, Spring Boot 4.1.2, Spring Security (JWT Stateless), Spring Data JPA, Flyway, MySQL (`clinichub_api`).
- **Base URL padrão (desenvolvimento):** `http://localhost:8080`
- **Formato de dados:** `Content-Type: application/json` (UTF-8)
- **Fuso horário dos tokens:** `-03:00` (Horário de Brasília). Datas de consultas utilizam `LocalDateTime` sem offset (ISO-8601 local, ex.: `"2026-12-15T14:30:00"`).

### Pontos de Atenção Críticos para o Front-end

1. **CORS não está configurado no Backend (`SecurityConfigurations`):**
   - O backend **não** possui `http.cors()` ou `CorsConfigurationSource` habilitado, e qualquer requisição que não seja `POST /login` exige autenticação (`anyRequest().authenticated()`).
   - **Impacto no Front-end:** Chamadas diretas do navegador a partir de outra origem (ex.: `http://localhost:5173` ou `http://localhost:3000`) falharão no preflight `OPTIONS` por bloqueio de CORS / `401`.
   - **Como resolver no Front-end:** Configurar **proxy reverso** no bundler/framework do front-end (ex.: `server.proxy` no Vite, `rewrites` no `next.config.js` do Next.js ou `proxy.conf.json` no Angular) redirecionando chamadas para `http://localhost:8080`, evitando preflight cross-origin.
2. **`PUT` não recebe `{id}` na URL:**
   - As rotas de atualização são `PUT /medicos` e `PUT /pacientes` (sem `/{id}` no path). O `id` deve ser enviado **dentro do JSON body**.
   - Campos sensíveis/imutáveis **não** podem ser atualizados:
     - **Médico:** `email`, `crm` e `especialidade` são imutáveis após o cadastro.
     - **Paciente:** `email` e `cpf` são imutáveis após o cadastro.
3. **`DELETE /consultas` recebe JSON Body:**
   - Diferente de `DELETE /medicos/{id}` e `DELETE /pacientes/{id}`, o cancelamento de consulta é feito em `DELETE /consultas` enviando `idConsulta` e `motivo` no **corpo da requisição**.
   - Em bibliotecas como Axios, use `axios.delete('/consultas', { data: payload })`.
4. **`POST /consultas` retorna `201 Created` com header `Location`:**
   - Assim como `POST /medicos` e `POST /pacientes`, `POST /consultas` retorna `201 Created` com header `Location: .../consultas/{id}`.
5. **Listagem de Consultas retorna apenas IDs de Médico e Paciente e inclui canceladas:**
   - `GET /consultas` e `POST /consultas` retornam `{ id, idMedico, idPaciente, data, motivoCancelamento }`. Consultas canceladas possuem `motivoCancelamento` preenchido (`"PACIENTE_DESISTIU"`, `"MEDICO_CANCELOU"` ou `"OUTROS"`), enquanto ativas possuem `null`. Para exibir nomes de médicos e pacientes na tela de agenda/consultas, o front-end deve buscar/cachear os dados em `/medicos` (ou `/medicos/{id}`) e `/pacientes` (ou `/pacientes/{id}`).
6. **Exclusão Lógica (Soft Delete):**
   - `DELETE /medicos/{id}` e `DELETE /pacientes/{id}` apenas marcam `ativo = false` no banco.
   - `GET /medicos` e `GET /pacientes` listam **somente registros ativos** (`ativo = true`).
   - Porém, `GET /medicos/{id}` e `GET /pacientes/{id}` continuam retornando `200 OK` mesmo para médicos/pacientes inativados (útil para resolver nomes em consultas históricas!).
7. **Constraints de Banco sem Tratamento Customizado (`500 Internal Server Error`):**
   - Duplicidade de `email` (médico/paciente), `crm` (médico) ou `cpf` (paciente), bem como enviar `uf` com mais de **2 caracteres** (`CHAR(2)`), geram `500 Internal Server Error` no banco. O front-end deve validar tamanhos e formatos previamente.

---

## 2. Autenticação e Segurança (JWT)

Todas as rotas da API são protegidas por autenticação JWT Bearer, **exceto `POST /login`**.

- **Header obrigatório nas rotas autenticadas:**
  ```http
  Authorization: Bearer <token_jwt>
  ```
- **Características do Token JWT:**
  - **Algoritmo:** HMAC256
  - **Issuer (`iss`):** `"API ClinicHub"`
  - **Subject (`sub`):** Login do usuário (ex.: `"admin@clinichub.com"`)
  - **Validade (`exp`):** **2 horas** a partir da emissão (`ZoneOffset -03:00`)

- **Usuário padrão pré-cadastrado via Flyway (`V7__insert-usuario.sql`):**
  - **Login:** `admin@clinichub.com`
  - **Senha:** `123456`

---

## 3. Padrão Global de Respostas de Erro

O cliente HTTP do front-end deve estar preparado para tratar **3 formatos distintos de erro** conforme o status e a origem:

### 3.1. Erro `400 Bad Request` — Validação de Campos (Bean Validation `@Valid`)
Retorna um **Array JSON** contendo cada campo inválido e sua mensagem:
```json
[
  {
    "campo": "email",
    "mensagem": "deve ser um endereço de e-mail bem formado"
  },
  {
    "campo": "endereco.cep",
    "mensagem": "deve corresponder a \"\\d{8}\""
  }
]
```
> **Nota:** Campos aninhados de endereço vêm com prefixo `endereco.` (ex.: `"endereco.logradouro"`, `"endereco.cep"`, `"endereco.uf"`). As mensagens padrão do Jakarta Validation variam conforme o `Locale` da JVM ou header `Accept-Language`.

### 3.2. Erro `400 Bad Request` — Regra de Negócio, Argumento Inválido ou JSON/Enum Malformado
Quando ocorre `ValidacaoException` (regras de agendamento/cancelamento), `IllegalArgumentException` (campos em branco na atualização `PUT`) ou `HttpMessageNotReadableException` (JSON inválido, tipo incorreto ou valor de Enum inexistente), a API retorna um **Objeto JSON**:
```json
{
  "mensagem": "Consulta fora do horário de funcionamento da clínica!"
}
```

### 3.3. Erros `401 Unauthorized`
Existem três cenários para `401`:
1. **Requisição sem header `Authorization` em rota protegida:**
   - Status: `401 Unauthorized`
   - Corpo: **Vazio** (`""`)
2. **Token JWT inválido, malformado ou expirado:**
   - Status: `401 Unauthorized`
   - Corpo:
     ```json
     {
       "mensagem": "Token JWT inválido ou expirado!"
     }
     ```
3. **Falha no `POST /login` (usuário inexistente ou senha incorreta):**
   - Status: `401 Unauthorized`
   - Corpo:
     ```json
     {
       "mensagem": "Login ou senha inválidos"
     }
     ```

### 3.4. Erro `404 Not Found`
Quando um ID informado na URL ou no body de atualização (`GET /medicos/{id}`, `PUT /medicos`, `DELETE /medicos/{id}`, `GET /pacientes/{id}`, `PUT /pacientes`, `DELETE /pacientes/{id}`) não existe no banco (`EntityNotFoundException`):
- Status: `404 Not Found`
- Corpo: **Vazio** (`""`)

---

## 4. Padrão de Paginação (`Page<T>`)

Todos os endpoints de listagem (`GET /medicos`, `GET /pacientes`, `GET /consultas`) aceitam parâmetros de query do Spring Data `Pageable`:

| Query Param | Tipo | Padrão | Descrição |
| :--- | :--- | :--- | :--- |
| `page` | `number` | `0` | Índice da página (**começa em `0`**). |
| `size` | `number` | `10` | Quantidade de itens por página. |
| `sort` | `string` | Varia por rota | Campo e direção no formato `campo,asc` ou `campo,desc` (ex.: `?sort=nome,desc`). Padrão em `/medicos` e `/pacientes`: `nome,asc`. Padrão em `/consultas`: `data,asc`. |

**Exemplo de chamada paginada:** `GET /medicos?page=0&size=10&sort=nome,asc`

**Formato JSON da resposta paginada (`200 OK`):**
```json
{
  "content": [
    // Itens da página atual (T[])
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "sort": {
      "empty": false,
      "sorted": true,
      "unsorted": false
    },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "last": true,
  "totalPages": 1,
  "totalElements": 2,
  "first": true,
  "size": 10,
  "number": 0,
  "sort": {
    "empty": false,
    "sorted": true,
    "unsorted": false
  },
  "numberOfElements": 2,
  "empty": false
}
```

---

## 5. Enums e Objeto de Endereço

### 5.1. Enum `Especialidade`
Valores aceitos (case-sensitive, sempre em **MAIÚSCULAS**):
- `"CARDIOLOGIA"`
- `"DERMATOLOGIA"`
- `"GINECOLOGIA"`
- `"ORTOPEDIA"`

### 5.2. Enum `MotivoCancelamento`
Valores aceitos (case-sensitive, sempre em **MAIÚSCULAS**):
- `"PACIENTE_DESISTIU"`
- `"MEDICO_CANCELOU"`
- `"OUTROS"`

### 5.3. Objeto `Endereco` (`DadosEndereco`)
Usado aninhado em Médicos e Pacientes:

| Campo | Tipo | Obrigatório no `POST` | Validação / Limite no Banco | Comportamento no `PUT` (quando `endereco` é enviado) |
| :--- | :--- | :--- | :--- | :--- |
| `logradouro` | `string` | Sim (`@NotBlank`) | Máx. 100 caracteres | Se `null`, mantém atual. Se `""` (em branco), retorna `400` (`"Logradouro não pode estar em branco."`). |
| `bairro` | `string` | Sim (`@NotBlank`) | Máx. 100 caracteres | Se `null`, mantém atual. Se `""`, retorna `400` (`"Bairro não pode estar em branco."`). |
| `cep` | `string` | Sim (`@NotBlank`) | Regex `^\d{8}$` (**8 dígitos sem traço**, ex.: `"14800000"`). Coluna `VARCHAR(9)`. | Se `null`, mantém atual. Se `""`, retorna `400` (`"CEP não pode estar em branco."`). |
| `cidade` | `string` | Sim (`@NotBlank`) | Máx. 100 caracteres | Se `null`, mantém atual. Se `""`, retorna `400` (`"Cidade não pode estar em branco."`). |
| `uf` | `string` | Sim (`@NotBlank`) | **Exatamente 2 letras** (`CHAR(2)` no banco, ex.: `"SP"`). | Se `null`, mantém atual. Se `""`, retorna `400` (`"UF não pode estar em branco."`). |
| `numero` | `string \| null` | Não (Opcional) | Máx. 20 caracteres | Se `null`, mantém atual. Se enviado (inclusive `""`), sobrescreve. |
| `complemento` | `string \| null` | Não (Opcional) | Máx. 100 caracteres | Se `null`, mantém atual. Se enviado (inclusive `""`), sobrescreve. |

---

## 6. Especificação Detalhada dos Endpoints

---

### Módulo 1: Autenticação (`/login`)

#### `POST /login` — Efetuar Login
Autentica um usuário e retorna o token JWT.

- **Autenticação:** Pública (não requer token).
- **Request Body (`DadosAutenticacao`):**
  | Campo | Tipo | Obrigatório | Validação |
  | :--- | :--- | :--- | :--- |
  | `login` | `string` | Sim | `@NotBlank` (Máx. 100 chars) |
  | `senha` | `string` | Sim | `@NotBlank` |

  ```json
  {
    "login": "admin@clinichub.com",
    "senha": "123456"
  }
  ```
- **Respostas:**
  - **`200 OK` (`DadosTokenJWT`):**
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    }
    ```
  - **`400 Bad Request`:** Array `[{ "campo": "login", "mensagem": "..." }]` se campos vazios/ausentes.
  - **`401 Unauthorized`:**
    ```json
    {
      "mensagem": "Login ou senha inválidos"
    }
    ```

---

### Módulo 2: Médicos (`/medicos`)

> **Autenticação:** Todos os endpoints exigem header `Authorization: Bearer <token>`.

#### 2.1. `POST /medicos` — Cadastrar Médico
Cadastra um novo médico ativo (`ativo = true`).

- **Request Body (`DadosCadastroMedico`):**
  | Campo | Tipo | Obrigatório | Validações e Regras |
  | :--- | :--- | :--- | :--- |
  | `nome` | `string` | Sim | `@NotBlank`, máx. 100 caracteres. |
  | `email` | `string` | Sim | `@NotBlank`, `@Email`, **único** no banco, máx. 100 caracteres. |
  | `telefone` | `string` | Sim | `@NotBlank`, máx. 20 caracteres (ex.: `"16999990001"`). |
  | `crm` | `string` | Sim | `@NotBlank`, regex `^\d{4,6}$` (**apenas 4 a 6 dígitos numéricos**), **único** no banco. |
  | `especialidade` | `Especialidade` | Sim | `@NotNull`. Valores: `"CARDIOLOGIA"`, `"DERMATOLOGIA"`, `"GINECOLOGIA"`, `"ORTOPEDIA"`. |
  | `endereco` | `DadosEndereco` | Sim | `@NotNull`, `@Valid` (ver seção 5.3). |

  ```json
  {
    "nome": "Ana Souza",
    "email": "ana.souza@clinichub.com",
    "telefone": "16999990001",
    "crm": "123456",
    "especialidade": "CARDIOLOGIA",
    "endereco": {
      "logradouro": "Rua das Flores",
      "bairro": "Centro",
      "cep": "14800000",
      "cidade": "Araraquara",
      "uf": "SP",
      "numero": "100",
      "complemento": "Sala 2"
    }
  }
  ```
- **Respostas:**
  - **`201 Created` (`DadosDetalhamentoMedico`):**
    - **Header:** `Location: http://localhost:8080/medicos/{id}`
    - **Body:**
      ```json
      {
        "id": 1,
        "nome": "Ana Souza",
        "email": "ana.souza@clinichub.com",
        "telefone": "16999990001",
        "crm": "123456",
        "especialidade": "CARDIOLOGIA",
        "endereco": {
          "logradouro": "Rua das Flores",
          "bairro": "Centro",
          "cep": "14800000",
          "cidade": "Araraquara",
          "uf": "SP",
          "numero": "100",
          "complemento": "Sala 2"
        }
      }
      ```
  - **`400 Bad Request`:** Array de erros de validação ou objeto `{"mensagem": "..."}` se JSON/Enum inválido.
  - **`401 Unauthorized`:** Sem token ou token inválido.

---

#### 2.2. `GET /medicos` — Listar Médicos Ativos (Paginado)
Retorna apenas médicos com `ativo = true`.
> **Atenção:** O item da listagem (`DadosListagemMedico`) é resumido e **não** contém `telefone` nem `endereco`. Para editar um médico e preencher todos os campos no formulário, consulte `GET /medicos/{id}`.

- **Query Params:** `?page=0&size=10&sort=nome,asc` (todos opcionais; padrão `size=10`, ordenado por `nome` ascendente).
- **Respostas:**
  - **`200 OK` (`Page<DadosListagemMedico>`):**
    ```json
    {
      "content": [
        {
          "id": 1,
          "nome": "Ana Souza",
          "email": "ana.souza@clinichub.com",
          "crm": "123456",
          "especialidade": "CARDIOLOGIA"
        }
      ],
      "pageable": { "pageNumber": 0, "pageSize": 10, "offset": 0, "paged": true, "unpaged": false, "sort": { "empty": false, "sorted": true, "unsorted": false } },
      "last": true,
      "totalPages": 1,
      "totalElements": 1,
      "first": true,
      "size": 10,
      "number": 0,
      "sort": { "empty": false, "sorted": true, "unsorted": false },
      "numberOfElements": 1,
      "empty": false
    }
    ```
  - **`401 Unauthorized`:** Sem token ou token inválido.

---

#### 2.3. `GET /medicos/{id}` — Detalhar Médico por ID
Retorna todos os dados cadastrais do médico (incluindo `telefone` e `endereco`).

- **Path Param:** `id` (`number` / `Long`)
- **Respostas:**
  - **`200 OK` (`DadosDetalhamentoMedico`):**
    ```json
    {
      "id": 1,
      "nome": "Ana Souza",
      "email": "ana.souza@clinichub.com",
      "telefone": "16999990001",
      "crm": "123456",
      "especialidade": "CARDIOLOGIA",
      "endereco": {
        "logradouro": "Rua das Flores",
        "bairro": "Centro",
        "cep": "14800000",
        "cidade": "Araraquara",
        "uf": "SP",
        "numero": "100",
        "complemento": "Sala 2"
      }
    }
    ```
  - **`404 Not Found`:** Corpo vazio se o `id` não existir.
  - **`401 Unauthorized`:** Sem token ou token inválido.

---

#### 2.4. `PUT /medicos` — Atualizar Médico
Atualiza parcialmente `nome`, `telefone` e/ou `endereco` de um médico existente.

- **Request Body (`DadosAtualizacaoMedico`):**
  | Campo | Tipo | Obrigatório | Comportamento |
  | :--- | :--- | :--- | :--- |
  | `id` | `number` | **Sim** (`@NotNull`) | ID do médico a ser atualizado. |
  | `nome` | `string \| null` | Não | Se omitido/`null`, mantém atual. Se string vazia/em branco `""`, retorna `400` (`"Nome não pode estar em branco."`). |
  | `telefone` | `string \| null` | Não | Se omitido/`null`, mantém atual. Se `""`, retorna `400` (`"Telefone não pode estar em branco."`). |
  | `endereco` | `Partial<DadosEndereco> \| null` | Não | Atualização parcial campo a campo (ver seção 5.3). |

  ```json
  {
    "id": 1,
    "nome": "Dra. Ana Paula Souza",
    "telefone": "16988887777",
    "endereco": {
      "numero": "205",
      "complemento": "Conjunto 4"
    }
  }
  ```
- **Respostas:**
  - **`200 OK` (`DadosDetalhamentoMedico`):** Objeto completo atualizado do médico (mesmo formato do `GET /medicos/{id}`).
  - **`400 Bad Request`:**
    - Array `[{"campo": "id", "mensagem": "..."}]` se `id` for `null`.
    - Objeto `{"mensagem": "Nome não pode estar em branco."}` (ou equivalente de telefone/endereço) se algum campo obrigatório for enviado como string em branco.
  - **`404 Not Found`:** Corpo vazio se `id` não existir.
  - **`401 Unauthorized`:** Sem token ou token inválido.

---

#### 2.5. `DELETE /medicos/{id}` — Excluir Médico (Exclusão Lógica)
Marca o médico como inativo (`ativo = false`). Ele deixa de aparecer em `GET /medicos` e não pode receber novos agendamentos de consultas.

- **Path Param:** `id` (`number` / `Long`)
- **Respostas:**
  - **`204 No Content`:** Corpo vazio.
  - **`404 Not Found`:** Corpo vazio se o `id` não existir.
  - **`401 Unauthorized`:** Sem token ou token inválido.

---

### Módulo 3: Pacientes (`/pacientes`)

> **Autenticação:** Todos os endpoints exigem header `Authorization: Bearer <token>`.

#### 3.1. `POST /pacientes` — Cadastrar Paciente
Cadastra um novo paciente ativo (`ativo = true`).

- **Request Body (`DadosCadastroPaciente`):**
  | Campo | Tipo | Obrigatório | Validações e Regras |
  | :--- | :--- | :--- | :--- |
  | `nome` | `string` | Sim | `@NotBlank`, máx. 100 caracteres. |
  | `email` | `string` | Sim | `@NotBlank`, `@Email`, **único** no banco, máx. 100 caracteres. |
  | `telefone` | `string` | Sim | `@NotBlank`, máx. 20 caracteres. |
  | `cpf` | `string` | Sim | `@NotBlank`, regex `^\d{3}\.?\d{3}\.?\d{3}\-?\d{2}$` (aceita **com ou sem pontuação**, ex.: `"12345678901"` ou `"123.456.789-01"`). **Único** no banco (atenção: o banco armazena a string exata enviada; recomenda-se padronizar no front-end, ex.: somente dígitos ou sempre formatado). |
  | `endereco` | `DadosEndereco` | Sim | `@NotNull`, `@Valid` (ver seção 5.3). |

  ```json
  {
    "nome": "Carla Mendes",
    "email": "carla.mendes@email.com",
    "telefone": "16999990003",
    "cpf": "123.456.789-01",
    "endereco": {
      "logradouro": "Rua Sete",
      "bairro": "Vila Xavier",
      "cep": "14810000",
      "cidade": "Araraquara",
      "uf": "SP",
      "numero": "50",
      "complemento": "Apto 12"
    }
  }
  ```
- **Respostas:**
  - **`201 Created` (`DadosDetalhamentoPaciente`):**
    - **Header:** `Location: http://localhost:8080/pacientes/{id}`
    - **Body:**
      ```json
      {
        "id": 1,
        "nome": "Carla Mendes",
        "email": "carla.mendes@email.com",
        "telefone": "16999990003",
        "cpf": "123.456.789-01",
        "endereco": {
          "logradouro": "Rua Sete",
          "bairro": "Vila Xavier",
          "cep": "14810000",
          "cidade": "Araraquara",
          "uf": "SP",
          "numero": "50",
          "complemento": "Apto 12"
        }
      }
      ```
  - **`400 Bad Request`:** Array de erros de validação.
  - **`401 Unauthorized`:** Sem token ou token inválido.

---

#### 3.2. `GET /pacientes` — Listar Pacientes Ativos (Paginado)
Retorna apenas pacientes com `ativo = true`.
> **Atenção:** O item da listagem (`DadosListagemPaciente`) contém apenas `id`, `nome`, `email` e `cpf` (não inclui `telefone` nem `endereco`). Para edição completa, consulte `GET /pacientes/{id}`.

- **Query Params:** `?page=0&size=10&sort=nome,asc` (padrão `size=10`, ordenado por `nome` ascendente).
- **Respostas:**
  - **`200 OK` (`Page<DadosListagemPaciente>`):**
    ```json
    {
      "content": [
        {
          "id": 1,
          "nome": "Carla Mendes",
          "email": "carla.mendes@email.com",
          "cpf": "123.456.789-01"
        }
      ],
      "pageable": { "pageNumber": 0, "pageSize": 10, "offset": 0, "paged": true, "unpaged": false, "sort": { "empty": false, "sorted": true, "unsorted": false } },
      "last": true,
      "totalPages": 1,
      "totalElements": 1,
      "first": true,
      "size": 10,
      "number": 0,
      "sort": { "empty": false, "sorted": true, "unsorted": false },
      "numberOfElements": 1,
      "empty": false
    }
    ```
  - **`401 Unauthorized`:** Sem token ou token inválido.

---

#### 3.3. `GET /pacientes/{id}` — Detalhar Paciente por ID
Retorna todos os dados do paciente (incluindo `telefone` e `endereco`).

- **Path Param:** `id` (`number` / `Long`)
- **Respostas:**
  - **`200 OK` (`DadosDetalhamentoPaciente`):**
    ```json
    {
      "id": 1,
      "nome": "Carla Mendes",
      "email": "carla.mendes@email.com",
      "telefone": "16999990003",
      "cpf": "123.456.789-01",
      "endereco": {
        "logradouro": "Rua Sete",
        "bairro": "Vila Xavier",
        "cep": "14810000",
        "cidade": "Araraquara",
        "uf": "SP",
        "numero": "50",
        "complemento": "Apto 12"
      }
    }
    ```
  - **`404 Not Found`:** Corpo vazio se o `id` não existir.
  - **`401 Unauthorized`:** Sem token ou token inválido.

---

#### 3.4. `PUT /pacientes` — Atualizar Paciente
Atualiza parcialmente `nome`, `telefone` e/ou `endereco` de um paciente existente.

- **Request Body (`DadosAtualizacaoPaciente`):**
  | Campo | Tipo | Obrigatório | Comportamento |
  | :--- | :--- | :--- | :--- |
  | `id` | `number` | **Sim** (`@NotNull`) | ID do paciente a ser atualizado. |
  | `nome` | `string \| null` | Não | Se omitido/`null`, mantém atual. Se `""`, retorna `400` (`"Nome não pode estar em branco."`). |
  | `telefone` | `string \| null` | Não | Se omitido/`null`, mantém atual. Se `""`, retorna `400` (`"Telefone não pode estar em branco."`). |
  | `endereco` | `Partial<DadosEndereco> \| null` | Não | Atualização parcial campo a campo (ver seção 5.3). |

  ```json
  {
    "id": 1,
    "nome": "Carla Mendes Silva",
    "telefone": "16977776666"
  }
  ```
- **Respostas:**
  - **`200 OK` (`DadosDetalhamentoPaciente`):** Objeto completo atualizado do paciente.
  - **`400 Bad Request`:** Array de validação (se `id` ausente) ou `{"mensagem": "..."}` (se campo enviado em branco).
  - **`404 Not Found`:** Corpo vazio se o `id` não existir.
  - **`401 Unauthorized`:** Sem token ou token inválido.

---

#### 3.5. `DELETE /pacientes/{id}` — Excluir Paciente (Exclusão Lógica)
Marca o paciente como inativo (`ativo = false`). Ele deixa de aparecer em `GET /pacientes` e não pode agendar novas consultas.

- **Path Param:** `id` (`number` / `Long`)
- **Respostas:**
  - **`204 No Content`:** Corpo vazio.
  - **`404 Not Found`:** Corpo vazio se o `id` não existir.
  - **`401 Unauthorized`:** Sem token ou token inválido.

---

### Módulo 4: Consultas (`/consultas`)

> **Autenticação:** Todos os endpoints exigem header `Authorization: Bearer <token>`.

#### 4.1. `POST /consultas` — Agendar Consulta
Agenda uma consulta médica com duração presumida de **1 hora**, aplicando todas as validações de negócio da clínica.

- **Request Body (`DadosAgendamentoConsulta`):**
  | Campo Principal | Aliases Aceitos (`@JsonAlias`) | Tipo | Obrigatório | Validação e Comportamento |
  | :--- | :--- | :--- | :--- | :--- |
  | `idMedico` | `"id_medico"`, `"medicoId"` | `number \| null` | **Não** (Opcional) | Se informado, agenda com este médico. Se omitido/`null`, o sistema escolhe **aleatoriamente** um médico ativo e disponível na data/horário. |
  | `idPaciente` | `"id_paciente"`, `"pacienteId"` | `number` | **Sim** (`@NotNull`) | ID do paciente ativo. |
  | `data` | — | `string` (ISO LocalDateTime) | **Sim** (`@NotNull`, `@Future`) | Formato `"YYYY-MM-DDTHH:mm:ss"` ou `"YYYY-MM-DDTHH:mm"` (ex.: `"2026-12-10T14:00:00"`). **Não envie `Z` nem timezone offset** no final! |
  | `especialidade` | — | `Especialidade \| null` | **Não** (Opcional) | Usado quando `idMedico` é `null` para filtrar médicos disponíveis daquela especialidade. Se `idMedico` e `especialidade` forem ambos `null`, sorteia qualquer médico ativo disponível no horário. (Se `idMedico` for informado, `especialidade` é ignorada). |

  **Exemplo 1 (escolhendo médico específico):**
  ```json
  {
    "idMedico": 1,
    "idPaciente": 1,
    "data": "2026-12-10T10:00:00"
  }
  ```

  **Exemplo 2 (escolha automática de médico por especialidade):**
  ```json
  {
    "idPaciente": 1,
    "especialidade": "CARDIOLOGIA",
    "data": "2026-12-10T10:00:00"
  }
  ```

- **Regras de Negócio Validadas no Agendamento (todas retornam `400 Bad Request` com `{"mensagem": "..."}`):**
  1. **Existência do paciente:** `"Id do paciente informado não existe!"`
  2. **Existência do médico (se informado):** `"Id do médico informado não existe!"`
  3. **Horário de funcionamento da clínica:**
     - Funcionamento: **Segunda a Sábado**, das **07:00 às 19:00** (como cada consulta dura 1h, o **horário de início permitido é de `07:00` até `18:00` inclusive**; `18:01` ou `18:30` são recusados; **Domingos** são recusados).
     - Mensagem: `"Consulta fora do horário de funcionamento da clínica!"`
  4. **Antecedência mínima de 30 minutos:**
     - `Duration.between(agora, data).toMinutes() < 30`
     - Mensagem: `"Consulta deve ser agendada com antecedência mínima de 30 minutos!"`
  5. **Paciente ativo:**
     - Mensagem: `"Consulta não pode ser agendada com paciente inativo!"`
  6. **Médico ativo (se `idMedico` informado):**
     - Mensagem: `"Consulta não pode ser agendada com médico inativo!"`
  7. **Limite de 1 consulta por dia para o mesmo paciente:**
     - Verifica se já existe consulta **não cancelada** do paciente entre `00:00:00` e `23:59:59` do mesmo dia.
     - Mensagem: `"Paciente já possui uma consulta agendada nesse dia!"`
  8. **Conflito de horário do médico (janela de 1 hora):**
     - Um médico não pode ter outra consulta **não cancelada** com horário estritamente entre `(data - 1 hora)` e `(data + 1 hora)`. Ex.: se tem consulta às `10:00`, horários como `09:30`, `10:00` e `10:30` são bloqueados, mas `09:00` e `11:00` são permitidos!
     - Mensagem: `"Médico já possui outra consulta agendada nesse mesmo horário!"`
  9. **Disponibilidade na escolha automática (`idMedico == null`):**
     - Se nenhum médico ativo (da especialidade solicitada, quando informada) estiver livre na janela `(data - 1h, data + 1h)`:
     - Mensagem: `"Não existe médico disponível nessa data!"`

- **Respostas:**
  - **`201 Created` (`DadosDetalhamentoConsulta`):**
    - **Header:** `Location: http://localhost:8080/consultas/{id}`
    - **Body:**
      ```json
      {
        "id": 1,
        "idMedico": 1,
        "idPaciente": 1,
        "data": "2026-12-10T10:00:00",
        "motivoCancelamento": null
      }
      ```
  - **`400 Bad Request`:**
    - Array `[{ "campo": "data", "mensagem": "..." }]` se falhar em `@NotNull` ou `@Future`.
    - Objeto `{ "mensagem": "..." }` se violar qualquer uma das 9 regras de negócio acima.
  - **`401 Unauthorized`:** Sem token ou token inválido.

---

#### 4.2. `GET /consultas` — Listar Consultas (Paginado)
Lista todas as consultas (ativas e canceladas). Consultas canceladas são identificáveis pelo campo `motivoCancelamento` diferente de `null`.

- **Query Params:** `?page=0&size=10&sort=data,asc` (padrão `size=10`, ordenado por `data` ascendente).
- **Respostas:**
  - **`200 OK` (`Page<DadosDetalhamentoConsulta>`):**
    ```json
    {
      "content": [
        {
          "id": 1,
          "idMedico": 1,
          "idPaciente": 1,
          "data": "2026-12-10T10:00:00",
          "motivoCancelamento": null
        },
        {
          "id": 2,
          "idMedico": 1,
          "idPaciente": 2,
          "data": "2026-12-11T10:00:00",
          "motivoCancelamento": "PACIENTE_DESISTIU"
        }
      ],
      "pageable": { "pageNumber": 0, "pageSize": 10, "offset": 0, "paged": true, "unpaged": false, "sort": { "empty": false, "sorted": true, "unsorted": false } },
      "last": true,
      "totalPages": 1,
      "totalElements": 2,
      "first": true,
      "size": 10,
      "number": 0,
      "sort": { "empty": false, "sorted": true, "unsorted": false },
      "numberOfElements": 2,
      "empty": false
    }
    ```
  - **`401 Unauthorized`:** Sem token ou token inválido.

---

#### 4.2.1. `GET /consultas/{id}` — Detalhar Consulta por ID
Retorna os detalhes de uma consulta específica.

- **Path Param:** `id` (`number` / `Long`)
- **Respostas:**
  - **`200 OK` (`DadosDetalhamentoConsulta`)**
  - **`404 Not Found`:** Corpo vazio se o `id` não existir.
  - **`401 Unauthorized`:** Sem token ou token inválido.

---

#### 4.3. `DELETE /consultas` — Cancelar Consulta
Cancela uma consulta registrando o motivo do cancelamento.

- **Request Body (`DadosCancelamentoConsulta`):**
  | Campo Principal | Aliases Aceitos (`@JsonAlias`) | Tipo | Obrigatório | Descrição |
  | :--- | :--- | :--- | :--- | :--- |
  | `idConsulta` | `"id"`, `"id_consulta"`, `"consultaId"` | `number` | **Sim** (`@NotNull`) | ID da consulta a ser cancelada. |
  | `motivo` | `"motivo_cancelamento"`, `"motivoCancelamento"` | `MotivoCancelamento` | **Sim** (`@NotNull`) | `"PACIENTE_DESISTIU"`, `"MEDICO_CANCELOU"` ou `"OUTROS"`. |

  ```json
  {
    "idConsulta": 1,
    "motivo": "PACIENTE_DESISTIU"
  }
  ```

- **Regras de Negócio Validadas no Cancelamento (retornam `400 Bad Request` com `{"mensagem": "..."}`):**
  1. **Existência da consulta:** `"Id da consulta informado não existe!"`
  2. **Consulta já cancelada:** `"Consulta já foi cancelada!"`
  3. **Antecedência mínima de 24 horas:**
     - `Duration.between(agora, consulta.getData()).toHours() < 24`
     - Mensagem: `"Consulta somente pode ser cancelada com antecedência mínima de 24 horas!"`

- **Respostas:**
  - **`204 No Content`:** Corpo vazio. A consulta passa a ter `motivoCancelamento` preenchido e deixa de aparecer em `GET /consultas` (liberando também a agenda do médico e do paciente naquele horário/dia).
  - **`400 Bad Request`:** Array de validação (se faltar `idConsulta` ou `motivo`) ou objeto `{"mensagem": "..."}` (regras acima ou Enum inválido).
  - **`401 Unauthorized`:** Sem token ou token inválido.

---

## 7. Resumo Rápido de Rotas

| Método | Rota | Autenticação | Request Body | Status Sucesso | Response Body |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/login` | Pública | `DadosAutenticacao` | `200 OK` | `DadosTokenJWT` |
| `POST` | `/medicos` | Bearer JWT | `DadosCadastroMedico` | `201 Created` | `DadosDetalhamentoMedico` |
| `GET` | `/medicos` | Bearer JWT | — (Query: `page, size, sort`) | `200 OK` | `Page<DadosListagemMedico>` |
| `GET` | `/medicos/{id}` | Bearer JWT | — | `200 OK` | `DadosDetalhamentoMedico` |
| `PUT` | `/medicos` | Bearer JWT | `DadosAtualizacaoMedico` | `200 OK` | `DadosDetalhamentoMedico` |
| `DELETE` | `/medicos/{id}` | Bearer JWT | — | `204 No Content` | Vazio |
| `POST` | `/pacientes` | Bearer JWT | `DadosCadastroPaciente` | `201 Created` | `DadosDetalhamentoPaciente` |
| `GET` | `/pacientes` | Bearer JWT | — (Query: `page, size, sort`) | `200 OK` | `Page<DadosListagemPaciente>` |
| `GET` | `/pacientes/{id}` | Bearer JWT | — | `200 OK` | `DadosDetalhamentoPaciente` |
| `PUT` | `/pacientes` | Bearer JWT | `DadosAtualizacaoPaciente` | `200 OK` | `DadosDetalhamentoPaciente` |
| `DELETE` | `/pacientes/{id}` | Bearer JWT | — | `204 No Content` | Vazio |
| `POST` | `/consultas` | Bearer JWT | `DadosAgendamentoConsulta` | `201 Created` | `DadosDetalhamentoConsulta` |
| `GET` | `/consultas` | Bearer JWT | — (Query: `page, size, sort`) | `200 OK` | `Page<DadosDetalhamentoConsulta>` |
| `GET` | `/consultas/{id}` | Bearer JWT | — | `200 OK` | `DadosDetalhamentoConsulta` |
| `DELETE` | `/consultas` | Bearer JWT | `DadosCancelamentoConsulta` | `204 No Content` | Vazio |

---

## 8. Contratos TypeScript Prontos para o Front-end

Copie diretamente para o projeto Front-end (`types/api.ts`):

```typescript
// ===================== ENUMS =====================

export type Especialidade =
  | 'CARDIOLOGIA'
  | 'DERMATOLOGIA'
  | 'GINECOLOGIA'
  | 'ORTOPEDIA';

export type MotivoCancelamento =
  | 'PACIENTE_DESISTIU'
  | 'MEDICO_CANCELOU'
  | 'OUTROS';

// ===================== PAGINAÇÃO =====================

export interface SpringSort {
  empty: boolean;
  sorted: boolean;
  unsorted: boolean;
}

export interface SpringPageable {
  pageNumber: number;
  pageSize: number;
  sort: SpringSort;
  offset: number;
  paged: boolean;
  unpaged: boolean;
}

export interface Page<T> {
  content: T[];
  pageable: SpringPageable;
  last: boolean;
  totalPages: number;
  totalElements: number;
  first: boolean;
  size: number;
  number: number;
  sort: SpringSort;
  numberOfElements: number;
  empty: boolean;
}

export interface PageParams {
  page?: number; // 0-indexed
  size?: number; // default: 10
  sort?: string; // ex: "nome,asc" ou "data,desc"
}

// ===================== ERROS =====================

/** Retornado em 400 quando @Valid falha (Array de ErroValidacaoCampo) */
export interface ErroValidacaoCampo {
  campo: string;
  mensagem: string;
}

/** Retornado em 400 (regra de negócio / JSON inválido) e 401 (login ou token inválido) */
export interface ErroMensagemSimples {
  mensagem: string;
}

export type ApiErrorResponse = ErroValidacaoCampo[] | ErroMensagemSimples | '';

// ===================== AUTENTICAÇÃO =====================

export interface DadosAutenticacao {
  login: string;
  senha: string;
}

export interface DadosTokenJWT {
  token: string;
}

// ===================== ENDEREÇO =====================

export interface Endereco {
  logradouro: string;
  bairro: string;
  cep: string; // 8 dígitos numéricos no cadastro: /^\d{8}$/
  cidade: string;
  uf: string; // 2 caracteres: ex. "SP"
  numero?: string | null;
  complemento?: string | null;
}

export interface DadosAtualizacaoEndereco {
  logradouro?: string;
  bairro?: string;
  cep?: string;
  cidade?: string;
  uf?: string;
  numero?: string | null;
  complemento?: string | null;
}

// ===================== MÉDICOS =====================

export interface DadosCadastroMedico {
  nome: string;
  email: string;
  telefone: string;
  crm: string; // 4 a 6 dígitos numéricos: /^\d{4,6}$/
  especialidade: Especialidade;
  endereco: Endereco;
}

export interface DadosAtualizacaoMedico {
  id: number;
  nome?: string;
  telefone?: string;
  endereco?: DadosAtualizacaoEndereco;
}

export interface DadosListagemMedico {
  id: number;
  nome: string;
  email: string;
  crm: string;
  especialidade: Especialidade;
}

export interface DadosDetalhamentoMedico {
  id: number;
  nome: string;
  email: string;
  telefone: string;
  crm: string;
  especialidade: Especialidade;
  endereco: Endereco;
}

// ===================== PACIENTES =====================

export interface DadosCadastroPaciente {
  nome: string;
  email: string;
  telefone: string;
  cpf: string; // /^\d{3}\.?\d{3}\.?\d{3}\-?\d{2}$/
  endereco: Endereco;
}

export interface DadosAtualizacaoPaciente {
  id: number;
  nome?: string;
  telefone?: string;
  endereco?: DadosAtualizacaoEndereco;
}

export interface DadosListagemPaciente {
  id: number;
  nome: string;
  email: string;
  cpf: string;
}

export interface DadosDetalhamentoPaciente {
  id: number;
  nome: string;
  email: string;
  telefone: string;
  cpf: string;
  endereco: Endereco;
}

// ===================== CONSULTAS =====================

export interface DadosAgendamentoConsulta {
  idMedico?: number | null; // Também aceita "id_medico" ou "medicoId"
  idPaciente: number;       // Também aceita "id_paciente" ou "pacienteId"
  data: string;             // ISO LocalDateTime sem timezone: "YYYY-MM-DDTHH:mm:ss"
  especialidade?: Especialidade | null;
}

export interface DadosCancelamentoConsulta {
  idConsulta: number;         // Também aceita "id", "id_consulta" ou "consultaId"
  motivo: MotivoCancelamento; // Também aceita "motivo_cancelamento" ou "motivoCancelamento"
}

export interface DadosDetalhamentoConsulta {
  id: number;
  idMedico: number;
  idPaciente: number;
  data: string; // Ex: "2026-12-10T10:00:00"
  motivoCancelamento: MotivoCancelamento | null;
}
```
