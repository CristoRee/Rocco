package com.cristoree.rocco.entity;

import java.util.Arrays;
import java.util.List;

// Cada expresion existe como tres huesos en el modelo: eyes_<id>, mouth_<id> y talk_<id>.
// El modelo trae las 23 encimadas y el codigo esconde las que no corresponden, asi que esta
// lista tiene que estar COMPLETA: una cara que falte aca queda visible para siempre.
public enum RoccoExpression
{
    HAPPY("happy", true),
    BORED("bored", true),
    SERIOUS("serious", true),
    SCARED("scared", true),
    SAD("sad", true),
    ANGRY("angry", true),
    SMUG("smug", true),
    PSYCHO("psycho", true),
    DIZZY("dizzy", true),

    // Automaticas: las pone el mod en su momento, la IA no las elige.
    HURT_LIGHT("hurt_light", false),
    HURT("hurt", false),
    SLEEP("sleep", false),
    // Siempre al final: el orden viaja por red y queda guardado en el mundo.
    BLISS("bliss", false),
    FOCUS("focus", false),
    // Yendo rapido con el elytra (ojos > <, lagrimitas por el viento).
    WINDY("windy", false),
    // La puede pedir la IA: cara de "mewing" para las imitaciones.
    MEWING("mewing", true),
    // El bostezo al ir a dormir y al despertarse.
    YAWN("yawn", false),
    // Mientras piensa la respuesta.
    THINK("think", false),
    // La puede pedir la IA: festejo, "¡bien ahi!".
    CHEER("cheer", true),
    // Tarareando en el baile.
    HUM("hum", false),
    // Cantando (las animaciones sing_*).
    SING("sing", false),
    // La puede pedir la IA: asombro, "¡guau!".
    WOW("wow", true),
    // El "¡aaah!" de despues de la Roco Cola.
    REFRESH("refresh", false);

    private static final RoccoExpression[] VALUES = values();
    private static final List<RoccoExpression> SELECTABLE =
            Arrays.stream(VALUES).filter(RoccoExpression::selectableByAi).toList();

    private final String id;
    private final boolean selectableByAi;

    RoccoExpression(String id, boolean selectableByAi)
    {
        this.id = id;
        this.selectableByAi = selectableByAi;
    }

    public String id()
    {
        return id;
    }

    public boolean selectableByAi()
    {
        return this.selectableByAi;
    }

    // Con los ojos cerrados no tiene sentido parpadear (pensando si parpadea).
    public boolean eyesClosed()
    {
        return switch (this)
        {
            case SLEEP, BLISS, FOCUS, WINDY, MEWING, YAWN, CHEER, HUM, SING, REFRESH -> true;
            default -> false;
        };
    }

    public static RoccoExpression byId(int id)
    {
        return id >= 0 && id < VALUES.length ? VALUES[id] : HAPPY;
    }

    // Todas, para esconder las que no tocan.
    public static RoccoExpression[] all()
    {
        return VALUES;
    }

    // Solo las que la IA puede pedir con una etiqueta.
    public static List<RoccoExpression> selectable()
    {
        return SELECTABLE;
    }
}
