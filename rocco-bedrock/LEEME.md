# Rocco para Minecraft Bedrock

Complemento (add-on) de Rocco para Bedrock 1.26 o más nuevo: Windows, celular, consolas y servidores.
Es el mismo Rocco del mod de Java. Usa el mismo modelo, las mismas caras, las 159 animaciones, los sonidos, las partículas y las frases propias. Lo que hace está hecho de nuevo con las herramientas de Bedrock.

## Instalar

- **Lo más fácil:** abrí `Rocco-1.0.0.mcaddon` (doble clic en Windows, o "Abrir con Minecraft" en el celular). Se instalan los dos paquetes juntos.
- Creá un mundo (o editá uno), y en **Paquetes de comportamiento** activá **Rocco**. El de recursos se activa solo.
- No hace falta activar ningún experimento.

La primera vez que entrás al mundo, Rocco brota dormido como plantita al lado tuyo. Tocalo para despertarlo.

## Qué hace

- **Te sigue** a todos lados (si te alejás mucho, aparece al lado tuyo) y **te defiende**: pelea contra los monstruos que te atacan y los que ataca él. Los creepers lo ignoran, como en Java.
- **Su menú** (tocalo): hablar, seguir o quedarse, vení acá, su inventario, su equipo, mate, truco, asado, tortas fritas, Roco Cola, bailar, sentarse, si ataca a los monstruos, callarse un rato, lugares, personalidad (Chill o Sin filtro) y ayuda.
- **Charla** con sus frases propias de Java, más de 380 categorías. Entiende las mismas órdenes por texto: "seguime", "quedate", "cebame un mate", "hacé un asado", "jugamos un truco", "bailá", "dame una Roco Cola", "sentate", "a dormir"...
  - Se le habla desde el menú (**Hablar**) o con `/rocco:decir "tu mensaje"`.
- **Mate:** se toma uno solo cada tanto, y si se lo pedís **te ceba**. Te pasa el mate a la mano, lo tomás (da Prisa) y te lo vuelve a cebar.
- **Asado:** con carne cruda (vaca, cerdo u oveja) y carbón, que tenga él o que tengas vos, saca la parrilla y asa de a una pieza. Salen asado de tira, bondiola y costillitas.
- **Tortas fritas:** con trigo arma la olla y la mesa y fríe. Un trigo da una torta frita.
- **Truco:** mano a mano contra Rocco, uruguayo (con muestra y piezas) o argentino, a 15 o a 30, con Rocco tranqui, normal o pícaro. Hay truco, retruco, vale cuatro, envido, real envido, falta envido, flor, contraflor, irse al mazo y mate entre manos.
  - Rocco saca la mesa y las sillas con magia y se sienta. Las cartas jugadas quedan boca arriba sobre la mesa.
  - Juega con el mismo motor y la misma forma de pensar que en Java.
- **Equipo:** tocalo con una armadura, una espada, un hacha, un tridente, un arco, una ballesta o un escudo en la mano y se lo pone; se ve en el modelo. Las mochilas le agrandan el inventario y también se ven.
- **Darle de comer:** tocalo con comida en la mano.
- **Si lo derrotan** queda enterradito. Su dueño toca el montículo y lo desentierra.
- **Regalo al volver:** si estuviste más de una hora sin jugar, te junta cosas. Cuanto más tiempo, más cosas.
- **Rato libre:** toma mate, cada tanto una Roco Cola (3 %), saluda, charla solo cada unos minutos y se duerme cuando vos dormís.

## Comandos

- `/rocco:decir "mensaje"`: le hablás.
- `/rocco:menu`: abre su menú.
- `/rocco:ia`: muestra si la IA está conectada y su último error.
- `/rocco:brotar`: hace brotar un Rocco al lado tuyo si no tenés. Es solo para operadores.

## Lo que en Bedrock no se puede (y por qué)

Bedrock solo deja que los complementos hagan lo que su API permite. Comparado con Java:

