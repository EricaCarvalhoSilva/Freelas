-- =====================================================================
-- V4 - Índices de desempenho
-- Sugeridos na análise de pressão de leitura/escrita (2026-10-26).
-- =====================================================================

-- Lista de notificações não lidas do usuário (rajada de 1.200 avisos por vaga)
CREATE INDEX idx_notificacao_usuario_lida ON notificacao (usuario_id, lida, criada_em DESC);
-- Mensagens de uma conversa, da mais nova para a mais antiga
CREATE INDEX idx_mensagem_conversa_data ON mensagem (conversa_id, enviada_em DESC);
-- Busca de vagas abertas por função e data (RNF08: até 3 s)
CREATE INDEX idx_vaga_status_funcao_inicio ON vaga (status, funcao_id, inicio);
-- Contratações por status (painéis e tarefas agendadas)
CREATE INDEX idx_contratacao_status ON contratacao (status);
-- Vagas de uma empresa
CREATE INDEX idx_vaga_empresa ON vaga (empresa_id, status);
-- Candidaturas de uma vaga
CREATE INDEX idx_candidatura_vaga ON candidatura (vaga_id, status);
-- Ocorrências recentes de um usuário (cálculo do score)
CREATE INDEX idx_ocorrencia_usuario ON ocorrencia (usuario_id, registrada_em);
