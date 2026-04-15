import os
import logging
import asyncio
from telegram import Update, ReplyKeyboardMarkup, ReplyKeyboardRemove, InlineKeyboardMarkup, InlineKeyboardButton
from telegram.ext import (
    ApplicationBuilder,
    CommandHandler,
    MessageHandler,
    filters,
    ContextTypes,
    ConversationHandler,
    CallbackQueryHandler,
)
import json
from datetime import datetime

import database as db
from nutrition_utils import calculate_bmr, calculate_daily_calorie_goal, calculate_daily_water_goal
from ai_service import analyze_food_image, analyze_food_text

EXIBIR_LOGS = True

from dotenv import load_dotenv

load_dotenv()

# Enable logging
logging.basicConfig(
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    level=logging.INFO
)

# States
API_KEY, GENDER, AGE, WEIGHT, HEIGHT, ACTIVITY, GOAL_TYPE, EXPERIENCE = range(8)

MAIN_MENU_KEYBOARD = ReplyKeyboardMarkup(
    [
        ['📊 Meu Resumo de Hoje', '💧 Registrar Água'],
        ['💡 Sugerir Refeição', '🍽️ Registrar Comida'],
        ['🛒 Ler Rótulo', '📋 Lista de Compras']
    ],
    resize_keyboard=True
)

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    """Starts the onboarding process."""
    user = update.message.from_user
    user_db = await db.get_user(user.id)
    
    if user_db and user_db.get('api_key') and user_db.get('step') == 'DONE':
        await update.message.reply_text(
            f"Olá de volta, {user.first_name}! Eu já tenho seus dados. Sua meta diária é {user_db['daily_goal']} kcal.\n"
            "Mande fotos das suas refeições para eu contabilizar, ou use o menu abaixo para ver como está o seu dia.\n\n"
            "Se quiser refazer o perfil, digite /refazer",
            reply_markup=MAIN_MENU_KEYBOARD
        )
        return ConversationHandler.END
        
    if user_db and user_db.get('api_key'):
        await update.message.reply_text("Vamos continuar a configuração do seu perfil físico.\nQual é o seu sexo biológico? (M ou F)")
        await db.save_user(user.id, {"step": "GENDER"})
        return GENDER
        
    await update.message.reply_text(
        "Olá! Sou o seu Nutricionista Virtual de bolso. 🥗🤖\n"
        "Para eu poder te ajudar e garantir que seus dados sejam processados de forma privada, preciso da sua Chave de API do Google Gemini.\n\n"
        "🔑 Por favor, cole a sua chave de API abaixo:"
    )
    await db.save_user(user.id, {"name": user.first_name, "step": "API_KEY"})
    return API_KEY

