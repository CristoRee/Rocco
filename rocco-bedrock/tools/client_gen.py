# Genera lo del cliente que depende de la lista de animaciones y del modelo: la entidad del
# cliente, el controlador de animaciones, los controladores de dibujo (caras, accesorios,
# armadura, armas) y la tabla de animaciones que usa el script (scripts/gen/anims.js).
#
# Como funciona en Bedrock lo que en Java hace GeckoLib:
#  - El script (servidor) elige la animacion principal y la pone en la propiedad rocco:anim (un
#    numero de esta tabla). rocco:seq cambia cada vez que se pide, asi la misma animacion pedida
#    dos veces vuelve a arrancar. Con rocco:anim = 0 el cliente elige solo entre idle y walk.
#  - Las caras: la propiedad rocco:face, o la que ponga la linea de tiempo de la animacion
#    (v.tface), igual que el setAnimFace de Java.
#  - Lo que en RoccoModel se esconde por codigo, aca lo esconde part_visibility con Molang.
import json
import os

import rocco_data

GROUPS = [
    ("mate", lambda n: n == "mate" or n.startswith("mate_")),
    ("tortas", lambda n: n.startswith("tortas_")),
    ("truco", lambda n: n.startswith("truco_")),
    ("asado", lambda n: n.startswith("asado_")),
    ("cola", lambda n: n in ("cola_drink", "bottle_smash")),
    ("mic", lambda n: n in ("sing_groove", "sing_rap")),
    ("enchant", lambda n: n.startswith("enchant")),
    ("grave", lambda n: n.startswith("grave_")),
]
# Las que nunca son la principal: se suman encima.
OVERLAYS = {"talk", "blink"}
# Las que el cliente elige solo con rocco:anim = 0.
AUTO = {"idle", "walk"}
# Abrir la tapa sigue sola con su bucle (thenPlay + thenLoop en Java).
FOLLOW_UP = {"lid_open": "lid_dizzy", "lid_open_chill": "lid_dizzy_chill"}

MATE_BONES = ["mate", "termo", "mate_water"]
ASADO_BONES = ["grill", "meat_cook", "tongs", "apron_grill"]
TORTAS_BONES = ["fritura", "apron_cook", "dough_ball", "torta", "skimmer"]
TRUCO_BONES = ["cards_fan", "card_throw", "deal_card", "deck"]
MEATS = ["", "beef", "pork", "lamb"]
ARMOR_BONES = {
    "head": ["armor_head", "armor_head_jaw", "armor_head_top"],
    "chest": ["armor_chest", "armor_chest_right", "armor_chest_left", "armor_chest_right_wrist", "armor_chest_left_wrist"],
    "legs": ["armor_legs", "armor_legs_right", "armor_legs_left"],
    "feet": ["armor_feet_right", "armor_feet_left"],
}
WEAPON_BONES = ["weapon_sword", "weapon_axe", "weapon_trident", "weapon_bow", "weapon_crossbow", "weapon_shield"]
# El orden de las propiedades rocco:armor_* (0 = nada).
ARMOR_MATERIALS = ["leather", "chainmail", "iron", "gold", "diamond", "netherite"]
# rocco:weapon (0 = nada): que hueso. rocco:weapon_mat: con que textura.
WEAPON_KINDS = ["weapon_sword", "weapon_axe", "weapon_trident", "weapon_bow", "weapon_crossbow"]
WEAPON_MATERIALS = ["wood", "stone", "iron", "gold", "diamond", "netherite", "special"]


def short(full):
    return full[len("animation.rocco."):]


def order_animations(all_names):
    names = [short(n) for n in all_names]
    main = [n for n in names if n not in OVERLAYS and n not in AUTO]
    ordered = []
    ranges = {}
    for group, test in GROUPS:
        members = sorted(n for n in main if test(n) and n not in ordered)
        if members:
            start = len(ordered) + 1
            ordered.extend(members)
            ranges[group] = (start, len(ordered))
    ordered.extend(sorted(n for n in main if n not in ordered))
    index = {n: i + 1 for i, n in enumerate(ordered)}
    return ordered, index, ranges


def in_range(ranges, group):
    lo, hi = ranges[group]
    return "(v.a >= %d && v.a <= %d)" % (lo, hi)


