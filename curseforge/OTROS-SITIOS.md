# Publicar Rocco en otros sitios

Los textos son los mismos que en CurseForge: `DESCRIPCION.md`, `RESUMEN.txt` y los `ARCHIVO-*.md` de esta carpeta. Para Bedrock están en `bedrock/`.

## Modrinth (mod de Java)

Es el segundo sitio de mods más usado, y el que usan launchers como Prism, Modrinth App y muchos modpacks.

En https://modrinth.com → **Create a project** → Mod.

| Campo | Qué poner |
|---|---|
| Nombre | Rocco |
| URL | `rocco` (si está tomada, `rocco-companion`) |
| Resumen | El de `RESUMEN.txt` en inglés (Modrinth muestra el resumen en las búsquedas, conviene en inglés) |
| Descripción | `DESCRIPCION.md` (acepta Markdown, emojis y tablas) |
| Categorías | Mobs, Adventure, Social (las tres principales); Food y Equipment como adicionales |
| Entorno | Client: **Required**. Server: **Required** |
| Licencia | **Custom**: pegá el link al texto de la licencia si lo publicás en algún lado (GitHub, tu página); si no, elegí "All Rights Reserved" y aclará en la descripción lo que sí se permite (ya está en la sección License) |
| Ícono | El mismo render de Rocco que en CurseForge |
| Galería | Las mismas imágenes |

Al subir cada versión (**Create a version**):

| Archivo | Número de versión | Nombre | Loaders | Versiones del juego | Changelog |
|---|---|---|---|---|---|
| `rocco-forge-1.20.1-1.6.1.jar` | `1.6.1+1.20.1-forge` | Rocco 1.6.1 (Forge 1.20.1) | Forge | 1.20.1 | `ARCHIVO-1.6.1.md` |
| `rocco-neoforge-1.21.1-1.6.1.jar` | `1.6.1+1.21.1-neoforge` | Rocco 1.6.1 (NeoForge 1.21.1) | NeoForge | 1.21.1 | `ARCHIVO-1.6.1-neoforge.md` |
| `rocco-neoforge-26.3-1.6.1.jar` | `1.6.1+26.3-neoforge` | Rocco 1.6.1 (NeoForge 26.3) | NeoForge | 26.3 | `ARCHIVO-1.6.1-neoforge.md` |

- **Release channel:** Release.
- **Dependencias:** en cada versión, **GeckoLib** como *Required* (buscá "GeckoLib" en el selector de proyectos; Modrinth elige el archivo de la versión del juego).
- En Modrinth no está la 1.6.0: la primera que subas de 1.20.1 es la 1.6.1, así que en su changelog podés borrar la línea "Changes since 1.6.0" o dejarla, da igual.
- Modrinth revisa cada proyecto nuevo a mano (unos días). Lo que suelen preguntar es lo mismo que en CurseForge: a qué se conecta el mod, y está explicado en la sección de privacidad de la descripción. El mod no trae código ofuscado ni descarga nada al iniciar, que es lo que más rechazan.

## GitHub Releases (solo si el repositorio es público)

Si hacés público el repositorio, cada versión puede tener su "Release":

1. **Releases → Draft a new release**, etiqueta `v1.6.1`.
2. Título: `Rocco 1.6.1`.
3. Texto: `ARCHIVO-1.6.1.md`, y abajo una línea con qué jar es para qué versión.
4. Adjuntá los tres jars (sin los `-slim`). Si ya publicaste Bedrock, sumá el `.mcaddon` en un release aparte (`bedrock-v1.0.0`).

Si el repositorio es privado, no hace falta: los links de los sitios apuntan a CurseForge y Modrinth.

## Planet Minecraft (opcional)

Sirve más para mostrarlo que para descargarlo. Se publica como **Mod**, con la descripción en inglés, las imágenes y los links a CurseForge y Modrinth como descarga.

## Bedrock

- **CurseForge (Minecraft Bedrock)** y **MCPEDL**: ver `bedrock/PUBLICAR.md`.
- Modrinth todavía no acepta complementos de Bedrock.

## Orden recomendado

1. CurseForge: los tres jars de Java y la descripción nueva.
2. Modrinth: el proyecto y las tres versiones.
3. CurseForge Bedrock: el `.mcaddon` (y el paquete de IA como adicional).
4. MCPEDL, con el link de CurseForge Bedrock.
5. Cuando estén publicados, poné en cada descripción los links a los otros sitios.
