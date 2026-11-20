# Freelas: backend em Spring Boot

API REST do Freelas (app que liga freelancers a bares, restaurantes, mercados e eventos). Cobre os dois perfis (Empresa e Freelancer) e a moderação: cadastro e login, vagas, candidaturas, contratação com aceite em 6 h, cobrança no aceite com pagamento em custódia, check-in e check-out, liberação do pagamento, disputa, substituição (vaga relâmpago), avaliação mútua com nota de 0 a 5, score e nível de reputação, carteira e saque Pix, chat, notificações, Premium, destaque de vaga e Boost.

Feito a partir dos documentos do grupo: script do banco (PostgreSQL 16, 28 tabelas), diagrama de classes, mapeamento de endpoints da Empresa, Endpoints Freelancer V1.1, Padrão da API e Segurança, fluxo de pagamento e definição da stack (Java 21 + Spring Boot, Flyway, Docker Compose).

## Tecnologias

- Java 21, Spring Boot 4.1 (Web MVC, Data JPA, Validation, Security, OAuth2 Resource Server para o JWT, Actuator)
- PostgreSQL 16 com Flyway (migrações em `src/main/resources/db/migration`)
- Hibernate 7 com `ddl-auto: validate` (o Flyway cria as tabelas; o Hibernate só confere se as entidades batem com o banco)
- springdoc-openapi (Swagger em `/swagger-ui.html`), Lombok
- JUnit 5 nos testes

## Como rodar

Opção 1, tudo no Docker:

```
docker compose up --build
```

Opção 2, só o banco no Docker e a API pela IDE (IntelliJ/VS Code) ou pelo Maven:

```
docker compose up -d banco
mvn spring-boot:run
```

A API sobe em `http://localhost:8080`. A documentação interativa fica em `http://localhost:8080/swagger-ui.html` (clique em Authorize e cole o `tokenAcesso` do login).

Variáveis de ambiente (todas têm valor padrão para desenvolvimento):

| Variável | Para que serve |
|---|---|
| DB_URL, DB_USUARIO, DB_SENHA | Conexão com o PostgreSQL |
| JWT_SEGREDO | Segredo do token de acesso (32+ caracteres) |
| CHAVE_CRIPTOGRAFIA | Chave AES-256 em Base64 (32 bytes) para chave Pix e dados bancários |
| WEBHOOK_SEGREDO | Segredo do HMAC que assina os webhooks do gateway |
| ADMIN_EMAIL, ADMIN_SENHA | Se preenchidas, criam o primeiro Administrador ao subir |

O envio de e-mail está simulado: o código de 6 dígitos de confirmação aparece no log da aplicação (linha `[e-mail] para=...`). O gateway de pagamento também está simulado (aprova tudo, menos um `tokenGateway` que contenha "recusado").

Primeiro uso: `POST /empresas` ou `POST /freelancers`, pegar o código no log, `POST /autenticacao/confirmacao-email`, `POST /autenticacao/login` e usar o `tokenAcesso` no cabeçalho `Authorization: Bearer ...`.

## Organização do código

```
br.com.freelas
  entidade/      36 classes JPA (uma por tabela; Usuario abstrata com Freelancer, Empresa e Administrador)
  enums/         31 enumerações do diagrama de classes
  repositorio/   32 repositórios Spring Data (consultas com trava FOR UPDATE onde há dinheiro ou vaga)
  servico/       17 serviços com as regras de negócio
  controlador/   15 controladores REST
  dto/           requisições (com Bean Validation) e respostas
  excecao/       exceções da API e o tratador global de erros
  fila/          fila de tarefas no PostgreSQL, worker e tarefas agendadas
  seguranca/     JWT, hash, criptografia AES-GCM
  integracao/    gateway de pagamento e e-mail (simulados, atrás de interfaces)
  config/        segurança, Swagger, propriedades, criação do admin
```

Cada pedido passa por controlador (valida a entrada) → serviço (regra de negócio, transação) → repositório (banco). O controlador nunca fala direto com o repositório.

## Banco de dados e migrações

