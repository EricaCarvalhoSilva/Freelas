-- =====================================================================
-- Freelas (MeuFreela) — Modelo físico do banco de dados
-- SGBD de referência: PostgreSQL 16
-- Base: requisitos, casos de uso e regras de negócio da pasta
-- "Freelas git/Freelas/documentação".
-- Os comentários "RN"/"RF"/"RNF" apontam a regra que motivou a decisão.
-- =====================================================================

-- ---------------------------------------------------------------------
-- MÓDULO 1 — USUÁRIOS E PERFIS
-- ---------------------------------------------------------------------

-- Endereço (a distância do raio de substituição é calculada a partir do CEP)
CREATE TABLE endereco (
    id            BIGSERIAL PRIMARY KEY,
    cep           CHAR(8)      NOT NULL,
    logradouro    VARCHAR(150) NOT NULL,
    numero        VARCHAR(10)  NOT NULL,
    complemento   VARCHAR(60),
    bairro        VARCHAR(80)  NOT NULL,
    cidade        VARCHAR(80)  NOT NULL,
    uf            CHAR(2)      NOT NULL
);

-- Superclasse: dados de login comuns a Freelancer e Empresa (RNF01, RNF02)
CREATE TABLE usuario (
    id               BIGSERIAL PRIMARY KEY,
    email            VARCHAR(120) NOT NULL UNIQUE,              -- RN01: e-mail único
    senha_hash       VARCHAR(255) NOT NULL,                     -- RNF02: só o hash
    telefone         VARCHAR(20)  NOT NULL,
    tipo             VARCHAR(10)  NOT NULL CHECK (tipo IN ('FREELANCER','EMPRESA')),
    status           VARCHAR(12)  NOT NULL DEFAULT 'PENDENTE'
                     CHECK (status IN ('PENDENTE','ATIVO','SUSPENSO','BLOQUEADO','EXCLUIDO')),
    email_confirmado BOOLEAN      NOT NULL DEFAULT FALSE,       -- RN03
    criado_em        TIMESTAMP    NOT NULL DEFAULT now()
);

-- Subclasse Freelancer (1:1 com usuario, mesma chave)
CREATE TABLE freelancer (
    usuario_id        BIGINT PRIMARY KEY REFERENCES usuario(id),
    nome              VARCHAR(120) NOT NULL,
    cpf               CHAR(11)     NOT NULL UNIQUE,
    data_nascimento   DATE         NOT NULL,
    foto_url          VARCHAR(255),
    apresentacao      TEXT,                                     -- RF5
    curriculo_url     VARCHAR(255),                             -- RF8
    endereco_id       BIGINT REFERENCES endereco(id),           -- localização base
    score             NUMERIC(3,2) NOT NULL DEFAULT 0 CHECK (score BETWEEN 0 AND 5), -- derivado
    nivel_reputacao   VARCHAR(10)  CHECK (nivel_reputacao IN ('EXCELENTE','BOM','REGULAR','EM_RISCO')),
    verificado        BOOLEAN      NOT NULL DEFAULT FALSE,      -- Badge Verificado
    suspenso_ate      TIMESTAMP                                 -- no-show: 7 dias sem candidatar
);

-- Subclasse Empresa / estabelecimento (KAN-63)
CREATE TABLE empresa (
    usuario_id        BIGINT PRIMARY KEY REFERENCES usuario(id),
    razao_social      VARCHAR(150) NOT NULL,
    nome_fantasia     VARCHAR(150) NOT NULL,
    cnpj              CHAR(14)     NOT NULL UNIQUE,              -- RN01, RN09
    categoria         VARCHAR(12)  NOT NULL
                      CHECK (categoria IN ('RESTAURANTE','BAR','MERCADO','EVENTO','OUTRO')),
    responsavel_nome  VARCHAR(120),
    endereco_id       BIGINT NOT NULL REFERENCES endereco(id),
    score             NUMERIC(3,2) NOT NULL DEFAULT 0 CHECK (score BETWEEN 0 AND 5)
);

