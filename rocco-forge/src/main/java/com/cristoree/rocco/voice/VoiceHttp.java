package com.cristoree.rocco.voice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// HTTP para audio: manda y recibe bytes crudos (la voz, la grabacion del microfono).
// HttpURLConnection, igual que el resto del mod: java.net.http no siempre esta bajo ModLauncher.
final class VoiceHttp
{
    record Response(int status, byte[] body)
    {
        String text()
        {
            return new String(this.body, StandardCharsets.UTF_8);
        }
    }

    static Response post(String url, Map<String, String> headers, byte[] body, int timeoutSeconds) throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(Math.min(timeoutSeconds * 1000, 10_000));
        connection.setReadTimeout(timeoutSeconds * 1000);
        connection.setDoOutput(true);
        headers.forEach(connection::setRequestProperty);

        try
        {
            try (OutputStream out = connection.getOutputStream())
            {
                out.write(body);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Response(status, readAll(stream));
        }
        finally
        {
            connection.disconnect();
        }
    }

    static Response get(String url, Map<String, String> headers, int timeoutSeconds) throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(Math.min(timeoutSeconds * 1000, 10_000));
        connection.setReadTimeout(timeoutSeconds * 1000);
        headers.forEach(connection::setRequestProperty);

        try
        {
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Response(status, readAll(stream));
        }
        finally
        {
            connection.disconnect();
        }
    }

    private static byte[] readAll(InputStream stream) throws IOException
    {
        if (stream == null)
        {
            return new byte[0];
        }
        try (stream)
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16_384];
            int read;
            while ((read = stream.read(buffer)) > 0)
            {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    // El "message" de un error JSON, o el texto recortado.
    static String errorText(Response response)
    {
        String text = response.text().replaceAll("\\s+", " ").trim();
        java.util.regex.Matcher message = java.util.regex.Pattern.compile("\"(message|err_msg|reason)\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
        if (message.find())
        {
            return message.group(2);
        }
        return text.length() > 160 ? text.substring(0, 160) + "..." : text;
    }

    private VoiceHttp() {}
}
