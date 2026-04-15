import sqlite3
import aiosqlite
from datetime import datetime
import pytz
import json

DB_NAME = "nutri_bot.db"
EXIBIR_LOGS = True

class AsyncDBContext:
    async def __aenter__(self):
        self.conn = await aiosqlite.connect(DB_NAME)
        self.conn.row_factory = aiosqlite.Row
        return self.conn
        
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        await self.conn.close()

async def get_async_connection():
    return AsyncDBContext()

def get_connection():
    conn = sqlite3.connect(DB_NAME)
    conn.row_factory = sqlite3.Row
    return conn

def get_sp_time():
    sp_tz = pytz.timezone("America/Sao_Paulo")
    return datetime.now(sp_tz)

def init_db():
    conn = get_connection()
    c = conn.cursor()
    
    # 1. Tenta criar a tabela com TODAS as colunas (funciona para bancos novos)
    c.execute('''
        CREATE TABLE IF NOT EXISTS users (
            telegram_id INTEGER PRIMARY KEY,
            name TEXT,
            age INTEGER,
            weight REAL,
            height REAL,
            gender TEXT,
            activity_level TEXT,
            api_key TEXT,
            daily_goal INTEGER,
            daily_water_goal INTEGER,
            goal_type TEXT,
            experience_level TEXT,
            diet_start_date DATETIME,
            diet_phase INTEGER DEFAULT 1,
            step TEXT,
            last_water_reminder DATETIME,
            last_food_reminder DATETIME
        )
    ''')
    
    # 2. Tenta injetar colunas novas uma a uma (necessário para quem já tem o arquivo .db)
    colunas_para_adicionar = [
        ("goal_type", "TEXT"),
        ("experience_level", "TEXT"),
        ("diet_start_date", "DATETIME"),
        ("diet_phase", "INTEGER DEFAULT 1"),
        ("api_key", "TEXT")
    ]
    
    for nome_col, tipo_col in colunas_para_adicionar:
        try:
            c.execute(f"ALTER TABLE users ADD COLUMN {nome_col} {tipo_col}")
            if EXIBIR_LOGS: print(f"✅ Coluna '{nome_col}' injetada com sucesso.")
        except sqlite3.OperationalError:
            # Se a coluna já existir, o SQLite lança erro e nós apenas ignoramos
            pass
        
    # Create Meals table
    c.execute('''
        CREATE TABLE IF NOT EXISTS meals (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER,
            timestamp DATETIME,
            food_description TEXT,
            calories INTEGER,
            macros TEXT,
            micronutrients TEXT,
            FOREIGN KEY(user_id) REFERENCES users(telegram_id)
        )
    ''')
    
    # Create Water logs table
    c.execute('''
        CREATE TABLE IF NOT EXISTS water_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER,
            timestamp DATETIME,
            amount_ml INTEGER,
            FOREIGN KEY(user_id) REFERENCES users(telegram_id)
        )
    ''')
    
    if EXIBIR_LOGS:
        print("🔧 Verificando ou criando tabela de exercícios no banco...")
        
    # Create Exercises table
    c.execute('''
        CREATE TABLE IF NOT EXISTS exercises (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER,
            timestamp DATETIME,
            description TEXT,
            duration_min INTEGER,
            calories_burned INTEGER,
            FOREIGN KEY(user_id) REFERENCES users(telegram_id)
        )
    ''')
    
    conn.commit()
    conn.close()

async def get_user(telegram_id):
    if EXIBIR_LOGS:
        print(f"🚀 Iniciando busca assíncrona do usuário: {telegram_id}...")
    async with await get_async_connection() as conn:
        async with conn.execute("SELECT * FROM users WHERE telegram_id = ?", (telegram_id,)) as cursor:
            row = await cursor.fetchone()
            if EXIBIR_LOGS:
                print(f"✅ Sucesso na leitura do usuário {telegram_id}.")
            return dict(row) if row else None

async def save_user(telegram_id, data):
    if EXIBIR_LOGS:
        print(f"🚀 Iniciando gravação assíncrona para o usuário: {telegram_id}...")
    async with await get_async_connection() as conn:
        async with conn.execute("SELECT telegram_id FROM users WHERE telegram_id = ?", (telegram_id,)) as cursor:
            exists = await cursor.fetchone()
        
        if exists:
            set_clauses = []
            values = []
            for key, value in data.items():
                set_clauses.append(f"{key} = ?")
                values.append(value)
            
            if set_clauses:
                query = f"UPDATE users SET {', '.join(set_clauses)} WHERE telegram_id = ?"
                values.append(telegram_id)
                await conn.execute(query, values)
        else:
            data['telegram_id'] = telegram_id
            keys = ', '.join(data.keys())
            placeholders = ', '.join(['?'] * len(data))
            values = list(data.values())
            
            query = f"INSERT INTO users ({keys}) VALUES ({placeholders})"
            await conn.execute(query, values)
            
        await conn.commit()
        if EXIBIR_LOGS:
            print(f"✅ Sucesso ao salvar os dados do usuário {telegram_id}.")

