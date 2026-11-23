# Prepara el servidor oficial de Bedrock con los paquetes de Rocco y le manda comandos por la
# consola. Todo lo que dice el servidor queda en bds.log.
#   python run_bds.py <carpeta_bds> <carpeta_build> <comandos.txt> [ia]
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time

BDS, BUILD, SCRIPT = sys.argv[1], sys.argv[2], sys.argv[3]
EXTRA = sys.argv[4:]   # carpetas de paquetes de comportamiento extra (la IA, la prueba)
LEVEL = "rocco_prueba"


def manifest(folder):
    return json.load(open(os.path.join(folder, "manifest.json"), encoding="utf-8"))["header"]


def set_props(path, values):
    lines = open(path, encoding="utf-8").read().split("\n")
    out = []
    seen = set()
    for line in lines:
        key = line.split("=", 1)[0]
        if key in values:
            out.append(key + "=" + values[key])
            seen.add(key)
        else:
            out.append(line)
    for k, v in values.items():
        if k not in seen:
            out.append(k + "=" + v)
    open(path, "w", encoding="utf-8", newline="\n").write("\n".join(out))


set_props(os.path.join(BDS, "server.properties"), {
    "level-name": LEVEL, "gamemode": "creative", "allow-cheats": "true", "online-mode": "false",
    "content-log-file-enabled": "true", "content-log-console-output-enabled": "true", "content-log-level": "verbose",
    "level-seed": "rocco", "default-player-permission-level": "operator", "allow-list": "false", "enable-lan-visibility": "false",
})

packs = [("behavior_packs", os.path.join(BUILD, "Rocco_BP")), ("resource_packs", os.path.join(BUILD, "Rocco_RP"))]
for extra in EXTRA:
    packs.append(("behavior_packs", extra))
world = os.path.join(BDS, "worlds", LEVEL)
os.makedirs(world, exist_ok=True)
bp_list, rp_list = [], []
for kind, src in packs:
    dst = os.path.join(BDS, kind, os.path.basename(src))
    if os.path.isdir(dst):
        shutil.rmtree(dst)
    shutil.copytree(src, dst)
    h = manifest(src)
    entry = {"pack_id": h["uuid"], "version": h["version"]}
    (bp_list if kind == "behavior_packs" else rp_list).append(entry)
json.dump(bp_list, open(os.path.join(world, "world_behavior_packs.json"), "w"))
json.dump(rp_list, open(os.path.join(world, "world_resource_packs.json"), "w"))

log = open(os.path.join(os.path.dirname(SCRIPT), "bds.log"), "w", encoding="utf-8")
proc = subprocess.Popen([os.path.join(BDS, "bedrock_server.exe")], cwd=BDS, stdin=subprocess.PIPE,
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
started = threading.Event()
lines = []


def reader():
    for line in proc.stdout:
        line = line.rstrip()
        lines.append(line)
        log.write(line + "\n")
        log.flush()
        if "Server started" in line:
            started.set()


threading.Thread(target=reader, daemon=True).start()
if not started.wait(180):
    print("el servidor no arranco")
    proc.kill()
    sys.exit(2)


def send(cmd):
    log.write(">>> " + cmd + "\n")
    proc.stdin.write(cmd + "\n")
    proc.stdin.flush()


for raw in open(SCRIPT, encoding="utf-8"):
    raw = raw.strip()
    if not raw or raw.startswith("#"):
        continue
    m = re.match(r"^esperar (\d+(?:\.\d+)?)$", raw)
    if m:
        time.sleep(float(m.group(1)))
        continue
    send(raw)
    time.sleep(0.4)

send("stop")
try:
    proc.wait(60)
except subprocess.TimeoutExpired:
    proc.kill()
log.close()
print("listo, lineas:", len(lines))
