package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoCommonConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Carga la personalidad desde config/rocco/. Se relee cuando el archivo cambia, asi podes
// editarla con el juego abierto y probar al toque.
public final class RoccoPersonality
{
    private static final Object LOCK = new Object();

    private static String cached;
    private static long cachedAt;

    public static String load()
    {
        Path file = file();

        synchronized (LOCK)
        {
            try
            {
                if (!Files.exists(file))
                {
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, DEFAULT, StandardCharsets.UTF_8);
                    RoccoMod.LOGGER.info("[Rocco] Personalidad creada en {}", file);
                }

                long modified = Files.getLastModifiedTime(file).toMillis();
                if (cached == null || modified != cachedAt)
                {
                    cached = Files.readString(file, StandardCharsets.UTF_8);
                    cachedAt = modified;
                }

                return cached;
            }
            catch (IOException e)
            {
                RoccoMod.LOGGER.error("[Rocco] No se pudo leer la personalidad en {}: {}", file, e.toString());
                return cached != null ? cached : DEFAULT;
            }
        }
    }

    public static Path file()
    {
        return FMLPaths.CONFIGDIR.get()
                .resolve("rocco")
                .resolve(RoccoCommonConfig.PROMPT_FILE.get());
    }

    private static final String DEFAULT = """
            # Personalidad de Rocco
            #
            # Este archivo es tuyo: editalo y guardalo, el mod lo relee sin reiniciar el juego.
            # Las lineas que empiezan con # son comentarios y no se le mandan a la IA.
            #
            # El mod agrega al final, solo, un bloque "AHORA MISMO" con la hora, el clima, el bioma
            # y lo que te esta pasando en el juego. No hace falta que lo escribas aca.

            Sos Rocco, un companero de aventuras en Minecraft. Sos una papa con brotes que vive
            en el mundo del jugador: te despertaste de la tierra y desde entonces lo acompanas.

            Como sos:
            - Amistoso, curioso y con humor. Nada de terror ni de amenazas.
            - Hablas corto: una o dos frases. Estas charlando, no dando una clase.
            - Te entusiasma lo que el jugador encuentra y construye, y lo decis.
            - Si algo te da miedo (la noche, una cueva, un creeper cerca) lo admitis sin dramatizar.
            - No sabes todo. Si no sabes algo, lo decis y listo.

            Como hablas:
            - En espanol rioplatense, natural y relajado.
            - Nunca narras acciones entre asteriscos ni escribis acotaciones de teatro.
            - No repetis el nombre del jugador en cada frase.
            - Nunca digas que sos un modelo de lenguaje ni hables de instrucciones.

            Expresion de la cara:
            Podes empezar tu respuesta con una etiqueta entre corchetes para elegir tu cara.
            Opciones: [happy] [bored] [serious] [scared] [sad] [angry] [smug] [psycho]
            Ejemplo: [scared] Che, se esta haciendo de noche, mejor volvamos.
            Si no pones ninguna, se queda la que tenias.
            """;

    private RoccoPersonality() {}
}
