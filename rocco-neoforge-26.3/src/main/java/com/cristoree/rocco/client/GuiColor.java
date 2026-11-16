package com.cristoree.rocco.client;

// Desde 1.21.6 el texto de la interfaz usa el color con su opacidad (0xAARRGGBB): un color sin
// opacidad (0xRRGGBB, como se escribia antes) saldria invisible. Si no la trae, va opaco.
public final class GuiColor
{
    public static int opaque(int color)
    {
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
    }

    private GuiColor() {}
}
