# Cómo publicar Rocco para Bedrock

Es un proyecto aparte del mod de Java (otro juego en CurseForge). Los textos están en esta carpeta.

## Los archivos

Se arman con `python rocco-bedrock/tools/build.py --test` (arma, verifica los nombres y corre la simulación) y quedan en `rocco-bedrock/build/`:

| Archivo | Qué es | ¿Se sube? |
|---|---|---|
| `Rocco-1.0.0.mcaddon` | Rocco (los dos paquetes juntos) | **Sí**: es el archivo principal |
| `Rocco-IA-servidor-1.0.0.mcpack` | El paquete opcional de IA para servidores dedicados | Opcional, como archivo adicional y aclarando que es solo para servidores |

Antes de subir una versión nueva conviene correr también la prueba en el servidor oficial (`rocco-bedrock/test/bds/probar.py`, ver el LEEME de `rocco-bedrock`).

## CurseForge (Minecraft Bedrock)

En https://authors.curseforge.com → **Create Project** → **Minecraft Bedrock** → **Addons**.

| Campo | Qué poner |
|---|---|
| Nombre | Rocco (Bedrock) |
| Resumen | El de `RESUMEN.txt` |
| Descripción | `DESCRIPCION.md` |
| Categorías | Mobs (principal), y si las ofrece: Adventure, Utility |
| Licencia | La misma del mod de Java (`../LICENCIA-curseforge.txt`) |
| Avatar | El mismo render de Rocco que el proyecto de Java |
| Archivo | `Rocco-1.0.0.mcaddon` |
| Nombre visible | Rocco 1.0.0 (Bedrock 1.26) |
| Tipo | Release (o Beta, si querés que lo prueben unos jugadores primero) |
| Versión del juego | 1.26 (todas las 1.26.x) |
| Changelog | `ARCHIVO-1.0.0.md` |

El paquete de IA se sube aparte como archivo adicional, con nombre visible "Rocco IA (solo servidores dedicados)". En su changelog conviene pegar los pasos de la sección "Rocco IA" del LEEME de `rocco-bedrock`.

Desde la descripción del proyecto de Java se puede enlazar a este, y al revés. Las dos descripciones ya mencionan la otra versión.

## MCPEDL

Es el sitio más visitado para complementos de Bedrock. No aloja el archivo: se pone el enlace de descarga, por ejemplo el de CurseForge.

| Campo | Qué poner |
|---|---|
| Título | Rocco: your adventure companion |
| Categoría | Addons |
| Versión de Minecraft | 1.26 |
| Descripción | `DESCRIPCION.md` (el editor no siempre interpreta Markdown: los títulos se marcan a mano) |
| Instalación | La sección "Getting started" de la descripción |
| Experimentos | Ninguno |
| Descarga | El enlace del archivo en CurseForge |
| Imágenes | Capturas de Bedrock: Rocco brotando, el menú, el truco, el asado, el mate |

MCPEDL revisa a mano cada publicación y suele tardar unos días.

## Antes de cada versión

1. Subir la versión en `rocco-bedrock/src/Rocco_RP/manifest.json`, `Rocco_BP/manifest.json` y `Rocco_IA_BP/manifest.json`: el `version` del header, el de los módulos y el de las dependencias entre ellos.
2. Si cambió la versión de Minecraft, actualizar en `Rocco_IA_BP/manifest.json` las versiones beta de `@minecraft/server`, `@minecraft/server-net` y `@minecraft/server-admin`.
3. `python rocco-bedrock/tools/build.py --test`, la prueba en el servidor oficial, y un rato en el juego.
4. Escribir `ARCHIVO-<versión>.md` en esta carpeta.
