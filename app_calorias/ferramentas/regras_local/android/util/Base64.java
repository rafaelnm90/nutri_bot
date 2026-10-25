package android.util;

/** Imitação do android.util.Base64 só para o Internet.kt compilar fora do Android. */
public class Base64 {
    public static final int NO_WRAP = 2;

    public static String encodeToString(byte[] dados, int opcoes) {
        return java.util.Base64.getEncoder().encodeToString(dados);
    }
}
