package com.cristoree.rocco.ai;

// Resultado de pedirle una respuesta a un proveedor. El tipo de falla decide que hace el
// router: casi todas pasan al proveedor siguiente, y algunas dejan al proveedor "en pausa".
public record ChatReply(boolean ok, String text, String error, Failure failure, String provider, String model)
{
    public enum Failure
    {
        NONE,
        // No hay clave, o el proveedor esta apagado en la config.
        NOT_CONFIGURED,
        // Clave invalida o sin permiso.
        AUTH,
        // Se acabo el cupo gratis o hay demasiadas consultas seguidas.
        RATE_LIMIT,
        // El modelo no existe o fue retirado.
        MODEL_MISSING,
        // El filtro de contenido del proveedor bloqueo la respuesta.
        BLOCKED,
        // Se quedo sin tokens antes de escribir algo.
        TRUNCATED,
        // Sin conexion, tiempo de espera agotado, o Ollama cerrado.
        NETWORK,
        // Error 5xx del proveedor.
        SERVER,
        // La peticion no le gusto al proveedor por otro motivo.
        BAD_REQUEST,
        // Llego algo que no se pudo leer.
        UNREADABLE
    }

    public static ChatReply success(String text, String provider, String model)
    {
        return new ChatReply(true, text, null, Failure.NONE, provider, model);
    }

    public static ChatReply fail(Failure failure, String error, String provider, String model)
    {
        return new ChatReply(false, null, error, failure, provider, model);
    }

    public ChatReply withSource(String provider, String model)
    {
        return new ChatReply(ok, text, error, failure, provider, model);
    }
}
