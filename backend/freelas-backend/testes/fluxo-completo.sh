#!/bin/bash
# Teste de ponta a ponta do backend Freelas (cerca de 125 checagens).
# Uso: suba a API com o log em arquivo e rode com o banco ZERADO:
#   java -jar target/freelas-backend-1.0.0.jar > app.log 2>&1 &   (com ADMIN_EMAIL=admin@freelas.local ADMIN_SENHA=Admin@12345)
#   ./testes/fluxo-completo.sh app.log
# Precisa de curl, jq, openssl e psql. O código de confirmação de e-mail é lido do log (o e-mail é simulado).
B=${API:-http://localhost:8080}
LOG=${1:-app.log}
PSQL=${PSQL:-psql postgresql://freelas:freelas@localhost:5432/freelas}
OUT=$(mktemp)
req() { # metodo caminho token corpo [cabecalho extra]
  local m=$1 p=$2 t=$3 d=$4 h=$5
  local args=(-s -o $OUT -w '%{http_code}' -X $m "$B$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t")
  [ -n "$h" ] && args+=(-H "$h")
  [ -n "$d" ] && args+=(-d "$d")
  CODE=$(curl "${args[@]}"); BODY=$(cat $OUT)
}
ok() { # descricao codigo_esperado
  if [ "$CODE" == "$2" ]; then echo "OK   $CODE $1"; else echo "FAIL $CODE (esperado $2) $1"; echo "     $BODY" | cut -c1-600; fi
}
show() { echo "     $(echo "$BODY" | cut -c1-${1:-400})"; }
codigo() { grep "para=$1 " $LOG | tail -1 | grep -oE '[0-9]{6}' | tail -1; }
login() { req POST /autenticacao/login "" "{\"email\":\"$1\",\"senha\":\"$2\"}"; echo "$BODY" | jq -r .tokenAcesso; }
END='{"cep":"87020-000","logradouro":"Av. Brasil","numero":"100","bairro":"Centro","cidade":"Maringá","uf":"PR"}'
CORPO='{"idTransacaoGateway":"x","evento":"PAGAMENTO_CONFIRMADO","status":"APROVADO"}'
INI=$(date -u -d '+30 min' +%Y-%m-%dT%H:%M:%S); FIM=$(date -u -d '+5 hour' +%Y-%m-%dT%H:%M:%S)

echo "== Erros de validação"
req POST /empresas "" '{"razaoSocial":"","cnpj":"123","email":"x","senha":"abc","telefone":"1","categoria":"PADARIA","endereco":{}}'; ok "cadastro empresa inválido (enum desconhecido)" 400; show 500
req POST /empresas "" '{"razaoSocial":"","cnpj":"123","email":"x","senha":"abc","telefone":"1","categoria":"BAR","endereco":{"cep":"1"}}'; ok "cadastro empresa inválido (campos)" 400; show 900
req POST /empresas "" '{"razaoSocial": '; ok "JSON malformado" 400; show
req GET /vagas/abc ""; ok "sem token" 401; show
req GET /funcoes ""; ok "funções públicas" 200; FUNCAO=$(echo "$BODY" | jq '.[0].id')

echo "== Cadastros"
req POST /empresas "" "{\"razaoSocial\":\"Bar do Zé LTDA\",\"nomeFantasia\":\"Bar do Zé\",\"cnpj\":\"11.222.333/0001-81\",\"email\":\"empresa@teste.com\",\"senha\":\"Senha123\",\"telefone\":\"44999990000\",\"categoria\":\"BAR\",\"endereco\":$END}"; ok "cadastrar empresa" 201; EMP=$(echo "$BODY" | jq .id)
req POST /empresas "" "{\"razaoSocial\":\"Outro\",\"nomeFantasia\":\"Outro\",\"cnpj\":\"11222333000181\",\"email\":\"outra@teste.com\",\"senha\":\"Senha123\",\"telefone\":\"44999990000\",\"categoria\":\"BAR\",\"endereco\":$END}"; ok "CNPJ duplicado" 409; show
req POST /freelancers "" "{\"nome\":\"Ana Souza\",\"cpf\":\"529.982.247-25\",\"dataNascimento\":\"2010-01-01\",\"email\":\"ana@teste.com\",\"senha\":\"Senha123\",\"telefone\":\"44988887777\",\"endereco\":$END,\"aceiteTermos\":true}"; ok "freelancer menor de idade" 422; show
req POST /freelancers "" "{\"nome\":\"Ana Souza\",\"cpf\":\"529.982.247-25\",\"dataNascimento\":\"1995-05-10\",\"email\":\"ana@teste.com\",\"senha\":\"Senha123\",\"telefone\":\"44988887777\",\"endereco\":$END,\"aceiteTermos\":true}"; ok "cadastrar freelancer" 201; FRE=$(echo "$BODY" | jq .id)
req POST /freelancers "" "{\"nome\":\"Bruno Lima\",\"cpf\":\"111.444.777-35\",\"dataNascimento\":\"1990-02-02\",\"email\":\"bruno@teste.com\",\"senha\":\"Senha123\",\"telefone\":\"44977776666\",\"endereco\":$END,\"aceiteTermos\":true}"; ok "cadastrar freelancer 2" 201; FRE2=$(echo "$BODY" | jq .id)
TP=$(login empresa@teste.com Senha123); req POST /empresas/$EMP/meios-pagamento "$TP" '{"tipo":"CARTAO","tokenGateway":"tok","finalCartao":"1234"}'; ok "conta pendente não cadastra meio de pagamento" 403; show
sleep 1
for e in empresa@teste.com ana@teste.com bruno@teste.com; do
  req POST /autenticacao/confirmacao-email "" "{\"email\":\"$e\",\"codigo\":\"$(codigo $e)\"}"; ok "confirmar e-mail $e" 200
done
req POST /autenticacao/login "" '{"email":"empresa@teste.com","senha":"errada1234"}'; ok "senha errada" 401; show
TE=$(login empresa@teste.com Senha123); TF=$(login ana@teste.com Senha123); TF2=$(login bruno@teste.com Senha123)
[ ${#TE} -gt 20 ] && echo "OK   tokens emitidos" || echo "FAIL token"
req GET /empresas/$EMP "$TF"; ok "freelancer vê empresa (perfil público)" 200
req PATCH /empresas/$EMP "$TF" '{"telefone":"4433334444"}'; ok "freelancer não altera empresa" 403; show
req GET /freelancers/me "$TE"; ok "empresa em rota de freelancer" 403
req PUT /freelancers/me/funcoes "$TF" "{\"funcoes\":[{\"funcaoId\":$FUNCAO,\"anosExperiencia\":3}]}"; ok "funções da Ana" 200
req PUT /freelancers/me/funcoes "$TF2" "{\"funcoes\":[{\"funcaoId\":$FUNCAO,\"anosExperiencia\":1}]}"; ok "funções do Bruno" 200
req PUT /freelancers/me/funcoes "$TF" '{"funcoes":[{"funcaoId":9999}]}'; ok "função inexistente" 422; show
req GET /freelancers/me/completude "$TF"; ok "completude" 200; show

echo "== Vaga"
VAGA="{\"funcaoId\":$FUNCAO,\"quantidadeProfissionais\":1,\"inicio\":\"$INI\",\"fim\":\"$FIM\",\"valor\":150.00,\"tipoValor\":\"TURNO\",\"ofereceAlimentacao\":true,\"formaPagamento\":\"CARTAO\",\"descricao\":\"Garçom para sexta\"}"
req POST /vagas "$TE" "{\"funcaoId\":$FUNCAO,\"quantidadeProfissionais\":0,\"inicio\":\"2020-01-01T10:00:00\",\"fim\":\"2020-01-01T09:00:00\",\"valor\":-1,\"tipoValor\":\"TURNO\",\"ofereceAlimentacao\":true,\"formaPagamento\":\"CARTAO\"}"; ok "vaga inválida" 400; show 900
req POST /vagas "$TE" "$VAGA"; ok "criar vaga" 201; V=$(echo "$BODY" | jq .id)
req POST /vagas/$V/publicacao "$TE"; ok "publicar sem meio de pagamento" 422; show
req POST /empresas/$EMP/meios-pagamento "$TE" '{"tipo":"CARTAO","tokenGateway":"tok_cartao_ok","finalCartao":"4242"}'; ok "meio de pagamento" 201
req POST /vagas/$V/publicacao "$TE"; ok "publicar vaga" 200
req POST /vagas/$V/publicacao "$TE"; ok "publicar de novo" 409; show
sleep 3
req GET "/vagas?cidade=Maring%C3%A1&ordenarPor=VALOR&ordem=ASC" "$TF"; ok "buscar vagas" 200; show 300
req GET "/vagas?uf=PRX&tamanho=500" "$TF"; ok "busca com parâmetros inválidos" 400; show 600
req GET /notificacoes "$TF"; ok "notificações da Ana (nova vaga pela fila)" 200; show 300

echo "== Candidatura e chat"
req POST /vagas/$V/candidaturas "$TF"; ok "Ana se candidata" 201; C=$(echo "$BODY" | jq .id)
req POST /vagas/$V/candidaturas "$TF"; ok "candidatura duplicada" 409; show
req POST /vagas/$V/candidaturas "$TF2"; ok "Bruno se candidata" 201; C2=$(echo "$BODY" | jq .id)
req GET "/vagas/$V/candidaturas?ordenarPor=SCORE" "$TE"; ok "candidatos" 200; show 300
req POST /candidaturas/$C/conversa "$TE"; ok "abrir conversa" 201; CV=$(echo "$BODY" | jq .id)
req POST /candidaturas/$C/conversa "$TF"; ok "abrir conversa de novo" 200
req POST /conversas/$CV/mensagens "$TF" '{"conteudo":"Oi, meu zap é 44 98888-7777 e e-mail ana@teste.com","idCliente":"m1"}'; ok "mensagem (contato mascarado)" 201; show
req POST /conversas/$CV/mensagens "$TF" '{"conteudo":"repetida","idCliente":"m1"}'; ok "mensagem repetida (idCliente)" 200
req GET /conversas/$CV/mensagens "$TE"; ok "ler mensagens" 200
req POST /conversas/$CV/leitura "$TE"; ok "marcar lidas" 200; show
req POST /conversas/$CV/mensagens "$TF2" '{"conteudo":"intruso"}'; ok "terceiro no chat" 403

echo "== Contratação"
req POST /vagas/$V/contratacoes "$TE" '{}'; ok "contratar sem candidatura" 400; show
req POST /vagas/$V/contratacoes "$TE" "{\"candidaturaId\":$C}"; ok "contratar Ana" 201; K=$(echo "$BODY" | jq .id); show 300
req POST /contratacoes/$K/aceite "$TF2"; ok "Bruno tenta aceitar contratação da Ana" 403
req POST /contratacoes/$K/check-in "$TF"; ok "check-in antes do aceite" 409
req POST /contratacoes/$K/aceite "$TF"; ok "aceite sem foto" 422; show
req PUT /freelancers/me/foto "$TF" '{"fotoUrl":"nao-e-url"}'; ok "foto URL inválida" 400; show
req PUT /freelancers/me/foto "$TF" '{"fotoUrl":"https://arquivos.freelas.app/ana.jpg"}'; ok "foto Ana" 200
req PUT /freelancers/me/foto "$TF2" '{"fotoUrl":"https://arquivos.freelas.app/bruno.jpg"}'; ok "foto Bruno" 200
req POST /contratacoes/$K/aceite "$TF"; ok "Ana aceita (cobrança)" 200; show 300
req GET /contratacoes/$K/pagamento "$TE"; ok "pagamento em custódia" 200; show 400
req POST /vagas/$V/contratacoes "$TE" "{\"candidaturaId\":$C2}"; ok "contratar além da quantidade" 409; show
req POST /contratacoes/$K/conclusao "$TE"; ok "concluir sem check-out" 422
req POST /contratacoes/$K/check-in "$TF" '{"latitude":-23.42,"longitude":-51.93}'; ok "check-in" 200
req POST /contratacoes/$K/check-out "$TF" '{"latitude":95}'; ok "check-out latitude inválida" 400; show
req POST /contratacoes/$K/check-out "$TF"; ok "check-out" 200
req POST /contratacoes/$K/conclusao "$TE"; ok "concluir e liberar" 200; show 200
req GET /contratacoes/$K/pagamento "$TF"; ok "pagamento liberado" 200; show 300

echo "== Avaliações"
req POST /contratacoes/$K/avaliacoes "$TE" '{"nota":7}'; ok "nota fora de 0-5" 400; show
req POST /contratacoes/$K/avaliacoes "$TE" '{"nota":4.5,"comentario":"Ótima","criterios":[{"criterio":"PONTUALIDADE","nota":5}]}'; ok "empresa avalia" 201; AV=$(echo "$BODY" | jq .id); show 300
req POST /contratacoes/$K/avaliacoes "$TE" '{"nota":4}'; ok "avaliar duas vezes" 409
req GET /contratacoes/$K/avaliacoes "$TF"; ok "avaliação cega (Ana ainda não vê)" 200; show 200
req POST /contratacoes/$K/avaliacoes "$TF" '{"nota":5,"comentario":"Lugar bom"}'; ok "Ana avalia" 201
req GET /contratacoes/$K/avaliacoes "$TF"; ok "as duas publicadas" 200; show 300
req GET /freelancers/me/score "$TF"; ok "score da Ana" 200; show 300
req POST /avaliacoes/$AV/contestacoes "$TF" '{"justificativa":"Nota injusta, cheguei no horário"}'; ok "contestação" 201; CT=$(echo "$BODY" | jq .id); show 200

echo "== Carteira e saque"
req GET /freelancers/me/carteira "$TF"; ok "carteira" 200; show
req POST /freelancers/me/dados-recebimento "$TF" '{"tipo":"PIX"}'; ok "conta PIX incompleta" 400; show
req POST /freelancers/me/dados-recebimento "$TF" '{"tipo":"PIX","tipoChavePix":"EMAIL","chavePix":"ana@teste.com"}'; ok "cadastrar PIX" 201; CR=$(echo "$BODY" | jq .id); show
req POST /freelancers/me/saques "$TF" "{\"contaRecebimentoId\":$CR,\"valor\":10}"; ok "saque sem Idempotency-Key" 400; show
req POST /freelancers/me/saques "$TF" "{\"contaRecebimentoId\":$CR,\"valor\":50}" "Idempotency-Key: s0"; ok "saque na quarentena de 24 h" 422; show
$PSQL -q -c "update conta_recebimento set liberada_saque_em = now() - interval '1 hour'"  # pula a quarentena de 24 h
req POST /freelancers/me/saques "$TF" "{\"contaRecebimentoId\":$CR,\"valor\":99999}" "Idempotency-Key: s1"; ok "saque acima do saldo" 422; show
req POST /freelancers/me/saques "$TF" "{\"contaRecebimentoId\":$CR,\"valor\":50}" "Idempotency-Key: s2"; ok "saque" 201; show
req POST /freelancers/me/saques "$TF" "{\"contaRecebimentoId\":$CR,\"valor\":50}" "Idempotency-Key: s2"; ok "saque repetido (mesma chave)" 200
sleep 4
req GET /freelancers/me/saques "$TF"; ok "saques (processado pela fila)" 200; show 300
req GET "/freelancers/me/carteira/extrato?periodo=TOTAL" "$TF"; ok "extrato" 200; show 400

echo "== Substituição (2ª vaga)"
req POST /vagas "$TE" "$VAGA"; V2=$(echo "$BODY" | jq .id); req POST /vagas/$V2/publicacao "$TE"; ok "publicar vaga 2" 200
req POST /vagas/$V2/candidaturas "$TF"; C3=$(echo "$BODY" | jq .id)
req POST /vagas/$V2/contratacoes "$TE" "{\"candidaturaId\":$C3}"; K2=$(echo "$BODY" | jq .id)
req POST /contratacoes/$K2/aceite "$TF"; ok "Ana aceita vaga 2" 200
req POST /contratacoes/$K2/substituicoes "$TE" '{"motivo":"NO_SHOW","raioKm":10}'; ok "pedir reposição" 201; SB=$(echo "$BODY" | jq .id); show 300
sleep 4
req GET /freelancers/me/convites-reposicao "$TF2"; ok "Bruno vê convite" 200; show 300
req POST /substituicoes/$SB/aceite "$TF2"; ok "Bruno aceita reposição" 200; show 300
req POST /substituicoes/$SB/aceite "$TF2"; ok "aceitar de novo" 409

echo "== Premium / Boost / Destaque"
req GET /planos ""; ok "planos" 200; show 300
req GET /opcoes-destaque ""; ok "opções destaque" 200
req POST /vagas "$TE" "$VAGA"; V3=$(echo "$BODY" | jq .id); req POST /vagas/$V3/publicacao "$TE"
req POST /vagas/$V3/destaques "$TE" '{"opcao":"VAGA_24H"}' "Idempotency-Key: d1"; ok "destacar vaga" 201; show 300
req POST /vagas/$V3/destaques "$TE" '{"opcao":"VAGA_24H"}' "Idempotency-Key: d1"; ok "destaque repetido (mesma chave)" 200
req POST /vagas/$V3/destaques "$TE" '{"opcao":"BOOST_24H"}' "Idempotency-Key: d2"; ok "opção errada no destaque" 422; show
req POST /freelancers/me/boosts "$TF2" '{"opcao":"BOOST_24H","aceiteExecucaoImediata":true}' "Idempotency-Key: b0"; ok "boost com perfil incompleto" 422; show
req PATCH /freelancers/me "$TF2" '{"apresentacao":"Garçom há 5 anos, eventos e bares."}'; ok "apresentação" 200
req PUT /freelancers/me/curriculo "$TF2" '{"curriculoUrl":"https://arquivos.freelas.app/bruno.pdf"}'; ok "currículo" 200
req POST /freelancers/me/dados-recebimento "$TF2" '{"tipo":"PIX","tipoChavePix":"TELEFONE","chavePix":"+5544977776666"}'; ok "Pix do Bruno" 201
$PSQL -q -c "update conta_recebimento set liberada_saque_em = now() - interval '1 hour'"  # pula a quarentena de 24 h
req POST /freelancers/me/boosts "$TF2" '{"opcao":"BOOST_24H","aceiteExecucaoImediata":true}' "Idempotency-Key: b1"; ok "boost freelancer" 201; show 300

echo "== Admin"
TA=$(login admin@freelas.local 'Admin@12345')
req GET /admin/contestacoes "$TE"; ok "empresa no admin" 403
req GET /admin/contestacoes "$TA"; ok "contestações abertas" 200; show 300
req POST /admin/contestacoes/$CT/decisao "$TA" '{"aceita":false}'; ok "decidir contestação" 200


echo "== Disputa de pagamento (3ª freelancer)"
req POST /freelancers "" "{\"nome\":\"Carla Dias\",\"cpf\":\"390.533.447-05\",\"dataNascimento\":\"1998-03-03\",\"email\":\"carla@teste.com\",\"senha\":\"Senha123\",\"telefone\":\"44966665555\",\"endereco\":$END,\"aceiteTermos\":true}"; ok "cadastrar Carla" 201
sleep 1; req POST /autenticacao/confirmacao-email "" "{\"email\":\"carla@teste.com\",\"codigo\":\"$(codigo carla@teste.com)\"}"; ok "confirmar Carla" 200
TF3=$(login carla@teste.com Senha123)
req PUT /freelancers/me/foto "$TF3" '{"fotoUrl":"https://arquivos.freelas.app/carla.jpg"}'
req PUT /freelancers/me/funcoes "$TF3" "{\"funcoes\":[{\"funcaoId\":$FUNCAO}]}"
req POST /vagas/$V3/candidaturas "$TF3"; ok "Carla se candidata à vaga 3" 201; C4=$(echo "$BODY" | jq .id)
req POST /vagas/$V3/contratacoes "$TE" "{\"candidaturaId\":$C4}"; K3=$(echo "$BODY" | jq .id)
req POST /contratacoes/$K3/aceite "$TF3"; ok "Carla aceita" 200
req POST /contratacoes/$K3/check-in "$TF3"; req POST /contratacoes/$K3/check-out "$TF3"; ok "Carla faz check-out" 200
req GET /contratacoes/$K3/pagamento "$TE"; P3=$(echo "$BODY" | jq .id); echo "     $(echo "$BODY" | jq -c '{status,valorBruto,taxaPlataforma,valorLiquido,liberarAte}')"
req POST /pagamentos/$P3/disputa "$TE" '{"motivo":""}'; ok "disputa sem motivo" 400
req POST /pagamentos/$P3/disputa "$TE" '{"motivo":"Saiu 2 h antes do fim"}'; ok "empresa abre disputa" 200; show 300
req POST /pagamentos/$P3/disputa "$TE" '{"motivo":"de novo"}'; ok "disputa repetida" 409
req GET /admin/disputas "$TA"; ok "admin lista disputas" 200
req POST /admin/disputas/$P3/decisao "$TA" '{"favoravelAoFreelancer":true,"observacao":"Check-out registrado no horário"}'; ok "admin decide a favor da Carla" 200; show 300
req GET /freelancers/me/carteira "$TF3"; ok "saldo da Carla" 200; show

echo "== Cancelamento tardio pelo freelancer (gera ocorrência e reposição automática)"
req POST /vagas "$TE" "$VAGA"; V4=$(echo "$BODY" | jq .id); req POST /vagas/$V4/publicacao "$TE"
req POST /vagas/$V4/candidaturas "$TF"; C5=$(echo "$BODY" | jq .id); ok "Ana se candidata à vaga 4" 201; show 200
req POST /vagas/$V4/contratacoes "$TE" "{\"candidaturaId\":$C5}"; K4=$(echo "$BODY" | jq .id)
req POST /contratacoes/$K4/aceite "$TF"; ok "Ana aceita vaga 4" 200; show 200
req POST /contratacoes/$K4/cancelamento "$TF" '{"motivo":"Imprevisto familiar"}'; ok "Ana cancela com menos de 24 h" 200; show 300
req GET /freelancers/me/score "$TF"; ok "score da Ana após ocorrência" 200; show 400
req GET /contratacoes/$K4/pagamento "$TE"; ok "pagamento estornado" 200; echo "     $(echo "$BODY" | jq -c '{status}')"

echo "== Webhook assinado"
ASS=$(printf '%s' "$CORPO" | openssl dgst -sha256 -hmac segredo-webhook-desenvolvimento | awk '{print $2}')
req POST /webhooks/gateway-pagamento "" "$CORPO" "X-Assinatura: $ASS"; ok "webhook com assinatura válida" 200

echo "== Webhook"
req POST /webhooks/gateway-pagamento "" "$CORPO" "X-Assinatura: errada"; ok "webhook assinatura errada" 401
req POST /webhooks/gateway-pagamento "" '{"evento":"X"}' "X-Assinatura: a"; ok "webhook sem idTransacao" 400; show

echo "== Outros"
req GET /vagas/999999 "$TF"; ok "vaga inexistente" 404; show
req DELETE /vagas/$V "$TE"; ok "excluir vaga publicada" 409; show
req PATCH /notificacoes/999999 "$TF" '{"lida":true}'; ok "notificação inexistente" 404
req GET "/empresas/$EMP/contratacoes?status=CONCLUIDA" "$TE"; ok "contratações da empresa com filtro" 200; show 200
req GET "/empresas/$EMP/pagamentos" "$TE"; ok "pagamentos da empresa" 200
req GET "/freelancers/me/contratacoes" "$TF"; ok "minhas contratações" 200
req GET "/freelancers/$FRE" "$TE"; ok "perfil da Ana para a empresa" 200; show 400
req GET "/freelancers/me/ganhos/resumo" "$TF"; ok "ganhos" 200; show
req GET "/vagas/$V/historico" "$TE"; ok "histórico" 200
req POST /autenticacao/logout "$TF" "{\"tokenRenovacao\":\"x\"}"; echo "     logout -> $CODE"
