# Lo del lado del comportamiento: la entidad de Rocco (componentes y propiedades que lee el
# cliente), los muebles del truco, los items, las recetas y los textos en tres idiomas.
import json
import os

import client_gen
import rocco_data

ANIM_MAX = 255

PROPERTIES = {
    # La animacion principal (scripts/gen/anims.js) y su numero de pedido.
    "rocco:anim": {"type": "int", "range": [0, ANIM_MAX], "default": 0, "client_sync": True},
    "rocco:seq": {"type": "int", "range": [0, 1000], "default": 0, "client_sync": True},
    # La cara (orden de RoccoExpression).
    "rocco:face": {"type": "int", "range": [0, len(rocco_data.FACES) - 1], "default": 0, "client_sync": True},
    "rocco:talk": {"type": "bool", "default": False, "client_sync": True},
    "rocco:eating": {"type": "bool", "default": False, "client_sync": True},
    "rocco:plant": {"type": "bool", "default": True, "client_sync": True},
    "rocco:pack": {"type": "int", "range": [0, 6], "default": 0, "client_sync": True},
    "rocco:garden": {"type": "bool", "default": False, "client_sync": True},
    "rocco:mate": {"type": "bool", "default": False, "client_sync": True},
    # Los huesos de las escenas (RoccoScene: carne, masa, pote, cartas), empaquetados.
    "rocco:bits": {"type": "int", "range": [0, 1048575], "default": 0, "client_sync": True},
    "rocco:armor_head": {"type": "int", "range": [0, 6], "default": 0, "client_sync": True},
    "rocco:armor_chest": {"type": "int", "range": [0, 6], "default": 0, "client_sync": True},
    "rocco:armor_legs": {"type": "int", "range": [0, 6], "default": 0, "client_sync": True},
    "rocco:armor_feet": {"type": "int", "range": [0, 6], "default": 0, "client_sync": True},
    "rocco:weapon": {"type": "int", "range": [0, 5], "default": 0, "client_sync": True},
    "rocco:weapon_mat": {"type": "int", "range": [0, 6], "default": 0, "client_sync": True},
    "rocco:shield": {"type": "bool", "default": False, "client_sync": True},
}

HOSTILE_FAMILIES = ["monster"]