async def add_meal(user_id, description, calories, macros=None, micronutrients=None):
    if EXIBIR_LOGS:
        print(f"🚀 Iniciando registro assíncrono de refeição: {description}...")
    macros_str = json.dumps(macros) if macros else None
    micros_str = json.dumps(micronutrients) if micronutrients else None
    current_time = get_sp_time().strftime('%Y-%m-%d %H:%M:%S')
    async with await get_async_connection() as conn:
        await conn.execute('''
            INSERT INTO meals (user_id, timestamp, food_description, calories, macros, micronutrients)
            VALUES (?, ?, ?, ?, ?, ?)
        ''', (user_id, current_time, description, calories, macros_str, micros_str))
        await conn.commit()
        if EXIBIR_LOGS:
            print("✅ Sucesso ao registrar a refeição.")

async def add_water(user_id, amount_ml):
    if EXIBIR_LOGS:
        print(f"🚀 Iniciando registro assíncrono de hidratação: {amount_ml}ml...")
    current_time = get_sp_time().strftime('%Y-%m-%d %H:%M:%S')
    async with await get_async_connection() as conn:
        await conn.execute('''
            INSERT INTO water_logs (user_id, timestamp, amount_ml)
            VALUES (?, ?, ?)
        ''', (user_id, current_time, amount_ml))
        await conn.commit()
        if EXIBIR_LOGS:
            print("✅ Sucesso ao registrar a hidratação.")

async def add_exercise(user_id, description, duration_min, calories_burned):
    if EXIBIR_LOGS:
        print(f"🚀 Iniciando registro assíncrono de exercício: {description}...")
    current_time = get_sp_time().strftime('%Y-%m-%d %H:%M:%S')
    async with await get_async_connection() as conn:
        await conn.execute('''
            INSERT INTO exercises (user_id, timestamp, description, duration_min, calories_burned)
            VALUES (?, ?, ?, ?, ?)
        ''', (user_id, current_time, description, duration_min, calories_burned))
        await conn.commit()
        if EXIBIR_LOGS:
            print("✅ Sucesso ao registrar o exercício.")

async def get_meals_today(user_id):
    if EXIBIR_LOGS:
        print("🚀 Iniciando leitura assíncrona das refeições de hoje...")
    today_start = get_sp_time().strftime('%Y-%m-%d 00:00:00')
    async with await get_async_connection() as conn:
        async with conn.execute('''
            SELECT * FROM meals 
            WHERE user_id = ? AND timestamp >= ?
            ORDER BY timestamp ASC
        ''', (user_id, today_start)) as cursor:
            rows = await cursor.fetchall()
            if EXIBIR_LOGS:
                print("✅ Sucesso na leitura das refeições.")
            return [dict(row) for row in rows]

async def get_water_today(user_id):
    if EXIBIR_LOGS:
        print("🚀 Iniciando leitura assíncrona da hidratação de hoje...")
    today_start = get_sp_time().strftime('%Y-%m-%d 00:00:00')
    async with await get_async_connection() as conn:
        async with conn.execute('''
            SELECT SUM(amount_ml) as total_ml FROM water_logs 
            WHERE user_id = ? AND timestamp >= ?
        ''', (user_id, today_start)) as cursor:
            row = await cursor.fetchone()
            if EXIBIR_LOGS:
                print("✅ Sucesso na leitura da hidratação.")
            return row['total_ml'] if row and row['total_ml'] else 0

async def delete_last_meal(user_id):
    if EXIBIR_LOGS:
        print("🚀 Iniciando exclusão assíncrona da última refeição...")
    async with await get_async_connection() as conn:
        async with conn.execute('''
            SELECT id, food_description, calories FROM meals 
            WHERE user_id = ? 
            ORDER BY timestamp DESC LIMIT 1
        ''', (user_id,)) as cursor:
            row = await cursor.fetchone()
        
        if row:
            await conn.execute('DELETE FROM meals WHERE id = ?', (row['id'],))
            await conn.commit()
            if EXIBIR_LOGS:
                print("✅ Sucesso ao excluir a refeição.")
            return dict(row)
        if EXIBIR_LOGS:
            print("⚠️ Nenhuma refeição encontrada para exclusão.")
        return None

async def get_all_users():
    if EXIBIR_LOGS:
        print("🚀 Iniciando varredura assíncrona de todos os usuários...")
    async with await get_async_connection() as conn:
        async with conn.execute("SELECT * FROM users WHERE step = 'DONE'") as cursor:
            rows = await cursor.fetchall()
            if EXIBIR_LOGS:
                print(f"✅ Sucesso. {len(rows)} usuários localizados.")
            return [dict(row) for row in rows]