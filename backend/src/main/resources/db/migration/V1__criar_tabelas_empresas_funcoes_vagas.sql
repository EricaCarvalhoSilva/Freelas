CREATE TABLE empresas (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome_fantasia VARCHAR(120) NOT NULL,
    razao_social  VARCHAR(160) NOT NULL,
    cnpj          CHAR(14)     NOT NULL,
    email         VARCHAR(160) NOT NULL,
    telefone      VARCHAR(20),
    cidade        VARCHAR(100) NOT NULL,
    estado        CHAR(2)      NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_empresas_cnpj  UNIQUE (cnpj),
    CONSTRAINT uq_empresas_email UNIQUE (email),
    CONSTRAINT ck_empresas_cnpj  CHECK (cnpj ~ '^[0-9]{14}$')
);

CREATE TABLE funcoes (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome      VARCHAR(80) NOT NULL,
    descricao VARCHAR(255),
    CONSTRAINT uq_funcoes_nome UNIQUE (nome)
);

CREATE TABLE vagas (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    empresa_id               BIGINT        NOT NULL,
    funcao_id                BIGINT        NOT NULL,
    titulo                   VARCHAR(120)  NOT NULL,
    descricao                TEXT,
    quantidade_profissionais INTEGER       NOT NULL,
    data_servico             DATE          NOT NULL,
    horario_inicio           TIME          NOT NULL,
    horario_fim              TIME          NOT NULL,
    valor                    NUMERIC(10,2) NOT NULL,
    alimentacao_inclusa      BOOLEAN       NOT NULL DEFAULT FALSE,
    status                   VARCHAR(20)   NOT NULL DEFAULT 'RASCUNHO',
    criado_em                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_vagas_empresa    FOREIGN KEY (empresa_id) REFERENCES empresas (id),
    CONSTRAINT fk_vagas_funcao     FOREIGN KEY (funcao_id)  REFERENCES funcoes (id),
    CONSTRAINT ck_vagas_quantidade CHECK (quantidade_profissionais > 0),
    CONSTRAINT ck_vagas_valor      CHECK (valor > 0),
    CONSTRAINT ck_vagas_status     CHECK (status IN ('RASCUNHO', 'PUBLICADA', 'ENCERRADA'))
);

CREATE INDEX idx_vagas_empresa_id ON vagas (empresa_id);
CREATE INDEX idx_vagas_funcao_id  ON vagas (funcao_id);
CREATE INDEX idx_vagas_status     ON vagas (status);