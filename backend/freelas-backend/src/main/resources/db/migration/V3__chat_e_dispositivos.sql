-- =====================================================================
-- V3 - Idempotência do chat e dispositivos para push
-- (documento "Escolher o banco de dados e definir a persistência")
-- =====================================================================

-- O app gera um id por mensagem; se reenviar depois de uma falha, não duplica (RNF29)
ALTER TABLE mensagem ADD COLUMN id_cliente VARCHAR(64);
CREATE UNIQUE INDEX uq_mensagem_id_cliente ON mensagem (conversa_id, id_cliente)
    WHERE id_cliente IS NOT NULL;

-- Aparelhos que recebem notificação push (proposta da Helen, 31ª tabela)
CREATE TABLE dispositivo_push (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT       NOT NULL REFERENCES usuario(id),
    token_push     VARCHAR(255) NOT NULL UNIQUE,
    plataforma     VARCHAR(10)  NOT NULL CHECK (plataforma IN ('ANDROID','IOS','WEB')),
    registrado_em  TIMESTAMP    NOT NULL DEFAULT now(),
    ativo          BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_dispositivo_usuario ON dispositivo_push (usuario_id) WHERE ativo;
