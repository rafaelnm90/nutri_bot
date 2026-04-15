import os
from google import genai
from google.genai import types
import json
import re
import time

EXIBIR_LOGS = True
MODELOS_CASCATA = [
    'gemini-3.1-pro-preview', 
    'gemini-3-flash-preview', 
    'gemini-3.1-flash-lite-preview'
]

def analyze_food_image(image_bytes, mime_type="image/jpeg", api_key=None):
    """
    Analyzes an image of food using Google Gemini.
    Returns a dictionary with 'items' (lista de alimentos detectados).
    """
    if EXIBIR_LOGS:
        print("🚀 Iniciando a análise da imagem da refeição por itens...")
        
    client = genai.Client(api_key=api_key) if api_key else genai.Client()
    
    if EXIBIR_LOGS:
        print("🔍 Refinando parâmetros de calibragem visual do prompt...")

    prompt = """
Você é um nutricionista experiente de olho clínico prestando consultoria rápida no Telegram.
O paciente enviou esta foto.

Sua tarefa: identifique QUAL dos 3 cenários se aplica e responda adequadamente:

CENÁRIO 1 — Prato ou alimento visível (refeição, bebida, suplemento):
   * "is_food": true, "source": "visual_estimate"
   * CALIBRAGEM VISUAL OBRIGATÓRIA E REFINADA: Você deve obrigatoriamente analisar a textura dos alimentos e utilizar as bordas de pratos, copos, talheres ou mãos visíveis como referência direta de escala matemática para calcular as gramaturas com precisão. Não chute valores genéricos.
   * Identifique INDIVIDUALMENTE cada alimento e adicione à lista "items".
   * REGRA DE HIDRATAÇÃO: Avalie a natureza do item. Se o item for uma bebida alcoólica de qualquer tipo ou um refrigerante (mesmo zero açúcar), o campo "water_penalty_ml" (dentro de "micronutrients") deve ser EXATAMENTE IGUAL ao volume em ml ou peso em gramas estimado para a bebida (Proporção 1:1). Exemplo: estimou 330ml de cerveja, retorne 330. Para outros alimentos ou bebidas saudáveis, envie 0.

CENÁRIO 2 — Embalagem ou tabela nutricional visível:
   * "is_food": true, "source": "nutrition_label"
   * Leia LITERALMENTE os valores da tabela. Aplique a REGRA DE HIDRATAÇÃO descrita acima se for álcool/refrigerante.

CENÁRIO 3 — Código de barras isolado ou imagem não relacionada a alimento:
   * "is_food": false, "source": "not_food"
   * Deixe "items" vazio ([]).
   * No campo "conversational_reply", comente amigavelmente.

É OBRIGATÓRIO responder EXATAMENTE neste formato JSON, apenas JSON válido sem blocos ```json:
{
  "is_food": true,
  "source": "visual_estimate",
  "items": [
    {
      "name": "Alimento Fictício A (1 porção)",
      "weight_g": 150,
      "calories": 200,
      "macros": {
        "carbs_g": 20, "sugar_g": 2, "protein_g": 10, "fat_g": 5, "fiber_g": 4
      },
      "micronutrients": {
        "sodium_mg": 50, "calcium_mg": 10, "zinc_mg": 0.5, "iron_mg": 0.2,
        "potassium_mg": 35, "vitamin_c_mg": 0, "vitamin_a_mcg": 0,
        "water_penalty_ml": 0
      }
    }
  ],
  "conversational_reply": ""
}
    """
    
    max_retries = len(MODELOS_CASCATA)
    for attempt in range(max_retries):
        model_id = MODELOS_CASCATA[attempt]
        try:
            response = client.models.generate_content(
                model=model_id,
                contents=[
                    prompt,
                    types.Part.from_bytes(
                        data=bytes(image_bytes),
                        mime_type=mime_type,
                    )
                ]
            )
            
            text = response.text.strip()
            match = re.search(r'\{.*\}', text, re.DOTALL)
            
            if match:
                json_str = match.group(0)
                try:
                    data = json.loads(json_str)
                    if EXIBIR_LOGS:
                        print(f"✅ Sucesso! Detectados {len(data.get('items', []))} itens na imagem usando {model_id}.")
                    return data
                except json.JSONDecodeError as e:
                    if EXIBIR_LOGS:
                        print(f"⚠️ Falha ao decodificar o JSON: {e}")
                    return {
                        "is_food": False,
                        "conversational_reply": "Desculpe, a IA teve dificuldade em montar a resposta.",
                        "items": []
                    }
            else:
                if EXIBIR_LOGS:
                    print("⚠️ Nenhum JSON encontrado na resposta da imagem.")
                return {
                    "is_food": False,
                    "conversational_reply": "Ainda estou aprendendo a ver certas imagens, me mande outra foto?",
                    "items": []
                }
                
        except Exception as e:
            error_msg = str(e)
            
            if ("429" in error_msg or "503" in error_msg or "RESOURCE_EXHAUSTED" in error_msg or "UNAVAILABLE" in error_msg) and attempt < max_retries - 1:
                if EXIBIR_LOGS:
                    print(f"⚠️ Modelo {model_id} esgotado ou indisponível. 🔄 Acionando modelo secundário {MODELOS_CASCATA[attempt+1]}...")
                time.sleep(2)
                continue
            
            if EXIBIR_LOGS:
                print(f"⚠️ Erro na comunicação com a API após {max_retries} tentativas: {error_msg}")
                
            if "429" in error_msg or "RESOURCE_EXHAUSTED" in error_msg:
                wait_time = "alguns"
                match_time = re.search(r'in (\d+\.?\d*)s', error_msg)
                if match_time:
                    wait_time = str(int(float(match_time.group(1))))
                    
                resposta_amigavel = f"⏳ Opa! O sistema de IA precisa de um breve descanso para processar tanta informação. O limite volta em {wait_time} segundos. Pode enviar a sua foto novamente logo a seguir?"
                
                return {
                    "is_food": False,
                    "conversational_reply": resposta_amigavel,
                    "items": []
                }
                
            raise e

