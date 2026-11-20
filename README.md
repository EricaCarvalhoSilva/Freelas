# Freelas (MeuFreela)

Aplicativo que conecta freelancers a empregadores de bares, restaurantes, mercados, eventos e outros estabelecimentos que precisam de mão de obra temporária. A plataforma cobre todo o processo: publicação da vaga, candidatura, contratação, pagamento e avaliação mútua.

Projeto desenvolvido para a disciplina de Gestão de Projetos Tecnológicos, curso de Análise e Desenvolvimento de Sistemas (Unicesumar).

## Equipe

- Lucas Fernandes de Araujo
- Helen da Cruz Alves
- Erica Cristiana Carvalho Silva
- Carlos Felipe Dutra
- Juliano Rafael De Oliveira Hauari
- Luan Vinícius Da Silva Sacoman

## Estrutura da documentação

- `documentação/pesquisa-mercado/` — pesquisas de mercado, concorrentes e comparativo de modelos de monetização
- `documentação/projeto/` — visão geral do projeto, pitch e glossário de termos
- `documentação/requisitos/` — requisitos funcionais e não funcionais, separados por Empresa e Freelancers
- `documentação/regras-negocio/` — regras de negócio detalhadas (fluxos, condições e exceções), separadas por Empresa e Freelancers

## Status

Em desenvolvimento


## Subindo o banco de dados (PostgreSQL 16)

**Pré-requisito:** Docker Desktop instalado e aberto (com "Engine running").

1. Na raiz do repositório, suba o banco:

   ```
   docker compose up -d
   ```

2. Confirme que está saudável (STATUS `healthy`):

   ```
   docker compose ps
   ```

3. Rode o backend. O Flyway aplica as migrações automaticamente na primeira execução e cria as tabelas `empresas`, `funcoes` e `vagas`, com os dados de teste (uma empresa, cinco funções e uma vaga).

4. Para conferir o banco:

   ```
   docker compose exec db psql -U freelas -d freelas -c "\dt"
   docker compose exec db psql -U freelas -d freelas -c "SELECT * FROM vagas;"
   ```

**Dados de conexão:** host `localhost`, porta `5432`, banco `freelas`, usuário `freelas`, senha `freelas`.

**Parar o banco:** `docker compose down`

**Zerar o banco** (apaga os dados e refaz as migrações na próxima execução): `docker compose down -v`

**Problemas comuns**

- Porta 5432 ocupada (já existe um PostgreSQL instalado): crie um arquivo `.env` na raiz com `POSTGRES_PORT=5433` e rode `docker compose up -d` de novo.
- Não edite migrações que já foram aplicadas: crie uma nova (V3, V4...) ou rode `docker compose down -v`.

**Migrações:** ficam em `backend/src/main/resources/db/migration/` (V1 cria as tabelas, V2 insere os dados de teste).