def rocco_entity():
    hostile_filter = {"all_of": [
        {"test": "is_family", "subject": "other", "value": "monster"},
        {"test": "is_family", "subject": "other", "operator": "!=", "value": "creeper"},
    ]}
    groups = {
        # Plantado (durmiendo como brote): no se mueve ni lo empujan.
        "rocco:planted": {
            "minecraft:movement": {"value": 0.0},
            "minecraft:knockback_resistance": {"value": 1.0},
            "minecraft:damage_sensor": {"triggers": [{"cause": "all", "deals_damage": "no"}]},
            "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False},
        },
        # Despierto: camina, se defiende y te sigue (las metas de seguir van aparte).
        "rocco:active": {
            "minecraft:movement": {"value": 0.3},
            "minecraft:pushable": {"is_pushable": True, "is_pushable_by_piston": True},
            "minecraft:behavior.float": {"priority": 0},
            "minecraft:behavior.melee_box_attack": {"priority": 2, "speed_multiplier": 1.25, "track_target": True},
            "minecraft:behavior.hurt_by_target": {
                "priority": 1,
                "entity_types": {"filters": {"test": "is_family", "subject": "other", "operator": "!=", "value": "player"}},
            },
            "minecraft:behavior.look_at_player": {"priority": 8, "look_distance": 8.0, "probability": 0.05},
            "minecraft:behavior.random_look_around": {"priority": 9},
        },
        "rocco:follow": {
            "minecraft:behavior.follow_owner": {
                "priority": 4, "speed_multiplier": 1.15, "start_distance": 6, "stop_distance": 2.5,
                "can_teleport": True, "ignore_vibration": True, "max_distance": 48,
            },
            "minecraft:behavior.owner_hurt_by_target": {"priority": 1},
            "minecraft:behavior.owner_hurt_target": {"priority": 2},
        },
        # Quieto en un lugar (dejar de seguir, cuidar la casa): da vueltas cerca.
        "rocco:stay": {
            "minecraft:behavior.random_stroll": {"priority": 7, "speed_multiplier": 0.8, "xz_dist": 5, "y_dist": 2},
        },
        "rocco:hunt_hostiles": {
            "minecraft:behavior.nearest_attackable_target": {
                "priority": 3, "reselect_targets": True, "within_radius": 12.0, "must_see": True,
                "entity_types": [{"filters": hostile_filter, "max_dist": 12}],
            },
        },
        # En una escena (asado, tortas, truco, mate): ni camina ni lo empujan.
        "rocco:busy": {
            "minecraft:movement": {"value": 0.0},
            "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False},
        },
        "rocco:not_busy": {},
        "rocco:noop": {},
        "rocco:tamed": {"minecraft:is_tamed": {}},
        # Sentado a la mesa del truco: lo ubica el script (0,5625 arriba de su silla, como en Java).
        "rocco:seated": {
            "minecraft:physics": {"has_gravity": False, "has_collision": False},
            "minecraft:movement": {"value": 0.0},
            "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False},
            "minecraft:knockback_resistance": {"value": 1.0},
        },
        # En la tumba: no lo pueden lastimar.
        "rocco:grave": {
            "minecraft:movement": {"value": 0.0},
            "minecraft:damage_sensor": {"triggers": [{"cause": "all", "deals_damage": "no"}]},
            "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False},
        },
    }
    events = {
        "minecraft:entity_spawned": {"add": {"component_groups": ["rocco:planted"]}},
        "rocco:tamed": {"add": {"component_groups": ["rocco:tamed"]}},
        "rocco:seat": {"add": {"component_groups": ["rocco:seated", "rocco:busy"]},
                       "remove": {"component_groups": ["rocco:active"]}},
        "rocco:unseat": {"add": {"component_groups": ["rocco:active"]},
                         "remove": {"component_groups": ["rocco:seated", "rocco:busy"]}},
        "rocco:interacted": {"add": {"component_groups": ["rocco:noop"]}},
        "rocco:died": {"add": {"component_groups": ["rocco:grave"]},
                       "remove": {"component_groups": ["rocco:active", "rocco:follow", "rocco:stay", "rocco:hunt_hostiles", "rocco:busy"]}},
        "rocco:plant": {"add": {"component_groups": ["rocco:planted"]},
                        "remove": {"component_groups": ["rocco:active", "rocco:follow", "rocco:stay", "rocco:hunt_hostiles", "rocco:busy", "rocco:grave"]}},
        "rocco:wake": {"add": {"component_groups": ["rocco:active", "rocco:follow", "rocco:hunt_hostiles"]},
                       "remove": {"component_groups": ["rocco:planted", "rocco:grave", "rocco:stay"]}},
        "rocco:follow": {"add": {"component_groups": ["rocco:follow"]}, "remove": {"component_groups": ["rocco:stay"]}},
        "rocco:stay": {"add": {"component_groups": ["rocco:stay"]}, "remove": {"component_groups": ["rocco:follow"]}},
        "rocco:hunt_on": {"add": {"component_groups": ["rocco:hunt_hostiles"]}},
        "rocco:hunt_off": {"remove": {"component_groups": ["rocco:hunt_hostiles"]}},
        "rocco:busy": {"add": {"component_groups": ["rocco:busy"]}, "remove": {"component_groups": ["rocco:active"]}},
        "rocco:free": {"add": {"component_groups": ["rocco:active"]}, "remove": {"component_groups": ["rocco:busy"]}},
        "rocco:grave": {"add": {"component_groups": ["rocco:grave"]},
                        "remove": {"component_groups": ["rocco:active", "rocco:follow", "rocco:stay", "rocco:hunt_hostiles", "rocco:busy"]}},
    }
    components = {
        "minecraft:type_family": {"family": ["rocco", "mob", "companion"]},
        # Un golpe que lo mataria lo deja en la tumba (el script lo maneja), como en Java. Va en
        # la base para que valga tambien ocupado o sentado; plantado y en la tumba lo pisan sus
        # grupos (no le hace nada ningun golpe).
        "minecraft:damage_sensor": {"triggers": [
            {"on_damage": {"filters": {"test": "has_damage", "value": "fatal"}, "event": "rocco:died"},
             "deals_damage": "no"},
            {"cause": "fall", "deals_damage": "no"},
        ]},
        "minecraft:collision_box": {"width": 0.6, "height": 1.3},
        "minecraft:health": {"value": 40, "max": 40},
        "minecraft:attack": {"damage": 4},
        "minecraft:movement.basic": {},
        "minecraft:navigation.walk": {
            "can_path_over_water": True, "avoid_water": True, "avoid_damage_blocks": True,
            "can_open_doors": True, "can_pass_doors": True, "can_walk": True,
        },
        "minecraft:jump.static": {},
        "minecraft:can_climb": {},
        "minecraft:physics": {},
        "minecraft:nameable": {"always_show": False, "allow_name_tag_renaming": True},
        "minecraft:persistent": {},
        "minecraft:tameable": {"probability": 0.0, "tame_event": {"event": "rocco:tamed", "target": "self"}},
        "minecraft:breathable": {"total_supply": 15, "suffocate_time": 0, "breathes_water": True},
        "minecraft:fall_damage": {"value": 0.0},
        "minecraft:inventory": {"container_type": "inventory", "inventory_size": 54, "private": True},
        "minecraft:equipment": {},
        "minecraft:experience_reward": {"on_death": "0"},
        # Sin una interaccion declarada, en el celular no aparece el boton para tocarlo.
        "minecraft:interact": {"interactions": [{
            "on_interact": {"filters": {"test": "is_family", "subject": "other", "value": "player"}, "event": "rocco:interacted"},
            "interact_text": "action.interact.rocco",
        }]},
        "minecraft:conditional_bandwidth_optimization": {},
        "minecraft:balloonable": {"mass": 0.5},
        "minecraft:leashable": {"soft_distance": 4.0, "hard_distance": 6.0, "max_distance": 10.0},
    }
    return {
        "format_version": "1.21.40",
        "minecraft:entity": {
            "description": {
                "identifier": "rocco:rocco",
                "is_spawnable": True,
                "is_summonable": True,
                "properties": PROPERTIES,
            },
            "component_groups": groups,
            "components": components,
            "events": events,
        },
    }


