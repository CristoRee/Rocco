## Rocco 1.6.0

**New**
- Truco with Rocco: "¿jugamos un truco?" opens the game menu (Uruguayan with muestra or Argentine, 15 or 30 points, flor, Rocco's level, mate, teams). Players within 12 blocks join from chat, up to 7. Rocco brings out a table and a chair for each one; 1 vs 1, three-handed, 2 vs 2, 3 vs 3 and 4 vs 4 (with 4 or 6 players he deals and serves mate). Press **R** to look around.
- Asado: he grills raw beef, pork and mutton from his inventory with coal (16 pieces per coal). Right-click the grill to take a piece.
- Tortas fritas: one per wheat, while he sips mate. Right-click the bowl to take one.
- Empty Roco Cola bottle: throw it, or sneak + right-click the ground to break it into a broken bottle (3 damage, 40 hits).
- Rocco sometimes smashes an empty Roco Cola to fight with the broken bottle.
- New food: torta frita, asado de tira, bondiola a la parrilla, costillitas de cordero.

**Changed**
- Redesigned backpacks, now in 3D in hand, on the ground and in item frames.
- Rocco's menu is reorganized into three columns: With you, Tasks and Plans (mate, truco, asado, tortas fritas and Roco Cola). Sitting, sleeping and dancing are asked for in chat.
- "Remove backpack" is now at the bottom of the backpack look panel in Gear.
- The guide shows up in every new world, the first time each player hatches a Rocco.
- Faster replies: what Rocco sends to the AI is a third shorter, with the same personality and abilities. It now fits Groq's free tier, and when the per-minute quota fills up he waits a few seconds instead of switching AI. With more than one AI set up, if the first one takes over 5 seconds he also asks the next one and uses whichever answers first.
- His face changes on its own: after talking he keeps that expression for about 30 seconds and then goes back to happy, with his cute face now and then. If you hit him he cries for a while, then gets angry, then serious, and gets over it.
- Pick the AI model from the menu: next to each "Model" there is a button with the free models that work (with a note on each one) and another that lists what your account offers today. Gemini defaults to gemma-4-26b-a4b-it and goes first.
- More natural chat: short messages get short answers, and he comments on his own a bit more often.
- Truco: the scoreboard shows each team's names instead of "Us" and "Them".
- Fish Audio voice: the default model is now the free one (s2.1-pro-free). If the chosen model has no API credit (separate from the app plan), Rocco switches to the free one and tells you instead of going silent. Voice emotions also work with s2 models.
- Rocco holds the broken bottle in his fist like the player does, and it looks bigger in your hand. Everything he holds (tools, food, what he fishes or gives you) is held like a player would, at the size of his paw. The empty bottle waits one second after drinking before it can be thrown.
- Tweaked grill, tortas fritas, truco and bottle animations, plus new sounds.

**Fixed**
- Rocco's head no longer spins when a dance is cut short.
- Rocco no longer stays on guard forever if what he was attacking vanishes mid-swing.
- Truco: standing up while Rocco was setting up the table ended the game; now you stay seated until it starts.
- Rocco's head no longer does a weird spin when the tortas fritas start and end.
- After a surprise gift (like the "fresh forest air"), Rocco goes back to his weapon or broken bottle instead of keeping the empty glass bottle.
- The underside of the grill lid, the tortas fritas in the bowl and other scene props no longer flicker.

---

## Rocco 1.6.0 (español)

**Nuevo**
- Truco con Rocco: "¿jugamos un truco?" abre el menú de la partida (uruguayo con muestra o argentino, a 15 o 30, flor, nivel de Rocco, mate, equipos). Los que están a 12 bloques o menos se suman desde el chat, hasta 7. Rocco saca una mesa y una silla para cada uno; 1 contra 1, de a tres, 2 contra 2, 3 contra 3 y 4 contra 4 (con 4 o 6 jugadores reparte y ceba). Con **R** mirás alrededor.
- Asado: asa la carne cruda de vaca, cerdo y oveja de su inventario con carbón (16 piezas por carbón). Clic derecho a la parrilla y te pasa una.
- Tortas fritas: una por cada trigo, tomando mate. Clic derecho al pote y te da una.
- Botella vacía de Roco Cola: se tira, o agachado con clic derecho al piso se rompe y queda la botella rota (3 de daño, 40 golpes).
- Rocco a veces rompe una Roco Cola vacía para pelear con la botella rota.
- Comidas nuevas: torta frita, asado de tira, bondiola a la parrilla y costillitas de cordero.

**Cambiado**
- Mochilas rediseñadas, ahora en 3D en la mano, en el piso y en los marcos.
- El menú de Rocco, en tres columnas: Con vos, Tareas y Planes (mate, truco, asado, tortas fritas y Roco Cola). Sentarse, dormir y bailar se piden por chat.
- "Quitar mochila" ahora está abajo de todo en el panel de apariencia de la mochila, en Equipo.
- La guía aparece en cada mundo nuevo, la primera vez que a cada jugador le nace un Rocco.
- Contesta más rápido: lo que se le manda a la IA es un tercio más corto, con la misma forma de ser y lo mismo que sabe hacer. Ya entra en el cupo gratis de Groq, y si el cupo del minuto se llena espera unos segundos en vez de cambiar de IA. Con más de una IA, si la primera tarda más de 5 segundos le pregunta también a la siguiente y usa la que conteste antes.
- Sus caras cambian solas: después de hablar mantiene la cara unos 30 segundos y vuelve a estar contento, con su carita tierna cada tanto. Si le pegás, llora un rato, se enoja, se pone serio y se le pasa.
- Elegir el modelo de la IA desde el menú: al lado de cada "Modelo" hay un botón con los modelos gratis que andan (con una nota de cada uno) y otro con los que ofrece hoy tu cuenta. Gemini viene con gemma-4-26b-a4b-it y va primero.
- Charla más natural: a los mensajes cortos contesta corto, y comenta por su cuenta un poco más seguido.
- Truco: el tanteador muestra los nombres de cada equipo en vez de "Nosotros" y "Ellos".
- Voz con Fish Audio: el modelo por defecto ahora es el gratis (s2.1-pro-free). Si el elegido no tiene crédito de API (que es aparte del plan de la app), pasa solo al gratis y avisa, en vez de quedarse sin voz. Las emociones de la voz andan también con los modelos s2.
- Rocco agarra la botella rota en el puño como el jugador, y en tu mano se ve más grande. Todo lo que tiene en la mano (herramientas, comida, lo que pesca o te da) lo agarra como un jugador y del tamaño de su patita. La botella vacía espera un segundo después de tomarla antes de poder tirarse.
- Animaciones de la parrilla, las tortas fritas, el truco y la botella ajustadas, y sonidos nuevos.

**Arreglado**
- La cabeza de Rocco ya no da una vuelta entera al cortar un baile.
- Rocco ya no se queda en guardia para siempre si lo que atacaba desaparece a mitad de un golpe.
- Truco: pararse de la silla mientras Rocco armaba la mesa cortaba la partida; ahora te quedás sentado hasta que arranca.
- La cabeza de Rocco ya no da una vuelta rara al empezar y al terminar las tortas fritas.
- Después de un regalo sorpresa (como el "aire puro del bosque"), Rocco vuelve a agarrar su arma o la botella rota en vez de quedarse con el frasco.
- La parte de abajo de la tapa de la parrilla, las tortas fritas en el pote y otros objetos de las escenas ya no titilan.
