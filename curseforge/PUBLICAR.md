# Cómo publicar Rocco en CurseForge

Guía de cada campo que pide CurseForge al crear el proyecto y al subir un archivo. Los textos para pegar están en esta misma carpeta.

## 1. Crear el proyecto

En https://authors.curseforge.com → **Create Project** → Minecraft → Mods.

| Campo | Qué poner |
|---|---|
| Nombre | Rocco |
| Resumen (Summary) | El de `RESUMEN.txt` |
| Descripción | El contenido de `DESCRIPCION.md` (el editor acepta Markdown) |
| Categoría principal | Mobs |
| Otras categorías | Adventure and RPG, Utility & QoL |
| Licencia | **Custom License**, pegando el texto completo de `../LICENSE.md` |
| Avatar del proyecto | Un render cuadrado de Rocco. Conviene exportar uno nuevo desde Blockbench de 512 × 512 o más: el `logo.png` del mod es de 256 × 256 y escalado se ve borroso |
| Código fuente / issues | El link de GitHub solo si el repositorio es público. Si es privado, dejalo vacío (los links del `mods.toml` apuntan ahí) |

## 2. Subir el archivo

Armá el jar con `gradlew build` dentro de `rocco-forge`. Queda en `rocco-forge/build/libs/rocco-forge-1.20.1-1.5.0.jar`.

| Campo | Qué poner |
|---|---|
| Archivo | `rocco-forge-1.20.1-1.5.0.jar` |
| Nombre visible | Rocco 1.5.0 (Forge 1.20.1) |
| Tipo | Release (o Beta, si querés probarlo primero con algunos jugadores) |
| Versión del juego | 1.20.1 |
| Mod loader | Forge |
| Entorno | Client y Server (va en los dos) |
| Java | 17 |
| Changelog | El de `ARCHIVO-1.5.0.md` |
| Dependencias | **GeckoLib**: Required Dependency |

## 3. Imágenes

- Capturas y GIFs para la galería: sirven los de `UsaryVaciaROCCO/rocco_completo/previews` (armas, armaduras, tumba, nacimiento, Roco Cola, cofre de ender, canto), más capturas tuyas jugando: el menú, el mate compartido, volando con elytra, el patito y el barquito.
- Una imagen ancha para el encabezado de la descripción, si querés.

## 4. Lo que revisa la moderación

- **Servicios externos:** la descripción ya explica a qué se conecta el mod y solo si el jugador pone una clave. Es lo que más piden en mods con IA.
- **Voces de personas reales:** el mod no trae ninguna. Las imitaciones usan voces públicas que la comunidad sube a Fish Audio, bajo sus términos, y la búsqueda se puede apagar (`voice.impressionVoiceSearch`). Si la moderación lo objeta, apagarla de fábrica es un cambio de una línea.
- **Contenido generado con IA:** antes de subir, revisá las reglas vigentes de CurseForge por si piden declarar assets hechos con IA (texturas, sonidos, frases grabadas).
- **Música desde links:** solo abre archivos de audio, radios, Bandcamp y Twitch, con lavaplayer (Apache 2.0, licencias en `META-INF/THIRD-PARTY-NOTICES.txt`). No baja audio de YouTube, Spotify ni SoundCloud (esos links los rechaza), no ejecuta programas externos y no guarda nada en disco: el link lo pone el jugador. Si la moderación pregunta, es lo mismo que un reproductor de radio.
- **Tamaño del jar (unos 19 MB):** casi todo son los decodificadores de audio de lavaplayer para Windows, Linux y Mac.
- **Nombre "Roco Cola":** es una parodia; no usa logos ni el diseño de ninguna marca.

## 5. Antes de cada versión

1. Subir `mod_version` en `rocco-forge/gradle.properties`.
2. Sumar la sección nueva en `CHANGELOG.md` y armar el texto del archivo como `ARCHIVO-<versión>.md`.
3. Probar en un mundo nuevo y en uno viejo (que cargue bien con un Rocco de la versión anterior).
4. `gradlew build` y subir el jar.