def card_entity():
    return {
        "format_version": "1.21.40",
        "minecraft:entity": {
            "description": {
                "identifier": "rocco:truco_carta", "is_spawnable": False, "is_summonable": True,
                "properties": {"rocco:card": {"type": "int", "range": [0, 40], "default": 40, "client_sync": True}},
            },
            "components": {
                "minecraft:type_family": {"family": ["rocco_furniture"]},
                "minecraft:collision_box": {"width": 0.01, "height": 0.01},
                "minecraft:health": {"value": 1000, "max": 1000},
                "minecraft:damage_sensor": {"triggers": [{"cause": "all", "deals_damage": "no"}]},
                "minecraft:physics": {"has_gravity": False, "has_collision": False},
                "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False},
                "minecraft:knockback_resistance": {"value": 1.0},
            },
        },
    }


def furniture_entity(name, seat):
    comps = {
        "minecraft:type_family": {"family": ["rocco_furniture"]},
        "minecraft:collision_box": {"width": 0.9 if not seat else 0.6, "height": 0.8 if not seat else 0.5},
        "minecraft:health": {"value": 1000, "max": 1000},
        "minecraft:damage_sensor": {"triggers": [{"cause": "all", "deals_damage": "no"}]},
        "minecraft:knockback_resistance": {"value": 1.0},
        "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False},
        "minecraft:physics": {"has_gravity": False, "has_collision": False},
        "minecraft:movement": {"value": 0.0},
        "minecraft:persistent": {},
    }
    description = {"identifier": "rocco:" + name, "is_spawnable": False, "is_summonable": True}
    groups = {}
    events = {}
    if seat:
        # La silla de Rocco es la mas chica (silla_rocco); la tuya es para sentarse.
        description["properties"] = {"rocco:for_rocco": {"type": "bool", "default": False, "client_sync": True}}
        groups["rocco:for_player"] = {"minecraft:rideable": {
            "seat_count": 1, "family_types": ["player"], "interact_text": "action.interact.sit",
            "pull_in_entities": False, "seats": {"position": [0, 0.1, 0]},
        }}
        events["rocco:for_player"] = {"add": {"component_groups": ["rocco:for_player"]}}
    else:
        description["properties"] = {"rocco:size": {"type": "int", "range": [0, 2], "default": 0, "client_sync": True}}
    out = {
        "format_version": "1.21.40",
        "minecraft:entity": {"description": description, "components": comps},
    }
    if groups:
        out["minecraft:entity"]["component_groups"] = groups
        out["minecraft:entity"]["events"] = events
    return out