def analyze_food_text(text_input, context_meal=None, chat_history=None, api_key=None):
    """
    Analyzes a text description of food using Google Gemini.
    Returns a dictionary with a list of 'items'.
    """
    if EXIBIR_LOGS:
        print("🚀 Iniciando análise de texto com suporte a itens, contexto de edição e histórico de memória...")
        
    client = genai.Client(api_key=api_key) if api_key else genai.Client()
    
    context_str = ""
    if context_meal:
        context_str += f"\nCONTEXTO DE EDIÇÃO: O usuário está corrigindo uma análise anterior. O bot havia entendido: {context_meal}. Considere esta informação para ajustar a resposta conforme a nova mensagem do usuário."

    if chat_history:
        history_text = "\n".join([f"{msg['role'].capitalize()}: {msg['text']}" for msg in chat_history])
        context_str += f"\n\nHISTÓRICO RECENTE DA CONVERSA (Use para entender o contexto de mensagens curtas):\n{history_text}\n"

    if EXIBIR_LOGS:
        print("🧠 Injetando diretrizes de interpretação e edição cirúrgica no prompt...")

    prompt = f"""
Você é um nutricionista amigável, experiente e disposto a conversar sobre qualquer assunto no Telegram.
O seu paciente enviou a seguinte mensagem:
"{text_input}"
{context_str}

Sua tarefa:
1. Classifique a intenção do paciente. Se contiver correções ou edições de alimentos, classifique como "is_food": true.
2. MODO DE EDIÇÃO E CORREÇÃO: Se houver contexto de edição, atue como um interpretador cirúrgico. Se o paciente disser "não foi pão de queijo e sim pão de leite ninho" ou "não foi 80g e sim 100g", você deve compreender a substituição exata de ingredientes ou o ajuste matemático direto e retornar o JSON com o alimento correto, com as gramaturas, calorias e macros recalculados para a nova realidade.
3. Se ele estiver REGISTRANDO O CONSUMO ("is_food": true):
   * Identifique INDIVIDUALMENTE cada alimento consumido na lista "items".
   * Estime peso, calorias, macros e micros.
   * REGRA DE HIDRATAÇÃO: Avalie a natureza do item. Se o item for uma bebida alcoólica de qualquer tipo ou um refrigerante (mesmo zero), o campo "water_penalty_ml" (dentro de "micronutrients") deve ser EXATAMENTE IGUAL ao volume em ml ou peso em gramas consumido (Proporção 1:1). Exemplo: o paciente relatou ter bebido 200ml de Coca-Cola, retorne 200. Para qualquer outro alimento, envie 0.
4. Se for QUALQUER OUTRA COISA ("is_food": false):
   * Deixe a lista "items" vazia ([]). Converse livremente no campo "conversational_reply".

É OBRIGATÓRIO responder EXATAMENTE neste formato JSON puro e válido:
{{
  "is_food": true,
  "items": [
    {{
      "name": "Exemplo Genérico X",
      "weight_g": 145,
      "calories": 210,
      "macros": {{ "carbs_g": 15, "sugar_g": 3, "protein_g": 20, "fat_g": 8, "fiber_g": 4 }},
      "micronutrients": {{
        "sodium_mg": 300, "calcium_mg": 25, "zinc_mg": 2.0, "iron_mg": 2.5,
        "potassium_mg": 250, "vitamin_c_mg": 5, "vitamin_a_mcg": 15,
        "water_penalty_ml": 500
      }}
    }}
  ],
  "conversational_reply": ""
}}
    """
    
    max_retries = len(MODELOS_CASCATA)
    for attempt in range(max_retries):
        model_id = MODELOS_CASCATA[attempt]
        try:
            response = client.models.generate_content(
                model=model_id,
                contents=[prompt]
            )
            
            text = response.text.strip()
            match = re.search(r'\{.*\}', text, re.DOTALL)
            
            if match:
                json_str = match.group(0)
                try:
                    data = json.loads(json_str)
                    if EXIBIR_LOGS:
                        print(f"✅ Sucesso! Processados {len(data.get('items', []))} itens no texto usando {model_id}!")
                    return data
                except json.JSONDecodeError as e:
                    if EXIBIR_LOGS:
                        print(f"⚠️ Erro ao fazer parsing do JSON no texto: {e}")
                    return {
                        "is_food": False,
                        "conversational_reply": "Desculpe, tive uma pequena confusão agora.",
                        "items": []
                    }
            else:
                if EXIBIR_LOGS:
                    print("⚠️ Nenhum JSON retornado pelo Gemini no modo texto.")
                return {
                    "is_food": False,
                    "conversational_reply": "Não consegui entender essa mensagem agora.",
                    "items": []
                }
                
        except Exception as e:
            error_msg = str(e)
            
            if ("429" in error_msg or "503" in error_msg or "RESOURCE_EXHAUSTED" in error_msg or "UNAVAILABLE" in error_msg) and attempt < max_retries - 1:
                if EXIBIR_LOGS:
                    print(f"⚠️ Modelo {model_id} esgotado ou indisponível. 🔄 Acionando modelo secundário {MODELOS_CASCATA[attempt+1]}...")
                time.sleep(2)
                continue
                
            if EXIBIR_LOGS:
                print(f"⚠️ Erro na comunicação com a API após {max_retries} tentativas: {error_msg}")
                
            if "429" in error_msg or "RESOURCE_EXHAUSTED" in error_msg:
                wait_time = "alguns"
                match_time = re.search(r'in (\d+\.?\d*)s', error_msg)
                if match_time:
                    wait_time = str(int(float(match_time.group(1))))
                    
                resposta_amigavel = f"⏳ Opa! O Google pediu para eu respirar um pouco. O limite gratuito volta em {wait_time} segundos. Pode mandar a mensagem de novo logo a seguir?"
                
                return {
                    "is_food": False,
                    "conversational_reply": resposta_amigavel,
                    "items": []
                }
                
            raise e

