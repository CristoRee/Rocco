package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Decide que modelo usar con cada proveedor. Con "auto" le pregunta al proveedor que modelos
// tiene HOY y elige el mejor: los catalogos gratis cambian cada pocos meses y un nombre fijo
// termina dando 404. El valor que el jugador escribio en la config nunca se reescribe.
public final class ModelResolver
{
    public static final String AUTO = "auto";

    // Elegido por "auto" en esta sesion.
    private static final Map<AiProvider, String> RESOLVED = new ConcurrentHashMap<>();
    // Reemplazo de un modelo configurado que dio 404 (clave: proveedor|modelo configurado).
    private static final Map<String, String> REPLACEMENTS = new ConcurrentHashMap<>();

    // Para las pruebas: permite simular el catalogo de un proveedor sin red.
    interface Catalog
    {
        List<String> list(AiProvider provider) throws IOException;
    }

    static Catalog catalog = ModelResolver::fetchCatalog;

    public static String modelFor(AiProvider provider) throws IOException
    {
        // El que contesto cuando el elegido estaba saturado: se sigue con ese un rato.
        Temporary temporary = TEMPORARY.get(provider);
        if (temporary != null)
        {
            if (System.currentTimeMillis() < temporary.until())
            {
                return temporary.model();
            }
            TEMPORARY.remove(provider);
        }

        String configured = provider.configuredModel();

        if (!configured.isEmpty() && !configured.equalsIgnoreCase(AUTO))
        {
            return REPLACEMENTS.getOrDefault(replacementKey(provider, configured), configured);
        }

        String cached = RESOLVED.get(provider);
        if (cached != null)
        {
            return cached;
        }

        String chosen = choose(provider, catalog.list(provider), null);
        if (chosen == null)
        {
            throw new IOException("no encontre ningun modelo para charlar en " + provider.label());
        }

        RESOLVED.put(provider, chosen);
        RoccoMod.LOGGER.info("[Rocco] {}: modelo automatico -> {}", provider.label(), chosen);
        return chosen;
    }

    // El modelo dio 404: se busca otro en el catalogo actual. Devuelve null si no hay alternativa.
    public static String replaceMissing(AiProvider provider, String missing)
    {
        try
        {
            String chosen = provider == AiProvider.GEMINI
                    ? GeminiClient.fastAlternative(cachedCatalog(provider), missing)
                    : choose(provider, catalog.list(provider), missing);
            if (chosen == null || chosen.equalsIgnoreCase(missing))
            {
                return null;
            }

            String configured = provider.configuredModel();
            if (configured.isEmpty() || configured.equalsIgnoreCase(AUTO))
            {
                RESOLVED.put(provider, chosen);
            }
            else
            {
                REPLACEMENTS.put(replacementKey(provider, configured), chosen);
            }

            RoccoMod.LOGGER.warn("[Rocco] {}: '{}' ya no esta disponible, uso '{}' en su lugar. "
                    + "Para que no vuelva a pasar, pone 'auto' como modelo.", provider.label(), missing, chosen);
            return chosen;
        }
        catch (IOException | RuntimeException e)
        {
            RoccoMod.LOGGER.warn("[Rocco] {}: no pude buscar un modelo de reemplazo: {}", provider.label(), e.toString());
            return null;
        }
    }

    // Otro modelo del mismo proveedor para charlar ya, cuando el que se uso esta saturado o no
    // contesto (la eleccion de la sesion no cambia). null si no hay otro o es una IA de tu PC.
    public static String alternative(AiProvider provider, String current)
    {
        if (provider == AiProvider.OLLAMA || provider == AiProvider.CUSTOM)
        {
            return null;
        }
        try
        {
            List<String> available = cachedCatalog(provider);
            String chosen = provider == AiProvider.GEMINI
                    ? GeminiClient.fastAlternative(available, current)
                    : choose(provider, available, current);
            return chosen == null || chosen.equalsIgnoreCase(current) ? null : chosen;
        }
        catch (IOException | RuntimeException e)
        {
            RoccoMod.LOGGER.debug("[Rocco] {}: no pude buscar otro modelo: {}", provider.label(), e.toString());
            return null;
        }
    }

