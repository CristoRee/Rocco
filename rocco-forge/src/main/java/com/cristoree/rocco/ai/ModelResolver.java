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
            String chosen = choose(provider, catalog.list(provider), missing);
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

    // Lo ultimo que se uso, para mostrar en /rocco ia sin hacer consultas.
    public static String lastKnown(AiProvider provider)
    {
        String configured = provider.configuredModel();
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