-- Catálogo de funções (garçom, cozinheiro, segurança...) — KAN-69
CREATE TABLE funcao (
    id    SERIAL PRIMARY KEY,
    nome  VARCHAR(60) NOT NULL UNIQUE
);

-- N:N Freelancer x Função (funções que o freela sabe exercer)
CREATE TABLE freelancer_funcao (
    freelancer_id  BIGINT REFERENCES freelancer(usuario_id),
    funcao_id      INT    REFERENCES funcao(id),
    anos_experiencia SMALLINT,
    PRIMARY KEY (freelancer_id, funcao_id)
);

-- Formações e conhecimentos do freela (RF6)
CREATE TABLE formacao (
    id             BIGSERIAL PRIMARY KEY,
    freelancer_id  BIGINT NOT NULL REFERENCES freelancer(usuario_id),
    titulo         VARCHAR(120) NOT NULL,
    instituicao    VARCHAR(120),
    tipo           VARCHAR(20) CHECK (tipo IN ('CURSO','CERTIFICADO','GRADUACAO','TECNICO','OUTRO')),
    ano_conclusao  SMALLINT
);

-- ---------------------------------------------------------------------
-- MÓDULO 2 — VAGAS E CANDIDATURAS
-- ---------------------------------------------------------------------

CREATE TABLE vaga (
    id                      BIGSERIAL PRIMARY KEY,
    empresa_id              BIGINT NOT NULL REFERENCES empresa(usuario_id),
    funcao_id               INT    NOT NULL REFERENCES funcao(id),
    endereco_id             BIGINT NOT NULL REFERENCES endereco(id),   -- herdado ou outro local
    descricao               TEXT,
    quantidade_profissionais SMALLINT NOT NULL CHECK (quantidade_profissionais >= 1),
    inicio                  TIMESTAMP NOT NULL,
    fim                     TIMESTAMP NOT NULL,
    valor                   NUMERIC(10,2) NOT NULL CHECK (valor > 0),   -- RN04: mínimo validado na aplicação
    tipo_valor              VARCHAR(7) NOT NULL CHECK (tipo_valor IN ('HORA','TURNO','DIARIA')),
    oferece_alimentacao     BOOLEAN NOT NULL,
    tipo_alimentacao        VARCHAR(40),
    forma_pagamento         VARCHAR(10) NOT NULL CHECK (forma_pagamento IN ('PIX','CARTAO')), -- RF13
    relampago               BOOLEAN NOT NULL DEFAULT FALSE,            -- vaga de substituição
    status                  VARCHAR(15) NOT NULL DEFAULT 'RASCUNHO'
                            CHECK (status IN ('RASCUNHO','PUBLICADA','PREENCHIDA','CONCLUIDA',
                                              'CANCELADA','ENCERRADA_PRAZO')),
    publicada_em            TIMESTAMP,
    disponivel_ate          TIMESTAMP,                                 -- UC5 passo 5
    CHECK (fim > inicio)
);

-- Associativa Freelancer x Vaga (RF7, UC7). Um freela candidata-se uma vez por vaga.
CREATE TABLE candidatura (
    id             BIGSERIAL PRIMARY KEY,
    vaga_id        BIGINT NOT NULL REFERENCES vaga(id),
    freelancer_id  BIGINT NOT NULL REFERENCES freelancer(usuario_id),
    criada_em      TIMESTAMP NOT NULL DEFAULT now(),
    status         VARCHAR(12) NOT NULL DEFAULT 'ENVIADA'
                   CHECK (status IN ('ENVIADA','SELECIONADA','RECUSADA','DESISTENCIA','CONTRATADA')),
    UNIQUE (vaga_id, freelancer_id)                                     -- UC7 3a
);

