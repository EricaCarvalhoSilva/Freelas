-- =====================================================================
-- V5 - Ajustes operacionais
-- =====================================================================

-- Perfil Administrador (RF21: mediação de disputas e contestações)
ALTER TABLE usuario DROP CONSTRAINT usuario_tipo_check;
ALTER TABLE usuario ADD CONSTRAINT usuario_tipo_check
    CHECK (tipo IN ('FREELANCER','EMPRESA','ADMINISTRADOR'));
ALTER TABLE usuario ALTER COLUMN tipo TYPE VARCHAR(13);

CREATE TABLE administrador (
    usuario_id  BIGINT PRIMARY KEY REFERENCES usuario(id),
    nome        VARCHAR(120) NOT NULL
);

-- Saque Pix com chave de idempotência (não sacar duas vezes se o app repetir o pedido)
ALTER TABLE movimentacao_carteira ADD COLUMN chave_idempotencia VARCHAR(80) UNIQUE;

-- Motivo da disputa aberta pela empresa (estado EM_DISPUTA do pagamento)
ALTER TABLE pagamento ADD COLUMN motivo_disputa VARCHAR(500);

-- Correção do script base: 'AGUARDANDO_SUBSTITUTO' tem 21 letras e a coluna tinha VARCHAR(20)
ALTER TABLE substituicao ALTER COLUMN status TYPE VARCHAR(25);
