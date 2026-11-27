package com.cristoree.rocco.ai;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// HTTP minimo para hablar con las IAs. Usa HttpURLConnection a proposito: java.net.http vive
// en su propio modulo y no siempre esta disponible bajo ModLauncher.
final class AiHttp
{
    // Un tope mas corto para los pedidos de este hilo (la charla, que alguien espera). 0 = el de la config.
    static final ThreadLocal<Integer> TIMEOUT_OVERRIDE_MS = ThreadLocal.withInitial(() -> 0);

    record Response(int status, String body, int retryAfterSeconds) {}

    static Response post(String url, Map<String, String> headers, JsonObject body) throws IOException
    {
        return send("POST", url, headers, body.toString());
    }

    static Response get(String url, Map<String, String> headers) throws IOException
    {
        return send("GET", url, headers, null);
    }

    private static Response send(String method, String url, Map<String, String> headers, String body)
            throws IOException
    {
        int timeout = RoccoCommonConfig.AI_TIMEOUT_SECONDS.get() * 1000;
        int cap = TIMEOUT_OVERRIDE_MS.get();
        if (cap > 0 && cap < timeout)
        {
            timeout = cap;
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(Math.min(timeout, 10_000));
        connection.setReadTimeout(timeout);
        connection.setRequestProperty("Accept", "application/json");
        // OpenRouter la usa para mostrar de donde vienen las consultas; los demas la ignoran.
        connection.setRequestProperty("X-Title", "Rocco (Minecraft mod)");
        headers.forEach(connection::setRequestProperty);

        try
        {
            if (body != null)
            {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out = connection.getOutputStream())
                {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            int status = connection.getResponseCode();
            String payload = read(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            return new Response(status, payload, retryAfter(connection));
        }
        catch (IOException e)
        {
            // Solo si fallo, para no dejar guardada una conexion rota. Si salio bien la respuesta
            // ya se leyo entera y Java deja la conexion abierta para el proximo pedido: no se vuelve
            // a negociar TLS, que hasta los servidores de las IAs son cientos de milisegundos.
            connection.disconnect();
            throw e;
        }
    }

    private static int retryAfter(HttpURLConnection connection)
    {
        String header = connection.getHeaderField("Retry-After");
        if (header == null)
        {
            return 0;
        }
        try
        {
            return (int) Math.ceil(Double.parseDouble(header.trim()));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private static String read(InputStream stream) throws IOException
    {
        if (stream == null)
        {
            return "";
        }

        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                out.append(line).append('\n');
            }
        }
        return out.toString().trim();
    }

    // Saca el mensaje legible de un error JSON tipico ({"error":{"message":...}}).
    static String errorMessage(String payload)
    {
        return com.cristoree.rocco.config.RoccoConfig.redact(errorMessageRaw(payload));
    }

    private static String errorMessageRaw(String payload)
    {
        try
        {
            JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
            if (root.has("error"))
            {
                if (root.get("error").isJsonObject())
                {
                    JsonObject error = root.getAsJsonObject("error");
                    if (error.has("message"))
                    {
                        return error.get("message").getAsString();
                    }
                }
                else if (root.get("error").isJsonPrimitive())
                {
                    return root.get("error").getAsString();
                }
            }
        }
        catch (RuntimeException ignored)
        {
            // Si no es JSON, se devuelve el crudo recortado.
        }

        String trimmed = payload.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
    }

    private AiHttp() {}
}