-- N:N Freelancer x Vaga "preferida" (RF18)
CREATE TABLE vaga_favorita (
    freelancer_id  BIGINT REFERENCES freelancer(usuario_id),
    vaga_id        BIGINT REFERENCES vaga(id),
    criada_em      TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (freelancer_id, vaga_id)
);

-- ---------------------------------------------------------------------
-- MÓDULO 3 — CONTRATAÇÃO (O TURNO), SUBSTITUIÇÃO E OCORRÊNCIAS
-- ---------------------------------------------------------------------

-- Uma candidatura aceita vira no máximo uma contratação (1:0..1). KAN-70 RN08–RN17
CREATE TABLE contratacao (
    id                BIGSERIAL PRIMARY KEY,
    candidatura_id    BIGINT NOT NULL UNIQUE REFERENCES candidatura(id),
    valor_acordado    NUMERIC(10,2) NOT NULL,                     -- comprovante RN10
    status            VARCHAR(18) NOT NULL DEFAULT 'AGUARDANDO_ACEITE'
                      CHECK (status IN ('AGUARDANDO_ACEITE','CONFIRMADA','EM_ANDAMENTO',
                                        'CONCLUIDA','CANCELADA','NO_SHOW','SUBSTITUIDA')),
    confirmada_em     TIMESTAMP,
    checkin_em        TIMESTAMP,
    checkout_em       TIMESTAMP,
    encerrada_em      TIMESTAMP,                                  -- troca no meio do turno
    cancelada_por     BIGINT REFERENCES usuario(id),
    motivo_encerramento VARCHAR(255)
);

-- Pedido de substituição (UC13 Empresa, "Solicitação de substituição")
CREATE TABLE substituicao (
    id                      BIGSERIAL PRIMARY KEY,
    contratacao_original_id BIGINT NOT NULL REFERENCES contratacao(id),
    nova_contratacao_id     BIGINT UNIQUE REFERENCES contratacao(id), -- "primeiro que aceitar"
    motivo                  VARCHAR(15) NOT NULL
                            CHECK (motivo IN ('CANCELAMENTO','NO_SHOW','AUSENCIA_TURNO','AVISO_PREVIO')),
    raio_km                 NUMERIC(5,1) NOT NULL CHECK (raio_km > 0),  -- RF17
    solicitada_em           TIMESTAMP NOT NULL DEFAULT now(),
    status                  VARCHAR(20) NOT NULL DEFAULT 'AGUARDANDO_SUBSTITUTO'
                            CHECK (status IN ('AGUARDANDO_SUBSTITUTO','PREENCHIDA','SEM_CANDIDATOS','CANCELADA'))
);

-- Fatos negativos que pesam no score (no-show, cancelamento tardio, abandono)
CREATE TABLE ocorrencia (
    id              BIGSERIAL PRIMARY KEY,
    contratacao_id  BIGINT NOT NULL REFERENCES contratacao(id),
    usuario_id      BIGINT NOT NULL REFERENCES usuario(id),        -- quem causou
    tipo            VARCHAR(20) NOT NULL
                    CHECK (tipo IN ('NO_SHOW','CANCELAMENTO_TARDIO','CANCELAMENTO_PREVIO','ABANDONO_TURNO')),
    registrada_em   TIMESTAMP NOT NULL DEFAULT now(),
    impacto_score   NUMERIC(3,2) NOT NULL
);

-- ---------------------------------------------------------------------
-- MÓDULO 4 — AVALIAÇÃO E REPUTAÇÃO
-- ---------------------------------------------------------------------