| Migração | Conteúdo |
|---|---|
| V1__esquema_base.sql | O script do grupo (28 tabelas, sem latitude/longitude) |
| V2__fila_e_autenticacao.sql | fila_tarefa, token_renovacao, token_verificacao (códigos de e-mail e senha) |
| V3__chat_e_dispositivos.sql | id_cliente na mensagem (evita duplicar no reenvio) e dispositivo_push |
| V4__indices.sql | Índices do estudo de leitura e escrita (notificação, mensagem, vaga, contratação, candidatura, ocorrência) |
| V5__ajustes_operacionais.sql | Tipo ADMINISTRADOR e tabela administrador, chave de idempotência no saque, motivo da disputa, correção do status da substituição |
| V6__dados_iniciais.sql | 18 funções (garçom, bartender, cozinheiro...) e 2 planos Premium |

Correção encontrada no teste: no script original a coluna `substituicao.status` é `VARCHAR(20)`, mas o valor `AGUARDANDO_SUBSTITUTO` tem 21 letras, então nenhuma substituição conseguia ser gravada. A V5 aumenta a coluna para `VARCHAR(25)`. Vale corrigir também o `freelas_modelo_fisico.sql` do grupo.

## Segurança

- Senha com BCrypt. Token de acesso JWT (HS256) de 15 minutos com o perfil do usuário (EMPRESA, FREELANCER ou ADMINISTRADOR).
- Token de renovação aleatório, guardado só como hash SHA-256: expira com 30 minutos sem uso ou 7 dias no máximo, e é trocado a cada renovação.
- Cada rota exige o perfil certo (`@PreAuthorize`) e cada serviço confere se o recurso é do usuário logado (uma empresa não mexe na vaga de outra).
- Conta PENDENTE pode entrar, mas só faz operações depois de confirmar o e-mail (403).
- Chave Pix e dados bancários são criptografados (AES-GCM) e aparecem mascarados nas respostas.
- Dado de recebimento novo ou alterado fica 24 h em quarentena para saque.
- No chat, telefone e e-mail são trocados por "[contato oculto até a contratação]" até a contratação ser confirmada.
- Webhook do gateway: assinatura HMAC-SHA256 do corpo no cabeçalho `X-Assinatura`; evento repetido não muda nada.
- Cobranças, saques, destaques e Boost exigem o cabeçalho `Idempotency-Key`: a mesma chave repetida devolve o resultado da primeira vez (200) em vez de cobrar de novo.

## Erros de validação

Todos os erros saem no mesmo formato:

```json
{
  "timestamp": "2026-11-19T19:23:54.136",
  "status": 400,
  "erro": "VALIDACAO",
  "mensagem": "Um ou mais campos estão inválidos.",
  "caminho": "/empresas",
  "campos": [
    { "campo": "email", "mensagem": "deve ser um endereço de e-mail bem formado", "valorRejeitado": "x" },
    { "campo": "endereco.cep", "mensagem": "CEP deve ter 8 dígitos (ex.: 87020-000)", "valorRejeitado": "1" }
  ]
}
```

| Status | erro | Quando |
|---|---|---|
| 400 | VALIDACAO | Campo obrigatório vazio, formato errado (CPF, CNPJ, e-mail, CEP, telefone, senha, URL), número fora da faixa, data no passado, enum desconhecido (a mensagem lista os valores aceitos), parâmetro de URL inválido, cabeçalho obrigatório ausente |
| 400 | JSON_INVALIDO | Corpo que não é JSON |
| 401 | NAO_AUTENTICADO | Sem token, token vencido, senha errada, assinatura de webhook inválida |
| 403 | ACESSO_NEGADO | Perfil errado para a rota, recurso de outro usuário, conta sem e-mail confirmado |
| 404 | NAO_ENCONTRADO | Recurso ou rota inexistente |
| 405 / 415 | METODO_NAO_PERMITIDO / TIPO_MIDIA_NAO_SUPORTADO | Verbo HTTP ou Content-Type errado |
| 409 | CONFLITO | Duplicidade (CPF, CNPJ, e-mail, candidatura repetida, avaliar duas vezes) ou estado que não permite a ação (publicar vaga já publicada, check-in sem aceite, vaga sem posição livre) |
| 422 | REGRA_NEGOCIO | Dados válidos, mas a regra não deixa: menor de 18 anos, valor abaixo do mínimo, sem meio de pagamento, aceite sem foto, saldo insuficiente, quarentena do Pix, conflito de horário. Quando há mais de um motivo, vêm na lista `motivos` |
| 500 | ERRO_INTERNO | Erro inesperado (detalhe só no log; a resposta nunca mostra senha nem stack trace) |

