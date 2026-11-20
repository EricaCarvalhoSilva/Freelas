package br.com.freelas.dto;

import br.com.freelas.entidade.Endereco;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Endereço usado nos cadastros e nas vagas (entrada e saída). */
public record EnderecoDto(
        Long id,
        @NotBlank @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP deve ter 8 dígitos (ex.: 87020-000)") String cep,
        @NotBlank @Size(max = 150) String logradouro,
        @NotBlank @Size(max = 10) String numero,
        @Size(max = 60) String complemento,
        @NotBlank @Size(max = 80) String bairro,
        @NotBlank @Size(max = 80) String cidade,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "UF deve ter 2 letras (ex.: PR)") String uf
) {
    public static EnderecoDto de(Endereco e) {
        if (e == null) return null;
        return new EnderecoDto(e.getId(), e.getCep(), e.getLogradouro(), e.getNumero(), e.getComplemento(),
                e.getBairro(), e.getCidade(), e.getUf());
    }

    public Endereco paraEntidade() {
        return copiarPara(new Endereco());
    }

    public Endereco copiarPara(Endereco e) {
        e.setCep(cep.replace("-", ""));
        e.setLogradouro(logradouro.trim());
        e.setNumero(numero.trim());
        e.setComplemento(complemento);
        e.setBairro(bairro.trim());
        e.setCidade(cidade.trim());
        e.setUf(uf.toUpperCase());
        return e;
    }
}
