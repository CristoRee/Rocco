# Rocco: forma de planta, despertar, caras y habla

Este archivo explica cómo funcionan el modelo y las animaciones nuevas, para programarlo en el mod (GeckoLib 4, Forge 1.20.1).
Se usa junto con `rocco.geo.json` y `rocco.animation.json`.

## Huesos (bones) del modelo

| Hueso | Para qué sirve |
|---|---|
| `torso` | Cuerpo. |
| `head` | Cabeza. Contiene `jaw`, `teeth_upper`, `eyes`, `smile` y `mouth_talk`. |
| `jaw` | Mandíbula (se abre en `eat` y `swallowterror`). Contiene `teeth_lower`. |
| `teeth_upper`, `teeth_lower` | Dientes. Las animaciones tiernas los esconden; la de terror los muestra. |
| `eyes` → `blink` → `eyes_<expresion>` | Ojos, cejas, cachetes y marcas de cada expresión. `blink` sirve para parpadear. |
| `smile` → `mouth_<expresion>` | La boca de cada expresión. |
| `mouth_talk` → `talk_<expresion>` | Boca abierta para hablar, una por expresión (la de `psycho` muestra los colmillos). |
| `arm_right`, `arm_left`, `leg_right`, `leg_left` | Brazos y piernas. |
| `grab` | Hueso vacío donde se dibuja el bloque que se traga. |
| `plant` → `stem` → `leaf_1..4` (cada una con `leaf_N_tip`) y `flower` | Tierra, raíces, pastito, piedritas, tallo, hojas y florcita de la forma de planta. |

Expresiones (`<expresion>`): `happy`, `bored`, `serious`, `scared`, `sad`, `angry`, `smug`, `psycho`.

## Animaciones

| Animación | Bucle | Controlador sugerido | Qué hace |
|---|---|---|---|
| `animation.rocco.plant_idle` | sí | main | Papa dormida en la tierra: respira y mueve las hojas. Sin cara, brazos ni piernas. |
| `animation.rocco.awaken` | no (3,3 s) | main | Se sacude, abre los ojos, sonríe, saca brazos y piernas, salta fuera de la tierra; la planta se cae, se hunde y desaparece. |
| `animation.rocco.idle` | sí | main | Reposo. |
| `animation.rocco.walk` | sí | main | Caminar. |
| `animation.rocco.run` | sí | main | Correr. |
| `animation.rocco.wave` | no | main | Saludar. |
| `animation.rocco.eat` | no | main | Comer algo del piso. |
| `animation.rocco.swallowterror` | no | main | Tragar un bloque o mob (terror). |
| `animation.rocco.talk` | sí | mouth | Mover la boca al hablar. Solo toca `smile` y `mouth_talk`, así que funciona a la vez que caminar. |
| `animation.rocco.blink` | sí | blink | Parpadeo cada 3,6 s. Solo toca `blink`. |

## Reglas que tiene que cumplir el código

Las animaciones mueven las partes; el código decide qué partes existen en cada momento. En la clase del modelo (`GeoModel`), en `setCustomAnimations`, cada fotograma:

1. **Expresión:** mostrar solo `eyes_<actual>` y `mouth_<actual>`; esconder las otras siete de cada tipo.
2. **Habla:** `mouth_talk` visible solo mientras habla, y dentro de él solo `talk_<actual>`.
3. **Planta:** `plant` visible solo en los estados PLANTA y DESPERTANDO.

Ejemplo (ajustar nombres a tu código y confirmar contra la versión de GeckoLib):

```java
private static final String[] EXPRESSIONS = {"happy", "bored", "serious", "scared", "sad", "angry", "smug", "psycho"};

@Override
public void setCustomAnimations(RoccoEntity rocco, long instanceId, AnimationState<RoccoEntity> state) {
    super.setCustomAnimations(rocco, instanceId, state);
    String current = rocco.getExpression();            // por ejemplo "happy"
    for (String e : EXPRESSIONS) {
        boolean hide = !e.equals(current);
        getBone("eyes_" + e).ifPresent(b -> b.setHidden(hide));
        getBone("mouth_" + e).ifPresent(b -> b.setHidden(hide));
        getBone("talk_" + e).ifPresent(b -> b.setHidden(hide));
    }
    getBone("mouth_talk").ifPresent(b -> b.setHidden(!rocco.isTalking()));
    getBone("plant").ifPresent(b -> b.setHidden(!rocco.isPlantOrAwakening()));
}
```

Controladores en la entidad:

```java
controllers.add(new AnimationController<>(this, "main", 3, this::mainAnimation));
controllers.add(new AnimationController<>(this, "mouth", 0,
        s -> isTalking() ? s.setAndContinue(RawAnimation.begin().thenLoop("animation.rocco.talk")) : PlayState.STOP));
controllers.add(new AnimationController<>(this, "blink", 0,
        s -> isPlantOrAwakening() ? PlayState.STOP : s.setAndContinue(RawAnimation.begin().thenLoop("animation.rocco.blink"))));
```

## Flujo del juego

1. **Al crear el mundo** (o la primera vez que el jugador entra), Rocco aparece en estado PLANTA y el controlador `main` reproduce `plant_idle` en bucle. No se mueve ni sigue al jugador.
2. **Clic derecho** del jugador sobre la planta: pasa a DESPERTANDO y se reproduce `awaken` una vez (3,3 s, unos 66 ticks).
3. **Al terminar:** pasa a ACTIVO, gira el cuerpo y la cabeza hacia el jugador (por código, con la rotación de la entidad y del hueso `head`) y empieza a hablarle: `isTalking() = true` y expresión `happy`.
4. **Expresión:** guardarla en `SynchedEntityData` para que el servidor la mande a todos los jugadores. Ideas de uso:
   - `bored` si pasa un rato sin que el jugador haga nada.
   - `scared` cerca de mobs hostiles o con poca vida.
   - `sad` si el jugador lo golpea.
   - `angry` si lo golpea varias veces.
   - `smug` cuando el jugador hace algo que él sugirió.
   - `serious` para avisos importantes.
   - `psycho` solo durante `swallowterror`: ponerla al empezar esa animación y volver a la anterior al terminar.

## Notas

- En Blockbench, en reposo se ven todas las caras encimadas, el brote con la flor sobre la cabeza y la tierra enterrada debajo de los pies. Es normal: en el juego el código esconde lo que no corresponde, y las animaciones de planta suben la tierra y hunden a Rocco. Para editar cómodo, ocultá esas partes con el ojito del panel de huesos.
- En `awaken`, mientras Rocco está hundido, las piernas aparecen debajo del nivel del suelo. En Blockbench se ven colgando; en el juego quedan tapadas por el bloque de abajo.
- La textura ahora es de 512 x 512 píxeles (antes 256). GeckoLib toma el tamaño del `.geo.json`, no hay que tocar nada.
