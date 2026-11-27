# Arma el complemento de Rocco para Minecraft Bedrock.
#
# Los recursos (modelo, animaciones, texturas, sonidos y particulas) salen del mod de Java
# (rocco-neoforge-1.21.1), asi hay una sola fuente: si se cambia el modelo alla, aca solo hay
# que volver a correr esto. Lo escrito a mano (manifiestos, entidades, scripts, textos) vive en
# rocco-bedrock/src y se copia tal cual.
#
# Uso:  python rocco-bedrock/tools/build.py            -> rocco-bedrock/build/ y el .mcaddon
#       python rocco-bedrock/tools/build.py --install  -> ademas lo copia a las carpetas de
#                                                         desarrollo de Minecraft Bedrock
import json
import math
import os
import re
import shutil
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
BEDROCK = os.path.dirname(HERE)
REPO = os.path.dirname(BEDROCK)
JAVA = os.path.join(REPO, "rocco-neoforge-1.21.1", "src", "main")
ASSETS = os.path.join(JAVA, "resources", "assets", "rocco")
JAVA_SRC = os.path.join(JAVA, "java", "com", "cristoree", "rocco")
SRC = os.path.join(BEDROCK, "src")
OUT = os.path.join(BEDROCK, "build")
RP = os.path.join(OUT, "Rocco_RP")
BP = os.path.join(OUT, "Rocco_BP")

sys.path.insert(0, HERE)
import rocco_data  # noqa: E402  (listas compartidas: caras, animaciones, propiedades)


def read_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def write_json(path, data, pretty=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        if pretty:
            json.dump(data, f, ensure_ascii=False, indent=2)
        else:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))


def copy_tree(src, dst):
    for root, _, files in os.walk(src):
        for name in files:
            rel = os.path.relpath(os.path.join(root, name), src)
            target = os.path.join(dst, rel)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            shutil.copyfile(os.path.join(root, name), target)


# ---------------------------------------------------------------- modelo

def build_geometry():
    """El modelo de GeckoLib ya es formato Bedrock. Solo faltan los localizadores: GeckoLib usa
    huesos fx_* como puntos de particulas, Bedrock quiere 'locators' con nombre."""
    geo = read_json(os.path.join(ASSETS, "geo", "entity", "rocco.geo.json"))
    model = geo["minecraft:geometry"][0]
    names = {b["name"] for b in model["bones"]}
    for bone in model["bones"]:
        if bone["name"] in rocco_data.LOCATORS:
            bone.setdefault("locators", {})[bone["name"]] = list(bone["pivot"])
    missing = [n for n in rocco_data.LOCATORS if n not in names]
    if missing:
        raise SystemExit("faltan huesos localizadores en el modelo: " + ", ".join(missing))
    write_json(os.path.join(RP, "models", "entity", "rocco.geo.json"), geo)
    for name in ("truco_mesa", "truco_silla"):
        shutil.copyfile(os.path.join(ASSETS, "geo", "entity", name + ".geo.json"),
                        os.path.join(RP, "models", "entity", name + ".geo.json"))
    return names


# ---------------------------------------------------------------- animaciones

def plain_keyframes(channel):
    """GeckoLib guarda cada fotograma como {"vector": [...]}; Bedrock quiere la lista sola."""
    if isinstance(channel, list) or isinstance(channel, (int, float, str)):
        return channel
    out = {}
    for time, frame in channel.items():
        if isinstance(frame, dict) and "vector" in frame:
            if frame.get("easing") not in (None, "linear"):
                raise SystemExit("hay un fotograma con easing que Bedrock no entiende: " + frame["easing"])
            out[time] = frame["vector"]
        else:
            out[time] = frame
    return out


def as_list(value):
    return value if isinstance(value, list) else [value]