def generate_meal_suggestion(cal_target, time_of_day, goal_type="manter", consumed_summary="", api_key=None):
    """
    Generates meal suggestions based on the remaining calorie target.
    """
    if EXIBIR_LOGS:
        print(f"🚀 Iniciando busca inteligente de receitas para {time_of_day} compensando os macros do dia...")
        
    client = genai.Client(api_key=api_key) if api_key else genai.Client()
    
    contexto_meta = "manter o peso atual"
    if goal_type == "emagrecer":
        contexto_meta = "garantir o déficit calórico focado em emagrecimento"
    elif goal_type == "ganhar":
        contexto_meta = "atingir o supra calórico para ganho de massa muscular"

    prompt = f"""
Você é um nutricionista experiente prestando consultoria rápida no Telegram. 
O seu paciente tem um alvo de EXATAMENTE {cal_target} kcal disponíveis para essa refeição, visando {contexto_meta}. 
O período atual do dia dele é: {time_of_day}.

ATENÇÃO AO RAIO X NUTRICIONAL DO DIA: Até o momento, o paciente já consumiu: {consumed_summary}.
Sua tarefa é atuar de forma compensatória. Se notar excesso de gordura, sugira opções magras. Se faltar proteína ou micronutrientes como Vitamina C, priorize alimentos ricos nessas fontes.

Por favor, sugira 3 opções diferentes de refeições saudáveis, práticas e que se encaixem precisamente nesse valor calórico restante (a soma dos ingredientes precisa bater próximo do alvo).
Para cada opção, inclua as porções em gramas ou medidas caseiras e justifique rapidamente por que essa escolha ajuda a equilibrar o saldo atual do dia. 
Não use blocos de código markdown como ```json ou crases. Responda em formato de texto Markdown amigável e validado (feche corretamente todos os asteriscos e sublinhados para evitar erros de renderização). Seja encorajador!
"""
    max_retries = len(MODELOS_CASCATA)
    for attempt in range(max_retries):
        model_id = MODELOS_CASCATA[attempt]
        try:
            response = client.models.generate_content(
                model=model_id,
                contents=[prompt]
            )
            if EXIBIR_LOGS:
                print(f"✅ Sugestão de refeição inteligente gerada com sucesso usando {model_id}!")
            return response.text.strip()
        except Exception as e:
            error_msg = str(e)
            if ("429" in error_msg or "503" in error_msg or "RESOURCE_EXHAUSTED" in error_msg or "UNAVAILABLE" in error_msg) and attempt < max_retries - 1:
                if EXIBIR_LOGS:
                    print(f"⚠️ Modelo {model_id} esgotado ou indisponível. 🔄 Acionando modelo secundário {MODELOS_CASCATA[attempt+1]}...")
                time.sleep(2)
                continue
                
            if EXIBIR_LOGS:
                print(f"⚠️ Erro ao gerar sugestão de refeição: {error_msg}")
            return "Os servidores da IA estão todos congestionados no momento. Pode tentar pedir a sugestão de novo em uns minutinhos?"

