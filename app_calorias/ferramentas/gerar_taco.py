"""
Gera o app/src/main/assets/taco.json a partir da planilha oficial da TACO
(Tabela Brasileira de Composição de Alimentos, 4ª edição, NEPA/UNICAMP).

Roda no GitHub Actions (workflow taco.yml), que tem acesso ao site da UNICAMP:
procura a planilha nos endereços conhecidos e nas páginas do NEPA, junta as abas
pelo número do alimento e grava, por alimento, nome, categoria, kcal, proteína,
carboidrato, gordura, fibra, sódio, cálcio, ferro, zinco, potássio e vitaminas A
e C de 100 g (minerais e vitamina C em mg, vitamina A em mcg).

    python ferramentas/gerar_taco.py SAIDA.json
"""
import io
import json
import os
import re
import sys
import unicodedata
from urllib.parse import urljoin, urlparse

import pandas as pd
import requests

AGENTE = {"User-Agent": "Mozilla/5.0 (app Calorias; gerar tabela TACO)"}

# Endereços em que a planilha já esteve publicada. O site muda de tempos em
# tempos; se nenhum responder, as páginas abaixo são lidas atrás do link novo.
PLANILHAS_CONHECIDAS = [
    "https://www.nepa.unicamp.br/wp-content/uploads/sites/27/2023/10/Taco-4a-Edicao.xlsx",
    "https://www.nepa.unicamp.br/wp-content/uploads/sites/27/2023/10/Taco_4a_edicao_2011_TACO.xls",
    "http://www.nepa.unicamp.br/taco/contar/Taco_4a_edicao_2011_TACO.xls?arquivo=Taco_4a_edicao_2011_TACO.xls",
    "https://www.nepa.unicamp.br/taco/contar/Taco_4a_edicao_2011_TACO.xls?arquivo=Taco_4a_edicao_2011_TACO.xls",
]
PAGINAS = [
    "https://www.nepa.unicamp.br/",
    "https://www.nepa.unicamp.br/publicacoes/tabela-taco-excel/",
    "https://www.nepa.unicamp.br/publicacoes/",
    "https://www.nepa.unicamp.br/taco/",
    "https://www.nepa.unicamp.br/taco/tabela.php?ativo=tabela",
]
MAX_PAGINAS = 40


def sem_acento(texto):
    return unicodedata.normalize("NFD", str(texto)).encode("ascii", "ignore").decode().lower()


def baixar(url):
    try:
        r = requests.get(url, headers=AGENTE, timeout=60)
    except requests.RequestException as e:
        print(f"  {url} -> falhou ({type(e).__name__})")
        return None
    print(f"  {url} -> {r.status_code}, {len(r.content)} bytes, {r.headers.get('content-type', '?')}")
    return r if r.status_code == 200 else None


def parece_planilha(conteudo):
    # .xls começa com o cabeçalho OLE; .xlsx é um zip.
    return conteudo[:8] == b"\xd0\xcf\x11\xe0\xa1\xb1\x1a\xe1" or conteudo[:2] == b"PK"


def achar_planilhas():
    """Devolve o conteúdo de cada planilha encontrada, na ordem em que apareceu."""
    print("Endereços conhecidos:")
    for url in PLANILHAS_CONHECIDAS:
        r = baixar(url)
        if r is not None and parece_planilha(r.content):
            yield url, r.content

    print("Procurando o link nas páginas do NEPA:")
    vistas, fila = set(), list(PAGINAS)
    while fila and len(vistas) < MAX_PAGINAS:
        pagina = fila.pop(0)
        if pagina in vistas:
            continue
        vistas.add(pagina)
        r = baixar(pagina)
        if r is None or "html" not in r.headers.get("content-type", ""):
            continue
        for href in re.findall(r'href=["\']([^"\']+)["\']', r.text, re.IGNORECASE):
            link = urljoin(pagina, href)
            if "nepa.unicamp.br" not in urlparse(link).netloc:
                continue
            nome = sem_acento(link)
            if re.search(r"\.xlsx?($|\?)", nome) and "taco" in nome:
                print(f"  link de planilha: {link}")
                p = baixar(link)
                if p is not None and parece_planilha(p.content):
                    yield link, p.content
            elif "taco" in nome and link not in vistas:
                fila.append(link)


def numero(valor):
    """Número da célula; "Tr" (traço), "NA" (não analisado), "*" e vazio viram 0."""
    if isinstance(valor, (int, float)) and not pd.isna(valor):
        return float(valor)
    texto = str(valor).strip().replace(",", ".")
    try:
        return float(texto)
    except ValueError:
        return 0.0