def item(name, extra, stack=64, category="items"):
    comps = {
        "minecraft:icon": {"textures": {"default": "rocco_" + name}},
        "minecraft:display_name": {"value": "item.rocco:%s" % name},
        "minecraft:max_stack_size": stack,
    }
    comps.update(extra)
    return {
        "format_version": "1.21.40",
        "minecraft:item": {
            "description": {"identifier": "rocco:" + name, "menu_category": {"category": category}},
            "components": comps,
        },
    }


def food(nutrition, saturation, anim="eat", seconds=1.6, always=False, converts=None):
    comps = {
        "minecraft:food": {"nutrition": nutrition, "saturation_modifier": saturation, "can_always_eat": always},
        "minecraft:use_modifiers": {"use_duration": seconds, "movement_modifier": 0.35},
        "minecraft:use_animation": anim,
    }
    if converts:
        comps["minecraft:food"]["using_converts_to"] = converts
    return comps


def items():
    out = {
        # El mate: lo pasa Rocco, se toma (y vuelve a quedar en la mano para devolverlo).
        "mate": item("mate", food(0, 0.0, "drink", 1.6, True), 1, "equipment"),
        "roco_cola": item("roco_cola", food(2, 0.3, "drink", 1.6, True, "rocco:roco_cola_half"), 16),
        "roco_cola_half": item("roco_cola_half", food(2, 0.3, "drink", 1.6, True, "rocco:roco_cola_empty"), 16),
        "roco_cola_empty": item("roco_cola_empty", {}, 16),
        "botella_rota": item("botella_rota", {
            "minecraft:damage": 3, "minecraft:durability": {"max_durability": 40},
            "minecraft:hand_equipped": True, "minecraft:can_destroy_in_creative": False,
            "minecraft:enchantable": {"slot": "sword", "value": 5},
        }, 1, "equipment"),
        "torta_frita": item("torta_frita", food(4, 0.5)),
        "asado_de_tira": item("asado_de_tira", food(10, 0.8)),
        "bondiola_asada": item("bondiola_asada", food(10, 0.8)),
        "costillitas_de_cordero": item("costillitas_de_cordero", food(8, 0.8)),
        "semillita": item("semillita", {"minecraft:glint": False}, 1),
        "flor_amistad": item("flor_amistad", {"minecraft:glint": True}, 1),
    }
    for tier in range(1, 7):
        name = "mochila_%d" % tier
        out[name] = item(name, {"minecraft:glint": tier >= 6}, 1, "equipment")
    return out


LEAVES = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "azalea", "pale_oak"]


