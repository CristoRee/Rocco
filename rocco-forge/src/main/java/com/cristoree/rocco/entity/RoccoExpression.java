package com.cristoree.rocco.entity;

// Cada expresion existe como tres huesos en el modelo: eyes_<id>, mouth_<id> y talk_<id>.
public enum RoccoExpression
{
    HAPPY("happy"),
    BORED("bored"),
    SERIOUS("serious"),
    SCARED("scared"),
    SAD("sad"),
    ANGRY("angry"),
    SMUG("smug"),
    PSYCHO("psycho");

    private static final RoccoExpression[] VALUES = values();

    private final String id;

    RoccoExpression(String id)
    {
        this.id = id;
    }

    public String id()
    {
        return id;
    }

    public static RoccoExpression byId(int id)
    {
        return id >= 0 && id < VALUES.length ? VALUES[id] : HAPPY;
    }

    public static RoccoExpression[] all()
    {
        return VALUES;
    }
}
