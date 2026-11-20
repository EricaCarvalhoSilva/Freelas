package br.com.freelas.dto;

import br.com.freelas.enums.TipoValor;
import br.com.freelas.enums.FormaPagamento;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Confere as anotações de Bean Validation dos DTOs (o que vira erro 400). */
class ValidacaoDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final EnderecoDto endereco = new EnderecoDto(null, "87020-000", "Av. Brasil", "100", null, "Centro", "Maringá", "PR");

    private Set<String> camposInvalidos(Object dto) {
        Set<ConstraintViolation<Object>> v = validator.validate(dto);
        return v.stream().map(c -> c.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void cadastroDeFreelancerValido() {
        var req = new FreelancerDto.CadastroRequest("Ana Souza", "529.982.247-25", LocalDate.of(1995, 5, 10),
                "ana@teste.com", "Senha123", "44988887777", endereco, true);
        assertTrue(camposInvalidos(req).isEmpty());
    }

    @Test
    void cadastroDeFreelancerComErros() {
        var req = new FreelancerDto.CadastroRequest("", "111.111.111-11", LocalDate.now().plusDays(1),
                "nao-e-email", "curta", "1", endereco, false);
        assertEquals(Set.of("nome", "cpf", "dataNascimento", "email", "senha", "telefone", "aceiteTermos"), camposInvalidos(req));
    }

    @Test
    void enderecoComCepEUfInvalidos() {
        var e = new EnderecoDto(null, "123", "Rua", "1", null, "Centro", "Maringá", "PRR");
        assertEquals(Set.of("cep", "uf"), camposInvalidos(e));
    }

    @Test
    void vagaComFimAntesDoInicio() {
        LocalDateTime inicio = LocalDateTime.now().plusDays(2);
        var req = new VagaDto.CriarRequest(1, null, null, 1, inicio, inicio.minusHours(1), new BigDecimal("150"),
                TipoValor.TURNO, false, null, FormaPagamento.PIX, null);
        assertTrue(camposInvalidos(req).contains("periodoValido"));
    }

    @Test
    void notaDaAvaliacaoVaiDeZeroACinco() {
        assertTrue(camposInvalidos(new AvaliacaoDto.Request(new BigDecimal("5.0"), null, null)).isEmpty());
        assertEquals(Set.of("nota"), camposInvalidos(new AvaliacaoDto.Request(new BigDecimal("5.5"), null, null)));
        assertEquals(Set.of("nota"), camposInvalidos(new AvaliacaoDto.Request(new BigDecimal("-1"), null, null)));
    }
}
