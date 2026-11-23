# Listas compartidas entre el conversor y el generador del cliente. Copian el orden del mod de
# Java (RoccoExpression, RoccoModel) porque el script de Bedrock usa los mismos numeros.

# El orden de RoccoExpression: la propiedad rocco:face guarda este indice.
FACES = [
    "happy", "bored", "serious", "scared", "sad", "angry", "smug", "psycho", "dizzy",
    "hurt_light", "hurt", "sleep", "bliss", "focus", "windy", "mewing", "yawn", "think",
    "cheer", "hum", "sing", "wow", "refresh",
]
# Caras con los ojos cerrados: ahi no parpadea.
EYES_CLOSED = {"sleep", "bliss", "focus", "windy", "mewing", "yawn", "cheer", "hum", "sing", "refresh"}

LOCATORS = [
    "fx_head", "fx_mate", "fx_spout", "fx_magic_right", "fx_magic_left", "fx_wing_right", "fx_wing_left",
    "fx_tear_right", "fx_tear_left", "fx_snorkel", "fx_ring", "fx_boat", "fx_paddle_right", "fx_paddle_left",
    "fx_garden", "fx_cola", "fx_mic", "fx_belly", "fx_body", "fx_grave", "fx_soil", "item_right", "fx_held",
    "fx_backpack", "fx_coals", "fx_grill_smoke", "fx_meat", "fx_oil", "fx_fire", "fx_table",
]

# Las de Java que se quedan quietas en el ultimo cuadro. En Bedrock TODAS las que no son bucle
# se quedan asi: el script sabe cuanto dura cada una y pide la siguiente a tiempo, y sin esto
# Rocco volveria un instante a la pose de fabrica entre una y otra.
HOLD_LAST = set()

# Sonidos de Java que en Bedrock no van (la voz y la musica llegan por internet en Java).
SKIP_SOUNDS = {"voice", "music", "dance_music"}

# Particulas de Minecraft que usan las animaciones, con su nombre en Bedrock.
VANILLA_PARTICLES = {
    "minecraft:splash": ("mc_splash", "minecraft:water_splash_particle"),
    "minecraft:bubble": ("mc_bubble", "minecraft:basic_bubble_particle"),
    "minecraft:portal": ("mc_portal", "minecraft:portal_reverse_particle"),
}


def particle_key(effect):
    if effect in VANILLA_PARTICLES:
        return VANILLA_PARTICLES[effect][0]
    return effect.split(":", 1)[1]


def particle_id(key):
    for short, real in VANILLA_PARTICLES.values():
        if short == key:
            return real
    return "rocco:" + key


# Las particulas con clase propia en Java (sin Look): parecido a mano.
CUSTOM_LOOKS = {
    "mate_steam": {"min": 20, "max": 30, "size": 0.08, "grow": 2.5, "gravity": -0.25, "by_age": True, "fade": 0.0,
                   "bright": False, "spin": 0.0, "sway": 0.0, "drag": 0.98},
    "bliss_flower": {"min": 30, "max": 40, "size": 0.08, "grow": 1.0, "gravity": 0.1, "by_age": False, "fade": 0.7,
                     "bright": False, "spin": 0.05, "sway": 0.02, "drag": 0.96},
    "magic_rune": {"min": 20, "max": 30, "size": 0.09, "grow": 0.8, "gravity": -0.1, "by_age": True, "fade": 0.6,
                   "bright": True, "spin": 0.1, "sway": 0.0, "drag": 0.95},
    "magic_spark": {"min": 10, "max": 16, "size": 0.06, "grow": 0.6, "gravity": 0.0, "by_age": True, "fade": 0.5,
                    "bright": True, "spin": 0.0, "sway": 0.0, "drag": 0.9},
    "magic_burst": {"min": 8, "max": 8, "size": 0.25, "grow": 3.0, "gravity": 0.0, "by_age": True, "fade": 0.2,
                    "bright": True, "spin": 0.0, "sway": 0.0, "drag": 0.0},
    "butterfly": {"min": 60, "max": 80, "size": 0.1, "grow": 1.0, "gravity": -0.02, "by_age": True, "fade": 0.85,
                  "bright": False, "spin": 0.0, "sway": 0.04, "drag": 0.98},
    "glass_shard": {"min": 20, "max": 30, "size": 0.06, "grow": 1.0, "gravity": 1.0, "by_age": False, "fade": 0.7,
                    "bright": False, "spin": 0.3, "sway": 0.0, "drag": 0.98},
    "dirt_clod": {"min": 15, "max": 25, "size": 0.07, "grow": 1.0, "gravity": 1.0, "by_age": False, "fade": 0.7,
                  "bright": False, "spin": 0.2, "sway": 0.0, "drag": 0.98},
}


def emit(count, vel=(0, 0, 0), vel_spread=(0, 0, 0), pos_spread=(0, 0, 0), up_only=False):
    return {"count": count, "vel": vel, "vel_spread": vel_spread, "pos_spread": pos_spread, "up_only": up_only}


DEFAULT_EMIT = emit(3, (0, 0.02, 0), (0.02, 0.01, 0.02))

