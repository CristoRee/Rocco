package com.cristoree.rocco.ai;

// Un mensaje del historial: lo dijo el jugador o lo dijo Rocco.
public record ChatTurn(boolean fromPlayer, String text) {}
