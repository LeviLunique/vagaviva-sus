package br.com.vagaviva.support;

import java.util.concurrent.ThreadLocalRandom;

/** Gera CNS provisórios e CPFs fictícios válidos (algoritmos oficiais) para testes que exigem unicidade. */
public final class TestDocuments {

    private TestDocuments() {
    }

    /** CNS provisório (começa com 7): 14 dígitos aleatórios + dígito que torna a soma ponderada múltipla de 11. */
    public static String randomCns() {
        while (true) {
            StringBuilder cns = new StringBuilder("7");
            int sum = 7 * 15;
            for (int i = 1; i < 14; i++) {
                int digit = ThreadLocalRandom.current().nextInt(10);
                cns.append(digit);
                sum += digit * (15 - i);
            }
            int last = (11 - sum % 11) % 11;
            if (last < 10) {
                return cns.append(last).toString();
            }
        }
    }

    public static String randomCpf() {
        int[] digits = new int[11];
        do {
            for (int i = 0; i < 9; i++) {
                digits[i] = ThreadLocalRandom.current().nextInt(10);
            }
        } while (digits[0] == digits[1] && digits[1] == digits[2]);
        digits[9] = check(digits, 9);
        digits[10] = check(digits, 10);
        StringBuilder cpf = new StringBuilder();
        for (int digit : digits) {
            cpf.append(digit);
        }
        return cpf.toString();
    }

    private static int check(int[] digits, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digits[i] * (length + 1 - i);
        }
        int digit = (sum * 10) % 11;
        return digit == 10 ? 0 : digit;
    }
}