def analyze_exercise_text(text_input, api_key=None):
    """
    Analisa um texto descrevendo um exercício físico.
    Retorna: { is_exercise, description, duration_min, calories_burned, conversational_reply }
    """
    if EXIBIR_LOGS:
        print("🚀 Analisando exercício via texto...")

    client = genai.Client(api_key=api_key) if api_key else genai.Client()

    prompt = f"""
Você é um personal trainer e nutricionista experiente.
O paciente enviou esta mensagem:
"{text_input}"

Sua tarefa:
1. Determine se a mensagem descreve um exercício físico ("is_exercise": true) ou se é outra coisa ("is_exercise": false).
2. Se SIM (exercício):
   - Extraia o nome/descrição do exercício.
   - Estime a duração em minutos. Se não mencionada, assuma 30 min.
   - Estime as calorias queimadas para uma pessoa adulta média (70kg). Use valores realistas:
     * Caminhada leve: ~4 kcal/min
     * Corrida moderada: ~10 kcal/min
     * Musculação: ~6 kcal/min
     * Ciclismo moderado: ~8 kcal/min
     * Natação: ~9 kcal/min
     * HIIT: ~12 kcal/min
   - Deixe "conversational_reply" vazio ("").
3. Se NÃO (não é exercício):
   - Zere duration_min e calories_burned.
   - No campo "conversational_reply", responda amigavelmente como nutricionista/personal.

Responda APENAS com JSON válido puro, sem blocos ```json:
{{
  "is_exercise": true,
  "description": "Corrida em ritmo moderado",
  "duration_min": 30,
  "calories_burned": 300,
  "conversational_reply": ""
}}
"""
    max_retries = len(MODELOS_CASCATA)
    for attempt in range(max_retries):
        model_id = MODELOS_CASCATA[attempt]
        try:
            response = client.models.generate_content(model=model_id, contents=[prompt])
            text = response.text.strip()
            match = re.search(r'\{.*\}', text, re.DOTALL)
            if match:
                data = json.loads(match.group(0))
                if EXIBIR_LOGS:
                    print(f"✅ Exercício analisado usando {model_id}: {data.get('description')} — {data.get('calories_burned')} kcal")
                return data
            return {"is_exercise": False, "conversational_reply": "Não consegui identificar um exercício na sua mensagem."}
        except Exception as e:
            error_msg = str(e)
            if ("429" in error_msg or "503" in error_msg or "RESOURCE_EXHAUSTED" in error_msg or "UNAVAILABLE" in error_msg) and attempt < max_retries - 1:
                if EXIBIR_LOGS:
                    print(f"⚠️ Modelo {model_id} esgotado ou indisponível. 🔄 Acionando modelo secundário {MODELOS_CASCATA[attempt+1]}...")
                time.sleep(2)
                continue
            return {"is_exercise": False, "conversational_reply": "Desculpe, todos os nossos servidores estão ocupados agora. Tente relatar o treino novamente em instantes!"}