def children_map(geo_bones):
    kids = {}
    for b in geo_bones:
        if "parent" in b:
            kids.setdefault(b["parent"], []).append(b["name"])
    return kids


def descendants(name, kids):
    out = [name]
    for k in kids.get(name, []):
        out.extend(descendants(k, kids))
    return out


class Visibility:
    """part_visibility con Molang. Un hueso escondido arrastra a todos sus hijos (se listan
    explicitamente, asi no depende de si Bedrock esconde la jerarquia o no)."""

    def __init__(self, kids):
        self.kids = kids
        self.rules = {}

    def show_when(self, bone, expr):
        for b in descendants(bone, self.kids):
            prev = self.rules.get(b)
            self.rules[b] = expr if prev is None else "(%s) && (%s)" % (prev, expr)

    def as_list(self, star=None):
        out = []
        if star is not None:
            out.append({"*": star})
        for bone, expr in self.rules.items():
            out.append({bone: expr})
        return out


def body_visibility(kids, ranges, index):
    vis = Visibility(kids)
    all_bones = set(kids) | {c for cs in kids.values() for c in cs}
    for i, face in enumerate(rocco_data.FACES):
        for prefix in ("eyes_", "mouth_", "talk_", "face_"):
            bone = prefix + face
            if bone in all_bones:
                vis.show_when(bone, "v.face_shown == %d" % i)
    vis.show_when("mouth_talk", "q.property('rocco:talk') || q.property('rocco:eating')")
    vis.show_when("plant", "q.property('rocco:plant')")
    for tier in range(1, 7):
        vis.show_when("backpack_%d" % tier, "q.property('rocco:pack') == %d" % tier)
    vis.show_when("garden", "q.property('rocco:garden')")
    vis.show_when("lid_garden", "q.property('rocco:garden')")
    for bones in ARMOR_BONES.values():
        for b in bones:
            vis.show_when(b, "0")
    for b in WEAPON_BONES:
        vis.show_when(b, "0")
    vis.show_when("grave", in_range(ranges, "grave"))
    mate = "(q.property('rocco:mate') || %s || %s || %s)" % (
        in_range(ranges, "mate"), in_range(ranges, "tortas"), in_range(ranges, "truco"))
    for b in MATE_BONES:
        vis.show_when(b, mate)
    vis.show_when("cola", in_range(ranges, "cola"))
    vis.show_when("cola_cap", in_range(ranges, "cola"))
    vis.show_when("mic", in_range(ranges, "mic"))
    for b in ASADO_BONES:
        vis.show_when(b, in_range(ranges, "asado"))
    for b in TORTAS_BONES:
        vis.show_when(b, in_range(ranges, "tortas"))
    for b in TRUCO_BONES:
        vis.show_when(b, in_range(ranges, "truco"))
    bits = "q.property('rocco:bits')"

    def field(shift, mask):
        return "math.mod(math.floor(%s / %d), %d)" % (bits, 2 ** shift, mask + 1)

    for meat in range(1, 4):
        vis.show_when("meat_cook_" + MEATS[meat], "%s == %d" % (field(0, 3), meat))
        vis.show_when("raw_next_" + MEATS[meat], "%s == %d" % (field(2, 3), meat))
        for slot in range(4):
            vis.show_when("warm_%d_%s" % (slot + 1, MEATS[meat]), "%s == %d" % (field(4 + slot * 2, 3), meat))
    for size in range(1, 4):
        vis.show_when("dough_%d" % size, "%s == %d" % (field(12, 3), size))
    for level in range(1, 5):
        vis.show_when("bowl_fill_%d" % level, "%s >= %d" % (field(14, 7), level))
    for card in range(1, 4):
        vis.show_when("card_fan_%d" % card, "%s >= %d" % (field(17, 3), card))
    # Con la coronita (casco de oro) se esconden las hojitas.
    crown = "q.property('rocco:armor_head') != %d" % (ARMOR_MATERIALS.index("gold") + 1)
    vis.show_when("head_leaves", crown)
    vis.show_when("hair_a", crown)
    # Las esferas de magia: solo sus cubos (adentro estan los localizadores de las manos).
    vis.rules["magic_right"] = in_range(ranges, "enchant")
    vis.rules["magic_left"] = in_range(ranges, "enchant")
    return vis.as_list()