A validação de entrada usa Bean Validation nos DTOs (`@NotBlank`, `@CPF`, `@CNPJ`, `@Email`, `@Pattern`, `@DecimalMin`, `@Future`, `@AssertTrue` para regras entre campos como "fim depois do início") e nos parâmetros dos controladores (página, tamanho, UF, CEP). O tratamento fica em `excecao/TratadorGlobalDeErros.java`. Violações de restrição do banco também viram respostas legíveis (UNIQUE → 409, CHECK → 422).

## Fila de tarefas e rotinas

Sem RabbitMQ ou Kafka: a fila é a tabela `fila_tarefa`, lida pelo worker a cada 2 s com `FOR UPDATE SKIP LOCKED` (vários servidores podem rodar juntos sem pegar a mesma tarefa) e com novas tentativas em caso de erro. Tarefas: avisar freelancers de vaga nova (um único INSERT...SELECT em lote), buscar substitutos, liberar pagamento, processar saque e processar webhook.

Rotinas de minuto em minuto: expirar aceites não respondidos em 6 h, liberar pagamentos cuja janela de 6 h acabou sem disputa, publicar avaliações após 72 h, encerrar vagas vencidas e concluir turnos terminados.

## Regras com números propostos

Os documentos não fixam alguns números. Ficaram em `application.yml` (`freelas.regras`) para o grupo ajustar sem mexer no código: taxa da plataforma 10%, valor mínimo R$ 15/hora, R$ 80/turno e R$ 120/diária, cancelamento sem penalidade até 24 h antes, suspensão de 7 dias por no-show, Premium vê vagas 15 min antes, raio padrão de substituição 10 km, check-in a partir de 60 min antes. Score = média das notas visíveis dos últimos 90 dias menos as penalidades das ocorrências (no-show 1,00; abandono 0,75; cancelamento tardio 0,50; cancelamento prévio 0,10), entre 0 e 5. Níveis: EXCELENTE a partir de 4,5; BOM a partir de 3,5; REGULAR a partir de 2,5; abaixo disso EM_RISCO.

## Testes

- `mvn test`: 14 testes de unidade (cálculo do valor da vaga, níveis de reputação, dias úteis, palavras proibidas, validação dos DTOs, criptografia e HMAC).
- `testes/fluxo-completo.sh`: teste de ponta a ponta com a API rodando e o banco zerado (125 checagens). Passa por cadastro, confirmação de e-mail, login, todos os tipos de erro, vaga, candidatura, chat, contratação, aceite com cobrança, check-in/out, conclusão, avaliações, contestação, carteira, saque, substituição, destaque, Boost, disputa decidida pelo admin, cancelamento tardio com penalidade e webhook. Última execução: 125 de 125 OK.
- `docs/openapi.json`: a especificação OpenAPI gerada (dá para importar no Postman ou Insomnia).

## Diferenças em relação aos documentos e o que ficou de fora

Escolhas que divergem de algum documento do grupo (vale decidir em grupo):

- Erro de campo devolve 400, como pediu o mapeamento de endpoints da Empresa; o documento Padrão da API sugere 422 para validação. Aqui 422 ficou só para regra de negócio.
- Mudanças de estado são `POST` em sub-recurso (`/candidaturas/{id}/cancelamento`, `/contratacoes/{id}/aceite`), seguindo o padrão do mapeamento da Empresa; o documento do Freelancer usa `PATCH` em alguns casos.
- Não há `POST /contratacoes/{id}/pagamento`: a cobrança acontece no aceite do freelancer, como o grupo decidiu.
- Notificações e dispositivos ficaram em rotas comuns aos dois perfis (`/notificacoes`, `/dispositivos`).
- Preço do Premium da Empresa (R$ 49,90) e das opções de destaque/Boost são propostas.