# Coluna da planilha -> campo do taco.json. Cada padrão é procurado no título da
# coluna sem acento; a vitamina A usa o RAE e, sem ele, o RE ou o retinol.
COLUNAS = {
    "kcal": [r"energia.*kcal"],
    "proteina": [r"\bprote"],
    "gordura": [r"\blipid"],
    "carboidrato": [r"\bcarboid"],
    "fibra": [r"\bfibra"],
    "sodio": [r"\bsodio\b"],
    "calcio": [r"\bcalcio\b"],
    "ferro": [r"\bferro\b"],
    "zinco": [r"\bzinco\b"],
    "potassio": [r"\bpotassio\b"],
    "vitamina_c": [r"vitamina c\b"],
    "vitamina_a": [r"\brae\b", r"\bre\b", r"\bretinol\b"],
}
CASAS = {"kcal": 1, "sodio": 1, "calcio": 1, "potassio": 1, "vitamina_a": 1}


def ler_aba(tabela):
    """
    Alimentos de uma aba, por número do alimento: {numero: {"nome", "categoria",
    e os campos de COLUNAS que a aba tiver}}. Vazio se a aba não tem a coluna de
    descrição dos alimentos.
    """
    linhas = tabela.fillna("").astype(object).values.tolist()
    for i, linha in enumerate(linhas[:30]):
        if not any("descri" in sem_acento(c) for c in linha):
            continue
        # O cabeçalho ocupa até 3 linhas (ex.: "Energia" em cima e "(kcal)" embaixo).
        largura = max(len(l) for l in linhas[i:i + 3])
        titulos = [
            " ".join(sem_acento(l[c]) for l in linhas[i:i + 3] if c < len(l) and str(l[c]).strip())
            for c in range(largura)
        ]
        print(f"    cabeçalho na linha {i}: {titulos}")

        def coluna(padroes):
            for padrao in padroes:
                for c, t in enumerate(titulos):
                    if re.search(padrao, t):
                        return c
            return None

        col_numero = coluna([r"\bnumero\b"])
        col_nome = coluna([r"descri"])
        if col_nome is None:
            return {}
        if col_numero is None:
            col_numero = 0
        campos = {campo: coluna(padroes) for campo, padroes in COLUNAS.items()}
        campos = {k: v for k, v in campos.items() if v is not None}
        print(f"    colunas: numero={col_numero}, nome={col_nome}, {campos}")

        alimentos, categoria = {}, None
        for l in linhas[i + 1:]:
            if len(l) <= max([col_numero, col_nome] + list(campos.values())):
                continue
            ident = numero(l[col_numero])
            if ident <= 0:
                # Linha com um texto só: o título da categoria dos alimentos que vêm a seguir.
                textos = [str(c).strip() for c in l if str(c).strip()]
                if len(textos) == 1 and numero(textos[0]) == 0 and not any(
                    p in sem_acento(textos[0]) for p in ("descri", "energia", "tabela", "numero")
                ):
                    categoria = re.sub(r"\s+", " ", textos[0])
                continue
            nome = re.sub(r"\s+", " ", str(l[col_nome])).strip()
            if not nome:
                continue
            item = {"nome": nome, "categoria": categoria}
            for campo, c in campos.items():
                item[campo] = round(numero(l[c]), CASAS.get(campo, 2))
            alimentos.setdefault(int(ident), item)
        return alimentos
    return {}


def ler_planilha(conteudo):
    """Junta as abas pelo número do alimento: a primeira que traz um campo é a que vale."""
    abas = pd.read_excel(io.BytesIO(conteudo), sheet_name=None, header=None)
    juntos = {}
    for nome, tabela in abas.items():
        print(f"  aba '{nome}': {tabela.shape[0]} linhas x {tabela.shape[1]} colunas")
        da_aba = ler_aba(tabela)
        print(f"    {len(da_aba)} alimentos")
        for ident, item in da_aba.items():
            atual = juntos.setdefault(ident, {})
            for campo, valor in item.items():
                if campo not in atual or (campo == "categoria" and not atual[campo]):
                    atual[campo] = valor
    alimentos = [juntos[i] for i in sorted(juntos) if "kcal" in juntos[i]]
    return alimentos if len(alimentos) >= 300 else []


def main():
    saida = sys.argv[1]
    for url, conteudo in achar_planilhas():
        print(f"Lendo {url}")
        alimentos = ler_planilha(conteudo)
        if alimentos:
            # Nome repetido (cabeçalho de página, aba duplicada): fica o primeiro.
            vistos, unicos = set(), []
            for a in alimentos:
                if a["nome"] not in vistos:
                    vistos.add(a["nome"])
                    unicos.append(a)
            # A pasta assets não existe no checkout enquanto não há arquivo nela.
            os.makedirs(os.path.dirname(saida) or ".", exist_ok=True)
            with open(saida, "w", encoding="utf-8") as f:
                json.dump(unicos, f, ensure_ascii=False, indent=0)
            categorias = sorted({a["categoria"] or "-" for a in unicos})
            print(f"OK: {len(unicos)} alimentos, {len(categorias)} categorias -> {saida}")
            for campo in COLUNAS:
                print(f"  {campo}: {sum(1 for a in unicos if a.get(campo))} alimentos com valor")
            print("Categorias:", categorias)
            for a in unicos[:5] + unicos[-3:]:
                print("  exemplo:", a)
            return 0
    print("Nenhuma planilha da TACO encontrada ou lida.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