# Cuantas salen y para donde (blocks por tick, como en RoccoParticleFx). vel + random(-1..1)*spread.
EMIT = {
    "leaf_sparkle": emit(2, (0, 0.02, 0), (0.02, 0.01, 0.02), (0.15, 0.15, 0.15)),
    "leaf_burst": emit(6, (0, 0.1, 0), (0.12, 0.05, 0.12)),
    "speed_leaf": emit(1, (0, 0, 0), (0.1, 0.05, 0.1), (0.2, 0.2, 0.2)),
    "wind_streak": emit(1, (0, 0, 0), (0.2, 0.02, 0.2), (0.6, 0.1, 0.6)),
    "wind_puff": emit(1, (0, 0, 0), (0.03, 0.02, 0.03)),
    "wind_tear": emit(1, (0, 0, 0), (0.08, 0.02, 0.08)),
    "land_dust": emit(6, (0, 0.01, 0), (0.06, 0.0, 0.06)),
    "duck_pop": emit(6, (0, 0, 0), (0.12, 0.1, 0.12)),
    "water_drop": emit(3, (0, 0.09, 0), (0.14, 0.03, 0.14)),
    "think_dot": emit(1, (0, 0.02, 0), (0.005, 0, 0.005)),
    "clap_spark": emit(4, (0, 0, 0), (0.06, 0.04, 0.06)),
    "confetti": emit(14, (0, 0.24, 0), (0.12, 0.06, 0.12)),
    "music_note": emit(1, (0, 0.03, 0)),
    "dance_sparkle": emit(2, (0, 0.005, 0), (0, 0, 0), (0.1, 0.1, 0.1)),
    "cola_fizz": emit(3, (0, 0.04, 0), (0.005, 0, 0.005), (0.04, 0, 0.04)),
    "cola_pop": emit(7, (0, 0.085, 0), (0.08, 0.025, 0.08)),
    "refresh_sparkle": emit(4, (0, 0, 0), (0.04, 0.04, 0.04)),
    "hic_bubble": emit(1, (0, 0.015, 0)),
    "swing_sparkle": emit(1),
    "proud_sparkle": emit(5, (0, 0.145, 0), (0.05, 0.025, 0.05)),
    "ender_spark": emit(4, (0, 0.02, 0), (0.04, 0, 0.04), (0.3, 0.15, 0.3)),
    "ender_burst": emit(1),
    "ender_ring": emit(10, (0, 0, 0), (0.12, 0, 0.12)),
    "dirt_clod": emit(5, (0, 0.125, 0), (0.1, 0.025, 0.1)),
    "heart_pop": emit(3, (0, 0.04, 0), (0.01, 0, 0.01), (0.2, 0, 0.2)),
    "friend_glow": emit(4, (0, 0.02, 0), (0.015, 0, 0.015), (0.2, 0.2, 0.2)),
    "joy_tear": emit(1, (0, 0.03, 0), (0.02, 0, 0.02)),
    "seed_pop": emit(1, (0, 0.1, 0), (0.04, 0, 0.04)),
    "butterfly": emit(1),
    "petal": emit(4, (0, 0.07, 0), (0.04, 0.02, 0.04)),
    "grill_smoke": emit(2, (0, 0.03, 0), (0.008, 0, 0.008), (0.12, 0, 0.12)),
    "heat_wave": emit(1, (0, 0.06, 0), (0, 0, 0), (0.1, 0, 0.1)),
    "ember": emit(2, (0, 0.05, 0), (0.02, 0, 0.02), (0.1, 0, 0.1)),
    "fat_sizzle": emit(2, (0, 0.125, 0), (0.05, 0.025, 0.05), (0.08, 0, 0.08)),
    "flame_burst": emit(1),
    "oil_bubble": emit(2, (0, 0.008, 0), (0, 0, 0), (0.1, 0, 0.1)),
    "oil_splash": emit(3, (0, 0.125, 0), (0.07, 0.025, 0.07)),
    "oil_drip": emit(1),
    "flour_puff": emit(3, (0, 0.01, 0), (0.01, 0, 0.01), (0.08, 0, 0.08)),
    "glass_shard": emit(8, (0, 0.125, 0), (0.15, 0.075, 0.15)),
    "mate_steam": emit(2, (0, 0.025, 0), (0.005, 0, 0.005), (0.03, 0, 0.03)),
    "bliss_flower": emit(3, (0, 0.05, 0), (0.04, 0.02, 0.04), (0.2, 0.1, 0.2)),
    "magic_rune": emit(2, (0, 0.02, 0), (0.05, 0.02, 0.05)),
    "magic_spark": emit(3, (0, 0.02, 0), (0.06, 0.04, 0.06)),
    "magic_burst": emit(6, (0, 0, 0), (0.02, 0.02, 0.02), (0.25, 0.2, 0.25)),
    "burp_wind": emit(3, (0, 0, 0), (0.08, 0.03, 0.08)),
    "burp_leaf": emit(4, (0, 0.05, 0), (0.08, 0.04, 0.08)),
    "burp_puff": emit(1),
}

# Las que el script lanza por su cuenta (regalos, eructo, aparecer la mesa del truco...).
SCRIPT_PARTICLES = ["magic_burst", "heart_pop", "friend_glow", "seed_pop", "dirt_clod", "mate_steam",
                    "refresh_sparkle", "burp_wind", "burp_leaf", "leaf_burst", "confetti"]

# Las cartas en cartas.png: columnas = 1..7, 10, 11, 12; filas = espadas, bastos, oros, copas.
CARD_SIZE = (40, 60)
CARD_SUITS = ["espada", "basto", "oro", "copa"]
CARD_NUMBERS = [1, 2, 3, 4, 5, 6, 7, 10, 11, 12]
CARD_BACK = (0, 240)