async def receive_api_key(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    texto = update.message.text.strip()
    user = update.message.from_user
    
    if EXIBIR_LOGS:
        logging.info(f"✅ Chave de API recebida e armazenada com sucesso para o usuário {user.id}.")
        
    await db.save_user(user.id, {"api_key": texto, "step": "GENDER"})
    
    await update.message.reply_text(
        "✅ Chave de API salva com sucesso! Ela está segura. 🔒\n\n"
        "Agora, preciso calcular seu metabolismo. "
        "Qual é o seu sexo biológico? (Responda M para masculino ou F para feminino)"
    )
    return GENDER

async def redo_profile(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    user = update.message.from_user
    user_db = await db.get_user(user.id)
    
    if not user_db or not user_db.get('api_key'):
        await update.message.reply_text("Você precisa iniciar com /start primeiro para configurar sua chave de API.")
        return ConversationHandler.END
        
    await update.message.reply_text(
        "Vamos refazer seu perfil físico (sua chave de API foi mantida salva em segurança 🔒).\n"
        "Qual é o seu sexo biológico? (Responda M para masculino ou F para feminino)"
    )
    await db.save_user(user.id, {"step": "GENDER"})
    return GENDER

async def gender(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    text = update.message.text.strip().upper()
    if text not in ['M', 'F']:
        await update.message.reply_text("Por favor, responda com M ou F.")
        return GENDER
        
    user = update.message.from_user
    await db.save_user(user.id, {"gender": text, "step": "AGE"})
    
    await update.message.reply_text("Entendido. Quantos anos você tem? (ex: 28)")
    return AGE

async def age(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    try:
        idade = int(update.message.text.strip())
    except ValueError:
        await update.message.reply_text("Por favor, digite apenas números para a idade.")
        return AGE
        
    user = update.message.from_user
    await db.save_user(user.id, {"age": idade, "step": "WEIGHT"})
    
    await update.message.reply_text("Qual é o seu peso em kg? (ex: 75.5)")
    return WEIGHT

async def weight(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    try:
        peso = float(update.message.text.strip().replace(',', '.'))
    except ValueError:
        await update.message.reply_text("Por favor, digite um número válido para o peso.")
        return WEIGHT
        
    user = update.message.from_user
    await db.save_user(user.id, {"weight": peso, "step": "HEIGHT"})
    
    await update.message.reply_text("Qual é a sua altura em cm? (ex: 175)")
    return HEIGHT

async def height(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    try:
        altura = float(update.message.text.strip().replace(',', '.'))
    except ValueError:
        await update.message.reply_text("Por favor, digite um número válido para a altura.")
        return HEIGHT
        
    user = update.message.from_user
    await db.save_user(user.id, {"height": altura, "step": "ACTIVITY"})
    
    reply_keyboard = [['Sedentario', 'Leve', 'Moderado', 'Intenso']]
    await update.message.reply_text(
        "Última pergunta! Qual o seu nível de atividade física?\n"
        "- Sedentario (pouco a nenhum exercício)\n"
        "- Leve (exercício leve 1-3 dias/semana)\n"
        "- Moderado (exercício moderado 3-5 dias/semana)\n"
        "- Intenso (exercício pesado 6-7 dias/semana)",
        reply_markup=ReplyKeyboardMarkup(
            reply_keyboard, one_time_keyboard=True, input_field_placeholder='Atividade?'
        ),
    )
    return ACTIVITY

async def activity(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    texto = update.message.text.strip().lower()
    import unicodedata
    texto_norm = ''.join(c for c in unicodedata.normalize('NFD', texto) if unicodedata.category(c) != 'Mn')
    
    if texto_norm not in ['sedentario', 'leve', 'moderado', 'intenso']:
        await update.message.reply_text("Por favor, escolha uma das opções do teclado.")
        return ACTIVITY
        
    user = update.message.from_user
    await db.save_user(user.id, {"activity_level": texto_norm, "step": "GOAL_TYPE"})
    
    if EXIBIR_LOGS:
        logging.info(f"✅ Nível de atividade ({texto_norm}) salvo. Solicitando objetivo...")
        
    goal_keyboard = [['Emagrecer 📉', 'Manter Peso ⚖️', 'Ganhar Massa 💪']]
    await update.message.reply_text(
        "Perfeito! E qual é o seu objetivo principal?",
        reply_markup=ReplyKeyboardMarkup(
            goal_keyboard, one_time_keyboard=True, input_field_placeholder='Objetivo?'
        ),
    )
    return GOAL_TYPE

async def goal_type(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    texto = update.message.text.strip().lower()
    user_id = update.message.from_user.id
    
    if 'emagrecer' in texto:
        await db.save_user(user_id, {"goal_type": "emagrecer"})
        exp_keyboard = [['Estou começando agora 🌱', 'Já tenho experiência 🏋️']]
        await update.message.reply_text(
            "Excelente escolha! Como o seu objetivo é *emagrecer*, precisamos ajustar o déficit calórico.\n\n"
            "Você está começando o seu processo de dieta agora ou já tem experiência com restrição calórica?",
            parse_mode='Markdown',
            reply_markup=ReplyKeyboardMarkup(exp_keyboard, one_time_keyboard=True)
        )
        return EXPERIENCE
        
    elif 'ganhar' in texto:
        await db.save_user(user_id, {"goal_type": "ganhar"})
        exp_keyboard = [['Estou começando agora 🌱', 'Já tenho experiência 🏋️']]
        await update.message.reply_text(
            "Excelente escolha! Como o seu objetivo é *ganhar massa*, precisamos calcular o supra calórico.\n\n"
            "Você está começando esse processo agora ou já treina pesado e tem experiência?",
            parse_mode='Markdown',
            reply_markup=ReplyKeyboardMarkup(exp_keyboard, one_time_keyboard=True)
        )
        return EXPERIENCE
        
    else:
        user_db = await db.get_user(user_id)
        bmr = calculate_bmr(user_db['weight'], user_db['height'], user_db['age'], user_db['gender'])
        tdee = calculate_daily_calorie_goal(bmr, user_db['activity_level'])
        water_goal_ml = calculate_daily_water_goal(user_db['weight'])
        
        await db.save_user(user_id, {
            "goal_type": "manter",
            "daily_goal": tdee,
            "daily_water_goal": water_goal_ml,
            "experience_level": "manutencao",
            "diet_start_date": db.get_sp_time().strftime('%Y-%m-%d %H:%M:%S'),
            "step": "DONE"
        })
        
        await update.message.reply_text(
            f"Perfil completo! 🎉\n"
            f"Seu gasto energético diário estimado é de *{tdee} kcal*.\n"
            f"Para *manutenção de peso*, sua meta é exatamente *{tdee} kcal*.\n"
            f"💧 Meta hídrica ideal: *{water_goal_ml / 1000:.1f} L/dia*.\n\n"
            "Agora mande fotos das refeições para eu contabilizar! 📸🍽️",
            parse_mode='Markdown',
            reply_markup=MAIN_MENU_KEYBOARD
        )
        return ConversationHandler.END

async def experience_level(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    texto = update.message.text.strip().lower()
    user_id = update.message.from_user.id
    user_db = await db.get_user(user_id)
    
    nivel_exp = 'iniciante' if 'começando' in texto else 'experiente'
    tipo_meta = user_db.get('goal_type', 'emagrecer')
    
    if tipo_meta == 'emagrecer':
        f1, f2, f3 = -200, -400, -600
        verbo = "perder"
    else:
        f1, f2, f3 = +200, +400, +600
        verbo = "ganhar"
        
    fase_atual = 1 if nivel_exp == 'iniciante' else 2
    ajuste_atual = f1 if fase_atual == 1 else f2
        
    bmr = calculate_bmr(user_db['weight'], user_db['height'], user_db['age'], user_db['gender'])
    tdee = calculate_daily_calorie_goal(bmr, user_db['activity_level'])
    goal = max(1200, tdee + ajuste_atual)
    
    await db.save_user(user_id, {
        "daily_goal": goal,
        "daily_water_goal": calculate_daily_water_goal(user_db['weight']),
        "experience_level": nivel_exp,
        "diet_start_date": db.get_sp_time().strftime('%Y-%m-%d %H:%M:%S'),
        "diet_phase": fase_atual,
        "step": "DONE"
    })

    mapa = (
        f"📊 *Sua Jornada de {tipo_meta.capitalize()} em 3 Fases:*\n\n"
        f"🌱 *Fase 1 (Adaptação):* Ajuste de {f1} kcal. Ótima para começar sem choque e criar o hábito.\n\n"
        f"🔥 *Fase 2 (Ideal):* Ajuste de {f2} kcal. **Esta é a fase recomendada.** Ela equilibra a mudança visual com a manutenção da sua saúde e massa magra.\n\n"
        f"⚡ *Fase 3 (Drástica):* Ajuste de {f3} kcal. Uso pontual para quebrar platôs. **Atenção:** Se usada por muito tempo, há risco real de perda de massa muscular e fraqueza.\n\n"
        f"Iniciamos na *Fase {fase_atual}* (Meta: *{goal} kcal*).\n"
        f"Estimativa: Você deve {verbo} cerca de *{((abs(ajuste_atual)*7)/7700.0):.2f} kg/semana*."
    )

    await update.message.reply_text(mapa, parse_mode='Markdown', reply_markup=MAIN_MENU_KEYBOARD)
    return ConversationHandler.END

async def cancel(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    """Cancels and ends the conversation."""
    await update.message.reply_text("Operação cancelada.", reply_markup=MAIN_MENU_KEYBOARD)
    return ConversationHandler.END

async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not update.message: return
    user_id = update.message.from_user.id
    
    if EXIBIR_LOGS:
        logging.info(f"🚀 Iniciando processamento de mensagem do usuário {user_id}...")
        
    user_db = await db.get_user(user_id)
    
    step = user_db.get('step') if user_db else None
    if not user_db or step not in ['DONE', 'WAITING_WATER', 'WAITING_FOOD', 'WAITING_EXERCISE', 'WAITING_LABEL']:
        if EXIBIR_LOGS:
            logging.info("⚠️ Usuário bloqueado por perfil incompleto ou erro de estado. Redirecionando para onboarding...")
        await update.message.reply_text("Por favor, configure seu perfil primeiro usando /start.")
        return
        
    if EXIBIR_LOGS:
        logging.info("✅ Permissão validada. Verificando tipo de entrada (texto ou mídia)...")
    
    photo_file = None
    text_input = update.message.text or ""
    
    if text_input in ["📊 Meu Resumo de Hoje", "💧 Registrar Água", "🍽️ Registrar Comida", "💡 Sugerir Refeição", "🏋️ Registrar Exercício", "🛒 Ler Rótulo", "📋 Lista de Compras"]:
        if EXIBIR_LOGS:
            logging.info("🧹 Limpando estados temporários de edição para nova navegação...")
        context.user_data.pop('editing_index', None)
        context.user_data.pop('temp_meal_items', None)

    if text_input == "📊 Meu Resumo de Hoje":
        await db.save_user(user_id, {"step": "DONE"})
        await resumo_dia(update, context)
        return
    elif text_input == "💧 Registrar Água":
        await db.save_user(user_id, {"step": "WAITING_WATER"})
        await update.message.reply_text("💧 Quantos ml de água você bebeu agora? (Digite apenas o número, ex: 250)\n\n_Para cancelar, toque em qualquer botão do menu._", parse_mode='Markdown', reply_markup=ReplyKeyboardRemove())
        return
    elif text_input == "🍽️ Registrar Comida":
        await db.save_user(user_id, {"step": "WAITING_FOOD"})
        await update.message.reply_text("🍽️ Certo! Pode me mandar a foto do seu prato, ou descrever em texto o que você comeu!\n\n_Para cancelar, toque em qualquer botão do menu._", parse_mode='Markdown', reply_markup=ReplyKeyboardRemove())
        return
    elif text_input == "🏋️ Registrar Exercício":
        await db.save_user(user_id, {"step": "WAITING_EXERCISE"})
        await update.message.reply_text("🏋️ Muito bem! Descreva o exercício que você fez e, se possível, o tempo de duração. Ex: 'Corri na esteira por 40 minutos'.\n\n_Para cancelar, toque em qualquer botão do menu._", parse_mode='Markdown', reply_markup=ReplyKeyboardRemove())
        return
    elif text_input == "🛒 Ler Rótulo":
        await db.save_user(user_id, {"step": "WAITING_LABEL"})
        await update.message.reply_text("🛒 Pode me mandar a foto da tabela nutricional ou lista de ingredientes do produto!\n\n_Para cancelar, toque em qualquer botão do menu._", parse_mode='Markdown', reply_markup=ReplyKeyboardRemove())
        return
    elif text_input == "📋 Lista de Compras":
        await db.save_user(user_id, {"step": "DONE"})
        msg = await update.message.reply_text("Anotando os itens para sua próxima ida ao supermercado... 🛒📝", reply_markup=ReplyKeyboardRemove())
        try:
            from ai_service import generate_shopping_list
            goal_type_val = user_db.get('goal_type', 'manter')
            user_api_key = user_db.get('api_key')
            list_data = await asyncio.to_thread(generate_shopping_list, goal_type_val, user_api_key)
            
            keyboard = []
            for i, item in enumerate(list_data.get('items', [])):
                keyboard.append([InlineKeyboardButton(f"⬜ {item}", callback_data=f"shop_{i}")])
                
            texto_lista = f"📋 *Sua Lista de Compras*\nFoco: {goal_type_val.capitalize()}\n\n_Toque nos itens para marcar o que já colocou no carrinho:_"
            await msg.edit_text(texto_lista, parse_mode='Markdown', reply_markup=InlineKeyboardMarkup(keyboard))
        except Exception as e:
            logging.error(f"Erro ao gerar lista: {e}")
            await msg.edit_text("Ops, tive um probleminha ao gerar sua lista. Pode tentar de novo?")
        
        await update.message.reply_text("O que deseja fazer agora?", reply_markup=MAIN_MENU_KEYBOARD)
        return
    elif text_input == "💡 Sugerir Refeição":
        await db.save_user(user_id, {"step": "DONE"})
        
        cal_goal = user_db.get('daily_goal', 0)
        meals = await db.get_meals_today(user_id)
        cal_eaten = sum(m['calories'] for m in meals)
        deficit = cal_goal - cal_eaten
        
        if deficit <= 50:
            await update.message.reply_text("Você já atingiu (ou está muito perto) da sua meta calórica diária! Que tal focar apenas na hidratação e descanso agora?", reply_markup=MAIN_MENU_KEYBOARD)
            return
            
        msg = await update.message.reply_text("Estudando seu diário de hoje e pensando em sugestões deliciosas... 🧠👨‍🍳", reply_markup=ReplyKeyboardRemove())
        
        current_hour = db.get_sp_time().hour
        if current_hour < 11:
            time_of_day = "manhã/café da manhã"
            ideal_calories = cal_goal * 0.25
        elif current_hour < 15:
            time_of_day = "almoço"
            ideal_calories = cal_goal * 0.35
        elif current_hour < 19:
            time_of_day = "tarde/lanche da tarde"
            ideal_calories = cal_goal * 0.15
        else:
            time_of_day = "noite/jantar ou ceia"
            ideal_calories = cal_goal * 0.25
            
        # O alvo da refeição será a porção ideal para o horário, MAS nunca ultrapassando o déficit total que resta no dia
        meal_target = int(min(deficit, ideal_calories))
        
        # Se for a última refeição do dia e o déficit restante for aceitável (ex: até 600 kcal), podemos tentar fechar a meta
        if current_hour >= 19 and deficit <= 600:
            meal_target = deficit
            
        if EXIBIR_LOGS:
            logging.info(f"🚀 Alvo da refeição ajustado para {meal_target} kcal (Déficit total: {deficit} kcal). Compilando relatório...")
            
        total_prot, total_carb, total_fat, total_vit_c, total_zinc = 0, 0, 0, 0, 0
        for m in meals:
            if m.get('macros'):
                try:
                    mac = json.loads(m['macros'])
                    total_prot += float(mac.get('protein_g', 0))
                    total_carb += float(mac.get('carbs_g', 0))
                    total_fat += float(mac.get('fat_g', 0))
                except: pass
            if m.get('micronutrients'):
                try:
                    mic = json.loads(m['micronutrients'])
                    total_vit_c += float(mic.get('vitamin_c_mg', 0))
                    total_zinc += float(mic.get('zinc_mg', 0))
                except: pass
                
        consumed_summary = (
            f"Proteínas: {total_prot:.1f}g, Carbs: {total_carb:.1f}g, "
            f"Gorduras: {total_fat:.1f}g, Vitamina C: {total_vit_c:.1f}mg, Zinco: {total_zinc:.1f}mg"
        )
            
        try:
            from ai_service import generate_meal_suggestion
            goal_type_val = user_db.get('goal_type', 'manter')
            user_api_key = user_db.get('api_key')
            suggestion_text = await asyncio.to_thread(generate_meal_suggestion, meal_target, time_of_day, goal_type_val, consumed_summary, user_api_key)
            
            if EXIBIR_LOGS:
                logging.info("✅ Sugestão gerada com sucesso! Excluindo mensagem de espera e enviando resultado...")
                
            try:
                await msg.delete()
            except Exception:
                pass
                
            try:
                await update.message.reply_text(suggestion_text, parse_mode='Markdown')
            except Exception as e:
                if EXIBIR_LOGS:
                    logging.warning(f"⚠️ Erro de formatação Markdown detectado ({e}). Enviando texto limpo...")
                await update.message.reply_text(suggestion_text)
        except Exception as e:
            logging.error(f"Erro ao gerar sugestao: {e}")
            try:
                await msg.delete()
            except Exception:
                pass
            await update.message.reply_text("Deu um pequeno tilt aqui na hora de pensar, pode tentar de novo em instantes?")
            
        await update.message.reply_text("Qual vai ser o próximo passo?", reply_markup=MAIN_MENU_KEYBOARD)
        return
        
    if step == "WAITING_WATER":
        if text_input.isdigit():
            ml = int(text_input)
            await db.add_water(user_id, ml)
            await db.save_user(user_id, {"step": "DONE"})
            await update.message.reply_text(f"Glub glub! 💧 {ml}ml adicionados ao seu diário.", reply_markup=MAIN_MENU_KEYBOARD)
        else:
            await update.message.reply_text("Por favor, digite apenas números em ml (exemplo: 200).")
        return
        
    if step == "WAITING_EXERCISE":
        if EXIBIR_LOGS:
            logging.info("🚀 Iniciando processamento do relato de exercício físico...")
        msg = await update.message.reply_text("A avaliar o seu treino... Aguarde um instante! 🏃")
        try:
            from ai_service import analyze_exercise_text
            user_api_key = user_db.get('api_key')
            ex_data = await asyncio.to_thread(analyze_exercise_text, text_input, user_api_key)
            if ex_data.get("is_exercise"):
                await db.add_exercise(user_id, ex_data["description"], ex_data["duration_min"], ex_data["calories_burned"])
                await db.save_user(user_id, {"step": "DONE"})
                if EXIBIR_LOGS:
                    logging.info("✅ Exercício físico salvo com sucesso!")
                await msg.edit_text(f"✅ *Treino Registrado!*\n\n🏃 {ex_data['description']}\n⏱️ {ex_data['duration_min']} min\n🔥 ~{ex_data['calories_burned']} kcal queimadas", parse_mode='Markdown')
                await update.message.reply_text("O que mais posso fazer por você?", reply_markup=MAIN_MENU_KEYBOARD)
            else:
                reply = ex_data.get("conversational_reply", "Não entendi bem o relato do exercício. Pode descrever de outra forma?")
                await msg.edit_text(reply)
        except Exception as e:
            logging.error(f"Erro no módulo de exercício: {e}")
            await msg.edit_text("Ops, tive um imprevisto ao anotar seu treino. Pode tentar novamente?")
        return

    await db.save_user(user_id, {"step": "DONE"})

    if update.message.photo:
        photo_file = await update.message.photo[-1].get_file()
    elif update.message.document:
        mime = update.message.document.mime_type or "Desconhecido"
        if EXIBIR_LOGS: logging.info(f"Documento Recebido de MimeType: {mime}")
        photo_file = await update.message.document.get_file()
    elif not text_input:
        if EXIBIR_LOGS: logging.warning("⚠️ Nenhuma imagem ou texto identificado na mensagem.")
        await update.message.reply_text("Não consegui encontrar nenhuma foto anexada na sua mensagem e nem texto. Você precisa me mandar uma imagem da sua comida ou descrever livremente (em texto).")
        return

    if step == "WAITING_LABEL" and photo_file:
        if EXIBIR_LOGS: logging.info("🚀 Foto de rótulo detectada. Iniciando análise visual...")
        msg = await update.message.reply_text("🔍 Lendo as letrinhas miúdas do rótulo... Aguarde um instante!")
        try:
            image_bytes = await photo_file.download_as_bytearray()
            from ai_service import analyze_label
            user_api_key = user_db.get('api_key')
            label_analysis = await asyncio.to_thread(analyze_label, image_bytes, "image/jpeg", user_api_key)
            await msg.edit_text(label_analysis, parse_mode='Markdown')
            if EXIBIR_LOGS: logging.info("✅ Análise de rótulo entregue com sucesso.")
        except Exception as e:
            logging.error(f"Erro na análise do rótulo: {e}")
            await msg.edit_text("Não consegui analisar esse rótulo direito. A foto ficou tremida?")
            
        await db.save_user(user_id, {"step": "DONE"})
        await update.message.reply_text("Deseja fazer mais alguma coisa?", reply_markup=MAIN_MENU_KEYBOARD)
        return

    if EXIBIR_LOGS:
        logging.info("🚀 A definir a mensagem de espera dinâmica...")
        
    edit_idx = context.user_data.get('editing_index')
    current_items = context.user_data.get('temp_meal_items', [])
    
    if photo_file:
        mensagem_espera = "Hummm... estou a analisar a sua imagem. Aguarde um instante! 🔍"
    else:
        if edit_idx is not None:
            mensagem_espera = "A ajustar os valores conforme o seu pedido... Aguarde um instante! ✏️"
            if edit_idx >= 0 and edit_idx < len(current_items):
                item_name = current_items[edit_idx].get('name', 'o item selecionado')
                context.user_data['edit_context'] = f"O utilizador quer corrigir os dados apenas de: {item_name}. Ele enviou a seguinte correção: "
            else:
                context.user_data['edit_context'] = "O utilizador quer adicionar um novo alimento à refeição. O novo alimento é: "
        elif current_items:
            mensagem_espera = "A aplicar as alterações no prato... Aguarde um instante! 🔄"
            itens_str = ", ".join([f"{it.get('name')} ({it.get('weight_g')}g)" for it in current_items])
            context.user_data['edit_context'] = f"ATENÇÃO: O usuário está editando o prato inteiro de forma conversacional. O PRATO ATUAL CONTÉM: [{itens_str}]. Aplique a alteração pedida e RETORNE A LISTA 'items' COMPLETA. É OBRIGATÓRIO MANTER O PESO INDIVIDUAL (weight_g) DE TODOS OS ITENS. Nunca devolva um item sem peso. Devolva os itens não alterados intactos."
        else:
            mensagem_espera = "A ler a sua mensagem... Aguarde um instante! 🧠"
            
    msg = await update.message.reply_text(mensagem_espera)
    
    if EXIBIR_LOGS:
        logging.info("✅ Mensagem de espera enviada com sucesso!")
    
    try:
        user_api_key = user_db.get('api_key')
        if photo_file:
            logging.info("Fazendo download da imagem...")
            image_bytes = await photo_file.download_as_bytearray()
            logging.info("Enviando imagem para o Gemini via thread...")
            analysis = await asyncio.to_thread(analyze_food_image, image_bytes, "image/jpeg", user_api_key)
        else:
            logging.info("Enviando texto para o Gemini via thread...")
            edit_context = context.user_data.get('edit_context')
            chat_history = context.user_data.get('chat_history', [])
            
            analysis = await asyncio.to_thread(analyze_food_text, text_input, edit_context, chat_history, user_api_key)
            context.user_data.pop('edit_context', None)
            
        logging.info(f"Retorno do Gemini: {analysis}")
        
        if EXIBIR_LOGS:
            logging.info("📝 Atualizando memória de curto prazo da conversa...")
            
        hist = context.user_data.get('chat_history', [])
        user_msg = text_input if text_input else "[Imagem enviada]"
        bot_msg = analysis.get('conversational_reply') if not analysis.get('items') else "[Sumário de refeição gerado para o diário]"
        
        hist.append({"role": "usuário", "text": user_msg})
        hist.append({"role": "nutricionista", "text": bot_msg})
        context.user_data['chat_history'] = hist[-6:]
        
        items = analysis.get("items", [])
        
        if not analysis.get('is_food', True) or not items:
            reply = analysis.get('conversational_reply')
            if not reply:
                reply = "Hmm, não consegui identificar o alimento. Queria me descrever o que você comeu?"
            reply_formatado = reply.replace('**', '*')
            
            try:
                await msg.edit_text(reply_formatado, parse_mode='Markdown')
            except Exception as e:
                await msg.edit_text(reply)
                
            await update.message.reply_text("O que posso fazer por você?", reply_markup=MAIN_MENU_KEYBOARD)
            return
            
        import re
        if EXIBIR_LOGS: logging.info("🚀 Extraindo pesos ocultos nos nomes dos alimentos...")
        for it in items:
            try:
                peso = int(it.get('weight_g') or 0)
                if peso == 0:
                    match = re.search(r'\(?(\d+)\s*g\)?', str(it.get('name', '')), re.IGNORECASE)
                    if match:
                        it['weight_g'] = int(match.group(1))
            except Exception:
                pass

        if edit_idx is not None:
            if edit_idx == -1: 
                current_items.extend(items)
            elif items:
                if edit_idx >= 0 and edit_idx < len(current_items):
                    current_items[edit_idx] = items[0]
                else:
                    if EXIBIR_LOGS: logging.warning("⚠️ Índice fora dos limites. A lista principal foi protegida.")
            
            context.user_data.pop('editing_index', None)
            context.user_data['temp_meal_items'] = current_items
            await msg.delete()
            return await handle_meal_confirmation(update, context)

        context.user_data['temp_meal_items'] = items
        
        if EXIBIR_LOGS: logging.info("🚀 Calculando somatórios...")
            
        t_cal = sum(int(it.get('calories') or 0) for it in items)
        t_weight = sum(int(it.get('weight_g') or 0) for it in items)
        t_prot = sum(float((it.get('macros') or {}).get('protein_g') or 0) for it in items)
        t_carb = sum(float((it.get('macros') or {}).get('carbs_g') or 0) for it in items)
        t_fat = sum(float((it.get('macros') or {}).get('fat_g') or 0) for it in items)
        t_sugar = sum(float((it.get('macros') or {}).get('sugar_g') or 0) for it in items)

        t_na = sum(float((it.get('micronutrients') or {}).get('sodium_mg') or 0) for it in items)
        t_ca = sum(float((it.get('micronutrients') or {}).get('calcium_mg') or 0) for it in items)
        t_zn = sum(float((it.get('micronutrients') or {}).get('zinc_mg') or 0) for it in items)
        t_fe = sum(float((it.get('micronutrients') or {}).get('iron_mg') or 0) for it in items)
        t_k = sum(float((it.get('micronutrients') or {}).get('potassium_mg') or 0) for it in items)
        t_vc = sum(float((it.get('micronutrients') or {}).get('vitamin_c_mg') or 0) for it in items)
        t_va = sum(float((it.get('micronutrients') or {}).get('vitamin_a_mcg') or 0) for it in items)

        lista_comidas = ""
        lista_bebidas = ""
        for it in items:
            nome = str(it.get('name') or 'Item').replace('*', '').replace('_', '').replace('`', '')
            peso = int(it.get('weight_g') or 0)
            cal = int(it.get('calories') or 0)
            peso_str = f" ({peso}g)" if peso > 0 and str(peso) not in nome else ""
            
            nome_lower = nome.lower()
            if any(word in nome_lower for word in ['refrigerante', 'suco', 'água', 'agua', 'chá', 'cha', 'café', 'cafe', 'cerveja', 'vinho', 'ml']):
                if 'ml' in nome_lower and 'g' in peso_str:
                    peso_str = peso_str.replace('g', 'ml')
                lista_bebidas += f"• {nome}{peso_str}: {cal} kcal\n"
            else:
                lista_comidas += f"• {nome}{peso_str}: {cal} kcal\n"

        texto_estruturado = ""
        if lista_comidas:
            texto_estruturado += f"🍽️ *Análise da Refeição*\n{lista_comidas}\n"
        if lista_bebidas:
            texto_estruturado += f"🥤 *Análise da Bebida*\n{lista_bebidas}\n"

        micros_text = ""
        if any([t_na, t_ca, t_zn, t_fe, t_k, t_vc, t_va]):
            micros_text = (
                f"\n🔬 *Micronutrientes estimados:*\n"
                f"🧂 Sódio: {t_na:.1f}mg\n"
                f"🥛 Cálcio: {t_ca:.1f}mg\n"
                f"🥩 Zinco: {t_zn:.1f}mg\n"
                f"🧲 Ferro: {t_fe:.1f}mg\n"
                f"🍌 Potássio: {t_k:.1f}mg\n"
                f"🍊 Vit. C: {t_vc:.1f}mg\n"
                f"🥕 Vit. A: {t_va:.1f}mcg\n"
            )

        response_text = (
            f"{texto_estruturado}"
            f"⚖️ *Total do Prato:* {t_weight}g\n"
            f"🔥 *Calorias Totais:* {t_cal} kcal\n"
            f"💪 Proteínas: {t_prot:.1f}g\n"
            f"🍞 Carbs: {t_carb:.1f}g\n"
            f"🥑 Gorduras: {t_fat:.1f}g\n"
            f"🍬 Açúcar: {t_sugar:.1f}g\n"
            f"{micros_text}\n"
            f"_Deseja confirmar o registro total ou ajustar os itens individualmente?_"
        )
        
        keyboard = [
            [InlineKeyboardButton("✅ Confirmar Tudo", callback_data="meal_confirm_all")],
            [InlineKeyboardButton("✏️ Ajustar Itens", callback_data="meal_list_edit")],
            [InlineKeyboardButton("❌ Cancelar", callback_data="meal_reject")]
        ]
        
        await msg.edit_text(response_text, parse_mode='Markdown', reply_markup=InlineKeyboardMarkup(keyboard))
        
    except Exception as e:
        logging.error(f"Erro na analise da IA: {e}")
        await msg.edit_text("Desculpe, deu um erro ao analisar a sua comida. Você pode tentar outra foto?")
        await update.message.reply_text("O que mais posso fazer?", reply_markup=MAIN_MENU_KEYBOARD)

async def handle_meal_confirmation(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    if query:
        await query.answer()
        user_id = query.from_user.id
        action = query.data
    else:
        user_id = update.message.from_user.id
        action = "meal_list_edit"

    items = context.user_data.get('temp_meal_items', [])

    if action == "meal_confirm_all":
        if not items:
            msg_erro = "⚠️ A sessão expirou ou o prato está vazio. Por favor, envie a foto ou descrição novamente."
            if query: await query.edit_message_text(msg_erro)
            else: await update.message.reply_text(msg_erro)
            return
            
        if EXIBIR_LOGS: logging.info(f"🚀 Gravando {len(items)} itens no banco de dados...")
        agua_extra = 0
        for it in items:
            nome_item = it.get('name', 'Item')
            await db.add_meal(user_id, nome_item, it.get('calories', 0), it.get('macros', {}), it.get('micronutrients', {}))
            
            # Captura a decisão analítica tomada pelo Gemini
            agua_extra += int(it.get('micronutrients', {}).get('water_penalty_ml', 0))
        
        if EXIBIR_LOGS: logging.info("✅ Registro concluído. Formatando mensagem de sucesso...")
        msg_final = f"✅ Sucesso! {len(items)} itens foram registrados no seu diário."
        
        if agua_extra > 0:
            if EXIBIR_LOGS: logging.info(f"💧 Aplicando penalidade de hidratação: +{agua_extra}ml")
            msg_final += f"\n\n⚠️ *Ajuste de Rota:* Identifiquei bebidas que exigem mais do seu metabolismo. Adicionei temporariamente +{agua_extra} ml na sua meta de água de hoje para auxiliar o corpo no processo de filtragem."
            
        if EXIBIR_LOGS: logging.info("🧊 Congelando texto de análise limpo e enviando nova mensagem de sucesso...")
        
        if query:
            t_cal = sum(int(it.get('calories') or 0) for it in items)
            t_weight = sum(int(it.get('weight_g') or 0) for it in items)
            t_prot = sum(float((it.get('macros') or {}).get('protein_g') or 0) for it in items)
            t_carb = sum(float((it.get('macros') or {}).get('carbs_g') or 0) for it in items)
            t_fat = sum(float((it.get('macros') or {}).get('fat_g') or 0) for it in items)
            t_sugar = sum(float((it.get('macros') or {}).get('sugar_g') or 0) for it in items)
            
            t_na = sum(float((it.get('micronutrients') or {}).get('sodium_mg') or 0) for it in items)
            t_ca = sum(float((it.get('micronutrients') or {}).get('calcium_mg') or 0) for it in items)
            t_zn = sum(float((it.get('micronutrients') or {}).get('zinc_mg') or 0) for it in items)
            t_fe = sum(float((it.get('micronutrients') or {}).get('iron_mg') or 0) for it in items)
            t_k = sum(float((it.get('micronutrients') or {}).get('potassium_mg') or 0) for it in items)
            t_vc = sum(float((it.get('micronutrients') or {}).get('vitamin_c_mg') or 0) for it in items)
            t_va = sum(float((it.get('micronutrients') or {}).get('vitamin_a_mcg') or 0) for it in items)
            
            lista_comidas = ""
            lista_bebidas = ""
            for it in items:
                nome = str(it.get('name') or 'Item').replace('*', '').replace('_', '').replace('`', '')
                peso = int(it.get('weight_g') or 0)
                cal = int(it.get('calories') or 0)
                peso_str = f" ({peso}g)" if peso > 0 and str(peso) not in nome else ""
                
                nome_lower = nome.lower()
                if any(word in nome_lower for word in ['refrigerante', 'suco', 'água', 'agua', 'chá', 'cha', 'café', 'cafe', 'cerveja', 'vinho', 'ml']):
                    if 'ml' in nome_lower and 'g' in peso_str:
                        peso_str = peso_str.replace('g', 'ml')
                    lista_bebidas += f"• {nome}{peso_str}: {cal} kcal\n"
                else:
                    lista_comidas += f"• {nome}{peso_str}: {cal} kcal\n"
                    
            texto_estruturado = ""
            if lista_comidas: texto_estruturado += f"🍽️ *Análise da Refeição*\n{lista_comidas}\n"
            if lista_bebidas: texto_estruturado += f"🥤 *Análise da Bebida*\n{lista_bebidas}\n"
            
            micros_text = ""
            if any([t_na, t_ca, t_zn, t_fe, t_k, t_vc, t_va]):
                micros_text = (
                    f"\n🔬 *Micronutrientes estimados:*\n"
                    f"🧂 Sódio: {t_na:.1f}mg\n"
                    f"🥛 Cálcio: {t_ca:.1f}mg\n"
                    f"🥩 Zinco: {t_zn:.1f}mg\n"
                    f"🧲 Ferro: {t_fe:.1f}mg\n"
                    f"🍌 Potássio: {t_k:.1f}mg\n"
                    f"🍊 Vit. C: {t_vc:.1f}mg\n"
                    f"🥕 Vit. A: {t_va:.1f}mcg\n"
                )
                
            clean_summary = (
                f"{texto_estruturado}"
                f"⚖️ *Total do Prato:* {t_weight}g\n"
                f"🔥 *Calorias Totais:* {t_cal} kcal\n"
                f"💪 Proteínas: {t_prot:.1f}g\n"
                f"🍞 Carbs: {t_carb:.1f}g\n"
                f"🥑 Gorduras: {t_fat:.1f}g\n"
                f"🍬 Açúcar: {t_sugar:.1f}g\n"
                f"{micros_text}"
            )
            
            await query.edit_message_text(text=clean_summary, parse_mode='Markdown')
            await context.bot.send_message(chat_id=user_id, text=msg_final, parse_mode='Markdown')
        else: 
            await update.message.reply_text(msg_final, parse_mode='Markdown')
            
        await context.bot.send_message(chat_id=user_id, text="O que deseja fazer agora?", reply_markup=MAIN_MENU_KEYBOARD)
        context.user_data.pop('temp_meal_items', None)
        return

    elif action == "meal_list_edit":
        import re
        if not items:
            msg_erro = "⚠️ A sessão expirou ou o prato está vazio. Por favor, envie a foto ou descrição novamente."
            if query: await query.edit_message_text(msg_erro)
            else: await update.message.reply_text(msg_erro)
            return

        for it in items:
            try:
                peso = int(it.get('weight_g') or 0)
                if peso == 0:
                    match = re.search(r'\(?(\d+)\s*g\)?', str(it.get('name', '')), re.IGNORECASE)
                    if match:
                        it['weight_g'] = int(match.group(1))
            except:
                pass

        t_weight = sum(int(it.get('weight_g') or 0) for it in items)
        t_cal = sum(int(it.get('calories') or 0) for it in items)
        
        txt = f"⚙️ *Painel de Ajustes*\n⚖️ Total do prato: {t_weight}g | 🔥 {t_cal} kcal\n\n"
        keyboard = []
        
        for i, it in enumerate(items):
            nome = str(it.get('name') or 'Item').replace('*', '').replace('_', '').replace('`', '')
            peso = int(it.get('weight_g') or 0)
            cal = int(it.get('calories') or 0)
            peso_str = f" ({peso}g)" if peso > 0 and str(peso) not in nome else ""
            
            txt += f"*{i+1}.* {nome}{peso_str}: {cal} kcal\n"
            
            keyboard.append([
                InlineKeyboardButton(f"✏️ Editar {i+1}", callback_data=f"it_ed_{i}"),
                InlineKeyboardButton(f"🗑️ Remover {i+1}", callback_data=f"it_rem_{i}")
            ])
        
        txt += "\n_Toque no botão correspondente ao número do item:_"
        
        keyboard.append([InlineKeyboardButton("➕ Adicionar Novo Item", callback_data="it_add")])
        keyboard.append([InlineKeyboardButton("🔙 Voltar ao Sumário", callback_data="meal_back")])
        
        try:
            if query: await query.edit_message_text(txt, parse_mode='Markdown', reply_markup=InlineKeyboardMarkup(keyboard))
            else: await update.message.reply_text(txt, parse_mode='Markdown', reply_markup=InlineKeyboardMarkup(keyboard))
        except:
            pass
        return

    elif action.startswith("it_rem_"):
        idx = int(action.split("_")[-1])
        if 0 <= idx < len(items):
            items.pop(idx)
            context.user_data['temp_meal_items'] = items
        
        t_weight = sum(int(it.get('weight_g') or 0) for it in items)
        t_cal = sum(int(it.get('calories') or 0) for it in items)
        
        txt = f"⚙️ *Painel de Ajustes*\n⚖️ Total do prato: {t_weight}g | 🔥 {t_cal} kcal\n\n"
        keyboard = []
        
        for i, it in enumerate(items):
            nome = str(it.get('name') or 'Item').replace('*', '').replace('_', '').replace('`', '')
            peso = int(it.get('weight_g') or 0)
            cal = int(it.get('calories') or 0)
            peso_str = f" ({peso}g)" if peso > 0 and str(peso) not in nome else ""
            
            txt += f"*{i+1}.* {nome}{peso_str}: {cal} kcal\n"
            
            keyboard.append([
                InlineKeyboardButton(f"✏️ Editar {i+1}", callback_data=f"it_ed_{i}"),
                InlineKeyboardButton(f"🗑️ Remover {i+1}", callback_data=f"it_rem_{i}")
            ])
        
        txt += "\n_Toque no botão correspondente ao número do item:_"
        keyboard.append([InlineKeyboardButton("➕ Adicionar Novo Item", callback_data="it_add")])
        keyboard.append([InlineKeyboardButton("🔙 Voltar ao Sumário", callback_data="meal_back")])
        
        try:
            if query: await query.edit_message_text(txt, parse_mode='Markdown', reply_markup=InlineKeyboardMarkup(keyboard))
            else: await update.message.reply_text(txt, parse_mode='Markdown', reply_markup=InlineKeyboardMarkup(keyboard))
        except:
            pass
        return

    elif action.startswith("it_ed_") or action == "it_add":
        idx = int(action.split("_")[-1]) if "it_ed_" in action else -1
        context.user_data['editing_index'] = idx
        await db.save_user(user_id, {"step": "WAITING_FOOD"})
        
        if idx >= 0 and idx < len(items):
            it = items[idx]
            nome = str(it.get('name') or 'Item').replace('*', '').replace('_', '').replace('`', '')
            peso = int(it.get('weight_g') or 0)
            cal = int(it.get('calories') or 0)
            peso_str = f" ({peso}g)" if peso > 0 and str(peso) not in nome else ""
            
            msg_edit = (
                f"✏️ *MODO DE EDIÇÃO*\n"
                f"━━━━━━━━━━━━━━━━━━━\n"
                f"Você está ajustando o item:\n"
                f"🎯 *{nome}*{peso_str}: {cal} kcal\n\n"
                f"👇 _Escreva abaixo a correção desejada:_\n"
                f"_(Ex: 'eram 2 pedaços', 'sem molho', 'foi frito e não assado')_"
            )
        else:
            msg_edit = (
                f"➕ *ADICIONAR NOVO ITEM*\n"
                f"━━━━━━━━━━━━━━━━━━━\n"
                f"👇 _Escreva abaixo o que você deseja incluir no prato:_\n"
                f"_(Ex: 'esqueci de anotar 1 copo de suco natural de 200ml')_"
            )
        
        await query.edit_message_text(msg_edit, parse_mode='Markdown')
        return

    elif action == "meal_back":
        import re
        if not items:
            msg_erro = "⚠️ A sessão expirou ou o prato está vazio. Por favor, envie a foto ou descrição novamente."
            if query: await query.edit_message_text(msg_erro)
            else: await update.message.reply_text(msg_erro)
            return

        for it in items:
            try:
                peso = int(it.get('weight_g') or 0)
                if peso == 0:
                    match = re.search(r'\(?(\d+)\s*g\)?', str(it.get('name', '')), re.IGNORECASE)
                    if match:
                        it['weight_g'] = int(match.group(1))
            except:
                pass

        t_cal = sum(int(it.get('calories') or 0) for it in items)
        t_weight = sum(int(it.get('weight_g') or 0) for it in items)
        t_prot = sum(float((it.get('macros') or {}).get('protein_g') or 0) for it in items)
        t_carb = sum(float((it.get('macros') or {}).get('carbs_g') or 0) for it in items)
        t_fat = sum(float((it.get('macros') or {}).get('fat_g') or 0) for it in items)
        t_sugar = sum(float((it.get('macros') or {}).get('sugar_g') or 0) for it in items)

        t_na = sum(float((it.get('micronutrients') or {}).get('sodium_mg') or 0) for it in items)
        t_ca = sum(float((it.get('micronutrients') or {}).get('calcium_mg') or 0) for it in items)
        t_zn = sum(float((it.get('micronutrients') or {}).get('zinc_mg') or 0) for it in items)
        t_fe = sum(float((it.get('micronutrients') or {}).get('iron_mg') or 0) for it in items)
        t_k = sum(float((it.get('micronutrients') or {}).get('potassium_mg') or 0) for it in items)
        t_vc = sum(float((it.get('micronutrients') or {}).get('vitamin_c_mg') or 0) for it in items)
        t_va = sum(float((it.get('micronutrients') or {}).get('vitamin_a_mcg') or 0) for it in items)

        lista_comidas = ""
        lista_bebidas = ""
        for it in items:
            nome = str(it.get('name') or 'Item').replace('*', '').replace('_', '').replace('`', '')
            peso = int(it.get('weight_g') or 0)
            cal = int(it.get('calories') or 0)
            peso_str = f" ({peso}g)" if peso > 0 and str(peso) not in nome else ""
            
            nome_lower = nome.lower()
            if any(word in nome_lower for word in ['refrigerante', 'suco', 'água', 'agua', 'chá', 'cha', 'café', 'cafe', 'cerveja', 'vinho', 'ml']):
                if 'ml' in nome_lower and 'g' in peso_str:
                    peso_str = peso_str.replace('g', 'ml')
                lista_bebidas += f"• {nome}{peso_str}: {cal} kcal\n"
            else:
                lista_comidas += f"• {nome}{peso_str}: {cal} kcal\n"

        texto_estruturado = ""
        if lista_comidas:
            texto_estruturado += f"🍽️ *Análise da Refeição*\n{lista_comidas}\n"
        if lista_bebidas:
            texto_estruturado += f"🥤 *Análise da Bebida*\n{lista_bebidas}\n"

        micros_text = ""
        if any([t_na, t_ca, t_zn, t_fe, t_k, t_vc, t_va]):
            micros_text = (
                f"\n🔬 *Micronutrientes estimados:*\n"
                f"🧂 Sódio: {t_na:.1f}mg\n"
                f"🥛 Cálcio: {t_ca:.1f}mg\n"
                f"🥩 Zinco: {t_zn:.1f}mg\n"
                f"🧲 Ferro: {t_fe:.1f}mg\n"
                f"🍌 Potássio: {t_k:.1f}mg\n"
                f"🍊 Vit. C: {t_vc:.1f}mg\n"
                f"🥕 Vit. A: {t_va:.1f}mcg\n"
            )

        response_text = (
            f"{texto_estruturado}"
            f"⚖️ *Total do Prato:* {t_weight}g\n"
            f"🔥 *Calorias Totais:* {t_cal} kcal\n"
            f"💪 Proteínas: {t_prot:.1f}g\n"
            f"🍞 Carbs: {t_carb:.1f}g\n"
            f"🥑 Gorduras: {t_fat:.1f}g\n"
            f"🍬 Açúcar: {t_sugar:.1f}g\n"
            f"{micros_text}\n"
            f"_Deseja confirmar o registro total ou ajustar os itens individualmente?_"
        )
        
        keyboard = [
            [InlineKeyboardButton("✅ Confirmar Tudo", callback_data="meal_confirm_all")],
            [InlineKeyboardButton("✏️ Ajustar Itens", callback_data="meal_list_edit")],
            [InlineKeyboardButton("❌ Cancelar", callback_data="meal_reject")]
        ]
        
        try:
            await query.edit_message_text(response_text, parse_mode='Markdown', reply_markup=InlineKeyboardMarkup(keyboard))
        except:
            pass
        return

    elif action == "meal_reject":
        context.user_data.pop('temp_meal_items', None)
        if query: await query.edit_message_text("❌ Ação cancelada pelo utilizador.")
        else: await update.message.reply_text("❌ Ação cancelada pelo utilizador.")
        await context.bot.send_message(chat_id=user_id, text="O que deseja fazer agora?", reply_markup=MAIN_MENU_KEYBOARD)
        return

async def registrar_agua(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.message.from_user.id
    user_db = await db.get_user(user_id)
    
    if not user_db or user_db.get('step') != 'DONE':
        await update.message.reply_text("Por favor, configure seu perfil primeiro usando /start.")
        return
        
    if not context.args or not context.args[0].isdigit():
        await update.message.reply_text("Por favor, me diga em formato número quantos mililitros (mL) você bebeu.\nExemplo: `/agua 200`", parse_mode='Markdown')
        return
        
    ml = int(context.args[0])
    await db.add_water(user_id, ml)
    
    await update.message.reply_text(
        f"Glub glub! 💧 {ml}ml adicionados ao seu diário.",
        reply_markup=MAIN_MENU_KEYBOARD
    )

async def desfazer_refeicao(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.message.from_user.id
    if EXIBIR_LOGS:
        logging.info("🚀 A iniciar a reversão do último registo de comida...")
        
    deleted_meal = await db.delete_last_meal(user_id)
    
    if deleted_meal:
        desc = deleted_meal['food_description']
        cal = deleted_meal['calories']
        msg = f"✅ Sucesso! Apaguei o registo de *{desc}* ({cal} kcal) do seu diário.\nPode enviar a anotação correta agora!"
        if EXIBIR_LOGS:
            logging.info(f"✅ Refeição apagada do banco de dados: {desc}")
    else:
        msg = "⚠️ Não encontrei nenhuma refeição no seu diário para apagar."
        if EXIBIR_LOGS:
            logging.info("⚠️ Tentativa de desfazer falhou: nenhuma refeição encontrada.")
            
    await update.message.reply_text(msg, parse_mode='Markdown', reply_markup=MAIN_MENU_KEYBOARD)

async def resumo_dia(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.message.from_user.id
    user_db = await db.get_user(user_id)
    
    if not user_db or user_db.get('step') != 'DONE':
        await update.message.reply_text("Por favor, configure seu perfil primeiro usando /start.")
        return
        
    meals = await db.get_meals_today(user_id)
    goal = user_db['daily_goal']
    water_drunk_ml = await db.get_water_today(user_id)
    water_goal_ml = user_db.get('daily_water_goal', 0)

    import json
    total_cal = 0
    total_sugar = 0
    total_prot = 0
    total_carb = 0
    total_fat = 0
    total_fiber = 0
    
    total_sodium = 0
    total_calcium = 0
    total_zinc = 0
    total_iron = 0
    total_potassium = 0
    total_vit_c = 0
    total_vit_a = 0

    agua_extra = 0

    if EXIBIR_LOGS:
        logging.info("🚀 Iniciando a varredura e extração de nutrientes do diário de refeições...")

    for m in meals:
        total_cal += m.get('calories', 0)
            
        if m.get('macros'):
            try:
                macros_dict = json.loads(m['macros'])
                total_prot += float(macros_dict.get('protein_g', 0))
                total_carb += float(macros_dict.get('carbs_g', 0))
                total_fat += float(macros_dict.get('fat_g', 0))
                total_sugar += float(macros_dict.get('sugar_g', 0))
                total_fiber += float(macros_dict.get('fiber_g', 0))
            except Exception as e:
                if EXIBIR_LOGS: logging.error(f"⚠️ Erro ao desempacotar macronutrientes: {e}")
                
        if m.get('micronutrients'):
            try:
                micros_dict = json.loads(m['micronutrients'])
                total_sodium += float(micros_dict.get('sodium_mg', 0))
                total_calcium += float(micros_dict.get('calcium_mg', 0))
                total_zinc += float(micros_dict.get('zinc_mg', 0))
                total_iron += float(micros_dict.get('iron_mg', 0))
                total_potassium += float(micros_dict.get('potassium_mg', 0))
                total_vit_c += float(micros_dict.get('vitamin_c_mg', 0))
                total_vit_a += float(micros_dict.get('vitamin_a_mcg', 0))
                agua_extra += int(micros_dict.get('water_penalty_ml', 0))
            except Exception as e:
                if EXIBIR_LOGS: logging.error(f"⚠️ Erro ao desempacotar micronutrientes: {e}")

    if EXIBIR_LOGS:
        logging.info("✅ Extração concluída. Nutrientes somados com sucesso!")

    def barra_simples(valor, meta, tamanho=8):
        pct = min(valor / meta, 1.0) if meta > 0 else 0
        cheios = round(pct * tamanho)
        return "█" * cheios + "░" * (tamanho - cheios)

    def barra_faixa(valor, meta_min, meta_max, tamanho=10):
        scale_max = meta_max * 1.4
        pos_min = round(meta_min / scale_max * tamanho)
        pos_max = round(meta_max / scale_max * tamanho)
        pos_val = min(round(valor / scale_max * tamanho), tamanho + 1)

        bar = []
        for i in range(tamanho):
            if i < pos_min:
                bar.append("█" if i < pos_val else "░")
            elif i < pos_max:
                bar.append("█" if i < pos_val else "░")
            else:
                bar.append("▓" if i < pos_val else "▓" if i < tamanho else "░")
        if 0 < pos_max < tamanho:
            bar.insert(pos_max, "│")
        if 0 < pos_min < tamanho:
            bar.insert(pos_min, "│")
        return "".join(bar)

    def linha_nutriente(emoji, nome, valor, meta_min, unidade, modo='min', meta_max=None):
        if modo == 'max':
            bar = barra_faixa(valor, 0, meta_min, tamanho=10)
            icone = "✅" if valor <= meta_min else "❌"
            pct = round((valor / meta_min * 100)) if meta_min > 0 else 0
            obs = "dentro do limite" if icone == "✅" else "acima do limite!"
            return (
                f"{emoji} *{nome}* {icone} _{obs}_\n"
                f"`{bar}` {valor}{unidade} / máx {meta_min}{unidade} _{pct}%_\n"
            )
        elif modo == 'range' and meta_max is not None:
            bar = barra_faixa(valor, meta_min, meta_max, tamanho=10)
            if valor < meta_min:
                icone, obs = "❌", "abaixo do mínimo"
            elif valor > meta_max:
                icone, obs = "❌", "acima do limite!"
            else:
                icone, obs = "✅", "na faixa ideal"
            return (
                f"{emoji} *{nome}* {icone} _{obs}_\n"
                f"`{bar}` {valor}{unidade}  _{meta_min}→{meta_max}{unidade}_\n"
            )
        else:
            bar = barra_simples(valor, meta_min)
            icone = "✅" if valor >= meta_min else "❌"
            pct = round((valor / meta_min * 100)) if meta_min > 0 else 0
            return f"{emoji} *{nome}* {icone}\n`{bar}` {valor}{unidade}/{meta_min}{unidade} _{pct}%_\n"

    GOAL_SODIUM    = 2300
    GOAL_CALCIUM   = 1000
    GOAL_ZINC      = 11
    GOAL_IRON      = 18
    GOAL_POTASSIUM = 3500
    GOAL_VIT_C     = 90
    GOAL_VIT_A_MIN = 900
    GOAL_VIT_A_MAX = 3000

    hoje = db.get_sp_time().strftime("%d/%m/%Y")
    aviso_faixa = (
        "🎯 *Regra de Ouro:* O objetivo não é acertar o número exato, mas sim manter o consumo dentro da faixa recomendada. "
        "Ficar abaixo da faixa não acelera o emagrecimento (causa perda de massa magra) "
        "e ficar acima não melhora o ganho de músculos (gera acúmulo de gordura). Mantenha-se no alvo!\n\n"
    )
    resumo_text = f"╔══ 📊 *DIÁRIO NUTRICIONAL*\n╚══ _{hoje}_\n\n{aviso_faixa}"

    resumo_text += "🍽 *Refeições registradas:*\n"
    if len(meals) == 0:
        resumo_text += "  _Nenhuma refeição hoje ainda._\n"
    else:
        refeicoes_agrupadas = {}
        for m in meals:
            hora = int(m['timestamp'][11:13])
            minuto_registro = m['timestamp'][11:16]
            
            if 5 <= hora < 11:
                nome_ref = "☕ Café da Manhã"
            elif 11 <= hora < 15:
                nome_ref = "🍽️ Almoço"
            elif 15 <= hora < 19:
                nome_ref = "🥪 Lanche da Tarde"
            else:
                nome_ref = "🌙 Jantar/Ceia"
                
            chave_grupo = f"{nome_ref} ({minuto_registro})"
            
            if chave_grupo not in refeicoes_agrupadas:
                refeicoes_agrupadas[chave_grupo] = {"itens": [], "calorias": 0}
                
            refeicoes_agrupadas[chave_grupo]["itens"].append(m['food_description'])
            refeicoes_agrupadas[chave_grupo]["calorias"] += m['calories']
            
        for grupo, dados in refeicoes_agrupadas.items():
            resumo_text += f"\n  *{grupo}* — _{dados['calorias']} kcal_\n"
            for item in dados["itens"]:
                desc_curta = item[:35] + ("…" if len(item) > 35 else "")
                resumo_text += f"   • {desc_curta}\n"

    cal_min = max(1200, goal - 100)
    cal_max = goal + 100
    
    if total_cal < cal_min:
        cal_icon = "❌"
        faltam = cal_min - total_cal
        cal_status = f"_Abaixo da faixa! Faltam pelo menos *{faltam} kcal* para bater o piso da sua dieta._"
    elif total_cal > cal_max:
        cal_icon = "❌"
        excesso = total_cal - cal_max
        cal_status = f"_Acima da faixa! Você ultrapassou o teto em *{excesso} kcal*._"
    else:
        cal_icon = "✅"
        cal_status = "_Na faixa ideal! Excelente controle calórico._"

    resumo_text += f"\n━━━━━━━━━━━━━━━━━━━\n"
    resumo_text += f"🔥 *Calorias* {cal_icon}\n"
    cal_bar = barra_faixa(total_cal, cal_min, cal_max, tamanho=10)
    resumo_text += f"`{cal_bar}` *{total_cal}* kcal  _{cal_min}→{cal_max}_\n"

    water_goal_ml += agua_extra
    water_pct  = round((water_drunk_ml / water_goal_ml * 100)) if water_goal_ml > 0 else 0
    water_icon = "✅" if water_drunk_ml >= water_goal_ml else "❌"
    water_bar  = barra_simples(water_drunk_ml, water_goal_ml)
    resumo_text += f"\n💧 *Hidratação* {water_icon}\n"
    resumo_text += f"`{water_bar}` *{water_drunk_ml/1000:.1f}L* / {water_goal_ml/1000:.1f}L _{water_pct}%_\n"
    if agua_extra > 0:
        resumo_text += f"_(+ {agua_extra/1000:.1f}L de compensação metabólica hoje)_\n"

    GOAL_PROT = round((goal * 0.30) / 4)
    GOAL_CARB = round((goal * 0.40) / 4)
    GOAL_FAT  = round((goal * 0.30) / 9)
    
    resumo_text += f"\n━━━━━━━━━━━━━━━━━━━\n"
    resumo_text += "🍱 *Macronutrientes Principais:*\n\n"
    resumo_text += linha_nutriente("💪", "Proteínas", round(total_prot, 1), max(0, GOAL_PROT - 15), "g", modo='range', meta_max=GOAL_PROT + 15)
    resumo_text += linha_nutriente("🍞", "Carboidratos", round(total_carb, 1), max(0, GOAL_CARB - 20), "g", modo='range', meta_max=GOAL_CARB + 20)
    resumo_text += linha_nutriente("🥑", "Gorduras", round(total_fat, 1), max(0, GOAL_FAT - 10), "g", modo='range', meta_max=GOAL_FAT + 10)
    resumo_text += linha_nutriente("🌾", "Fibras", round(total_fiber, 1), 25, "g", modo='min')

    GOAL_SUGAR = 50 
    
    try:
        from nutrition_utils import calculate_micronutrient_goals
        user_age = user_db.get('age', 30)
        user_gender = user_db.get('gender', 'M')
        micros_goals = calculate_micronutrient_goals(user_age, user_gender)
        
        GOAL_SODIUM    = micros_goals.get('sodium', 2300)
        GOAL_CALCIUM   = micros_goals.get('calcium', 1000)
        GOAL_ZINC      = micros_goals.get('zinc', 11)
        GOAL_IRON      = micros_goals.get('iron', 18)
        GOAL_POTASSIUM = micros_goals.get('potassium', 3500)
        GOAL_VIT_C     = micros_goals.get('vit_c', 90)
        GOAL_VIT_A_MIN = micros_goals.get('vit_a_min', 900)
        GOAL_VIT_A_MAX = micros_goals.get('vit_a_max', 3000)
    except Exception as e:
        if EXIBIR_LOGS: logging.error(f"Erro ao calcular metas biologicas: {e}")
        GOAL_SODIUM, GOAL_CALCIUM, GOAL_ZINC, GOAL_IRON, GOAL_POTASSIUM, GOAL_VIT_C, GOAL_VIT_A_MIN, GOAL_VIT_A_MAX = 2300, 1000, 11, 18, 3500, 90, 900, 3000

    resumo_text += f"\n━━━━━━━━━━━━━━━━━━━\n"
    resumo_text += "🔬 *Nutrientes Monitorados:*\n\n"
    resumo_text += linha_nutriente("🍬", "Açúcar",    round(total_sugar, 1),     GOAL_SUGAR,     "g",   modo='max')
    resumo_text += linha_nutriente("🧂", "Sódio",     total_sodium,    GOAL_SODIUM,    "mg",  modo='max')
    resumo_text += linha_nutriente("🥛", "Cálcio",    total_calcium,   GOAL_CALCIUM,   "mg",  modo='min')
    resumo_text += linha_nutriente("🥩", "Zinco",     total_zinc,      GOAL_ZINC,      "mg",  modo='min')
    resumo_text += linha_nutriente("🧲", "Ferro",     total_iron,      GOAL_IRON,      "mg",  modo='min')
    resumo_text += linha_nutriente("🍌", "Potássio",  total_potassium, GOAL_POTASSIUM, "mg",  modo='min')
    resumo_text += linha_nutriente("🍊", "Vitamina C",total_vit_c,     GOAL_VIT_C,     "mg",  modo='min')
    resumo_text += linha_nutriente("🥕", "Vitamina A",total_vit_a,     GOAL_VIT_A_MIN, "mcg", modo='range', meta_max=GOAL_VIT_A_MAX)

    resumo_text += f"\n━━━━━━━━━━━━━━━━━━━\n{cal_status}\n"

    if EXIBIR_LOGS:
        logging.info("📤 Enviando o resumo do dia em formato de texto plano de forma direta...")

    await update.message.reply_text(resumo_text, parse_mode='Markdown', reply_markup=MAIN_MENU_KEYBOARD)

async def check_reminders(context: ContextTypes.DEFAULT_TYPE):
    current_time = db.get_sp_time()
    hour = current_time.hour
    
    # Restrict to 8:00 - 22:00
    if not (8 <= hour < 22):
        return
        
    users = await db.get_all_users()
    elapsed_hours = (current_time.hour - 8) + (current_time.minute / 60.0)
    target_fraction = elapsed_hours / 14.0 # 14 hours active window
    
    for user in users:
        user_id = user['telegram_id']
        
        # --- WATER REMINDER ---
        water_goal = user.get('daily_water_goal') or 0
        if water_goal > 0:
            meals = await db.get_meals_today(user_id)
            import json
            agua_extra = 0
            for m in meals:
                if m.get('micronutrients'):
                    try:
                        agua_extra += json.loads(m['micronutrients']).get('water_penalty_ml', 0)
                    except: pass
                    
            water_goal += agua_extra
            
            if EXIBIR_LOGS and agua_extra > 0:
                logging.info(f"💧 Ajuste dinâmico de água para {user_id}: +{agua_extra}ml ativos no lembrete.")
                
            water_drunk = await db.get_water_today(user_id)
            expected_water_now = water_goal * target_fraction
            deficit_ml = expected_water_now - water_drunk
            
            if deficit_ml >= 250:
                last_str = user.get('last_water_reminder')
                hours_since = 999
                if last_str:
                    try:
                        last_time = datetime.strptime(last_str, '%Y-%m-%d %H:%M:%S')
                        last_time = last_time.replace(tzinfo=current_time.tzinfo)
                        hours_since = (current_time - last_time).total_seconds() / 3600.0
                    except:
                        pass
                
                threshold = 1.5 if deficit_ml >= 500 else 2.5
                
                if hours_since >= threshold:
                    missing = water_goal - water_drunk
                    if missing > 0:
                        msg = (
                            "🔔 *Lembrete de Hidratação*\n\n"
                            f"Notei que você está um pouco atrás na sua meta de água hoje. Ainda faltam *{missing/1000:.1f}L*.\n\n"
                            "Que tal fazer uma pausa rápida agora e beber um copo de água? 🚰"
                        )
                        try:
                            await context.bot.send_message(chat_id=user_id, text=msg, parse_mode='Markdown')
                            await db.save_user(user_id, {"last_water_reminder": current_time.strftime('%Y-%m-%d %H:%M:%S')})
                            if EXIBIR_LOGS:
                                logging.info(f"✅ Lembrete de água enviado com sucesso para o usuário {user_id}.")
                        except Exception as e:
                            logging.error(f"Falha ao disparar alerta de água: {e}")
                            
        # --- FOOD REMINDER ---
        cal_goal = user.get('daily_goal') or 0
        if cal_goal > 0:
            meals = await db.get_meals_today(user_id)
            cal_eaten = sum(m['calories'] for m in meals)
            deficit = target_fraction - (cal_eaten / cal_goal)
            
            if deficit > 0.15:
                last_str = user.get('last_food_reminder')
                hours_since = 999
                if last_str:
                    try:
                        last_time = datetime.strptime(last_str, '%Y-%m-%d %H:%M:%S')
                        last_time = last_time.replace(tzinfo=current_time.tzinfo)
                        hours_since = (current_time - last_time).total_seconds() / 3600.0
                    except:
                        pass
                
                threshold = 2.0 if deficit > 0.3 else 4.0
                
                if hours_since >= threshold:
                    missing = cal_goal - cal_eaten
                    if missing > 0:
                        goal_type_val = user.get('goal_type', 'manter')
                        
                        if goal_type_val == 'ganhar':
                            msg_alerta = "Você está atrasado na sua meta de superávit para este horário.\nLembre-se de que precisa comer para construir músculos e evitar o catabolismo!"
                        elif goal_type_val == 'emagrecer':
                            msg_alerta = "Você está comendo menos do que o planejado para este horário.\nAtenção aos grandes intervalos para evitar picos de fome e compulsão mais tarde."
                        else:
                            msg_alerta = "Você está um pouco atrasado no seu fracionamento de energia diário."
                            
                        msg = (
                            f"🔔 *Lembrete de Refeição*\n\n"
                            f"{msg_alerta}\n\n"
                            f"Você ainda tem *{int(missing)} kcal* planejadas para hoje. Não se esqueça de comer e me mandar a foto do seu prato! 🍽️"
                        )
                        
                        try:
                            await context.bot.send_message(chat_id=user_id, text=msg, parse_mode='Markdown')
                            await db.save_user(user_id, {"last_food_reminder": current_time.strftime('%Y-%m-%d %H:%M:%S')})
                            if EXIBIR_LOGS:
                                logging.info(f"✅ Lembrete de comida enviado para o usuário {user_id} (Meta: {goal_type_val}).")
                        except Exception as e:
                            logging.error(f"Falha ao disparar alerta de comida: {e}")
       
        # --- WEEKLY CHECK-IN ---
        start_date_str = user.get('diet_start_date')
        if start_date_str:
            try:
                start_date = datetime.strptime(start_date_str, '%Y-%m-%d %H:%M:%S').replace(tzinfo=current_time.tzinfo)
                days_since = (current_time - start_date).total_seconds() / (3600.0 * 24.0)
                
                if days_since >= 7.0:
                    goal_type_val = user.get('goal_type', 'manter')
                    fase_atual = user.get('diet_phase', 1)
                    
                    if goal_type_val in ['emagrecer', 'ganhar']:
                        if EXIBIR_LOGS:
                            logging.info(f"🚀 Iniciando triagem semanal para o usuário {user_id}...")
                            
                        msg = (
                            f"🗓️ *Check-in Semanal*\n\n"
                            f"Parabéns! Você completou 7 dias de monitoramento na *Fase {fase_atual}*.\n\n"
                            "Antes de decidirmos o nosso próximo passo, como você descreveria seu nível de energia e fome nos últimos dias?"
                        )
                        keyboard = [
                            [InlineKeyboardButton("😫 Muita fome / Fraqueza", callback_data="checkin_diag_bad")],
                            [InlineKeyboardButton("😐 Fome moderada / Energia OK", callback_data="checkin_diag_ok")],
                            [InlineKeyboardButton("😎 Sem fome / Energia alta", callback_data="checkin_diag_good")]
                        ]
                        try:
                            await context.bot.send_message(chat_id=user_id, text=msg, parse_mode='Markdown', reply_markup=InlineKeyboardMarkup(keyboard))
                            if EXIBIR_LOGS:
                                logging.info(f"✅ Formulário de triagem enviado com sucesso para {user_id}.")
                        except Exception as e:
                            logging.error(f"Erro ao enviar check-in semanal: {e}")
                            
                    await db.save_user(user_id, {"diet_start_date": current_time.strftime('%Y-%m-%d %H:%M:%S')})
            except Exception as e:
                logging.error(f"Erro no processamento do check-in semanal: {e}")

async def handle_weekly_checkin(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer()
    user_id = query.from_user.id
    action = query.data
    user_db = await db.get_user(user_id)
    if not user_db: return
    
    fase_atual = user_db.get('diet_phase', 1)
    
    if action.startswith("checkin_diag_"):
        diag = ""
        recom = ""
        opts = []
        
        if action == "checkin_diag_bad":
            diag = "📝 *Diagnóstico:* Seu corpo está sinalizando um nível alto de estresse."
            recom = "⚠️ *Recomendação:* O cenário ideal agora é RECUAR ou MANTER a fase atual, protegendo seus músculos e estabilizando seu metabolismo antes de novas mudanças."
            if fase_atual > 1: opts.append([InlineKeyboardButton(f"🔙 Recuar para Fase {fase_atual-1}", callback_data="do_phase_down")])
        elif action == "checkin_diag_ok":
            diag = "📝 *Diagnóstico:* Você atingiu um ótimo ponto de equilíbrio metabólico."
            recom = "✅ *Recomendação:* A conduta mais segura e eficiente é MANTER esta fase para consolidar a sua saúde e os resultados obtidos."
            if fase_atual < 3: opts.append([InlineKeyboardButton(f"🚀 Avançar para Fase {fase_atual+1}", callback_data="do_phase_up")])
        else:
            diag = "📝 *Diagnóstico:* Você está com uma excelente reserva de energia."
            recom = "🚀 *Recomendação:* Com essa disposição, nós podemos AVANÇAR para a próxima etapa se você desejar acelerar os resultados."
            if fase_atual < 3: opts.append([InlineKeyboardButton(f"🚀 Avançar para Fase {fase_atual+1}", callback_data="do_phase_up")])
            
        opts.append([InlineKeyboardButton(f"🧘 Manter Fase {fase_atual}", callback_data="do_phase_stay")])
        
        texto_final = f"{diag}\n\n{recom}\n\n👇 _O que você prefere fazer agora?_"
        
        if EXIBIR_LOGS:
            logging.info(f"✅ Diagnóstico da triagem gerado. Aguardando decisão de fase...")
            
        await query.edit_message_text(texto_final, parse_mode='Markdown', reply_markup=InlineKeyboardMarkup(opts))
        return

    if action.startswith("do_phase_"):
        nova_fase = fase_atual
        if "up" in action: nova_fase += 1
        elif "down" in action: nova_fase -= 1
        
        if action == "do_phase_stay":
            await query.edit_message_text(f"Ótimo! Mantivemos a *Fase {fase_atual}*. A constância é o segredo! 🏃")
        else:
            goal_type = user_db.get('goal_type', 'emagrecer')
            ajustes = {1: 200, 2: 400, 3: 600}
            sinal = -1 if goal_type == 'emagrecer' else 1
            
            bmr = calculate_bmr(user_db['weight'], user_db['height'], user_db['age'], user_db['gender'])
            tdee = calculate_daily_calorie_goal(bmr, user_db['activity_level'])
            new_goal = int(tdee + (ajustes[nova_fase] * sinal))
            
            await db.save_user(user_id, {"daily_goal": new_goal, "diet_phase": nova_fase})
            
            final_msg = f"Meta atualizada para a *Fase {nova_fase}* ({new_goal} kcal)."
            if nova_fase == 3:
                final_msg += "\n\n🚨 *Atenção:* A Fase 3 é extrema. Não a utilize por mais de 2 semanas seguidas para evitar a perda de massa magra."
            
            await query.edit_message_text(final_msg, parse_mode='Markdown')
            
        await context.bot.send_message(chat_id=user_id, text="O que deseja fazer agora?", reply_markup=MAIN_MENU_KEYBOARD)

async def handle_shopping_check(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer()
    
    keyboard = query.message.reply_markup.inline_keyboard
    new_keyboard = []
    
    for row in keyboard:
        new_row = []
        for btn in row:
            if btn.callback_data == query.data:
                new_text = btn.text.replace("⬜", "✅") if "⬜" in btn.text else btn.text.replace("✅", "⬜")
                new_row.append(InlineKeyboardButton(new_text, callback_data=btn.callback_data))
            else:
                new_row.append(btn)
        new_keyboard.append(new_row)
        
    try:
        await query.edit_message_reply_markup(reply_markup=InlineKeyboardMarkup(new_keyboard))
    except Exception:
        pass

def main():
    # Token from environment
    token = os.environ.get("TELEGRAM_TOKEN")
    if not token:
        logging.error("TELEGRAM_TOKEN env variable is missing!")
        return
        
    # Initialize DB
    db.init_db()

    app = ApplicationBuilder().token(token).build()
    
    # Adicionar loop de alarmes inteligentes (runs every 30 minutes)
    app.job_queue.run_repeating(check_reminders, interval=1800, first=60)

    conv_handler = ConversationHandler(
        entry_points=[CommandHandler("start", start), CommandHandler("refazer", redo_profile)],
        states={
            API_KEY: [MessageHandler(filters.TEXT & ~filters.COMMAND, receive_api_key)],
            GENDER: [MessageHandler(filters.TEXT & ~filters.COMMAND, gender)],
            AGE: [MessageHandler(filters.TEXT & ~filters.COMMAND, age)],
            WEIGHT: [MessageHandler(filters.TEXT & ~filters.COMMAND, weight)],
            HEIGHT: [MessageHandler(filters.TEXT & ~filters.COMMAND, height)],
            ACTIVITY: [MessageHandler(filters.TEXT & ~filters.COMMAND, activity)],
            GOAL_TYPE: [MessageHandler(filters.TEXT & ~filters.COMMAND, goal_type)],
            EXPERIENCE: [MessageHandler(filters.TEXT & ~filters.COMMAND, experience_level)],
        },
        fallbacks=[
            CommandHandler("cancelar", cancel),
            CommandHandler("refazer", redo_profile),
            CommandHandler("start", start)
        ],
    )

    app.add_handler(conv_handler)
    app.add_handler(CommandHandler("resumo", resumo_dia))
    app.add_handler(CommandHandler("agua", registrar_agua))
    app.add_handler(CommandHandler("desfazer", desfazer_refeicao))
    app.add_handler(CallbackQueryHandler(handle_meal_confirmation, pattern="^(meal_|it_)"))
    app.add_handler(CallbackQueryHandler(handle_weekly_checkin, pattern="^(checkin_|do_phase_)"))
    app.add_handler(CallbackQueryHandler(handle_shopping_check, pattern="^shop_"))
    
    # General fallback for any message (other than commands) sent outside the ConversationHandler
    app.add_handler(MessageHandler(filters.ALL & ~filters.COMMAND, handle_message))

    logging.info("Bot is polling...")
    app.run_polling()

if __name__ == '__main__':
    main()