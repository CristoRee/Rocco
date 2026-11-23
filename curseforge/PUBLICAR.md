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

## 2. Qué subir ahora

En CurseForge está publicada la **1.6.0 para Forge 1.20.1**. Falta subir:

| # | Archivo (en `build/libs` de cada carpeta) | Nombre visible | Versión del juego | Loader | Java | GeckoLib (dependencia obligatoria) | Changelog |
|---|---|---|---|---|---|---|---|
| 1 | `rocco-forge/build/libs/rocco-forge-1.20.1-1.6.1.jar` | Rocco 1.6.1 (Forge 1.20.1) | 1.20.1 | Forge | 17 | GeckoLib para 1.20.1 (4.7.4 o más) | `ARCHIVO-1.6.1.md` |
| 2 | `rocco-neoforge-1.21.1/build/libs/rocco-neoforge-1.21.1-1.6.1.jar` | Rocco 1.6.1 (NeoForge 1.21.1) | 1.21.1 | **NeoForge** | 21 | GeckoLib para 1.21.1 (4.9.3 o más) | `ARCHIVO-1.6.1-neoforge.md` |
| 3 | `rocco-neoforge-26.3/build/libs/rocco-neoforge-26.3-1.6.1.jar` | Rocco 1.6.1 (NeoForge 26.3) | 26.3 | **NeoForge** | 25 | GeckoLib para 26.3 (5.5.7 o más, no la 4.x) | `ARCHIVO-1.6.1-neoforge.md` |

Para los tres:

- **Tipo:** Release.
- **Entorno:** Client y Server (va en los dos).
- **El jar que termina en `-slim` no se sube.**
- En cada archivo se marca **solo su versión del juego**. En el de 1.21.1 y el de 26.3, el loader es **NeoForge**, no Forge.
- **Antes de subir, actualizá la descripción del proyecto** con el `DESCRIPCION.md` nuevo. Ahora trae la tabla de las tres versiones y el aviso de la versión Bedrock.

Si los jars no están, se arman con `gradlew build` dentro de cada carpeta. Hace falta el Java de cada una (17, 21 y 25).

**1.20.2:** quedó en la 1.6.0, sin los arreglos de la 1.6.1. Casi nadie juega 1.20.2 (los modpacks usan 1.20.1), así que conviene no subirla. Si igual la querés: `rocco-forge-1.20.2/build/libs/rocco-forge-1.20.2-1.6.0.jar`, Forge 48, GeckoLib 4.3.1, Java 17, con el changelog de `ARCHIVO-1.6.0.md`.

**Bedrock** es otro proyecto (otro juego en CurseForge): ver `bedrock/PUBLICAR.md`.

### Notas de cada versión

La de 1.21.1 es para **NeoForge**, el que usan casi todos los modpacks de esa versión. Un mundo de 1.20.1 abierto en 1.21.1 conserva a Rocco, su arma, su inventario y lo que llevan adentro las mochilas.

La de 26.3 usa GeckoLib 5. En CurseForge se marca como dependencia la versión de GeckoLib para 26.3, no la 4.x. Pesa unos 23 MB: desde 26.x el juego ya no trae la librería con la que lavaplayer abre los links, así que va adentro del jar.

Las tres se probaron en el juego real (mundo nuevo, mundo guardado, un Rocco de 1.20.1, servidor dedicado y música con el jar final).

## 3. Imágenes

- Capturas y GIFs para la galería: sirven los de `UsaryVaciaROCCO/rocco_completo/previews` (armas, armaduras, tumba, nacimiento, Roco Cola, cofre de ender, canto), más capturas tuyas jugando: el menú, el mate compartido, volando con elytra, el patito y el barquito.
- Una imagen ancha para el encabezado de la descripción, si querés.

## 4. Lo que revisa la moderación

- **Servicios externos:** la descripción ya explica a qué se conecta el mod y solo si el jugador pone una clave. Es lo que más piden en mods con IA.
- **Voces de personas reales:** el mod no trae ninguna. Las imitaciones usan voces públicas que la comunidad sube a Fish Audio, bajo sus términos, y la búsqueda se puede apagar (`voice.impressionVoiceSearch`). Si la moderación lo objeta, apagarla de fábrica es un cambio de una línea.
- **Contenido generado con IA:** antes de subir, revisá las reglas vigentes de CurseForge por si piden declarar assets hechos con IA (texturas, sonidos, frases grabadas).
- **Música desde links:** solo abre links que ya son audio (archivos y radios), con lavaplayer (Apache 2.0, licencias en `META-INF/THIRD-PARTY-NOTICES.txt` y `META-INF/licenses`). El jar no trae las fuentes de lavaplayer para sacar audio de sitios: YouTube, Spotify, SoundCloud, Twitch, Bandcamp y parecidos se rechazan. No ejecuta programas externos y no guarda nada en disco: el link lo pone el jugador. Si la moderación pregunta, es lo mismo que un reproductor de radio.
- **Tamaño del jar (unos 22 MB; el de 26.3, 23 MB):** casi todo son los decodificadores de audio de lavaplayer para Windows, Linux y Mac.
- **Nombre "Roco Cola":** es una parodia; no usa logos ni el diseño de ninguna marca.

## 5. Antes de cada versión

1. Subir `mod_version` en el `gradle.properties` de cada carpeta (`rocco-forge`, `rocco-forge-1.20.2`, `rocco-neoforge-1.21.1`, `rocco-neoforge-26.3`). Un cambio en el mod se lleva a todas las carpetas.
2. Sumar la sección nueva en `CHANGELOG.md` y armar el texto del archivo como `ARCHIVO-<versión>.md`.
3. Probar en un mundo nuevo y en uno viejo (que cargue bien con un Rocco de la versión anterior).
4. `gradlew build` y subir el jar.