def analyze_label(image_bytes, mime_type="image/jpeg", api_key=None):
    if EXIBIR_LOGS:
        print("🔍 Analisando rótulo e ingredientes da embalagem...")
        
    client = genai.Client(api_key=api_key) if api_key else genai.Client()
    
    prompt = """
Você é um nutricionista rigoroso analisando rótulos no estilo do aplicativo 'Desrotulando'. O usuário está no mercado em Minas Gerais e precisa de uma análise visual, direta e completa.
ABANDONE saudações e textos longos.

Responda EXATAMENTE nesta estrutura:

🔍 *Raio-X dos Ingredientes*
(Liste os ingredientes identificados na embalagem. Marque com ✅ os inofensivos/naturais e com ❌ os ultraprocessados/aditivos/prejudiciais, adicionando o motivo em até 3 palavras para os itens com ❌)
- ✅ [Nome do Ingrediente]
- ❌ [Nome do Ingrediente] (Motivo curto)

📊 *Avaliação Geral*
(Dê um selo final: 🟢 Excelente, 🟡 Bom, 🟠 Atenção ou 🔴 Ruim/Ultraprocessado. Adicione uma frase curta resumindo o impacto do produto na saúde com base na proporção de ingredientes ruins).

🛒 *Alternativas Saudáveis*
(Aponte opções mais limpas para o MESMO produto, citando nominalmente marcas comerciais reais. Dê duas opções claras:
- 💰 *Custo-Benefício:* Marca acessível e fácil de achar.
- ⭐ *Premium:* Marca de melhor qualidade, independente do preço).
"""
    
    if EXIBIR_LOGS:
        print("🚀 Formatando prompt de auditoria completa de rótulo (Estilo Desrotulando)...")
        
    max_retries = len(MODELOS_CASCATA)
    for attempt in range(max_retries):
        model_id = MODELOS_CASCATA[attempt]
        try:
            response = client.models.generate_content(
                model=model_id,
                contents=[
                    prompt,
                    types.Part.from_bytes(data=bytes(image_bytes), mime_type=mime_type)
                ]
            )
            if EXIBIR_LOGS:
                print(f"✅ Análise do rótulo gerada com sucesso usando {model_id}!")
            return response.text.strip()
        except Exception as e:
            error_msg = str(e)
            if ("429" in error_msg or "503" in error_msg or "RESOURCE_EXHAUSTED" in error_msg or "UNAVAILABLE" in error_msg) and attempt < max_retries - 1:
                if EXIBIR_LOGS:
                    print(f"⚠️ Modelo {model_id} esgotado ou indisponível. 🔄 Acionando modelo secundário {MODELOS_CASCATA[attempt+1]}...")
                time.sleep(2)
                continue
                
            if EXIBIR_LOGS: print(f"⚠️ Erro ao analisar rótulo após {max_retries} tentativas: {error_msg}")
            return "Os servidores da inteligência artificial estão enfrentando um congestionamento enorme agora e não consegui ler esse rótulo. Pode tentar de novo em uns 2 minutinhos?"