def build_animations(bones):
    data = read_json(os.path.join(ASSETS, "animations", "entity", "model.animation.json"))
    faces = {face: i for i, face in enumerate(rocco_data.FACES)}
    used_particles = set()
    used_sounds = set()
    for name, anim in data["animations"].items():
        for bone, channels in list(anim.get("bones", {}).items()):
            if bone not in bones:
                del anim["bones"][bone]
                continue
            for key in list(channels):
                channels[key] = plain_keyframes(channels[key])
        # Las caras de la linea de tiempo ("face:happy;") pasan a una variable del cliente.
        for time, value in list(anim.get("timeline", {}).items()):
            out = []
            for item in as_list(value):
                for part in item.split(";"):
                    part = part.strip()
                    if not part:
                        continue
                    kind, _, arg = part.partition(":")
                    if kind != "face" or arg not in faces:
                        raise SystemExit("instruccion de linea de tiempo desconocida en %s: %s" % (name, part))
                    out.append("v.tface = %d;" % faces[arg])
            anim["timeline"][time] = out if len(out) != 1 else out[0]
        for time, value in list(anim.get("particle_effects", {}).items()):
            fixed = []
            for fx in as_list(value):
                key = rocco_data.particle_key(fx["effect"])
                used_particles.add(fx["effect"])
                entry = {"effect": key}
                if fx.get("locator"):
                    entry["locator"] = fx["locator"]
                fixed.append(entry)
            anim["particle_effects"][time] = fixed if len(fixed) != 1 else fixed[0]
        for time, value in list(anim.get("sound_effects", {}).items()):
            fixed = []
            for fx in as_list(value):
                key = fx["effect"].split(":", 1)[-1]
                used_sounds.add(key)
                fixed.append({"effect": key})
            anim["sound_effects"][time] = fixed if len(fixed) != 1 else fixed[0]
        # Las que no son bucle se quedan en su ultimo cuadro hasta que el script pide otra.
        if anim.get("loop") is not True:
            anim["loop"] = "hold_on_last_frame"
    data["format_version"] = "1.8.0"
    write_json(os.path.join(RP, "animations", "rocco.animation.json"), data)

    for name in ("truco_mesa", "truco_silla"):
        extra = read_json(os.path.join(ASSETS, "animations", "entity", name + ".animation.json"))
        for anim in extra["animations"].values():
            for channels in anim.get("bones", {}).values():
                for key in list(channels):
                    channels[key] = plain_keyframes(channels[key])
        write_json(os.path.join(RP, "animations", name + ".animation.json"), extra)
    return sorted(data["animations"]), used_particles, used_sounds


# ---------------------------------------------------------------- texturas

def build_textures():
    tex = os.path.join(RP, "textures")
    os.makedirs(os.path.join(tex, "entity", "rocco"), exist_ok=True)
    for name in ("rocco", "truco_mesa", "truco_silla"):
        shutil.copyfile(os.path.join(ASSETS, "textures", "entity", name + ".png"),
                        os.path.join(tex, "entity", "rocco", name + ".png"))
    # Solo las que usa el dibujo de Bedrock (sin los brillos ni el tinte del cuero de Java).
    import client_gen
    for m in client_gen.ARMOR_MATERIALS:
        shutil.copyfile(os.path.join(ASSETS, "textures", "entity", "armor", "rocco_armor_%s.png" % m),
                        os.path.join(tex, "entity", "rocco", "rocco_armor_%s.png" % m))
    for m in client_gen.WEAPON_MATERIALS:
        shutil.copyfile(os.path.join(ASSETS, "textures", "entity", "weapon", "rocco_weapon_%s.png" % m),
                        os.path.join(tex, "entity", "rocco", "rocco_weapon_%s.png" % m))
    items = os.path.join(ASSETS, "textures", "item")
    os.makedirs(os.path.join(tex, "items", "rocco"), exist_ok=True)
    for name in os.listdir(items):
        if name.endswith(".png") and "_atlas" not in name:
            shutil.copyfile(os.path.join(items, name), os.path.join(tex, "items", "rocco", name.replace("_gui", "")))
    # El mate de Java es un modelo 3D y mate.png es su textura, no un icono: se dibuja el modelo
    # de frente para que en la barra se vea el mate y no un mosaico.
    model_icon(os.path.join(ASSETS, "models", "item", "mate.json"), os.path.join(items, "mate.png"),
               os.path.join(tex, "items", "rocco", "mate.png"))
    # La semillita hace de huevo de Rocco.
    shutil.copyfile(os.path.join(items, "semillita.png"), os.path.join(tex, "items", "rocco", "rocco_spawn_egg.png"))
    # Cartas del truco sueltas: los formularios muestran una imagen por boton.
    cut_cards(os.path.join(ASSETS, "textures", "gui", "truco", "cartas.png"), os.path.join(tex, "entity", "rocco", "cartas"))


