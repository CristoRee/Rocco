# Rocco: forma de planta, despertar, caras, habla, tapa-cofre y trabajo

Este archivo explica cómo funcionan el modelo y las animaciones nuevas, para programarlo en el mod (GeckoLib 4, Forge 1.20.1).
Se usa junto con `rocco.geo.json` y `rocco.animation.json`.

## Huesos (bones) del modelo

| Hueso | Para qué sirve |
|---|---|
| `torso` | Cuerpo. |
| `head` | Cabeza. Contiene `jaw`, `teeth_upper`, `eyes`, `smile`, `mouth_talk`, `lid` y `goo`. Arriba tiene una cavidad carnosa que tapa la `lid`. |
| `lid` | La tapa de arriba con el pomo. Funciona como cofre: se corre hacia el costado derecho de Rocco (+X). Contiene `head_leaves`. |
| `head_leaves` → `head_leaf_1..3` (cada una con `head_leaf_N_tip`) | Las 3 hojitas que le salen del pomo. Van con la tapa y se mueven con ella. |
| `goo` → `goo_0..2` | Hilos de baba entre la cavidad y la tapa. En reposo quedan escondidos debajo de la tapa; al abrirla se estiran y se cortan. |
| `jaw` | Mandíbula (se abre en `eat` y `swallowterror`). Contiene `teeth_lower`. |
| `teeth_upper`, `teeth_lower` | Dientes cuadraditos (se ven en `eat` y en `swallowterror`). Contienen `fangs_upper` / `fangs_lower`: las puntas afiladas, que solo se ven en `swallowterror`. |
| `eyes` → `blink` → `eyes_<expresion>` | Ojos, cejas, cachetes y marcas de cada expresión. `blink` sirve para parpadear. |
| `smile` → `mouth_<expresion>` | La boca de cada expresión. |
| `mouth_talk` → `talk_<expresion>` | Boca abierta para hablar, una por expresión (la de `psycho` muestra los colmillos). |
| `arm_right`, `arm_left`, `leg_right`, `leg_left` | Brazos y piernas. |
| `item_right`, `item_left` | Huesos vacíos en la palma de cada mano (dentro de cada brazo). Ahí el juego dibuja la herramienta, la semilla o el cultivo que sostiene. |
| `grab` | Hueso vacío donde se dibuja el bloque que se traga. |
| `plant` → `stem` → `leaf_1..4` (cada una con `leaf_N_tip`) y `flower` | Tierra, raíces, pastito, piedritas, tallo, hojas y florcita de la forma de planta. |

Expresiones (`<expresion>`): `happy`, `bored`, `serious`, `scared`, `sad`, `angry`, `smug`, `psycho`, `dizzy` (mareado: ojos en espiral y boca medio abierta).

## Animaciones

| Animación | Bucle | Controlador sugerido | Qué hace |
|---|---|---|---|
| `animation.rocco.plant_idle` | sí | main | Papa dormida en la tierra: respira y mueve las hojas. Sin cara, brazos ni piernas. |
| `animation.rocco.awaken` | no (3,3 s) | main | Se sacude, abre los ojos, sonríe, saca brazos y piernas, salta fuera de la tierra; la planta se cae, se hunde y desaparece. |
| `animation.rocco.idle` | sí | main | Reposo. |
| `animation.rocco.walk` | sí | main | Caminar. |
| `animation.rocco.run` | sí | main | Correr. |
| `animation.rocco.wave` | no | main | Saludar. |
| `animation.rocco.eat` | no | main | Comer algo del piso. La boca chica se convierte en la grande, muestra los dientes y mastica. |
| `animation.rocco.swallowterror` | no | main | Tragar un bloque o mob (terror). |
| `animation.rocco.talk` | sí | mouth | Mover la boca al hablar. Solo toca `smile` y `mouth_talk`, así que funciona a la vez que caminar. |
| `animation.rocco.blink` | sí | blink | Parpadeo cada 3,6 s. Solo toca `blink`. |
| `animation.rocco.lid_open` | no (1,0 s) | main | Se abre la tapa: salta un poquito (pop), se corre hacia el costado con hilos de baba y queda apoyada en el borde de la cabeza. |
| `animation.rocco.lid_dizzy` | sí (2,0 s) | main | Tapa abierta: Rocco se tambalea en circulitos, con los brazos flojos. |
| `animation.rocco.lid_close` | no (1,3 s) | main | La tapa vuelve a su lugar (clonc), Rocco sacude la cabeza y queda normal. |
| `animation.rocco.tool_swing` | sí (0,6 s) | main | Golpe con herramienta en la mano derecha (pico, hacha, azada, espada). El golpe pega a los 0,26 s de cada vuelta. |
| `animation.rocco.interact` | no (0,9 s) | main | Estira la mano a la altura del pecho y empuja o gira algo (horno, cofre, puerta, botón, palanca). |
| `animation.rocco.plant` | no (1,8 s) | main | Se agacha mirando la tierra, pone la semilla, da dos palmaditas y se levanta contento. |
| `animation.rocco.harvest` | no (2,0 s) | main | Se agacha, agarra el cultivo con las dos manos, tira, lo arranca de un tirón y lo muestra. |

## Reglas que tiene que cumplir el código

Las animaciones mueven las partes; el código decide qué partes existen en cada momento. En la clase del modelo (`GeoModel`), en `setCustomAnimations`, cada fotograma:

