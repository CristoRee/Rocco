# Revisa que lo que nombran los scripts exista en los paquetes: sonidos, particulas, animaciones,
# textos (en los tres idiomas) y frases de Rocco. Asi un nombre mal escrito no llega al juego.
import json
import os
import re


def read(path):
    return open(path, encoding="utf-8").read()


def check(rp, bp):
    problems = []
    scripts = os.path.join(bp, "scripts")
    code = {}
    for root, _, files in os.walk(scripts):
        for name in files:
            if name.endswith(".js") and "gen" not in os.path.relpath(root, scripts).split(os.sep):
                code[os.path.relpath(os.path.join(root, name), scripts)] = read(os.path.join(root, name))
    sounds = json.load(open(os.path.join(rp, "sounds", "sound_definitions.json"), encoding="utf-8"))["sound_definitions"]
    particles = {f[:-5] for f in os.listdir(os.path.join(rp, "particles", "rocco"))}
    anims_js = read(os.path.join(scripts, "gen", "anims.js"))
    anims = set(re.findall(r'^ "([a-z0-9_]+)": \{', anims_js, re.M))
    offline = read(os.path.join(scripts, "gen", "offline.js"))
    lines = set(re.findall(r'^ "([a-z0-9_]+)": \[', offline, re.M))
    langs = {}
    for f in os.listdir(os.path.join(rp, "texts")):
        if f.endswith(".lang"):
            keys = set()
            for row in read(os.path.join(rp, "texts", f)).splitlines():
                if "=" in row:
                    keys.add(row.split("=", 1)[0])
            langs[f] = keys
    for file, text in code.items():
        for s in set(re.findall(r'"(rocco\.[a-z0-9_]+)"', text)):
            if s.startswith("rocco.") and s.count(".") == 1 and s not in sounds:
                problems.append("%s: sonido %s no existe" % (file, s))
        for p in set(re.findall(r'"rocco:([a-z_]+)"', text)):
            if p in particles or p in ("rocco", "truco_mesa", "truco_silla", "truco_carta", "mate", "roco_cola", "roco_cola_half",
                                       "roco_cola_empty", "botella_rota", "torta_frita", "asado_de_tira", "bondiola_asada",
                                       "costillitas_de_cordero", "semillita", "flor_amistad"):
                continue
            if text.count('"rocco:' + p + '"') and ("spawnParticle" in text and re.search(r'spawnParticle\("rocco:' + p, text)):
                problems.append("%s: particula rocco:%s no existe" % (file, p))
        for a in set(re.findall(r'(?:play|anim|hold)\((?:rocco|s\.rocco|s|attacker|r),\s*"([a-z0-9_]+)"', text)):
            if a not in anims and not a.endswith("_"):
                problems.append("%s: animacion %s no existe" % (file, a))
        for c in set(re.findall(r'line\("([a-z0-9_]+)"', text)):
            if c not in lines:
                problems.append("%s: no hay frases '%s'" % (file, c))
        for k in set(re.findall(r'translate: "([a-z0-9_.:]+)"', text)) | set(re.findall(r'tr\("([a-z0-9_.]+)"', text)):
            for lang, keys in ([] if k.endswith(".") else langs.items()):
                if k not in keys:
                    problems.append("%s: texto %s falta en %s" % (file, k, lang))
    return problems
