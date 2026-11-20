-- =====================================================================
-- V2 - Fila de tarefas e tabelas de autenticação
-- Previstas no System Design V1.0 e no documento de persistência (Helen):
-- fila_tarefa e token_renovacao. token_verificacao guarda os códigos de
-- confirmação de e-mail, troca de e-mail e recuperação de senha (15 min, RNF06).
-- =====================================================================

-- Fila de tarefas no próprio PostgreSQL (o worker lê com FOR UPDATE SKIP LOCKED)
CREATE TABLE fila_tarefa (
    id              BIGSERIAL PRIMARY KEY,
    tipo            VARCHAR(40)  NOT NULL,
    payload         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(12)  NOT NULL DEFAULT 'PENDENTE'
                    CHECK (status IN ('PENDENTE','PROCESSANDO','CONCLUIDA','FALHOU')),
    tentativas      SMALLINT     NOT NULL DEFAULT 0,
    max_tentativas  SMALLINT     NOT NULL DEFAULT 5,
    executar_em     TIMESTAMP    NOT NULL DEFAULT now(),
    criada_em       TIMESTAMP    NOT NULL DEFAULT now(),
    atualizada_em   TIMESTAMP    NOT NULL DEFAULT now(),
    ultimo_erro     VARCHAR(500)
);
-- Índice parcial: o worker só procura tarefas pendentes que já podem rodar
CREATE INDEX idx_fila_pendente ON fila_tarefa (executar_em) WHERE status = 'PENDENTE';

-- Token de renovação: só o hash fica no banco; o logout revoga (RNF05)
CREATE TABLE token_renovacao (
    id            BIGSERIAL PRIMARY KEY,
    usuario_id    BIGINT       NOT NULL REFERENCES usuario(id),
    token_hash    CHAR(64)     NOT NULL UNIQUE,     -- SHA-256 em hexadecimal
    criado_em     TIMESTAMP    NOT NULL DEFAULT now(),
    expira_em     TIMESTAMP    NOT NULL,            -- 30 min sem uso
    expira_max_em TIMESTAMP    NOT NULL,            -- limite de 7 dias
    revogado_em   TIMESTAMP
);
CREATE INDEX idx_token_renovacao_usuario ON token_renovacao (usuario_id);

-- Códigos enviados por e-mail (confirmação, troca de e-mail, recuperação de senha)
CREATE TABLE token_verificacao (
    id           BIGSERIAL PRIMARY KEY,
    usuario_id   BIGINT       NOT NULL REFERENCES usuario(id),
    finalidade   VARCHAR(20)  NOT NULL
                 CHECK (finalidade IN ('CONFIRMACAO_EMAIL','TROCA_EMAIL','RECUPERACAO_SENHA')),
    codigo_hash  CHAR(64)     NOT NULL,
    valor_novo   VARCHAR(120),                      -- novo e-mail, na troca de e-mail
    criado_em    TIMESTAMP    NOT NULL DEFAULT now(),
    expira_em    TIMESTAMP    NOT NULL,
    usado_em     TIMESTAMP
);
CREATE INDEX idx_token_verificacao_usuario ON token_verificacao (usuario_id, finalidade);