def recipes(java_res):
    out = {}
    folder = os.path.join(java_res, "data", "rocco", "recipe")
    for name in sorted(os.listdir(folder)):
        java = json.load(open(os.path.join(folder, name), encoding="utf-8"))
        base = name[:-5]
        variants = [None]
        if any(v.get("tag") == "minecraft:leaves" for v in java["key"].values()):
            variants = LEAVES
        for leaves in variants:
            key = {}
            for k, v in java["key"].items():
                if v.get("tag") == "minecraft:leaves":
                    key[k] = {"item": "minecraft:%s_leaves" % leaves}
                else:
                    key[k] = {"item": v["item"]}
            rid = "rocco:" + base + ("" if leaves is None else "_" + leaves)
            out[rid.split(":")[1]] = {
                "format_version": "1.20.10",
                "minecraft:recipe_shaped": {
                    "description": {"identifier": rid},
                    "tags": ["crafting_table"],
                    "pattern": java["pattern"],
                    "key": key,
                    "unlock": [{"item": "minecraft:crafting_table"}],
                    "result": {"item": java["result"]["id"], "count": java["result"].get("count", 1)},
                },
            }
    return out


# ---------------------------------------------------------------- textos

LANG_FILES = {"es_es": "es_ES", "en_us": "en_US", "pt_br": "pt_BR"}
JAVA_TO_BEDROCK_KEYS = {
    "entity.rocco.rocco": "entity.rocco:rocco.name",
    "entity.rocco.truco_mesa": "entity.rocco:truco_mesa.name",
    "entity.rocco.truco_silla": "entity.rocco:truco_silla.name",
}


def lang_files(rp, java_assets, src):
    texts = os.path.join(rp, "texts")
    os.makedirs(texts, exist_ok=True)
    item_names = list(items())
    for java_name, bedrock_name in LANG_FILES.items():
        java = json.load(open(os.path.join(java_assets, "lang", java_name + ".json"), encoding="utf-8"))
        lines = {}
        for jk, bk in JAVA_TO_BEDROCK_KEYS.items():
            if jk in java:
                lines[bk] = java[jk]
        for jk, v in java.items():
            if jk.startswith("gui.rocco.truco."):
                lines[jk] = v
        for name in item_names:
            jk = "item.rocco." + name
            if jk in java:
                lines["item.rocco:%s" % name] = java[jk]
        lines["item.spawn_egg.entity.rocco:rocco.name"] = java.get("item.rocco.rocco_spawn_egg", "Rocco")
        own = os.path.join(src, "texts", java_name + ".json")
        if os.path.exists(own):
            lines.update(json.load(open(own, encoding="utf-8")))
        with open(os.path.join(texts, bedrock_name + ".lang"), "w", encoding="utf-8", newline="\n") as f:
            for k, v in lines.items():
                f.write("%s=%s\n" % (k, str(v).replace("\n", "\\n")))
    with open(os.path.join(texts, "languages.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(list(LANG_FILES.values()), f)


def write_all(bp, rp, java_main, java_assets, write_json):
    write_json(os.path.join(bp, "entities", "rocco.json"), rocco_entity(), pretty=True)
    write_json(os.path.join(bp, "entities", "truco_mesa.json"), furniture_entity("truco_mesa", False), pretty=True)
    write_json(os.path.join(bp, "entities", "truco_silla.json"), furniture_entity("truco_silla", True), pretty=True)
    write_json(os.path.join(bp, "entities", "truco_carta.json"), card_entity(), pretty=True)
    for name, data in items().items():
        write_json(os.path.join(bp, "items", name + ".json"), data, pretty=True)
    for name, data in recipes(os.path.join(java_main, "resources")).items():
        write_json(os.path.join(bp, "recipes", name + ".json"), data, pretty=True)
    src = os.path.join(os.path.dirname(os.path.dirname(bp)), "src")
    lang_files(rp, java_assets, src)