def gear_visibility(kids, keep):
    """Para una pasada de armadura o arma: se ve solo lo de 'keep' (las demas piezas de equipo
    se esconden; el resto del cuerpo es transparente en esa textura)."""
    vis = Visibility(kids)
    for bones in ARMOR_BONES.values():
        for b in bones:
            vis.show_when(b, "1" if b in keep else "0")
    for b in WEAPON_BONES:
        if b not in keep:
            vis.show_when(b, "0")
    return vis.as_list()


def controller(ordered, index):
    """Un estado por animacion. Cualquier cambio pasa por 'hub' (que sostiene idle un instante y
    reparte en el mismo cuadro): asi son 2 transiciones por estado y no N."""
    states = {}
    blend = 0.15
    hub_transitions = [{"auto": "v.a == 0"}]
    follow_from = {v: k for k, v in FOLLOW_UP.items()}
    for name in ordered:
        own = [index[name]]
        if name in follow_from:
            own.append(index[follow_from[name]])
        leave = " && ".join("v.a != %d" % i for i in own)
        state = {
            "animations": [name],
            "blend_transition": blend,
            "on_entry": ["v.seq = q.property('rocco:seq');", "v.tface = -1;"],
            "transitions": [{"hub": "(%s) || q.property('rocco:seq') != v.seq" % leave}],
        }
        if name in FOLLOW_UP:
            state["transitions"].insert(0, {"s_" + FOLLOW_UP[name]: "q.all_animations_finished"})
        states["s_" + name] = state
        if name not in follow_from:
            hub_transitions.append({"s_" + name: "v.a == %d" % index[name]})
    states["auto"] = {
        "animations": [{"idle": "1 - v.moving"}, {"walk": "v.moving"}],
        "blend_transition": blend,
        "on_entry": ["v.seq = q.property('rocco:seq');", "v.tface = -1;"],
        "transitions": [{"hub": "v.a != 0 || q.property('rocco:seq') != v.seq"}],
    }
    states["hub"] = {
        "animations": ["idle"],
        "transitions": hub_transitions,
    }
    return {
        "format_version": "1.10.0",
        "animation_controllers": {
            "controller.animation.rocco.main": {"initial_state": "hub", "states": states},
        },
    }


def client_entity(anim_names, particles, sounds):
    anims = {short(n): n for n in anim_names}
    anims["ctrl_main"] = "controller.animation.rocco.main"
    textures = {"default": "textures/entity/rocco/rocco"}
    for m in ARMOR_MATERIALS:
        textures["armor_" + m] = "textures/entity/rocco/rocco_armor_" + m
    for m in WEAPON_MATERIALS:
        textures["weapon_" + m] = "textures/entity/rocco/rocco_weapon_" + m
    closed = [i for i, f in enumerate(rocco_data.FACES) if f in rocco_data.EYES_CLOSED]
    closed_expr = " || ".join("v.face_shown == %d" % i for i in closed)
    fx = {}
    for key in particles:
        fx[key] = "rocco:" + key
    for short_key, real in rocco_data.VANILLA_PARTICLES.values():
        fx[short_key] = real
    renders = ["controller.render.rocco.body"]
    for slot in ARMOR_BONES:
        renders.append({"controller.render.rocco.armor_" + slot: "q.property('rocco:armor_%s') > 0" % slot})
    renders.append({"controller.render.rocco.weapon": "q.property('rocco:weapon') > 0"})
    renders.append({"controller.render.rocco.shield": "q.property('rocco:shield')"})
    return {
        "format_version": "1.10.0",
        "minecraft:client_entity": {
            "description": {
                "identifier": "rocco:rocco",
                "materials": {"default": "entity_alphatest", "gear": "entity_alphatest"},
                "textures": textures,
                "geometry": {"default": "geometry.rocco.entity"},
                "animations": anims,
                "scripts": {
                    "initialize": ["v.tface = -1;", "v.seq = -1;", "v.a = 0;", "v.face_shown = 0;"],
                    "pre_animation": [
                        "v.a = q.property('rocco:anim');",
                        "v.face_shown = v.tface >= 0 ? v.tface : q.property('rocco:face');",
                        "v.moving = q.ground_speed > 0.3 ? 1 : 0;",
                    ],
                    "animate": [
                        "ctrl_main",
                        {"talk": "(q.property('rocco:talk') && !q.property('rocco:eating')) ? 1 : 0"},
                        {"blink": "(!q.property('rocco:plant') && !q.property('rocco:eating') && !(%s)) ? 1 : 0" % closed_expr},
                    ],
                },
                "particle_effects": fx,
                "sound_effects": {s.split(".", 1)[1]: s for s in sounds},
                "render_controllers": renders,
                "spawn_egg": {"base_color": "#C8A27A", "overlay_color": "#5DAA3C"},
            }
        },
    }