    // Por 10 minutos, este modelo en lugar del elegido (que estaba saturado o tardaba). Despues se
    // vuelve a probar el elegido.
    private record Temporary(String model, long until) {}
    private static final Map<AiProvider, Temporary> TEMPORARY = new ConcurrentHashMap<>();

    public static void useForAWhile(AiProvider provider, String model)
    {
        TEMPORARY.put(provider, new Temporary(model, System.currentTimeMillis() + 10 * 60_000L));
    }

    // El catalogo, guardado 10 minutos: buscar otro modelo no tiene que sumar una consulta cada vez.
    private record Listed(List<String> models, long at) {}
    private static final Map<AiProvider, Listed> CATALOGS = new ConcurrentHashMap<>();

    private static List<String> cachedCatalog(AiProvider provider) throws IOException
    {
        Listed listed = CATALOGS.get(provider);
        if (listed != null && System.currentTimeMillis() - listed.at() < 10 * 60_000L)
        {
            return listed.models();
        }
        List<String> models = catalog.list(provider);
        CATALOGS.put(provider, new Listed(models, System.currentTimeMillis()));
        return models;
    }

    // Los modelos para charlar que ofrece hoy la cuenta del jugador (para elegir desde el menu).
    // En Gemini tambien entran los gemma; en OpenRouter solo los gratis.
    public static List<String> chatModels(AiProvider provider) throws IOException
    {
        List<String> all = catalog.list(provider);
        java.util.stream.Stream<String> models = all.stream().map(String::trim).filter(id -> !id.isEmpty());
        if (provider == AiProvider.GEMINI)
        {
            models = models.filter(id -> GeminiClient.usableForChat(id));
        }
        else
        {
            models = models.filter(OpenAiCompatClient::usableForChat);
            if (provider == AiProvider.OPENROUTER)
            {
                models = models.filter(id -> id.endsWith(":free") || id.equals("openrouter/free"));
            }
        }
        return models.distinct().sorted().toList();
    }

    // Lo ultimo que se uso, para mostrar en /rocco ia sin hacer consultas.
    public static String lastKnown(AiProvider provider)
    {
        String configured = provider.configuredModel();
        Temporary temporary = TEMPORARY.get(provider);
        if (temporary != null && System.currentTimeMillis() < temporary.until())
        {
            return temporary.model() + " (el elegido estaba saturado)";
        }
        if (!configured.isEmpty() && !configured.equalsIgnoreCase(AUTO))
        {
            return REPLACEMENTS.getOrDefault(replacementKey(provider, configured), configured);
        }
        return RESOLVED.getOrDefault(provider, AUTO);
    }

    static void forgetAll()
    {
        RESOLVED.clear();
        REPLACEMENTS.clear();
        TEMPORARY.clear();
    }

    private static String choose(AiProvider provider, List<String> available, String exclude)
    {
        List<String> candidates = exclude == null
                ? available
                : available.stream().filter(id -> !id.equalsIgnoreCase(exclude)).toList();

        return provider == AiProvider.GEMINI
                ? GeminiClient.chooseModel(candidates)
                : OpenAiCompatClient.chooseModel(provider, candidates);
    }

    private static List<String> fetchCatalog(AiProvider provider) throws IOException
    {
        return provider == AiProvider.GEMINI
                ? GeminiClient.listModels(provider.apiKey())
                : OpenAiCompatClient.listModels(provider);
    }

    private static String replacementKey(AiProvider provider, String configured)
    {
        return provider.id() + "|" + configured.toLowerCase(Locale.ROOT);
    }

    private ModelResolver() {}
}
