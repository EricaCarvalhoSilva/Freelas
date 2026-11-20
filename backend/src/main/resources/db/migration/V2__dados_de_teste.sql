INSERT INTO empresas (nome_fantasia, razao_social, cnpj, email, telefone, cidade, estado)
VALUES ('Bar do Zé', 'José da Silva Alimentos LTDA', '12345678000195',
        'contato@bardoze.com.br', '44999990000', 'Maringá', 'PR');

INSERT INTO funcoes (nome, descricao) VALUES
    ('Garçom',              'Atendimento de mesas e serviço de salão'),
    ('Cozinheiro',          'Preparo de pratos e controle da cozinha'),
    ('Auxiliar de cozinha', 'Apoio no preparo e na higienização'),
    ('Bartender',           'Preparo de bebidas e drinks'),
    ('Atendente de caixa',  'Operação de caixa e atendimento ao cliente');

INSERT INTO vagas (empresa_id, funcao_id, titulo, descricao, quantidade_profissionais,
                   data_servico, horario_inicio, horario_fim, valor, alimentacao_inclusa, status)
VALUES (
    (SELECT id FROM empresas WHERE cnpj = '12345678000195'),
    (SELECT id FROM funcoes  WHERE nome = 'Garçom'),
    'Garçom para sábado à noite',
    'Evento com casa cheia, experiência prévia desejável.',
    3,
    CURRENT_DATE + 7,
    '18:00', '23:00',
    120.00,
    TRUE,
    'PUBLICADA'
);