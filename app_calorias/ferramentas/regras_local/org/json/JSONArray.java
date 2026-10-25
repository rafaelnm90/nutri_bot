package org.json;

import java.util.ArrayList;
import java.util.List;

/** Imitação mínima do org.json (ver JSONObject). */
public class JSONArray {
    final List<Object> lista = new ArrayList<>();

    public JSONArray() {}

    public JSONArray(String fonte) {
        Object v = new Leitor(fonte).valor();
        if (!(v instanceof JSONArray)) throw new JSONException("não é lista");
        lista.addAll(((JSONArray) v).lista);
    }

    public JSONArray put(Object v) { lista.add(v == null ? JSONObject.NULL : v); return this; }
    public JSONArray put(double v) { lista.add(v); return this; }
    public JSONArray put(int v) { lista.add(v); return this; }
    public JSONArray put(boolean v) { lista.add(v); return this; }

    public int length() { return lista.size(); }
    public Object opt(int i) { return i >= 0 && i < lista.size() ? lista.get(i) : null; }

    public String optString(int i) { return optString(i, ""); }
    public String optString(int i, String padrao) { return Leitor.comoTexto(opt(i), padrao); }
    public double optDouble(int i) { return optDouble(i, Double.NaN); }
    public double optDouble(int i, double padrao) { Number n = Leitor.comoNumero(opt(i)); return n == null ? padrao : n.doubleValue(); }
    public JSONObject optJSONObject(int i) { Object v = opt(i); return v instanceof JSONObject ? (JSONObject) v : null; }
    public JSONArray optJSONArray(int i) { Object v = opt(i); return v instanceof JSONArray ? (JSONArray) v : null; }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lista.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(Leitor.escrever(lista.get(i)));
        }
        return sb.append(']').toString();
    }
}
