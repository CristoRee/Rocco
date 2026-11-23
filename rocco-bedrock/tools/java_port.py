# Saca del mod de Java lo que Rocco dice sin IA: las frases de OfflineRocco (LINES) y los
# patrones con los que entiende lo que le escriben. Asi la version de Bedrock habla igual y, si
# se cambia una frase en Java, aca llega sola al volver a armar.
import json
import os
import re

STRING = r'"((?:[^"\\]|\\.)*)"'


def java_unescape(s):
    out = []
    i = 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            mapping = {"n": "\n", "t": "\t", '"': '"', "'": "'", "\\": "\\"}
            if n in mapping:
                out.append(mapping[n])
                i += 2
                continue
            if n == "u":
                out.append(chr(int(s[i + 2:i + 6], 16)))
                i += 6
                continue
        out.append(c)
        i += 1
    return "".join(out)


def extract_lines(text):
    start = text.index("private static final Map<String, String[]> LINES = Map.ofEntries(")
    body = text[start:]
    lines = {}
    for m in re.finditer(r'Map\.entry\("([a-z0-9_]+)",\s*new String\[\]\s*\{(.*?)\}\)', body, re.S):
        key = m.group(1)
        items = [java_unescape(s) for s in re.findall(STRING, m.group(2))]
        lines[key] = items
    return lines


def extract_patterns(text):
    patterns = {}
    for m in re.finditer(r"private static final Pattern (\w+) = Pattern\.compile\((.*?)\);", text, re.S):
        name, args = m.group(1), m.group(2)
        flags = ""
        if "CASE_INSENSITIVE" in args:
            flags += "i"
        args = re.sub(r",\s*Pattern\.\w+.*$", "", args, flags=re.S)
        parts = re.findall(STRING, args)
        patterns[name] = {"source": "".join(java_unescape(p) for p in parts), "flags": flags}
    return patterns


def write_offline(java_src, bp):
    path = os.path.join(java_src, "ai", "OfflineRocco.java")
    text = open(path, encoding="utf-8").read()
    lines = extract_lines(text)
    if len(lines) < 100:
        raise SystemExit("no pude leer las frases de OfflineRocco (%d)" % len(lines))
    patterns = extract_patterns(text)
    out = os.path.join(bp, "scripts", "gen", "offline.js")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write("// Generado por tools/java_port.py desde OfflineRocco.java: no editar a mano.\n")
        f.write("export const LINES = %s;\n" % json.dumps(lines, ensure_ascii=False, indent=1))
        f.write("export const P = {\n")
        for name, p in patterns.items():
            f.write("  %s: new RegExp(%s, %s),\n" % (name, json.dumps(p["source"], ensure_ascii=False), json.dumps(p["flags"])))
        f.write("};\n")
    return len(lines), len(patterns)
