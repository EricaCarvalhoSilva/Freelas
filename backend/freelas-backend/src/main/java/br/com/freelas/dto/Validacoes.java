package br.com.freelas.dto;

/** Expressões regulares reaproveitadas nas validações dos DTOs. */
public final class Validacoes {
    private Validacoes() {}

    public static final String SENHA = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String SENHA_MSG = "A senha precisa ter de 8 a 72 caracteres, com letras e números";

    public static final String TELEFONE = "^[0-9()+\\- ]{10,20}$";
    public static final String TELEFONE_MSG = "Telefone inválido (use DDD + número, ex.: 44999990000)";

    public static final String CODIGO = "^\\d{6}$";
    public static final String CODIGO_MSG = "O código tem 6 dígitos";

    public static String soDigitos(String valor) {
        return valor == null ? null : valor.replaceAll("\\D", "");
    }
}
