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
| Licencia | **Custom License**, pegando `LICENCIA-curseforge.txt` en el editor común (no interpreta Markdown y su ventana Source Code no deja pegar). Los títulos se marcan con el desplegable "Paragraph" |
| Avatar del proyecto | Un render cuadrado de Rocco. Conviene exportar uno nuevo desde Blockbench de 512 × 512 o más: el `logo.png` del mod es de 256 × 256 y escalado se ve borroso |
| Código fuente / issues | El link de GitHub solo si el repositorio es público. Si es privado, dejalo vacío (los links del `mods.toml` apuntan ahí) |

## 2. Subir el archivo

Armá el jar con `gradlew build` dentro de `rocco-forge`. Queda en `rocco-forge/build/libs/rocco-forge-1.20.1-1.6.0.jar`.

| Campo | Qué poner |
|---|---|
| Archivo | `rocco-forge-1.20.1-1.6.0.jar` |
| Nombre visible | Rocco 1.6.0 (Forge 1.20.1) |
| Tipo | Release (o Beta, si querés probarlo primero con algunos jugadores) |
| Versión del juego | 1.20.1 |
| Mod loader | Forge |
| Entorno | Client y Server (va en los dos) |
| Java | 17 |
| Changelog | El de `ARCHIVO-1.6.0.md` |
| Dependencias | **GeckoLib**: Required Dependency |

### Otras versiones de Minecraft

Cada versión de Minecraft tiene su carpeta y su jar. Se sube un archivo por versión, con el mismo changelog, y en cada uno se marca solo su versión del juego.

| Carpeta | Jar (en `build/libs`) | Versión del juego | Forge | GeckoLib | Java |
|---|---|---|---|---|---|
| `rocco-forge` | `rocco-forge-1.20.1-1.6.0.jar` | 1.20.1 | 47 | 4.7 o más | 17 |
| `rocco-forge-1.20.2` | `rocco-forge-1.20.2-1.6.0.jar` | 1.20.2 | 48 | 4.3.1 | 17 |

El nombre visible lleva la versión: "Rocco 1.6.0 (Forge 1.20.2)". El jar que termina en `-slim` no se sube.

## 3. Imágenes

- Capturas y GIFs para la galería: sirven los de `UsaryVaciaROCCO/rocco_completo/previews` (armas, armaduras, tumba, nacimiento, Roco Cola, cofre de ender, canto), más capturas tuyas jugando: el menú, el mate compartido, volando con elytra, el patito y el barquito.
- Una imagen ancha para el encabezado de la descripción, si querés.

## 4. Lo que revisa la moderación

- **Servicios externos:** la descripción ya explica a qué se conecta el mod y solo si el jugador pone una clave. Es lo que más piden en mods con IA.
- **Voces de personas reales:** el mod no trae ninguna. Las imitaciones usan voces públicas que la comunidad sube a Fish Audio, bajo sus términos, y la búsqueda se puede apagar (`voice.impressionVoiceSearch`). Si la moderación lo objeta, apagarla de fábrica es un cambio de una línea.
- **Contenido generado con IA:** antes de subir, revisá las reglas vigentes de CurseForge por si piden declarar assets hechos con IA (texturas, sonidos, frases grabadas).
- **Música desde links:** solo abre links que ya son audio (archivos y radios), con lavaplayer (Apache 2.0, licencias en `META-INF/THIRD-PARTY-NOTICES.txt` y `META-INF/licenses`). El jar no trae las fuentes de lavaplayer para sacar audio de sitios: YouTube, Spotify, SoundCloud, Twitch, Bandcamp y parecidos se rechazan. No ejecuta programas externos y no guarda nada en disco: el link lo pone el jugador. Si la moderación pregunta, es lo mismo que un reproductor de radio.
- **Tamaño del jar (unos 19 MB):** casi todo son los decodificadores de audio de lavaplayer para Windows, Linux y Mac.
- **Nombre "Roco Cola":** es una parodia; no usa logos ni el diseño de ninguna marca.

## 5. Antes de cada versión

1. Subir `mod_version` en el `gradle.properties` de cada carpeta (`rocco-forge`, `rocco-forge-1.20.2`, ...). Un cambio en el mod se lleva a todas las carpetas.
2. Sumar la sección nueva en `CHANGELOG.md` y armar el texto del archivo como `ARCHIVO-<versión>.md`.
3. Probar en un mundo nuevo y en uno viejo (que cargue bien con un Rocco de la versión anterior).
4. `gradlew build` y subir el jar.