Simplificado ou não implementado (precisa de tabela, serviço externo ou decisão do grupo):

- Sem latitude/longitude no banco, o raio da substituição é aproximado pelo CEP: até 5 km = mesmo início de CEP, até 30 km = mesma cidade, acima = mesma UF. A ordenação por distância na busca fica para quando entrar o Google Maps.
- Gateway (Asaas ou Mercado Pago) e e-mail estão simulados atrás de interfaces (`GatewayPagamento`, `EmailService`).
- Chat por REST com aviso por notificação; o WebSocket fica para a próxima etapa.
- Não implementados: limite de requisições (429, depende do Redis), partições mensais de notificação e log, 2FA para Pix, biometria, disponibilidade do freelancer, comprovante em PDF, relatório mensal, compensação ao freelancer por cancelamento em cima da hora, registro de quem recusou uma substituição.

## Endpoints

112 operações. A lista completa, com exemplos e esquemas, está no Swagger.

### Autenticação

- `POST /autenticacao/confirmacao-email`: Confirmar o e-mail (cadastro ou troca de e-mail) com o código de 6 dígitos
- `POST /autenticacao/confirmacao-email/reenvio`: Reenviar o código de confirmação de e-mail
- `POST /autenticacao/login`: Entrar com e-mail e senha; devolve o token de acesso e o de renovação
- `POST /autenticacao/logout`: Sair e invalidar o token de renovação
- `POST /autenticacao/recuperacao-senha`: Esqueci a senha: envia um código válido por 15 minutos
- `POST /autenticacao/redefinicao-senha`: Definir a nova senha com o código recebido
- `POST /autenticacao/renovacao`: Trocar o token de renovação por um novo par de tokens

### Empresa

- `POST /empresas`: Criar o cadastro da empresa (status PENDENTE até confirmar o e-mail)
- `DELETE /empresas/{id}`: Pedir a exclusão da conta (LGPD)
- `GET /empresas/{id}`: Consultar a empresa (completo para a própria empresa, público para os demais)
- `PATCH /empresas/{id}`: Alterar dados do perfil (CNPJ só pelo suporte; e-mail só depois de confirmar)
- `GET /empresas/{id}/assinaturas`: Assinatura atual e histórico
- `POST /empresas/{id}/assinaturas`: Assinar o Premium da empresa (exige Idempotency-Key)
- `GET /empresas/{id}/avaliacoes`: Avaliações recebidas pela empresa (só leitura)
- `GET /empresas/{id}/contratacoes`: Listar as contratações da empresa
- `GET /empresas/{id}/conversas`: Conversas da empresa com a última mensagem e as não lidas
- `PUT /empresas/{id}/endereco`: Substituir o endereço do estabelecimento
- `GET /empresas/{id}/meios-pagamento`: Listar os meios de pagamento
- `POST /empresas/{id}/meios-pagamento`: Cadastrar cartão ou Pix (só o token do gateway, nunca o número do cartão)
- `DELETE /empresas/{id}/meios-pagamento/{meioId}`: Desativar um meio de pagamento
- `GET /empresas/{id}/pagamentos`: Histórico de pagamentos e comprovantes
- `PUT /empresas/{id}/senha`: Alterar a senha informando a atual
- `GET /empresas/{id}/vagas`: Listar as vagas da empresa

### Freelancer