def model_icon(model_path, texture_path, out_path, size=32):
    """Icono de un item con modelo 3D de Java: la cara de frente (north) de cada cubo, de atras
    para adelante, mas un poco de la de arriba para que tenga volumen."""
    model = read_json(model_path)
    tex = Image.open(texture_path).convert("RGBA")
    tw, th = model.get("texture_size", [16, 16])
    sx, sy = tex.width / tw, tex.height / th
    k = size / 16.0
    icon = Image.new("RGBA", (size, size))
    for el in sorted(model["elements"], key=lambda e: -e["from"][2]):
        x1, y1, z1 = el["from"]
        x2, y2, z2 = el["to"]
        for face, (left, top, w, h) in (
            ("up", (16 - x2, 16 - y2 - (z2 - z1) * 0.35, x2 - x1, (z2 - z1) * 0.35)),
            ("north", (16 - x2, 16 - y2, x2 - x1, y2 - y1)),
        ):
            f = el.get("faces", {}).get(face)
            if not f or w <= 0 or h <= 0:
                continue
            u1, v1, u2, v2 = f["uv"]
            crop = tex.crop((int(min(u1, u2) * sx), int(min(v1, v2) * sy),
                             max(int(max(u1, u2) * sx), int(min(u1, u2) * sx) + 1),
                             max(int(max(v1, v2) * sy), int(min(v1, v2) * sy) + 1)))
            pw, ph = max(1, round(w * k)), max(1, round(h * k))
            part = crop.resize((pw, ph), Image.NEAREST)
            if face == "up":
                part = Image.eval(part, lambda p: p)  # sin sombra extra
            icon.alpha_composite(part, (max(0, round(left * k)), max(0, round(top * k))))
    # Centrado: se recorta lo vacio y se pone en el medio.
    box = icon.getbbox()
    if box:
        piece = icon.crop(box)
        out = Image.new("RGBA", (size, size))
        out.alpha_composite(piece, ((size - piece.width) // 2, (size - piece.height) // 2))
        icon = out
    icon.save(out_path)


def cut_cards(sheet_path, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    sheet = Image.open(sheet_path).convert("RGBA")
    w, h = rocco_data.CARD_SIZE
    for suit_row, suit in enumerate(rocco_data.CARD_SUITS):
        for col, number in enumerate(rocco_data.CARD_NUMBERS):
            u, v = col * w, suit_row * h
            sheet.crop((u, v, u + w, v + h)).save(os.path.join(out_dir, "%s_%d.png" % (suit, number)))
    u, v = rocco_data.CARD_BACK
    sheet.crop((u, v, u + w, v + h)).save(os.path.join(out_dir, "dorso.png"))


def build_texture_lists():
    item_texture = {"resource_pack_name": "rocco", "texture_name": "atlas.items", "texture_data": {}}
    for name in os.listdir(os.path.join(RP, "textures", "items", "rocco")):
        key = "rocco_" + name[:-4]
        item_texture["texture_data"][key] = {"textures": ["textures/items/rocco/" + name[:-4]]}
    write_json(os.path.join(RP, "textures", "item_texture.json"), item_texture, pretty=True)


# ---------------------------------------------------------------- sonidos

def build_sounds(anim_sounds):
    java = read_json(os.path.join(ASSETS, "sounds.json"))
    defs = {}
    for event, spec in java.items():
        if event in rocco_data.SKIP_SOUNDS:
            continue
        entries = []
        for s in spec.get("sounds", []):
            s = {"name": s} if isinstance(s, str) else dict(s)
            rel = s["name"].split(":", 1)[-1]
            path = "sounds/rocco/" + rel
            src = os.path.join(ASSETS, "sounds", rel + ".ogg")
            if not os.path.exists(src):
                raise SystemExit("falta el sonido " + path)
            os.makedirs(os.path.dirname(os.path.join(RP, path + ".ogg")), exist_ok=True)
            shutil.copyfile(src, os.path.join(RP, path + ".ogg"))
            entry = {"name": path}
            if "volume" in s:
                entry["volume"] = s["volume"]
            if "pitch" in s:
                entry["pitch"] = s["pitch"]
            if s.get("stream"):
                entry["stream"] = True
            entries.append(entry)
        defs["rocco." + event] = {
            "category": "neutral",
            "min_distance": 2.0,
            "max_distance": 24.0,
            "sounds": entries,
        }
    missing = [s for s in anim_sounds if "rocco." + s not in defs]
    if missing:
        raise SystemExit("las animaciones piden sonidos que no existen: " + ", ".join(sorted(missing)))
    write_json(os.path.join(RP, "sounds", "sound_definitions.json"),
               {"format_version": "1.14.0", "sound_definitions": defs}, pretty=True)
    return sorted(defs)


# ---------------------------------------------------------------- particulas

LOOK_RE = re.compile(r"public static final Look (\w+) = new Look\(([^)]*)\);")
FX_CASE_RE = re.compile(r'case "([a-z_:]+)" ->')


def parse_looks():
    text = open(os.path.join(JAVA_SRC, "client", "RoccoParticles.java"), encoding="utf-8").read()
    looks = {}
    for name, args in LOOK_RE.findall(text):
        parts = [a.strip().rstrip("FD") for a in args.split(",")]
        looks[name.lower()] = {
            "min": int(parts[0]), "max": int(parts[1]), "size": float(parts[2]), "grow": float(parts[3]),
            "gravity": float(parts[4]), "by_age": parts[5] == "true", "fade": float(parts[6]),
            "bright": parts[7] == "true", "spin": float(parts[8]), "sway": float(parts[9]), "drag": float(parts[10]),
        }
    # Las que no usan Look (tienen su propia clase en Java): valores parecidos a mano.
    looks.update(rocco_data.CUSTOM_LOOKS)
    return looks


def frames_of(name):
    spec = os.path.join(ASSETS, "particles", name + ".json")
    if not os.path.exists(spec):
        return []
    return [t.split(":", 1)[-1] for t in read_json(spec)["textures"]]


def strip_texture(name, frames):
    """Todas las imagenes de una particula en una tira horizontal (Bedrock usa una textura por efecto)."""
    images = [Image.open(os.path.join(ASSETS, "textures", "particle", f + ".png")).convert("RGBA") for f in frames]
    w = max(i.width for i in images)
    h = max(i.height for i in images)
    strip = Image.new("RGBA", (w * len(images), h))
    for i, img in enumerate(images):
        strip.paste(img, (i * w, 0))
    out = os.path.join(RP, "textures", "particle", "rocco", name + ".png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    strip.save(out)
    return w, h, len(images)


def mol(x):
    return round(x, 4)


def particle_json(name, look, emit):
    frames = frames_of(name)
    if not frames:
        raise SystemExit("la particula %s no tiene imagenes" % name)
    w, h, n = strip_texture(name, frames)
    life_min = look["min"] / 20.0
    life_max = look["max"] / 20.0
    size = look["size"]
    grow = look["grow"]
    # En Java la velocidad se multiplica por drag cada tick y la gravedad es g*0,04 por tick.
    drag = 0.0 if look["drag"] <= 0 else -math.log(look["drag"]) * 20.0
    if look["drag"] <= 0:
        drag = 40.0
    accel = -look["gravity"] * 0.04 * 400.0
    fade = look["fade"]
    t = "(v.particle_age / v.particle_lifetime)"
    size_expr = "%s * (1 + %s * %s)" % (mol(size), mol(grow - 1.0), t)
    alpha = "math.clamp(1 - (%s - %s) / %s, 0, 1)" % (t, mol(fade), mol(max(1.0 - fade, 0.01)))
    if look["by_age"] and n > 1:
        uv = {"texture_width": w * n, "texture_height": h,
              "flipbook": {"base_UV": [0, 0], "size_UV": [w, h], "step_UV": [w, 0],
                           "max_frame": n, "stretch_to_lifetime": True, "loop": False}}
    else:
        uv = {"texture_width": w * n, "texture_height": h,
              "uv": ["math.floor(v.particle_random_2 * %d) * %d" % (n, w), 0], "uv_size": [w, h]}
    count = emit["count"]
    vel = emit["vel"]
    spread = emit["vel_spread"]
    pos = emit["pos_spread"]
    components = {
        "minecraft:emitter_rate_instant": {"num_particles": count if isinstance(count, (int, float)) else count},
        "minecraft:emitter_lifetime_once": {"active_time": 0},
        "minecraft:emitter_shape_box": {
            "offset": [0, 0, 0],
            "half_dimensions": [mol(pos[0]), mol(pos[1]), mol(pos[2])],
            "direction": [
                "%s + (math.random(-1, 1) * %s)" % (mol(vel[0] * 20), mol(spread[0] * 20)),
                "%s + (math.random(0, 1) * %s)" % (mol(vel[1] * 20), mol(spread[1] * 20)) if emit.get("up_only")
                else "%s + (math.random(-1, 1) * %s)" % (mol(vel[1] * 20), mol(spread[1] * 20)),
                "%s + (math.random(-1, 1) * %s)" % (mol(vel[2] * 20), mol(spread[2] * 20)),
            ],
        },
        "minecraft:particle_initial_speed": 1.0,
        "minecraft:particle_lifetime_expression": {
            "max_lifetime": "%s + v.particle_random_1 * %s" % (mol(life_min), mol(life_max - life_min))},
        "minecraft:particle_motion_dynamic": {
            "linear_acceleration": [0, mol(accel), 0],
            "linear_drag_coefficient": mol(drag),
        },
        "minecraft:particle_appearance_billboard": {
            "size": [size_expr, size_expr],
            "facing_camera_mode": "rotate_xyz",
            "uv": uv,
        },
        "minecraft:particle_appearance_tinting": {"color": [1, 1, 1, alpha]},
    }
    if look["spin"]:
        components["minecraft:particle_initial_spin"] = {
            "rotation": "v.particle_random_3 * 360",
            "rotation_rate": mol(look["spin"] * 20 * 57.3)}
    if not look["bright"]:
        components["minecraft:particle_appearance_lighting"] = {}
    return {
        "format_version": "1.10.0",
        "particle_effect": {
            "description": {
                "identifier": "rocco:" + name,
                "basic_render_parameters": {"material": "particles_blend",
                                            "texture": "textures/particle/rocco/" + name},
            },
            "components": components,
        },
    }


def build_particles(used):
    looks = parse_looks()
    made = []
    for effect in sorted(used):
        if not effect.startswith("rocco:"):
            continue
        name = effect.split(":", 1)[1]
        look = looks.get(name)
        if look is None:
            raise SystemExit("no se como se ve la particula " + name)
        emit = rocco_data.EMIT.get(name, rocco_data.DEFAULT_EMIT)
        write_json(os.path.join(RP, "particles", "rocco", name + ".json"), particle_json(name, look, emit), pretty=True)
        made.append(name)
    # Las que el script lanza a mano (no salen de una animacion).
    for name in rocco_data.SCRIPT_PARTICLES:
        if name in made:
            continue
        look = looks[name]
        emit = rocco_data.EMIT.get(name, rocco_data.DEFAULT_EMIT)
        write_json(os.path.join(RP, "particles", "rocco", name + ".json"), particle_json(name, look, emit), pretty=True)
        made.append(name)
    return made


# ---------------------------------------------------------------- cliente: entidad y controladores

def build_client_entity(anim_names, particles, sounds):
    import client_gen
    client_gen.write_all(RP, anim_names, particles, sounds, write_json, BP)


# ---------------------------------------------------------------- empaquetar

def zip_pack(folder, zf, prefix):
    for root, _, files in os.walk(folder):
        for name in files:
            full = os.path.join(root, name)
            zf.write(full, os.path.join(prefix, os.path.relpath(full, folder)).replace("\\", "/"))


def package():
    version = read_json(os.path.join(SRC, "Rocco_BP", "manifest.json"))["header"]["version"]
    name = "Rocco-%d.%d.%d.mcaddon" % tuple(version)
    path = os.path.join(OUT, name)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
        zip_pack(RP, zf, "Rocco_RP")
        zip_pack(BP, zf, "Rocco_BP")
    ia = os.path.join(OUT, "Rocco_IA_BP")
    if os.path.isdir(ia):
        ia_path = os.path.join(OUT, "Rocco-IA-servidor-%d.%d.%d.mcpack" % tuple(version))
        with zipfile.ZipFile(ia_path, "w", zipfile.ZIP_DEFLATED) as zf:
            zip_pack(ia, zf, "")
    return path


def install():
    base = os.path.join(os.environ["APPDATA"], "Minecraft Bedrock", "Users", "Shared", "games", "com.mojang")
    if not os.path.isdir(base):
        raise SystemExit("no encontre Minecraft Bedrock en " + base)
    for kind, folder in (("development_resource_packs", RP), ("development_behavior_packs", BP)):
        target = os.path.join(base, kind, os.path.basename(folder))
        if os.path.isdir(target):
            shutil.rmtree(target)
        shutil.copytree(folder, target)
        print("instalado en", target)


def run_simulation():
    """Corre los scripts armados contra la imitacion del juego (test/): brotar, despertar, charlar,
    asado, tortas, mate, pelea, tumba, equipo, una partida de truco entera y el regalo al volver."""
    import subprocess
    test = os.path.join(BEDROCK, "test")
    work = os.path.join(OUT, "test")
    if os.path.isdir(work):
        shutil.rmtree(work)
    copy_tree(os.path.join(BP, "scripts"), work)
    for module, fake in (("server", "fake-server.js"), ("server-ui", "fake-ui.js")):
        folder = os.path.join(work, "node_modules", "@minecraft", module)
        os.makedirs(folder, exist_ok=True)
        write_json(os.path.join(folder, "package.json"), {"name": "@minecraft/" + module, "type": "module", "main": "index.js"})
        shutil.copyfile(os.path.join(test, fake), os.path.join(folder, "index.js"))
    write_json(os.path.join(work, "package.json"), {"type": "module"})
    shutil.copyfile(os.path.join(test, "sim.mjs"), os.path.join(work, "sim.mjs"))
    result = subprocess.run(["node", "sim.mjs"], cwd=work)
    shutil.rmtree(work, ignore_errors=True)
    if result.returncode != 0:
        raise SystemExit("la simulacion fallo")


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    copy_tree(os.path.join(SRC, "Rocco_RP"), RP)
    copy_tree(os.path.join(SRC, "Rocco_BP"), BP)
    if os.path.isdir(os.path.join(SRC, "Rocco_IA_BP")):
        copy_tree(os.path.join(SRC, "Rocco_IA_BP"), os.path.join(OUT, "Rocco_IA_BP"))
    bones = build_geometry()
    anim_names, used_particles, used_sounds = build_animations(bones)
    build_textures()
    build_texture_lists()
    sounds = build_sounds(used_sounds)
    particles = build_particles(used_particles)
    build_client_entity(anim_names, particles, sounds)
    import data_gen
    data_gen.write_all(BP, RP, JAVA, ASSETS, write_json)
    import java_port
    print("frases y patrones de OfflineRocco:", java_port.write_offline(JAVA_SRC, BP))
    ia = os.path.join(OUT, "Rocco_IA_BP")
    if os.path.isdir(ia):
        import prompt_gen
        sizes = prompt_gen.write(os.path.join(JAVA, "resources"), os.path.join(ia, "scripts", "gen", "prompt.js"))
        print("prompt de la IA (caracteres):", sizes)
    import verify
    problems = verify.check(RP, BP)
    if problems:
        raise SystemExit("Hay nombres que no existen:\n  " + "\n  ".join(sorted(set(problems))))
    if "--test" in sys.argv:
        run_simulation()
    path = package()
    print("listo:", path)
    if "--install" in sys.argv:
        install()


if __name__ == "__main__":
    main()
