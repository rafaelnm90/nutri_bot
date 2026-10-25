package org.json;

/** Leitor de JSON e conversões dos métodos opt*, como no org.json real. */
final class Leitor {
    private final String s;
    private int p = 0;

    Leitor(String fonte) {
        if (fonte == null) throw new JSONException("nulo");
        s = fonte;
    }

    /** Lê o primeiro valor do texto; o que vem depois é ignorado, como no org.json. */
    Object valor() {
        espacos();
        if (p >= s.length()) throw new JSONException("fim do texto");
        char c = s.charAt(p);
        if (c == '{') return objeto();
        if (c == '[') return lista();
        if (c == '"' || c == '\'') return texto(c);
        return solto();
    }

    private void espacos() {
        while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++;
    }

    private char proximo() {
        espacos();
        if (p >= s.length()) throw new JSONException("fim do texto");
        return s.charAt(p);
    }

    private JSONObject objeto() {
        JSONObject o = new JSONObject();
        p++;
        if (proximo() == '}') { p++; return o; }
        while (true) {
            char c = proximo();
            if (c != '"' && c != '\'') throw new JSONException("chave sem aspas na posição " + p);
            String chave = texto(c);
            if (proximo() != ':') throw new JSONException("faltou ':' na posição " + p);
            p++;
            o.mapa.put(chave, valor());
            char d = proximo();
            p++;
            if (d == '}') return o;
            if (d != ',') throw new JSONException("faltou ',' ou '}' na posição " + p);
        }
    }

    private JSONArray lista() {
        JSONArray a = new JSONArray();
        p++;
        if (proximo() == ']') { p++; return a; }
        while (true) {
            a.lista.add(valor());
            char d = proximo();
            p++;
            if (d == ']') return a;
            if (d != ',') throw new JSONException("faltou ',' ou ']' na posição " + p);
        }
    }

    private String texto(char aspa) {
        StringBuilder sb = new StringBuilder();
        p++;
        while (true) {
            if (p >= s.length()) throw new JSONException("texto sem fim");
            char c = s.charAt(p++);
            if (c == aspa) return sb.toString();
            if (c == '\\') {
                char e = s.charAt(p++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u': sb.append((char) Integer.parseInt(s.substring(p, p + 4), 16)); p += 4; break;
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Object solto() {
        int inicio = p;
        while (p < s.length() && ",:]}/\\\"[{;=#".indexOf(s.charAt(p)) < 0 && !Character.isWhitespace(s.charAt(p))) p++;
        String t = s.substring(inicio, p);
        if (t.isEmpty()) throw new JSONException("valor vazio na posição " + p);
        if (t.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (t.equalsIgnoreCase("false")) return Boolean.FALSE;
        if (t.equalsIgnoreCase("null")) return JSONObject.NULL;
        Number n = numero(t);
        return n != null ? n : t;
    }

    static Number numero(String t) {
        try {
            if (t.matches("-?\\d+")) {
                long l = Long.parseLong(t);
                return l == (int) l ? (Number) (int) l : (Number) l;
            }
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Number comoNumero(Object v) {
        if (v == null || v == JSONObject.NULL) return null;
        if (v instanceof Number) return (Number) v;
        if (v instanceof String) return numero(((String) v).trim());
        return null;
    }

    static String comoTexto(Object v, String padrao) {
        return v == null || v == JSONObject.NULL ? padrao : v.toString();
    }

    static boolean comoBooleano(Object v, boolean padrao) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) {
            if (((String) v).equalsIgnoreCase("true")) return true;
            if (((String) v).equalsIgnoreCase("false")) return false;
        }
        return padrao;
    }

    static String aspas(String t) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : t.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c == '\n') sb.append("\\n");
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }

    static String escrever(Object v) {
        if (v instanceof String) return aspas((String) v);
        return String.valueOf(v);
    }
}
