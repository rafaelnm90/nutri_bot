def calculate_bmr(weight_kg, height_cm, age_years, gender):
    """
    Calculates Basal Metabolic Rate using Harris-Benedict Equation.
    gender: 'M' or 'F'
    """
    if gender.upper() == 'M':
        # BMR = 88.362 + (13.397 x weight) + (4.799 x height) - (5.677 x age)
        return 88.362 + (13.397 * weight_kg) + (4.799 * height_cm) - (5.677 * age_years)
    elif gender.upper() == 'F':
        # BMR = 447.593 + (9.247 x weight) + (3.098 x height) - (4.330 x age)
        return 447.593 + (9.247 * weight_kg) + (3.098 * height_cm) - (4.330 * age_years)
    else:
        raise ValueError("Gender must be 'M' or 'F'")


def calculate_daily_calorie_goal(bmr, activity_level):
    """
    Calculates Total Daily Energy Expenditure (TDEE) and sets a basic goal.
    Activity strings match the bot onboarding choices.
    """
    multipliers = {
        'sedentario': 1.2,
        'leve': 1.375,
        'moderado': 1.55,
        'intenso': 1.725
    }
    
    # Default to lightly active if not found
    mult = multipliers.get(activity_level.lower(), 1.375)
    
    tdee = bmr * mult
    
    # We will just set the TDEE as the maintenance goal for now.
    # In a full app we could ask if they want to lose or gain weight.
    return int(tdee)

def calculate_daily_water_goal(weight_kg):
    """
    Calcula a meta de água diária em mililitros (mL).
    Geralmente 35ml por kg de peso corporal.
    """
    return int(weight_kg * 35)

EXIBIR_LOGS = True

def calculate_micronutrient_goals(age, gender):
    """Calcula as metas de micronutrientes baseadas em idade e sexo (DRIs)."""
    if EXIBIR_LOGS:
        print(f"🚀 Calculando metas biológicas de micronutrientes (Idade: {age}, Sexo: {gender})...")
        
    gender_upper = str(gender).upper()
    
    # Valores base gerais recomendados
    goals = {
        "sodium": 2000,
        "potassium": 3500,
        "vit_a_max": 3000
    }
    
    if gender_upper == 'F':
        # Mulheres necessitam de mais ferro na idade fértil e mais cálcio após a menopausa
        goals["calcium"] = 1200 if age >= 51 else 1000
        goals["iron"] = 8 if age >= 51 else 18
        goals["zinc"] = 8
        goals["vit_c"] = 75
        goals["vit_a_min"] = 700
    else:
        # Homens mantêm uma demanda de ferro baixa, mas exigem mais zinco e vitamina C
        goals["calcium"] = 1200 if age >= 71 else 1000
        goals["iron"] = 8
        goals["zinc"] = 11
        goals["vit_c"] = 90
        goals["vit_a_min"] = 900
        
    if EXIBIR_LOGS:
        print("✅ Metas calculadas com sucesso.")
        
    return goals