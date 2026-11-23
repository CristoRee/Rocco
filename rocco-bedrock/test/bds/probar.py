# Prueba de Rocco en el servidor oficial de Bedrock (Bedrock Dedicated Server) con un jugador
# simulado: brota, despierta, habla, te sigue, asado, tortas, mate, Roco Cola, pelea, equipo,
# comida, tumba, los muebles del truco y (con --ia) el paquete de la IA.
#
#   python rocco-bedrock/tools/build.py
#   python rocco-bedrock/test/bds/probar.py --bds <carpeta del servidor> [--ia] [--mock]
#
# --mock levanta un proveedor de IA de prueba (mockai.js, con la API de OpenAI) en esta PC, asi se
# prueba la vuelta entera de la IA sin claves. El servidor se baja de minecraft.net y tiene que ser
# de la misma version que las APIs beta de los manifiestos.
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
BEDROCK = os.path.dirname(os.path.dirname(HERE))
BUILD = os.path.join(BEDROCK, "build")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--bds", required=True)
    ap.add_argument("--ia", action="store_true")
    ap.add_argument("--mock", action="store_true")
    ap.add_argument("--segundos", type=int, default=300)
    a = ap.parse_args()
    work = os.path.join(BUILD, "prueba_bds")
    if os.path.isdir(work):
        shutil.rmtree(work)
    os.makedirs(work)
    pack = os.path.join(work, "Rocco_Prueba_BP")
    shutil.copytree(os.path.join(HERE, "Rocco_Prueba_BP"), pack)
    shutil.copyfile(os.path.join(BUILD, "Rocco_BP", "scripts", "gen", "anims.js"), os.path.join(pack, "scripts", "anims.js"))

    config = os.path.join(a.bds, "config", "default")
    perms = os.path.join(config, "permissions.json")
    d = json.load(open(perms))
    for m in ("@minecraft/server-net", "@minecraft/server-admin", "@minecraft/server-gametest"):
        if m not in d["allowed_modules"]:
            d["allowed_modules"].append(m)
    json.dump(d, open(perms, "w"), indent=2)
    variables = os.path.join(config, "variables.json")
    if a.mock:
        json.dump({"proveedor": "propio", "url": "http://127.0.0.1:18085/v1", "modelo": "auto"}, open(variables, "w"))
    elif a.ia and not os.path.exists(variables):
        json.dump({"proveedor": "gemini"}, open(variables, "w"))

    world = os.path.join(a.bds, "worlds", "rocco_prueba")
    if os.path.isdir(world):
        shutil.rmtree(world)
    run = lambda cmds, extra: subprocess.run([sys.executable, os.path.join(HERE, "run_bds.py"), a.bds, BUILD, cmds] + extra)
    crear = os.path.join(work, "crear.txt")
    open(crear, "w").write("esperar 3\n")
    run(crear, [])
    subprocess.run([sys.executable, os.path.join(HERE, "nbt_le.py"), os.path.join(world, "level.dat")], check=True)
    cmds = os.path.join(work, "cmds.txt")
    open(cmds, "w").write("esperar 8\ntickingarea add circle 0 0 0 4 prueba\nesperar 3\nscriptevent rocco:prueba\nesperar %d\n" % a.segundos)
    mock = subprocess.Popen(["node", os.path.join(HERE, "mockai.js")]) if a.mock else None
    try:
        time.sleep(1)
        run(cmds, [pack] + ([os.path.join(BUILD, "Rocco_IA_BP")] if a.ia or a.mock else []))
    finally:
        if mock:
            mock.kill()
    log = open(os.path.join(work, "bds.log"), encoding="utf-8").read()
    lines = [l for l in log.splitlines() if re.search(r"PRUEBA|ERROR|\[Log\]", l)]
    print("\n".join(lines))
    ok = "PRUEBA TERMINADO fallas=0" in log and not re.search(r"ERROR\] \[Scripting\]|\[Log\]", log)
    print("\nRESULTADO:", "todo bien" if ok else "hay fallas")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
