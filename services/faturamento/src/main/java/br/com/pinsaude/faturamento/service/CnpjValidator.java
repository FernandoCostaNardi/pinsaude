package br.com.pinsaude.faturamento.service;

public class CnpjValidator {

    private static final int[] FIRST_WEIGHTS  = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private CnpjValidator() {}

    public static boolean isValid(String cnpj) {
        if (cnpj == null) return false;
        String digits = cnpj.replaceAll("\\D", "");
        if (digits.length() != 14) return false;
        if (digits.chars().distinct().count() == 1) return false;

        int d1 = calcDigit(digits, FIRST_WEIGHTS);
        int d2 = calcDigit(digits, SECOND_WEIGHTS);

        return (digits.charAt(12) - '0') == d1
            && (digits.charAt(13) - '0') == d2;
    }

    private static int calcDigit(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        int rem = sum % 11;
        return rem < 2 ? 0 : 11 - rem;
    }
}
