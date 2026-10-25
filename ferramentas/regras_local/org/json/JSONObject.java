package org.json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Imitação mínima do org.json só para compilar e testar as regras do app nesta
 * máquina (o Maven Central está bloqueado). Segue a semântica dos métodos opt*
 * do org.json real: chave ausente ou null devolve o padrão; texto numérico
 * vale como número.
 */
public class JSONObject {
    public static final Object NULL = new Object() {
        @Override public String toString() { return "null"; }
    };

    final Map<String, Object> mapa = new LinkedHashMap<>();

    public JSONObject() {}

    public JSONObject(String fonte) {
        Object v = new Leitor(fonte).valor();
        if (!(v instanceof JSONObject)) throw new JSONException("não é objeto");
        mapa.putAll(((JSONObject) v).mapa);
    }

    public JSONObject put(String k, Object v) { mapa.put(k, v == null ? NULL : v); return this; }
    public JSONObject put(String k, double v) { mapa.put(k, v); return this; }
    public JSONObject put(String k, int v) { mapa.put(k, v); return this; }
    public JSONObject put(String k, long v) { mapa.put(k, v); return this; }
    public JSONObject put(String k, boolean v) { mapa.put(k, v); return this; }

    public Object opt(String k) { return mapa.get(k); }
    public boolean has(String k) { return mapa.containsKey(k); }
    public int length() { return mapa.size(); }

    public String optString(String k) { return optString(k, ""); }
    public String optString(String k, String padrao) { return Leitor.comoTexto(opt(k), padrao); }
    public double optDouble(String k) { return optDouble(k, Double.NaN); }
    public double optDouble(String k, double padrao) { Number n = Leitor.comoNumero(opt(k)); return n == null ? padrao : n.doubleValue(); }
    public int optInt(String k) { return optInt(k, 0); }
    public int optInt(String k, int padrao) { Number n = Leitor.comoNumero(opt(k)); return n == null ? padrao : n.intValue(); }
    public boolean optBoolean(String k) { return optBoolean(k, false); }
    public boolean optBoolean(String k, boolean padrao) { return Leitor.comoBooleano(opt(k), padrao); }
    public JSONObject optJSONObject(String k) { Object v = opt(k); return v instanceof JSONObject ? (JSONObject) v : null; }
    public JSONArray optJSONArray(String k) { Object v = opt(k); return v instanceof JSONArray ? (JSONArray) v : null; }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean primeiro = true;
        for (Map.Entry<String, Object> e : mapa.entrySet()) {
            if (!primeiro) sb.append(',');
            primeiro = false;
            sb.append(Leitor.aspas(e.getKey())).append(':').append(Leitor.escrever(e.getValue()));
        }
        return sb.append('}').toString();
    }
}