-- Avaliação mútua: cada contratação concluída gera no máximo 2 (uma por direção)
CREATE TABLE avaliacao (
    id              BIGSERIAL PRIMARY KEY,
    contratacao_id  BIGINT NOT NULL REFERENCES contratacao(id),
    avaliador_id    BIGINT NOT NULL REFERENCES usuario(id),
    avaliado_id     BIGINT NOT NULL REFERENCES usuario(id),
    direcao         VARCHAR(17) NOT NULL
                    CHECK (direcao IN ('EMPRESA_P_FREELA','FREELA_P_EMPRESA')),
    nota            NUMERIC(2,1) NOT NULL CHECK (nota BETWEEN 0 AND 5),   -- RF22
    comentario      VARCHAR(500),                                         -- RF24, moderado
    enviada_em      TIMESTAMP NOT NULL DEFAULT now(),
    visivel         BOOLEAN NOT NULL DEFAULT FALSE,                       -- avaliação cega 72h
    status          VARCHAR(10) NOT NULL DEFAULT 'PUBLICADA'
                    CHECK (status IN ('PUBLICADA','CONTESTADA','REMOVIDA')),
    UNIQUE (contratacao_id, direcao),                                     -- só uma por lado
    CHECK (avaliador_id <> avaliado_id)
);

-- Notas por critério (pontualidade, alimentação, segurança...)
CREATE TABLE avaliacao_criterio (
    avaliacao_id  BIGINT REFERENCES avaliacao(id),
    criterio      VARCHAR(30) NOT NULL,
    nota          NUMERIC(2,1) NOT NULL CHECK (nota BETWEEN 0 AND 5),
    PRIMARY KEY (avaliacao_id, criterio)
);

-- "Contestar Avaliação" em até 5 dias úteis
CREATE TABLE contestacao (
    id             BIGSERIAL PRIMARY KEY,
    avaliacao_id   BIGINT NOT NULL UNIQUE REFERENCES avaliacao(id),
    solicitante_id BIGINT NOT NULL REFERENCES usuario(id),
    justificativa  TEXT NOT NULL,
    evidencia_url  VARCHAR(255),
    status         VARCHAR(10) NOT NULL DEFAULT 'ABERTA'
                   CHECK (status IN ('ABERTA','ACEITA','NEGADA')),
    aberta_em      TIMESTAMP NOT NULL DEFAULT now(),
    resolvida_em   TIMESTAMP
);

-- ---------------------------------------------------------------------
-- MÓDULO 5 — FINANCEIRO (custódia, carteira, Pix)
-- ---------------------------------------------------------------------

-- Cartão/conta da empresa tokenizado no gateway (RN06, RNF17: nunca o número do cartão)
CREATE TABLE meio_pagamento_empresa (
    id             BIGSERIAL PRIMARY KEY,
    empresa_id     BIGINT NOT NULL REFERENCES empresa(usuario_id),
    tipo           VARCHAR(10) NOT NULL CHECK (tipo IN ('CARTAO','PIX')),
    token_gateway  VARCHAR(120) NOT NULL,
    final_cartao   CHAR(4),
    ativo          BOOLEAN NOT NULL DEFAULT TRUE
);

-- Pagamento do turno em custódia (escrow) com split
CREATE TABLE pagamento (
    id                   BIGSERIAL PRIMARY KEY,
    contratacao_id       BIGINT NOT NULL UNIQUE REFERENCES contratacao(id),
    meio_pagamento_id    BIGINT NOT NULL REFERENCES meio_pagamento_empresa(id),
    valor_bruto          NUMERIC(10,2) NOT NULL,
    taxa_plataforma      NUMERIC(10,2) NOT NULL,
    valor_liquido        NUMERIC(10,2) NOT NULL,
    status               VARCHAR(22) NOT NULL DEFAULT 'PAGO_CUSTODIA'
                         CHECK (status IN ('PAGO_CUSTODIA','AGUARDANDO_CONFIRMACAO','LIBERADO',
                                           'EM_DISPUTA','ESTORNADO')),
    id_transacao_gateway VARCHAR(80) NOT NULL UNIQUE,              -- RNF16
    chave_idempotencia   VARCHAR(80) NOT NULL UNIQUE,              -- RNF16
    pago_em              TIMESTAMP NOT NULL DEFAULT now(),
    liberado_em          TIMESTAMP,
    CHECK (valor_liquido = valor_bruto - taxa_plataforma)
);