1. **Expresión:** mostrar solo `eyes_<actual>` y `mouth_<actual>`; esconder las otras siete de cada tipo.
2. **Habla:** `mouth_talk` visible solo mientras habla, y dentro de él solo `talk_<actual>`.
3. **Planta:** `plant` visible solo en los estados PLANTA y DESPERTANDO.
4. **Boca grande = boca chica que se agranda:** mientras se reproduce `eat` o `swallowterror`, Rocco NO habla (`isTalking() = false`), así `mouth_talk` queda escondido. Las animaciones ya esconden la boca chica (`smile`) justo cuando se abre la mandíbula y la vuelven a mostrar cuando se cierra.

Ejemplo (ajustar nombres a tu código y confirmar contra la versión de GeckoLib):

```java
private static final String[] EXPRESSIONS = {"happy", "bored", "serious", "scared", "sad", "angry", "smug", "psycho", "dizzy"};

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

## La tapa como cofre

1. **Inventario:** Rocco tiene un inventario propio (por ejemplo, 27 espacios con `SimpleContainer` y `ChestMenu.threeRows`). Se guarda en el NBT de la entidad.
2. **Abrir:** shift + clic derecho sobre Rocco ACTIVO (el clic derecho normal queda para hablar y para despertar la planta). En el servidor:
   - guardar la expresión actual y poner `dizzy`;
   - marcar `LID_OPEN = true` (en `SynchedEntityData`) y frenar la navegación para que se quede quieto;
   - abrir el menú al jugador.
3. **Animación:** mientras `LID_OPEN` sea true, el controlador `main` reproduce
   `RawAnimation.begin().thenPlay("animation.rocco.lid_open").thenLoop("animation.rocco.lid_dizzy")`.
4. **Cerrar:** cuando el jugador cierra el menú (en `stopOpen` del contenedor), poner `LID_OPEN = false` y reproducir `animation.rocco.lid_close` una vez (1,3 s = 26 ticks). A los 23 ticks (cuando termina de sacudir la cabeza) devolver la expresión que tenía antes.
5. Si varios jugadores lo abren a la vez, contar cuántos lo tienen abierto (como los cofres) y cerrar solo cuando llegue a 0.
6. Mientras la tapa está abierta, que no hable ni coma (o que hable con la expresión `dizzy`, que también tiene su boca para hablar).

## Trabajo: herramientas, bloques y cultivos

**Lo que sostiene en la mano.** Agregar un `BlockAndItemGeoLayer` al renderer. En `getStackForBone`, devolver el ítem para `item_right` (y `item_left` si hace falta). El ítem sale de la entidad (por ejemplo, `getMainHandItem()`). Si la herramienta aparece girada, corregirlo en `renderStackForBone` con `poseStack.mulPose(Axis.XP.rotationDegrees(-90))` o similar, probando en el juego.

**Mirar el bloque.** En `setCustomAnimations`, sumar el giro hacia el bloque a lo que ya trae la animación (no reemplazarlo):
`head.setRotY(head.getRotY() + yaw)` y `head.setRotX(head.getRotX() + pitch)`.
En `plant` y `harvest` la mirada hacia abajo ya viene en la animación: ahí sumar solo el giro de costado (yaw).

**Distancia.** Que se acerque hasta quedar a unos 0,6 bloques del bloque o cultivo. Así la mano llega a la altura correcta.

**Cuándo pasa cada cosa** (para sincronizar el efecto real con la animación, contando ticks en el servidor):

| Animación | Momento | Qué hacer en el código |
|---|---|---|
| `tool_swing` | 0,26 s (tick 5) de cada vuelta de 12 ticks | Avanzar la rotura del bloque, partículas y sonido del golpe. |
| `interact` | 0,40 s (tick 8) | Abrir el horno o cofre, meter o sacar ítems, apretar el botón. |
| `plant` | 0,55 s (tick 11) | Colocar el cultivo y quitar la semilla del inventario. Mostrar la semilla en `item_right` de 0 a 0,55 s. |
| `harvest` | 1,10 s (tick 22) | Romper el cultivo y darle los drops. Mostrar el cultivo en `item_right` desde 0,55 s hasta 1,8 s. |

## Notas

- En Blockbench, en reposo se ven todas las caras encimadas, el brote con la flor sobre la cabeza y la tierra enterrada debajo de los pies. Es normal: en el juego el código esconde lo que no corresponde, y las animaciones de planta suben la tierra y hunden a Rocco. Para editar cómodo, ocultá esas partes con el ojito del panel de huesos.
- En `awaken`, mientras Rocco está hundido, las piernas aparecen debajo del nivel del suelo. En Blockbench se ven colgando; en el juego quedan tapadas por el bloque de abajo.
- La textura ahora es de 512 x 512 píxeles (antes 256). GeckoLib toma el tamaño del `.geo.json`, no hay que tocar nada.
- Las 3 hojitas de la cabeza se ven siempre en Blockbench. En el juego, `plant_idle` y `awaken` las esconden mientras es planta; cuando el brote grande se cae al despertar, las hojitas brotan con un rebote. No hace falta código para eso.
- En reposo, los hilos de baba (`goo`) quedan debajo de la tapa cerrada; no se ven. En `lid_open` se estiran y se cortan; en `lid_dizzy` y `lid_close` están escondidos.
