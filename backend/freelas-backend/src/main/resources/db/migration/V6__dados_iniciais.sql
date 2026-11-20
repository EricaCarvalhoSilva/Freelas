-- =====================================================================
-- V6 - Dados iniciais: catálogo de funções (KAN-69) e planos Premium.
-- Premium do freelancer: R$ 19,90 / 30 dias (Regras de Premium e Boost).
-- Premium da empresa: preço ainda não definido pelo grupo; 49,90 é proposta.
-- =====================================================================
INSERT INTO funcao (nome) VALUES
    ('Garçom'), ('Cozinheiro'), ('Auxiliar de cozinha'), ('Bartender'), ('Barista'),
    ('Churrasqueiro'), ('Pizzaiolo'), ('Copeiro'), ('Recepcionista'), ('Caixa'),
    ('Repositor'), ('Empacotador'), ('Auxiliar de limpeza'), ('Segurança'),
    ('Manobrista'), ('Promotor de eventos'), ('Montador de eventos'), ('Entregador');

INSERT INTO plano (nome, publico, preco, duracao_dias) VALUES
    ('Premium Freelancer', 'FREELANCER', 19.90, 30),
    ('Premium Empresa',    'EMPRESA',    49.90, 30);