def generate_daily_report(goal_type, cal_goal, t_cal, t_prot, t_carb, t_fat, water_drunk, water_goal, api_key=None):
    if EXIBIR_LOGS:
        print("🚀 Gerando relatório diário analítico com IA...")
        
    client = genai.Client(api_key=api_key) if api_key else genai.Client()
    
    prompt = f"""
Aja como um nutricionista clínico altamente analítico e encorajador.
Faça uma avaliação do dia de hoje do seu paciente antes de ele ir dormir.

DADOS DO PACIENTE HOJE:
- Objetivo: {goal_type}
- Meta Diária de Calorias: {cal_goal} kcal
- Consumo Realizado: {t_cal} kcal
- Divisão de Macros: Proteínas ({t_prot}g), Carboidratos ({t_carb}g), Gorduras ({t_fat}g)
- Hidratação: {water_drunk}ml ingeridos de uma meta de {water_goal}ml.

INSTRUÇÕES DE RESPOSTA:
1. Inicie com um cumprimento caloroso de fim de dia.
2. Avalie de forma direta o saldo calórico (se cumpriu o déficit/superávit planeado ou se sabotou o processo).
3. Analise rapidamente se a proteína foi suficiente para manter a massa magra e o equilíbrio dos outros macros.
4. Comente sobre a hidratação.
5. Finalize com um conselho prático ou uma palavra de motivação para amanhã.

Seja natural, não use jargões robóticos e insira alguns emojis. O texto deve ser curto e estruturado em Markdown.
"""
    max_retries = len(MODELOS_CASCATA)
    for attempt in range(max_retries):
        model_id = MODELOS_CASCATA[attempt]
        try:
            response = client.models.generate_content(
                model=model_id,
                contents=[prompt]
            )
            if EXIBIR_LOGS:
                print(f"✅ Relatório diário gerado com sucesso usando {model_id}!")
            return response.text.strip()
        except Exception as e:
            error_msg = str(e)
            if ("429" in error_msg or "503" in error_msg or "RESOURCE_EXHAUSTED" in error_msg or "UNAVAILABLE" in error_msg) and attempt < max_retries - 1:
                if EXIBIR_LOGS:
                    print(f"⚠️ Modelo {model_id} esgotado. 🔄 Tentando {MODELOS_CASCATA[attempt+1]}...")
                time.sleep(2)
                continue
                
            if EXIBIR_LOGS:
                print(f"⚠️ Erro ao gerar relatório após {max_retries} tentativas: {error_msg}")
            return "Tive um problema de comunicação com os servidores do Google para gerar a avaliação de hoje. Tente novamente amanhã!"
