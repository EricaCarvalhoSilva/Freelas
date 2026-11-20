package br.com.freelas.excecao;

import br.com.freelas.excecao.ErroResposta.CampoInvalido;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tratamento centralizado dos erros. Toda resposta de erro sai no formato {@link ErroResposta}.
 *
 * <ul>
 *   <li>400 - erro de validação (Bean Validation), JSON malformado, parâmetro ou cabeçalho ausente</li>
 *   <li>401 - não autenticado</li>
 *   <li>403 - sem permissão (perfil errado ou recurso de outra conta)</li>
 *   <li>404 - recurso ou rota inexistente</li>
 *   <li>409 - conflito com o estado atual (duplicidade, vaga já preenchida...)</li>
 *   <li>422 - regra de negócio impede a operação</li>
 *   <li>500 - erro inesperado (detalhe só no log do servidor)</li>
 * </ul>
 */
@RestControllerAdvice
public class TratadorGlobalDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorGlobalDeErros.class);

    /** Nomes das restrições UNIQUE do banco traduzidos para mensagens de 409. */
    private static final Map<String, String> MENSAGENS_UNIQUE = Map.ofEntries(
            Map.entry("usuario_email_key", "Já existe uma conta com este e-mail."),
            Map.entry("freelancer_cpf_key", "Já existe um freelancer com este CPF."),
            Map.entry("empresa_cnpj_key", "Já existe uma empresa com este CNPJ."),
            Map.entry("candidatura_vaga_id_freelancer_id_key", "Você já se candidatou a esta vaga."),
            Map.entry("avaliacao_contratacao_id_direcao_key", "Esta contratação já foi avaliada por você."),
            Map.entry("contestacao_avaliacao_id_key", "Esta avaliação já tem uma contestação."),
            Map.entry("pagamento_contratacao_id_key", "Esta contratação já tem pagamento."),
            Map.entry("pagamento_chave_idempotencia_key", "Cobrança já processada com esta Idempotency-Key."),
            Map.entry("dispositivo_push_token_push_key", "Este dispositivo já está registrado."),
            Map.entry("uq_mensagem_id_cliente", "Mensagem já enviada.")
    );

    // ---------------------------------------------------------------- 400

    /** @Valid em @RequestBody: lista cada campo inválido. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> validacaoCorpo(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<CampoInvalido> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(this::campo).toList();
        List<String> gerais = ex.getBindingResult().getGlobalErrors().stream()
                .map(e -> e.getDefaultMessage()).toList();
        return erroValidacao(req, campos, gerais);
    }

    /** Restrições em parâmetros do método (@RequestParam @Min, @PathVariable...). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErroResposta> validacaoParametros(HandlerMethodValidationException ex, HttpServletRequest req) {
        List<CampoInvalido> campos = new ArrayList<>();
        ex.getParameterValidationResults().forEach(resultado -> {
            if (resultado instanceof ParameterErrors erros) {
                erros.getFieldErrors().forEach(fe -> campos.add(campo(fe)));
            } else {
                String nome = resultado.getMethodParameter().getParameterName();
                resultado.getResolvableErrors().forEach(e ->
                        campos.add(new CampoInvalido(nome, e.getDefaultMessage(), resultado.getArgument())));
            }
        });
        return erroValidacao(req, campos, List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErroResposta> validacaoMetodo(ConstraintViolationException ex, HttpServletRequest req) {
        List<CampoInvalido> campos = ex.getConstraintViolations().stream()
                .map(v -> new CampoInvalido(ultimoTrecho(v.getPropertyPath().toString()), v.getMessage(), v.getInvalidValue()))
                .toList();
        return erroValidacao(req, campos, List.of());
    }

    /** JSON malformado, tipo errado ou valor fora da enumeração. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResposta> jsonInvalido(HttpMessageNotReadableException ex, HttpServletRequest req) {
        Throwable causa = ex.getCause();
        if (causa instanceof UnrecognizedPropertyException upe) {
            return erro(HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "O campo '" + upe.getPropertyName() + "' não existe neste recurso.", req);
        }
        if (causa instanceof InvalidFormatException ife) {
            String campo = caminho(ife);
            String mensagem = "Valor inválido";
            if (ife.getTargetType() != null && ife.getTargetType().isEnum()) {
                mensagem = "Valor inválido. Use um destes: " + Arrays.stream(ife.getTargetType().getEnumConstants())
                        .map(Object::toString).collect(Collectors.joining(", "));
            } else if (ife.getTargetType() != null) {
                mensagem = "Valor inválido para o tipo " + nomeTipo(ife.getTargetType());
            }
            return erroValidacao(req, List.of(new CampoInvalido(campo, mensagem, ife.getValue())), List.of());
        }
        if (causa instanceof DatabindException de && !de.getPath().isEmpty()) {
            return erroValidacao(req, List.of(new CampoInvalido(caminho(de), "Formato inválido para este campo", null)), List.of());
        }
        if (causa instanceof JacksonException) {
            return erro(HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "O corpo da requisição não é um JSON válido.", req);
        }
        return erro(HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "O corpo da requisição está ausente ou ilegível.", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResposta> tipoParametro(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        Class<?> tipo = ex.getRequiredType();
        String mensagem = tipo != null && tipo.isEnum()
                ? "Valor inválido. Use um destes: " + Arrays.stream(tipo.getEnumConstants()).map(Object::toString).collect(Collectors.joining(", "))
                : "Valor inválido para o tipo " + (tipo == null ? "esperado" : nomeTipo(tipo));
        return erroValidacao(req, List.of(new CampoInvalido(ex.getName(), mensagem, ex.getValue())), List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErroResposta> parametroAusente(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return erroValidacao(req, List.of(new CampoInvalido(ex.getParameterName(), "Parâmetro obrigatório", null)), List.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErroResposta> cabecalhoAusente(MissingRequestHeaderException ex, HttpServletRequest req) {
        return erroValidacao(req, List.of(new CampoInvalido(ex.getHeaderName(), "Cabeçalho obrigatório", null)), List.of());
    }

    // ---------------------------------------------------------------- 401 / 403

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResposta> naoAutenticado(AuthenticationException ex, HttpServletRequest req) {
        return erro(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Faça login para continuar.", req);
    }

    /** @PreAuthorize recusou: o perfil do token não pode usar este endpoint. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResposta> acessoNegado(AccessDeniedException ex, HttpServletRequest req) {
        return erro(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Seu perfil não tem permissão para esta operação.", req);
    }

    // ---------------------------------------------------------------- 404 / 405 / 415

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResposta> rotaInexistente(NoResourceFoundException ex, HttpServletRequest req) {
        return erro(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Rota não encontrada.", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResposta> metodo(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return erro(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NAO_PERMITIDO",
                "Método " + ex.getMethod() + " não é aceito nesta rota.", req);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroResposta> midia(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return erro(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_MIDIA_NAO_SUPORTADO",
                "Envie o corpo como application/json.", req);
    }

    // ---------------------------------------------------------------- exceções do negócio (404, 409, 422...)

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErroResposta> negocio(ApiException ex, HttpServletRequest req) {
        ErroResposta corpo = new ErroResposta(LocalDateTime.now(), ex.getStatus().value(), ex.getCodigo(),
                ex.getMessage(), req.getRequestURI(), List.of(), ex.getMotivos());
        return ResponseEntity.status(ex.getStatus()).body(corpo);
    }

    // ---------------------------------------------------------------- banco de dados

    /** Última barreira: restrição do banco (UNIQUE, FK, CHECK) que escapou da validação do serviço. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResposta> integridade(DataIntegrityViolationException ex, HttpServletRequest req) {
        String restricao = null;
        String sqlState = null;
        Throwable t = ex;
        while (t != null) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve) {
                restricao = cve.getConstraintName();
                sqlState = cve.getSQLState();
                break;
            }
            t = t.getCause();
        }
        log.warn("Violação de integridade em {} {}: restrição={} sqlState={} causa={}", req.getMethod(), req.getRequestURI(),
                restricao, sqlState, ex.getMostSpecificCause().getMessage());
        if ("23514".equals(sqlState)) { // CHECK
            return erro(HttpStatus.UNPROCESSABLE_CONTENT, "REGRA_NEGOCIO",
                    "Os dados violam uma regra do banco (" + restricao + ").", req);
        }
        if ("23503".equals(sqlState)) { // FOREIGN KEY
            return erro(HttpStatus.CONFLICT, "CONFLITO",
                    "O registro está ligado a outro que não existe ou ainda está em uso.", req);
        }
        String mensagem = restricao != null && MENSAGENS_UNIQUE.containsKey(restricao)
                ? MENSAGENS_UNIQUE.get(restricao)
                : "Registro duplicado ou em conflito com outro já existente.";
        return erro(HttpStatus.CONFLICT, "CONFLITO", mensagem, req);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ErroResposta> concorrencia(RuntimeException ex, HttpServletRequest req) {
        return erro(HttpStatus.CONFLICT, "CONFLITO",
                "O registro foi alterado por outra operação ao mesmo tempo. Tente de novo.", req);
    }

    // ---------------------------------------------------------------- 500

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> inesperado(Exception ex, HttpServletRequest req) {
        log.error("Erro inesperado em {} {}", req.getMethod(), req.getRequestURI(), ex);
        return erro(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO",
                "Erro inesperado. Tente novamente mais tarde.", req);
    }

    // ---------------------------------------------------------------- auxiliares

    private ResponseEntity<ErroResposta> erroValidacao(HttpServletRequest req, List<CampoInvalido> campos, List<String> gerais) {
        ErroResposta corpo = new ErroResposta(LocalDateTime.now(), 400, "VALIDACAO",
                "Um ou mais campos estão inválidos.", req.getRequestURI(),
                campos.stream().sorted(java.util.Comparator.comparing(CampoInvalido::campo)).toList(), gerais);
        return ResponseEntity.badRequest().body(corpo);
    }

    private ResponseEntity<ErroResposta> erro(HttpStatus status, String codigo, String mensagem, HttpServletRequest req) {
        return ResponseEntity.status(status).body(ErroResposta.de(status.value(), codigo, mensagem, req.getRequestURI()));
    }

    private CampoInvalido campo(FieldError fe) {
        Object valor = fe.getRejectedValue();
        // Nunca devolver senha no corpo do erro
        if (fe.getField().toLowerCase().contains("senha")) valor = null;
        return new CampoInvalido(fe.getField(), fe.getDefaultMessage(), valor);
    }

    private static String caminho(DatabindException ex) {
        return ex.getPath().stream()
                .map(r -> r.getPropertyName() != null ? r.getPropertyName() : "[" + r.getIndex() + "]")
                .collect(Collectors.joining(".")).replace(".[", "[");
    }

    private static String ultimoTrecho(String caminho) {
        int i = caminho.lastIndexOf('.');
        return i >= 0 ? caminho.substring(i + 1) : caminho;
    }

    private static String nomeTipo(Class<?> tipo) {
        return switch (tipo.getSimpleName()) {
            case "Long", "Integer", "Short", "long", "int", "short" -> "número inteiro";
            case "BigDecimal", "Double", "double" -> "número decimal";
            case "LocalDateTime" -> "data e hora (AAAA-MM-DDTHH:MM:SS)";
            case "LocalDate" -> "data (AAAA-MM-DD)";
            case "Boolean", "boolean" -> "verdadeiro/falso (true/false)";
            default -> tipo.getSimpleName();
        };
    }
}