-- Carteira digital: uma por freelancer (1:1)
CREATE TABLE carteira (
    id               BIGSERIAL PRIMARY KEY,
    freelancer_id    BIGINT NOT NULL UNIQUE REFERENCES freelancer(usuario_id),
    saldo_disponivel NUMERIC(12,2) NOT NULL DEFAULT 0,
    saldo_pendente   NUMERIC(12,2) NOT NULL DEFAULT 0,
    saldo_bloqueado  NUMERIC(12,2) NOT NULL DEFAULT 0
);

-- Chave Pix ou conta bancária do freela (RF9, RF12: quarentena de 24h)
CREATE TABLE conta_recebimento (
    id                 BIGSERIAL PRIMARY KEY,
    freelancer_id      BIGINT NOT NULL REFERENCES freelancer(usuario_id),
    tipo               VARCHAR(14) NOT NULL CHECK (tipo IN ('PIX','CONTA_BANCARIA')),
    tipo_chave_pix     VARCHAR(10) CHECK (tipo_chave_pix IN ('CPF','EMAIL','TELEFONE','ALEATORIA')),
    chave_pix_cript    VARCHAR(255),                             -- criptografada (RNF21)
    banco              VARCHAR(60),
    agencia_cript      VARCHAR(255),
    conta_cript        VARCHAR(255),
    status             VARCHAR(20) NOT NULL DEFAULT 'EM_QUARENTENA'
                       CHECK (status IN ('EM_QUARENTENA','ATIVA','PENDENTE_ATUALIZACAO','INATIVA')),
    liberada_saque_em  TIMESTAMP NOT NULL,                       -- cadastro + 24h
    criada_em          TIMESTAMP NOT NULL DEFAULT now()
);