- `POST /freelancers`: Cadastrar freelancer (18+ anos, aceite dos termos); cria a carteira
- `DELETE /freelancers/me`: Pedir a exclusão da conta (LGPD)
- `GET /freelancers/me`: Consultar o próprio perfil
- `PATCH /freelancers/me`: Alterar dados livres: telefone, apresentação e endereço
- `GET /freelancers/me/avaliacoes`: Avaliações que recebi (as ocultas pela regra cega não aparecem)
- `GET /freelancers/me/avaliacoes/pendentes`: Turnos com a janela de 72 h aberta para avaliar a empresa
- `GET /freelancers/me/candidaturas`: Minhas candidaturas
- `GET /freelancers/me/completude`: O que falta no perfil
- `GET /freelancers/me/contratacoes`: Minhas contratações (turnos)
- `GET /freelancers/me/conversas`: Minhas conversas com empresas
- `GET /freelancers/me/convites-reposicao`: Vagas relâmpago (substituições) abertas para mim
- `PUT /freelancers/me/curriculo`: Enviar ou trocar o currículo (URL no armazenamento de arquivos)
- `GET /freelancers/me/favoritos`: Vagas favoritas
- `DELETE /freelancers/me/favoritos/{vagaId}`: Remover dos favoritos
- `PUT /freelancers/me/favoritos/{vagaId}`: Favoritar vaga (não reserva a vaga)
- `POST /freelancers/me/formacoes`: Adicionar formação ou curso
- `DELETE /freelancers/me/formacoes/{formacaoId}`: Remover formação
- `PUT /freelancers/me/foto`: Enviar ou trocar a foto (URL no armazenamento de arquivos)
- `PUT /freelancers/me/funcoes`: Definir as funções (categorias de atuação) e os anos de experiência
- `GET /freelancers/me/score`: Score, nível de reputação e a composição do cálculo
- `PATCH /freelancers/me/senha`: Alterar a senha
- `GET /freelancers/{id}`: Perfil do candidato para a empresa (sem CPF; contato só após contratação confirmada)
- `GET /freelancers/{id}/avaliacoes`: Avaliações publicadas recebidas pelo freelancer

### Freelancer - carteira, Premium e Boost

- `POST /freelancers/me/assinaturas`: Assinar o Premium (R$ 19,90 / 30 dias) - exige Idempotency-Key
- `GET /freelancers/me/assinaturas/atual`: Assinatura vigente e vencimento
- `POST /freelancers/me/assinaturas/atual/cancelamento`: Cancelar a renovação (acesso até o fim dos 30 dias pagos)
- `GET /freelancers/me/boosts`: Boosts ativo e anteriores
- `POST /freelancers/me/boosts`: Contratar Boost de 24 h (perfil completo, Pix homologado, sem no-show recente)
- `GET /freelancers/me/carteira`: Saldos disponível, pendente e bloqueado
- `GET /freelancers/me/carteira/extrato`: Extrato com filtro de período (datas AAAA-MM-DD)
- `GET /freelancers/me/dados-recebimento`: Chaves Pix e contas (mascaradas)
- `POST /freelancers/me/dados-recebimento`: Cadastrar chave Pix ou conta; saques liberados após 24 h
- `PUT /freelancers/me/dados-recebimento/{id}`: Alterar chave ou conta (reinicia a quarentena de 24 h)
- `GET /freelancers/me/ganhos/resumo`: Painel de ganhos: HOJE, SETE_DIAS, MES ou TOTAL
- `GET /freelancers/me/saques`: Listar saques
- `POST /freelancers/me/saques`: Solicitar saque Pix do saldo disponível (exige Idempotency-Key)
- `GET /freelancers/me/saques/{id}`: Acompanhar um saque

### Vagas

- `GET /funcoes`: Funções (categorias) disponíveis para vagas e perfis
- `GET /vagas`: Buscar vagas publicadas (destaques primeiro; Premium vê 15 min antes)
- `POST /vagas`: Criar vaga como rascunho
- `DELETE /vagas/{id}`: Excluir rascunho
- `GET /vagas/{id}`: Detalhe da vaga
- `PATCH /vagas/{id}`: Editar vaga (rascunho livre; publicada só antes da primeira candidatura)
- `POST /vagas/{id}/cancelamento`: Cancelar vaga (recusa candidaturas pendentes e estorna pagamentos)
- `GET /vagas/{id}/candidaturas`: Candidatos da vaga, ordenados por score, trabalhos concluídos ou data
- `POST /vagas/{id}/candidaturas`: Candidatar-se à vaga
- `POST /vagas/{id}/contratacoes`: Contratar um candidato (o freelancer tem 6 h para aceitar; a cobrança acontece no aceite)
- `GET /vagas/{id}/destaques`: Destaques comprados para a vaga
- `POST /vagas/{id}/destaques`: Comprar destaque para a vaga (exige Idempotency-Key)
- `GET /vagas/{id}/historico`: Histórico de alterações da vaga (log de auditoria)
- `POST /vagas/{id}/publicacao`: Publicar vaga (valida regras e avisa freelancers da região pela fila)

