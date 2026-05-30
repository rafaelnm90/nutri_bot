import os
import ast
from dotenv import load_dotenv
from google import genai

def extrair_modelos_do_bot():
    diretorio_atual = os.path.dirname(os.path.abspath(__file__))
    caminho_ai_service = os.path.join(diretorio_atual, "ai_service.py")
    
    try:
        with open(caminho_ai_service, "r", encoding="utf-8") as f:
            codigo_fonte = f.read()
        
        arvore = ast.parse(codigo_fonte)
        for no in ast.walk(arvore):
            if isinstance(no, ast.Assign):
                for alvo in no.targets:
                    if getattr(alvo, "id", "") == "MODELOS_CASCATA":
                        if isinstance(no.value, ast.List):
                            return [item.value for item in no.value.elts]
    except Exception as e:
        print(f"⚠️ Erro ao ler ai_service.py: {e}")
    
    return []

# 1. Extração dinâmica da matriz no bot de nutrição
modelos_disponiveis = extrair_modelos_do_bot()

if not modelos_disponiveis:
    print("❌ Nenhum modelo encontrado. Verifique se a variável 'MODELOS_CASCATA' existe no ai_service.py.")
    exit()

print(f"📂 Lista sincronizada! Encontrados {len(modelos_disponiveis)} modelos na cascata do NutriBot.")

# 2. Resolução da Chave de API (Adaptação para o modelo BYOK)
load_dotenv()
api_key = os.getenv('GEMINI_KEY')

if not api_key:
    print("⚠️ A chave 'GEMINI_KEY' não foi encontrada no ficheiro .env do servidor.")
    api_key = input("🔑 Cole temporariamente uma chave de API do Google para rodar este teste: ").strip()

if not api_key:
    print("❌ Chave não fornecida. Encerrando o diagnóstico.")
    exit()

print("\n🔍 Iniciando varredura no catálogo da API...")

# 3. Execução do teste
client = genai.Client(api_key=api_key)

for modelo in modelos_disponiveis:
    try:
        response = client.models.generate_content(
            model=modelo,
            contents="Responda apenas a palavra 'teste'."
        )
        if response.text:
            print(f"🟢 {modelo}: ATIVO e operacional.")
    
    except Exception as e:
        erro = str(e).lower()
        if "not found" in erro or "404" in erro or "invalid" in erro:
            print(f"🔴 {modelo}: DESATIVADO ou inexistente.")
        elif "429" in erro or "quota" in erro:
            print(f"🟡 {modelo}: ATIVO, mas sem cota de requisições no momento.")
        else:
            print(f"🟠 {modelo}: FALHA DE CONEXÃO ({str(e)[:40]}...)")

print("\n✅ Verificação finalizada.")
