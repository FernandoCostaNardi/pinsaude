package br.com.pinsaude.onboarding.service;

public class CpfValidator {

    private static final int[] FIRST_WEIGHTS  = {10, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_WEIGHTS = {11, 10, 9, 8, 7, 6, 5, 4, 3, 2};

    private CpfValidator() {}

    public static boolean isValid(String cpf) {
        if (cpf == null) return false;
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() != 11) return false;
        if (digits.chars().distinct().count() == 1) return false;

        int d1 = calcDigit(digits.substring(0, 9), FIRST_WEIGHTS);
        int d2 = calcDigit(digits.substring(0, 10), SECOND_WEIGHTS);

        return (digits.charAt(9) - '0') == d1 && (digits.charAt(10) - '0') == d2;
    }

    private static int calcDigit(String cpfPart, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (cpfPart.charAt(i) - '0') * weights[i];
        }
        int rem = (sum * 10) % 11;
        return rem == 10 || rem == 11 ? 0 : rem;
    }
}