def render_controllers(kids, ranges, index):
    rc = {
        "controller.render.rocco.body": {
            "geometry": "Geometry.default",
            "materials": [{"*": "Material.default"}],
            "textures": ["Texture.default"],
            "part_visibility": body_visibility(kids, ranges, index),
        }
    }
    for slot, bones in ARMOR_BONES.items():
        arr = ["Texture.armor_" + m for m in ARMOR_MATERIALS]
        rc["controller.render.rocco.armor_" + slot] = {
            "arrays": {"textures": {"Array.armor": arr}},
            "geometry": "Geometry.default",
            "materials": [{"*": "Material.gear"}],
            "textures": ["Array.armor[math.max(0, q.property('rocco:armor_%s') - 1)]" % slot],
            "part_visibility": gear_visibility(kids, set(bones)),
        }
    rc["controller.render.rocco.weapon"] = {
        "arrays": {"textures": {"Array.weapon": ["Texture.weapon_" + m for m in WEAPON_MATERIALS]}},
        "geometry": "Geometry.default",
        "materials": [{"*": "Material.gear"}],
        "textures": ["Array.weapon[q.property('rocco:weapon_mat')]"],
        "part_visibility": gear_visibility(kids, set()) + [
            {b: "q.property('rocco:weapon') == %d" % (i + 1)} for i, b in enumerate(WEAPON_KINDS)],
    }
    rc["controller.render.rocco.shield"] = {
        "geometry": "Geometry.default",
        "materials": [{"*": "Material.gear"}],
        "textures": ["Texture.weapon_special"],
        "part_visibility": gear_visibility(kids, set()) + [{"weapon_shield": "1"}] + [
            {b: "0"} for b in WEAPON_KINDS],
    }
    return {"format_version": "1.10.0", "render_controllers": rc}


def card_client(write_json, rp):
    """Una carta boca arriba sobre la mesa: un plano con la textura de esa carta (40 = dorso)."""
    textures = {}
    names = []
    for suit in rocco_data.CARD_SUITS:
        for number in rocco_data.CARD_NUMBERS:
            key = "%s_%d" % (suit, number)
            textures[key] = "textures/entity/rocco/cartas/" + key
            names.append("Texture." + key)
    textures["dorso"] = "textures/entity/rocco/cartas/dorso"
    names.append("Texture.dorso")
    w, h = rocco_data.CARD_SIZE
    write_json(os.path.join(rp, "models", "entity", "truco_carta.geo.json"), {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry.truco_carta", "texture_width": w, "texture_height": h,
                            "visible_bounds_width": 1, "visible_bounds_height": 1, "visible_bounds_offset": [0, 0, 0]},
            "bones": [{"name": "carta", "pivot": [0, 0, 0], "cubes": [{
                "origin": [-2, 0, -3], "size": [4, 0.1, 6],
                "uv": {
                    "up": {"uv": [w, h], "uv_size": [-w, -h]},
                    "down": {"uv": [0, 0], "uv_size": [w, h]},
                    "north": {"uv": [0, 0], "uv_size": [1, 1]}, "south": {"uv": [0, 0], "uv_size": [1, 1]},
                    "east": {"uv": [0, 0], "uv_size": [1, 1]}, "west": {"uv": [0, 0], "uv_size": [1, 1]},
                },
            }]}],
        }],
    }, pretty=True)
    write_json(os.path.join(rp, "entity", "truco_carta.entity.json"), {
        "format_version": "1.10.0",
        "minecraft:client_entity": {"description": {
            "identifier": "rocco:truco_carta",
            "materials": {"default": "entity_alphatest"},
            "textures": textures,
            "geometry": {"default": "geometry.truco_carta"},
            "render_controllers": ["controller.render.rocco.truco_carta"],
        }},
    }, pretty=True)
    return {"controller.render.rocco.truco_carta": {
        "arrays": {"textures": {"Array.cards": names}},
        "geometry": "Geometry.default", "materials": [{"*": "Material.default"}],
        "textures": ["Array.cards[q.property('rocco:card')]"],
    }}


