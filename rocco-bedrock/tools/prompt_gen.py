# El prompt de la IA para Bedrock, armado con los mismos textos y las mismas reglas que
# PersonalityPromptBuilder de Java (rocco/personality/v3, de Bruno: aca solo se leen). Salen los
# dos modos de fabrica (Chill y Sin filtro); los bloques que cambian en cada pedido (acciones,
# caras, voz y AHORA MISMO) quedan marcados y los completa el paquete de la IA.
import json
import os
import re

COMMENT = re.compile(r"^[ \t]*#(?:[ \t].*)?$")
PLACEHOLDER = re.compile(r"\{\{(\w+)\}\}")
ONLY_PREFIX = re.compile(r"^\s*(?:-|\d+\.)?\s*$")
VOICE_TAG = re.compile(r"\s*\[[a-zA-Z ]{2,24}\]")

PROFILES = {
    "chill": dict(origin="chill", treatment="amable", swearing="suaves", sarcasm="suave", ego="engreido",
                  dark_humor="si_lo_piden", dark_side=False, jealousy="tiernos", length="normal", questions="normal",
                  facts="normal", life_advice=True, fourth_wall="muy_rara", fears=True, potato=True),
    "sin_filtro": dict(origin="sin_filtro", treatment="burlon", swearing="normales", sarcasm="afilado", ego="engreido",
                       dark_humor="libre", dark_side=True, jealousy="dramaticos", length="normal", questions="normal",
                       facts="normal", life_advice=True, fourth_wall="muy_rara", fears=True, potato=True),
}
# RoccoExpression.selectable(), en orden.
SELECTABLE = ["happy", "bored", "serious", "scared", "sad", "angry", "smug", "psycho", "dizzy", "mewing", "cheer", "wow"]


def read(folder, name):
    text = open(os.path.join(folder, name), encoding="utf-8").read().replace("\r\n", "\n")
    return "\n".join(line for line in text.split("\n") if not COMMENT.match(line)).strip()


def sections(text):
    out = {}
    name = None
    body = []
    for line in text.split("\n"):
        if line.startswith("### "):
            if name is not None:
                out[name] = "\n".join(body).strip()
            name = line[4:].strip()
            body = []
        elif name is not None:
            body.append(line)
    if name is not None:
        out[name] = "\n".join(body).strip()
    return out


def pairs(text):
    out = {}
    for line in text.split("\n"):
        at = line.find("=")
        if at > 0:
            out[line[:at].strip()] = line[at + 1:].strip()
    return out


def fill(template, values):
    out = []
    for line in template.split("\n"):
        if not PLACEHOLDER.search(line):
            out.append(line)
            continue
        any_empty = False

        def repl(m):
            nonlocal any_empty
            v = values.get(m.group(1), "")
            if not v.strip():
                any_empty = True
            return v

        result = PLACEHOLDER.sub(repl, line)
        if any_empty and ONLY_PREFIX.match(result):
            continue
        out.append(result.rstrip())
    return re.sub(r"\n{3,}", "\n\n", "\n".join(out)).strip()


def examples(text, allows_psycho):
    groups = []
    for line in text.split("\n"):
        if line.startswith("Player:") or not groups:
            groups.append([])
        groups[-1].append(line)
    out = []
    for g in groups:
        joined = "\n".join(g)
        if not allows_psycho and "@psycho" in joined:
            continue
        # En Bedrock no hay voz ni imitaciones de voz.
        if "@voz:" in joined:
            continue
        out.append(VOICE_TAG.sub("", joined))
    return "\n".join(out).strip()


def build(java_resources):
    folder = os.path.join(java_resources, "rocco", "personality", "v3")
    core = read(folder, "core.txt")
    traits = sections(read(folder, "traits.txt"))
    faces_meaning = pairs(read(folder, "faces.txt"))
    ex = {"chill": read(folder, "examples_chill.txt"), "sin_filtro": read(folder, "examples_sin_filtro.txt")}
    prompts = {}
    faces = {}
    for mode, p in PROFILES.items():
        v = {
            "origin": traits.get("origin." + p["origin"], ""),
            "treatment": traits.get("treatment." + p["treatment"], ""),
            "swearing": traits.get("swearing." + p["swearing"], ""),
            "sarcasm": traits.get("sarcasm." + p["sarcasm"], ""),
            "ego": traits.get("ego." + p["ego"], ""),
            "dark_humor": traits.get("dark_humor." + p["dark_humor"], ""),
            "dark_side": traits.get("dark_side.raro", "") if p["dark_side"] else "",
            "jealousy": traits.get("jealousy." + p["jealousy"], ""),
            "length": traits.get("length." + p["length"], ""),
            "questions": traits.get("questions." + p["questions"], ""),
            "facts": traits.get("facts." + p["facts"], ""),
            "life_advice": traits.get("life_advice.on", "") if p["life_advice"] else "",
            "fourth_wall": traits.get("fourth_wall." + p["fourth_wall"], ""),
            "potato": traits.get("potato.on", "") if p["potato"] else "",
            "fears": traits.get("fears.on", "") if p["fears"] else "",
            # El dialecto de fabrica de Java (LATINO).
            "dialect": traits.get("dialect.latino", ""),
            "nickname": "",
            # Bedrock no tiene voz: las imitaciones quedan apagadas, como con la opcion de Java.
            "impressions": traits.get("impressions.off", ""),
            "examples": examples(ex["chill" if p["treatment"] == "amable" else "sin_filtro"], p["dark_side"]),
            "actions": "{{actions}}",
            "faces": "{{faces}}",
            "voice_tags": traits.get("voice_tags.off", ""),
            "right_now": "{{right_now}}",
        }
        prompts[mode] = fill(core, v)
        parts = []
        for f in SELECTABLE:
            if f == "psycho" and not p["dark_side"]:
                continue
            meaning = faces_meaning.get(f)
            parts.append("@" + f + (" (" + meaning + ")" if meaning else ""))
        faces[mode] = ", ".join(parts)
    return prompts, faces


def write(java_resources, out_file):
    prompts, faces = build(java_resources)
    os.makedirs(os.path.dirname(out_file), exist_ok=True)
    with open(out_file, "w", encoding="utf-8", newline="\n") as f:
        f.write("// Generado por tools/prompt_gen.py desde rocco/personality/v3: no editar a mano.\n")
        f.write("export const PROMPTS = %s;\n" % json.dumps(prompts, ensure_ascii=False, indent=1))
        f.write("export const FACES = %s;\n" % json.dumps(faces, ensure_ascii=False, indent=1))
    return {k: len(v) for k, v in prompts.items()}