### Candidaturas

- `GET /candidaturas/{id}`: Consultar candidatura (o freelancer dono ou a empresa da vaga)
- `PATCH /candidaturas/{id}`: Empresa marca a candidatura como RECUSADA
- `POST /candidaturas/{id}/cancelamento`: Freelancer desiste da candidatura
- `POST /candidaturas/{id}/conversa`: Abrir a conversa da candidatura (201 se criou, 200 se já existia)

### Contratações

- `GET /contratacoes/{id}`: Consultar contratação (empresa ou freelancer envolvidos)
- `POST /contratacoes/{id}/aceite`: Freelancer aceita (dentro de 6 h); aqui a empresa é cobrada e o valor fica em custódia
- `GET /contratacoes/{id}/avaliacoes`: Avaliações da contratação visíveis para quem consulta
- `POST /contratacoes/{id}/avaliacoes`: Avaliar a outra parte (nota de 0 a 5, janela de 72 h, regra cega)
- `POST /contratacoes/{id}/cancelamento`: Cancelar (com menos de 24 h gera ocorrência; o pagamento é estornado)
- `POST /contratacoes/{id}/check-in`: Registrar chegada (a partir de 60 min antes do início)
- `POST /contratacoes/{id}/check-out`: Registrar saída
- `POST /contratacoes/{id}/conclusao`: Empresa confirma a conclusão e libera o pagamento ao freelancer
- `POST /contratacoes/{id}/ocorrencias`: Registrar ocorrência (no-show, abandono...)
- `GET /contratacoes/{id}/pagamento`: Pagamento da contratação
- `POST /contratacoes/{id}/recusa`: Freelancer recusa a contratação
- `POST /contratacoes/{id}/substituicoes`: Pedir reposição do freelancer (vaga relâmpago para a região)

### Pagamentos

- `POST /pagamentos/{id}/disputa`: Empresa contesta o pagamento antes da liberação (fica retido até a decisão)
- `POST /webhooks/gateway-pagamento`: Webhook do gateway de pagamento (assinado com HMAC-SHA256)

### Substituições

- `GET /substituicoes/{id}`: Acompanhar a reposição
- `PATCH /substituicoes/{id}`: Ampliar o raio de busca
- `POST /substituicoes/{id}/aceite`: Freelancer aceita a vaga relâmpago (o primeiro que aceitar fica com ela)
- `POST /substituicoes/{id}/cancelamento`: Cancelar a reposição

### Avaliações

- `POST /avaliacoes/{id}/contestacoes`: Contestar uma avaliação recebida (vai para moderação)

### Chat

- `POST /conversas/{id}/leitura`: Marcar como lidas as mensagens recebidas
- `GET /conversas/{id}/mensagens`: Mensagens da conversa, da mais nova para a mais antiga (use antesDe para paginar)
- `POST /conversas/{id}/mensagens`: Enviar mensagem (idCliente evita duplicar no reenvio)

### Notificações

- `POST /dispositivos`: Registrar o aparelho para receber push
- `GET /notificacoes`: Minhas notificações (filtro opcional por lida)
- `PATCH /notificacoes/{id}`: Marcar notificação como lida ou não lida

### Premium e Boost

- `PATCH /assinaturas/{id}`: Ligar ou desligar a renovação automática
- `POST /assinaturas/{id}/cancelamento`: Cancelar a assinatura (vale até o fim do período pago)
- `GET /boosts/opcoes`: Opções de Boost de perfil e preços
- `GET /opcoes-destaque`: Opções de destaque de vaga e preços
- `GET /planos`: Planos Premium (filtro por público: EMPRESA ou FREELANCER)

### Administração

- `GET /admin/contestacoes`: Contestações de avaliação abertas
- `POST /admin/contestacoes/{id}/decisao`: Aceitar (remove a avaliação) ou rejeitar a contestação
- `GET /admin/disputas`: Pagamentos em disputa
- `POST /admin/disputas/{pagamentoId}/decisao`: Decidir a disputa: libera ao freelancer ou estorna à empresa