- **Voz, micrófono y música por link:** Bedrock no puede reproducir audio que llega por internet ni grabar el micrófono. Rocco habla por escrito, con sus sonidos y gestos.
- **IA:** un complemento solo puede conectarse a internet en un **servidor dedicado (BDS)**, con las APIs beta. Para eso está el paquete opcional **Rocco IA**, explicado abajo. En un mundo normal contesta con sus frases propias, igual que Java sin clave.
- **Hablarle en el chat:** Bedrock no deja leer el chat sin las APIs beta. Con el paquete Rocco IA en un servidor sí se le habla por chat. Si no, desde el menú o con `/rocco:decir`.
- **Trabajos** (craftear, minar, talar, cultivar, ordenar cofres, encantar, pescar): Bedrock no tiene una forma de que un mob camine a donde el script quiera, así que no están. Rocco te acompaña, cocina, ceba mate, juega al truco y pelea.
- **Truco de a muchos:** en Bedrock es mano a mano contra Rocco.
- **Su tapa como cofre:** su inventario se ve y se usa desde el menú. Hasta 54 lugares con la mochila más grande.

## Rocco IA (opcional, solo servidores dedicados)

`Rocco-IA-servidor-1.0.0.mcpack` le da IA usando la misma personalidad de Java. Sirve con Gemini, Groq, OpenRouter, OpenAI o cualquier servicio compatible.

1. Instalá Rocco en el servidor y sumá el paquete **Rocco IA** a los paquetes de comportamiento del mundo.
2. En el mundo, activá el experimento **APIs beta** (Beta APIs).
3. En `config/default/permissions.json` del servidor, sumá `@minecraft/server-net` y `@minecraft/server-admin` a `allowed_modules`.
4. Creá `config/default/variables.json`:
   ```json
   { "proveedor": "gemini", "modelo": "gemma-4-26b-a4b-it" }
   ```
   `proveedor` puede ser `gemini`, `groq`, `openrouter`, `openai` o `propio`. Con `propio` se agrega `"url": "https://..."`. Con `"modelo": "auto"`, Groq y los compatibles eligen solos.
5. Creá `config/default/secrets.json` con tu clave. **No la pongas en el paquete ni en el mundo.**
   - Gemini: `{ "clave": "AIza..." }`
   - Groq, OpenRouter, OpenAI y compatibles (con "Bearer " adelante): `{ "clave": "Bearer gsk_..." }`
6. Reiniciá el servidor. `/rocco:ia` tiene que decir "IA conectada".

Las APIs beta cambian con cada versión de Minecraft. Si el servidor se actualiza y el paquete IA deja de cargar, hay que volver a armarlo para esa versión: en `src/Rocco_IA_BP/manifest.json` va la versión beta nueva. El paquete principal no depende de eso y sigue andando.

## Para armarlo (desarrolladores)

Los recursos salen del mod de Java (`rocco-neoforge-1.21.1`): modelo, animaciones, texturas, sonidos, partículas, frases y personalidad. Así hay una sola fuente. Necesita Python 3 con Pillow, y Node para las pruebas.

```
python rocco-bedrock/tools/build.py            # arma build/ y los .mcaddon/.mcpack
python rocco-bedrock/tools/build.py --test     # además corre la simulación (test/sim.mjs)
python rocco-bedrock/tools/build.py --install  # además lo copia a las carpetas de desarrollo de Bedrock
node rocco-bedrock/test/truco_sim.mjs          # 240 partidas de truco para probar el motor
```

- `tools/build.py` convierte el modelo y las animaciones al formato de Bedrock. También arma las partículas desde los valores de Java y genera el controlador de animaciones, los controladores de dibujo (caras, accesorios, armadura, armas), las entidades, los items, las recetas y los textos en tres idiomas.
- `tools/verify.py` revisa que cada sonido, animación, partícula, frase y texto que nombran los scripts exista de verdad.
- `test/` tiene una imitación de la API del juego para correr los scripts reales de punta a punta sin abrir Minecraft.