def furniture(write_json, rp):
    for name, anims in (("truco_mesa", ["aparecer"]), ("truco_silla", ["aparecer"])):
        extra = json.load(open(os.path.join(rp, "animations", name + ".animation.json"), encoding="utf-8"))
        anim_ids = {k.rsplit(".", 1)[-1]: k for k in extra["animations"]}
        write_json(os.path.join(rp, "entity", name + ".entity.json"), {
            "format_version": "1.10.0",
            "minecraft:client_entity": {
                "description": {
                    "identifier": "rocco:" + name,
                    "materials": {"default": "entity_alphatest"},
                    "textures": {"default": "textures/entity/rocco/" + name},
                    "geometry": {"default": "geometry." + name},
                    "animations": anim_ids,
                    "scripts": {"animate": [k for k in anim_ids if k == "aparecer"]},
                    "render_controllers": ["controller.render.rocco." + name],
                }
            },
        }, pretty=True)
    controllers = {
        "controller.render.rocco.truco_mesa": {
            "geometry": "Geometry.default", "materials": [{"*": "Material.default"}], "textures": ["Texture.default"],
            "part_visibility": [{"mesa_chica": "q.property('rocco:size') == 0"},
                                {"mesa_mediana": "q.property('rocco:size') == 1"},
                                {"mesa_grande": "q.property('rocco:size') == 2"}],
        },
        "controller.render.rocco.truco_silla": {
            "geometry": "Geometry.default", "materials": [{"*": "Material.default"}], "textures": ["Texture.default"],
            "part_visibility": [{"silla": "!q.property('rocco:for_rocco')"}, {"silla_rocco": "q.property('rocco:for_rocco')"}],
        },
    }
    controllers.update(card_client(write_json, rp))
    write_json(os.path.join(rp, "render_controllers", "rocco_furniture.render_controllers.json"), {
        "format_version": "1.10.0", "render_controllers": controllers}, pretty=True)


def anim_lengths(rp):
    data = json.load(open(os.path.join(rp, "animations", "rocco.animation.json"), encoding="utf-8"))
    return {short(k): v.get("animation_length", 0) for k, v in data["animations"].items()}


def write_all(rp, anim_names, particles, sounds, write_json, bp=None):
    geo = json.load(open(os.path.join(rp, "models", "entity", "rocco.geo.json"), encoding="utf-8"))
    kids = children_map(geo["minecraft:geometry"][0]["bones"])
    ordered, index, ranges = order_animations(anim_names)
    write_json(os.path.join(rp, "animation_controllers", "rocco.animation_controllers.json"),
               controller(ordered, index), pretty=True)
    write_json(os.path.join(rp, "entity", "rocco.entity.json"), client_entity(anim_names, particles, sounds), pretty=True)
    write_json(os.path.join(rp, "render_controllers", "rocco.render_controllers.json"),
               render_controllers(kids, ranges, index), pretty=True)
    furniture(write_json, rp)
    lengths = anim_lengths(rp)
    table = {n: {"id": index[n], "length": lengths.get(n, 0)} for n in ordered}
    if bp:
        path = os.path.join(bp, "scripts", "gen", "anims.js")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write("// Generado por tools/client_gen.py: no editar a mano.\n")
            f.write("export const ANIMS = %s;\n" % json.dumps(table, indent=1, sort_keys=True))
            f.write("export const FACES = %s;\n" % json.dumps(rocco_data.FACES))
            f.write("export const ARMOR_MATERIALS = %s;\n" % json.dumps(ARMOR_MATERIALS))
            f.write("export const WEAPON_KINDS = %s;\n" % json.dumps(WEAPON_KINDS))
            f.write("export const WEAPON_MATERIALS = %s;\n" % json.dumps(WEAPON_MATERIALS))
            f.write("export const ANIM_COUNT = %d;\n" % (len(ordered) + 1))
    return table