-- Extrato: créditos de turnos, saques Pix, bloqueios e estornos
CREATE TABLE movimentacao_carteira (
    id                    BIGSERIAL PRIMARY KEY,
    carteira_id           BIGINT NOT NULL REFERENCES carteira(id),
    pagamento_id          BIGINT REFERENCES pagamento(id),        -- quando vem de um turno
    conta_recebimento_id  BIGINT REFERENCES conta_recebimento(id),-- quando é saque
    tipo                  VARCHAR(10) NOT NULL CHECK (tipo IN ('CREDITO','SAQUE','BLOQUEIO','ESTORNO')),
    valor                 NUMERIC(12,2) NOT NULL CHECK (valor > 0),
    status                VARCHAR(10) NOT NULL DEFAULT 'CONCLUIDA'
                          CHECK (status IN ('PENDENTE','CONCLUIDA','FALHOU')),
    criada_em             TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- MÓDULO 6 — MONETIZAÇÃO (Premium, Boost, Destaque de vaga)
-- ---------------------------------------------------------------------

CREATE TABLE plano (
    id            SERIAL PRIMARY KEY,
    nome          VARCHAR(40) NOT NULL,
    publico       VARCHAR(10) NOT NULL CHECK (publico IN ('FREELANCER','EMPRESA')),
    preco         NUMERIC(8,2) NOT NULL,                          -- ex.: 19,90
    duracao_dias  SMALLINT NOT NULL DEFAULT 30
);

CREATE TABLE assinatura (
    id                   BIGSERIAL PRIMARY KEY,
    usuario_id           BIGINT NOT NULL REFERENCES usuario(id),
    plano_id             INT    NOT NULL REFERENCES plano(id),
    inicio               TIMESTAMP NOT NULL,
    fim                  TIMESTAMP NOT NULL,
    renovacao_automatica BOOLEAN NOT NULL DEFAULT TRUE,
    status               VARCHAR(10) NOT NULL CHECK (status IN ('ATIVA','CANCELADA','EXPIRADA')),
    id_transacao_gateway VARCHAR(80) NOT NULL UNIQUE
);

-- Boost de perfil (freela, R$ 4,90/24h) OU destaque de vaga (empresa, R$ 9,90/24h ou R$ 24,90/7d)
CREATE TABLE impulsionamento (
    id                   BIGSERIAL PRIMARY KEY,
    tipo                 VARCHAR(14) NOT NULL CHECK (tipo IN ('BOOST_PERFIL','DESTAQUE_VAGA')),
    freelancer_id        BIGINT REFERENCES freelancer(usuario_id),
    vaga_id              BIGINT REFERENCES vaga(id),
    inicio               TIMESTAMP NOT NULL,
    fim                  TIMESTAMP NOT NULL,
    valor                NUMERIC(8,2) NOT NULL,
    id_transacao_gateway VARCHAR(80) NOT NULL UNIQUE,
    -- exatamente um dos dois alvos, conforme o tipo
    CHECK ((tipo = 'BOOST_PERFIL'  AND freelancer_id IS NOT NULL AND vaga_id IS NULL) OR
           (tipo = 'DESTAQUE_VAGA' AND vaga_id IS NOT NULL AND freelancer_id IS NULL))
);

-- ---------------------------------------------------------------------
-- MÓDULO 7 — COMUNICAÇÃO E AUDITORIA
-- ---------------------------------------------------------------------

-- Chat existe só a partir de uma candidatura (UC10: "deve existir uma interação")
CREATE TABLE conversa (
    id              BIGSERIAL PRIMARY KEY,
    candidatura_id  BIGINT NOT NULL UNIQUE REFERENCES candidatura(id),
    criada_em       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE mensagem (
    id            BIGSERIAL PRIMARY KEY,
    conversa_id   BIGINT NOT NULL REFERENCES conversa(id),
    remetente_id  BIGINT NOT NULL REFERENCES usuario(id),
    conteudo      TEXT NOT NULL,
    enviada_em    TIMESTAMP NOT NULL DEFAULT now(),
    lida_em       TIMESTAMP
);

CREATE TABLE notificacao (
    id           BIGSERIAL PRIMARY KEY,
    usuario_id   BIGINT NOT NULL REFERENCES usuario(id),
    tipo         VARCHAR(30) NOT NULL,           -- NOVA_VAGA, CANDIDATURA, SUBSTITUICAO, PAGAMENTO...
    mensagem     VARCHAR(255) NOT NULL,
    referencia_id BIGINT,                         -- id da vaga/pagamento relacionado
    criada_em    TIMESTAMP NOT NULL DEFAULT now(),
    lida         BOOLEAN NOT NULL DEFAULT FALSE
);

-- Trilha de auditoria (RN10, RNF04, RNF12): alterações cadastrais, de vagas e transições de status
CREATE TABLE log_auditoria (
    id               BIGSERIAL PRIMARY KEY,
    usuario_id       BIGINT REFERENCES usuario(id),
    entidade         VARCHAR(30) NOT NULL,        -- 'vaga', 'empresa', 'conta_recebimento'...
    entidade_id      BIGINT NOT NULL,
    acao             VARCHAR(30) NOT NULL,        -- 'ALTERACAO', 'MUDANCA_STATUS', 'LOGIN'...
    dados_anteriores JSONB,
    dados_novos      JSONB,
    ip_origem        VARCHAR(45),
    registrado_em    TIMESTAMP NOT NULL DEFAULT now()
);

-- Índices para as buscas mais frequentes (RNF08: vagas e candidatos em até 3 s)
CREATE INDEX idx_vaga_status_inicio   ON vaga (status, inicio);
CREATE INDEX idx_vaga_funcao          ON vaga (funcao_id);
CREATE INDEX idx_candidatura_freela   ON candidatura (freelancer_id);
CREATE INDEX idx_avaliacao_avaliado   ON avaliacao (avaliado_id);
CREATE INDEX idx_mov_carteira         ON movimentacao_carteira (carteira_id, criada_em);
CREATE INDEX idx_endereco_cep         ON endereco (cep);
CREATE INDEX idx_log_entidade         ON log_auditoria (entidade, entidade_id);
