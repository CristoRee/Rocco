package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.ai.personality.PersonalityProfile;
import com.cristoree.rocco.ai.personality.PersonalityPromptBuilder;
import com.cristoree.rocco.config.RoccoCommonConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.regex.Pattern;

// El archivo de personalidad propio (config/rocco/<promptFile>), la opcion avanzada de
// Personalizado. La personalidad de fabrica ya no vive aca: la arma PersonalityPromptBuilder con
// los textos del jar. Este archivo solo se usa si el jugador activa "usar mi propio archivo", y
// se relee cuando cambia, asi se puede editar con el juego abierto.
public final class RoccoPersonality
{
    public static final String VERSION = "v7";
    private static final String VERSION_PREFIX = "# rocco-personalidad:";
    private static final String VERSION_LINE = VERSION_PREFIX + " " + VERSION;

    // SHA-256 de las personalidades de fabrica anteriores (texto normalizado). Un archivo identico
    // a una de estas nunca lo edito el jugador: no hay nada suyo que ofrecerle conservar.
    private static final Set<String> KNOWN_OLD_DEFAULTS = Set.of(
            "0d42bfe33487b7f6a78f7ce36a98bc2039fbba9b238ca9328423321564a693ac", // v1
            "248355986036c1794d3c5cec2b08ccfbb6074baaf8287d7ddacd79272f94caa7", // v1 con acciones
            "f8b4b12cda802cdf89220e8def1e041467e06195cf2fab6e6f31777a2d1d0d36", // v2
            "663e92c95256ac5ac59e61a84d4de1f9a48c7212dbc22772cc6d8c2f01585579", // v3
            "07c7fbdd387fefbdafd370d6d0f96eb581e6f6ffdd46bdbd4f4a7d1efe63b618", // v4
            "d0e494139d02c59378d6a2fbe765030b696ec385f3faf3896a4657ae51f12b49", // v5
            "9a7de551aef27c7731e5649a183457a76d7a52d07f7fe43d41b5f4b0647bcdbb"  // v6
    );

    // Comentario = linea que empieza con "#" seguido de espacio o nada. "#accion:" no lo es.
    private static final Pattern COMMENT_LINE = Pattern.compile("^[ \\t]*#(?:[ \\t].*)?$");

    private static final String HEADER = """
            # Personalidad propia de Rocco
            #
            # Este archivo solo se usa con el modo Personalizado y la opcion "Usar mi propio archivo de
            # personalidad" (Mods > Rocco > Config > Personalidad). Editalo y guardalo: el mod lo relee
            # sin reiniciar el juego. Las lineas que empiezan con # son comentarios y no se le mandan a la IA.
            #
            # {{actions}}, {{faces}}, {{voice_tags}} y {{right_now}} los completa el mod en cada respuesta
            # (lo que puede hacer, sus caras y lo que pasa en el juego). Si borras alguno, se agrega al final.
            """;

    private static final Object LOCK = new Object();

    private static String cached;
    private static long cachedAt;
    private static boolean warnedAboutVersion;

    // El texto del archivo para mandarle a la IA, o null si no se puede leer. Si no existe, se
    // crea con la personalidad del modo actual como punto de partida.
    @Nullable
    public static String loadOwnFile()
    {
        String raw = loadRaw();
        return raw == null ? null : stripHeader(raw);
    }

    // Saca la linea de version y el primer bloque de comentarios seguido (el que explica el
    // archivo). Termina en la primera linea en blanco despues de ese bloque.
    static String stripHeader(String text)
    {
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        int start = 0;

        while (start < lines.length && (lines[start].isBlank() || lines[start].trim().startsWith(VERSION_PREFIX)))
        {
            start++;
        }
        while (start < lines.length && COMMENT_LINE.matcher(lines[start]).matches())
        {
            start++;
        }

        StringBuilder out = new StringBuilder();
        for (int i = start; i < lines.length; i++)
        {
            if (!lines[i].trim().startsWith(VERSION_PREFIX))
            {
                out.append(lines[i]).append('\n');
            }
        }
        return out.toString().trim();
    }

    @Nullable
    private static String loadRaw()
    {
        Path file = file();

        synchronized (LOCK)
        {
            try
            {
                if (!Files.exists(file))
                {
                    write(file);
                }

                long modified = Files.getLastModifiedTime(file).toMillis();
                if (cached == null || modified != cachedAt)
                {
                    cached = Files.readString(file, StandardCharsets.UTF_8);
                    cachedAt = modified;
                    checkVersion(cached, file);
                }
                return cached;
            }
            catch (IOException e)
            {
                RoccoMod.LOGGER.error("[Rocco] No se pudo leer la personalidad en {}: {}", file, e.toString());
                return cached;
            }
        }
    }

    // Reescribe el archivo con la personalidad del modo actual. Lo usa /rocco personalidad reset.
    public static void reset() throws IOException
    {
        synchronized (LOCK)
        {
            write(file());
            cached = null;
            warnedAboutVersion = false;
        }
    }

    // El archivo existe y no es de esta version (si se usa, puede no saber las cosas nuevas).
    public static boolean isOutdated()
    {
        Path file = file();
        try
        {
            return Files.exists(file) && !Files.readString(file, StandardCharsets.UTF_8).contains(VERSION_LINE);
        }
        catch (IOException e)
        {
            return false;
        }
    }

    // Quien venia de la personalidad vieja y la habia editado: se le ofrece usar su archivo en vez
    // de pisarlo. Uno sin tocar (identico a uno de fabrica) no cuenta.
    public static boolean hasEditedOldFile()
    {
        Path file = file();
        try
        {
            if (!Files.exists(file))
            {
                return false;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return !text.contains(VERSION_LINE) && !isUntouchedOldDefault(text);
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public static Path file()
    {
        return FMLPaths.CONFIGDIR.get()
                .resolve("rocco")
                .resolve(RoccoCommonConfig.PROMPT_FILE.get());
    }

    static boolean isUntouchedOldDefault(String text)
    {
        return KNOWN_OLD_DEFAULTS.contains(sha256(text.replace("\r\n", "\n").trim()));
    }

    static String sha256(String text)
    {
        try
        {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash)
            {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            return "";
        }
    }

    // El punto de partida para editar: la personalidad del modo elegido, armada con sus rasgos.
    static String startingText()
    {
        return VERSION_LINE + "\n" + HEADER + "\n"
                + PersonalityPromptBuilder.staticPart(PersonalityProfile.current(), PersonalityProfile.mode()) + "\n";
    }

    private static void write(Path file) throws IOException
    {
        Files.createDirectories(file.getParent());
        Files.writeString(file, startingText(), StandardCharsets.UTF_8);
        RoccoMod.LOGGER.info("[Rocco] Personalidad propia escrita en {}", file);
    }

    private static void checkVersion(String text, Path file)
    {
        if (text.contains(VERSION_LINE) || warnedAboutVersion)
        {
            return;
        }

        warnedAboutVersion = true;
        RoccoMod.LOGGER.warn("[Rocco] Tu {} es de una version anterior y la editaste, asi que no la toque.", file);
        RoccoMod.LOGGER.warn("[Rocco] Para empezar de la personalidad nueva: /rocco personalidad reset (pisa tus cambios).");
    }

    private RoccoPersonality() {}
}
