# Cambios

## 1.5.0

### Nuevo
- **Armaduras en Rocco:** casco, pechera, pantalones y botas de cualquier material, cada uno con sus adornos tiernos: coronita con gemas (oro), cuerno de unicornio de cristal (diamante), orejitas, colita y botitas de gato (netherite), pluma roja de caballero (hierro), moño rosa (cota de malla), margarita (cuero, que se tiñe) y escamas con una estrellita de mar (tortuga). Acompañan cada animación (la tapa, tragar, volar, el barquito), brillan si están encantadas y las gemas brillan un poco a oscuras. Protegen como al jugador y se gastan igual.
- **Armas 3D:** espada y hacha de madera, piedra, hierro, oro, diamante y netherite, más tridente, arco (con la cuerda que se tensa y la flecha encajada), ballesta y escudo, todas con su propio modelo y una hojita colgando del pomo. Las armas de otros mods se ven como el ítem de siempre.
- **Menú de equipo:** desde el menú de Rocco (agachado + clic derecho), el botón **Equipo** abre sus lugares de armadura, arma y escudo, con tu inventario abajo para arrastrar las cosas. Por chat: "ponete la armadura" (se equipa lo mejor que tenga) y "sacate la armadura". Al estrenar algo se mira, se da palmaditas y posa.
- **Pelea:** te defiende de lo que te ataca, ataca lo que vos atacás y se defiende. En el menú elegís a quién puede atacar: mobs hostiles, pacíficos o jugadores (a vos nunca). A piñas, con espada (tajo), hacha (hachazo con salto), tridente (estocada), arco y ballesta (con las flechas de su inventario, se pone a buena distancia) y se cubre con el escudo. Lo que mata suelta experiencia como si lo hubieras matado vos.
- **Vida nueva:** 10 corazones y no come: hace fotosíntesis, se cura solo a la luz (a oscuras, mucho más lento). Si se muere, hace su animación de caída, suelta lo que lleva y reaparece **enterradito** al lado de donde reaparecés vos, con una crucecita de "R.I.P." y el montículo moviéndose. Clic derecho y sale disparado, se sacude la tierra y saluda. Se puede elegir que muera para siempre o que sea inmortal.
- **El poder del cofre de ender:** si le das un cofre de ender (o lo ponés cerca para que se lo coma), lo absorbe, siente algo raro en la panza y desde ahí no pierde sus cosas al morir. Si ya lo tiene, te lo dice.
- **Roco Cola:** su gaseosa favorita. Se la toma si se la pedís ("tomate una coca": te corrige, es Roco Cola) y muy de vez en cuando por su cuenta: saca la botella, la destapa, toma tres tragos cada vez más largos y termina con un "¡aaah!" y un hipito.
- **Nacimiento nuevo:** plantado se le ve la carita dormida asomando de la tierra; al despertar bosteza, mira para los costados, hace fuerza, sale de un salto, se sacude y saluda.
- **Caras nuevas:** asombro, cantando y el "¡qué fresco!" de la Roco Cola. La IA puede usar la de asombro.

### Cambiado
- **La caja de Rocco ya no le sobra medio bloque:** ahora mide un bloque de alto (antes 1,3), así encaja con el modelo y pasa por huecos de un bloque. Enterrado, la caja es chiquita y a ras del montículo.
- **Atado no se teletransporta:** con una rienda atada a un poste no aparece solo al lado tuyo ni vuela, nada o rema con vos. Para traerlo igual, `/rocco llamar` lo desata y lo trae. Se puede apagar en **Config > Movimiento > Teletransporte**.
- **Ya no es inmortal por defecto:** con la tumba, morirse ya no es perderlo. Las configs que tenían el valor viejo se actualizan solas.

### Arreglado
- **No imitaba las voces:** pedirle que imite a alguien (Vegeta, Messi...) cambiaba el texto pero no la voz, porque la búsqueda de voces públicas de Fish Audio venía apagada y la lista de voces vacía. Ahora busca sola la voz del personaje (elige la que coincide con el nombre y el idioma, la más usada) y la recuerda. Si una búsqueda falla, la vuelve a intentar más tarde en vez de olvidarse del personaje para siempre.
- **No encontraba los cofres de la casa de al lado:** buscaba solo a 16 bloques de él, así que parado afuera de la casa decía que no había cofres. Ahora busca alrededor suyo y tuyo, y si no encuentra ninguno busca al doble de distancia (vale para guardar, traer, buscar materiales al craftear y lo que sabe la IA). Además le da tiempo de caminar hasta un cofre lejos: antes, a los 8 segundos de camino, lo daba por imposible.
- **Llamarlo de lejos estando atado lo desataba:** ahora te avisa que está atado (y si está enterrado, dónde te espera).
- **Caras pisadas en todo el modelo:** además de la tierra, el cuerpo, la tapa, el patito, el barquito y las armas tenían caras a la misma altura que parpadeaban al mover la cámara. Ahora no se pisan.

## 1.4.0

### Nuevo
- **Menú de Rocco (agachado + clic derecho):** qué está haciendo, qué tan amigos son y si tiene la magia lista. Botones para seguir o dejar de seguir, vení, sentate, a dormir, su inventario, guardar todo, ordenar cofres, cuidar la huerta, cebarte un mate, baile y callarse un rato, más sus lugares (llevame, guardar uno nuevo, olvidar), la personalidad y la configuración. Si está ocupado, un botón para cancelar. En multijugador, solo lo usa el dueño.
- **Regalos al volver:** si estuviste desconectado al menos una hora, te deja en su tapa lo que juntó mientras no estabas (si no le entra, te tira lo que sobra): más y mejor cuanto más tiempo pasaste afuera (lo máximo, a la semana). Minerales, comida, cosas de bichos (de un solo tipo por vez), botellas de experiencia y, en las ausencias largas, manzanas doradas, netherita o un libro encantado.
- **Sorpresas:** muy de vez en cuando te regala una cosita rara que encontró (un palito perfecto, una piedra con forma de corazón, un dibujo tuyo...) o se le cae su diario, con sus anécdotas y sus quejas. Tenés unos segundos para agarrarlo antes de que lo levante. Y algunas cosas que le pidas en broma tienen su regalito con nombre.
- Los regalos y las sorpresas se apagan en **Vida y rutina > Regalos**.
- **Mate compartido:** pedile "pasame un mate" (o "cebame uno", "tomemos unos mates", o el botón del menú) y te ceba uno y te lo ofrece. Clic derecho para agarrarlo, lo tomás en tres sorbos (el agua baja) y se lo devolvés con clic derecho: se toma uno él y te ofrece otro. Si no lo agarrás en 10 segundos, se lo toma solo. Si te alejás, el mate vuelve con él.
- **Tapa del modo Chill:** en modo Chill, adentro de su cabeza hay un jardincito con flores, mariposas y pétalos en vez de la carne y la baba. Se puede forzar en **General > Pantalla > Adentro de su tapa**.
- **Animaciones pulidas:** nado bajo el agua rehecho (horizontal, como el jugador), sin brazos que dan la vuelta de golpe, y los objetos (mate, termo, antiparras, barquito) crecen en su lugar en vez de volar desde otro lado.

### Cambiado
- **Alcanza los cofres como vos:** parado al lado, llega hasta el de arriba de una columna de cinco cofres (y un par para abajo). Antes tenía que estar a dos bloques, y lo que iba a un cofre alto se quedaba sin guardar.
- **Plantar gasta semillas:** una por planta, como al jugador. Si no le alcanzan las que tiene encima, las busca en los cofres de alrededor; si no hay, te lo dice. Al cosechar replanta con una de las semillas que suelta la planta, y cuidando la huerta se guarda unas para la próxima vuelta. Al talar replanta el arbolito solo si tiene uno.
- **Tu voz primero:** con una voz de Fish Audio configurada y andando, Rocco dice todo con esa voz, también sus frases de siempre. Los audios grabados del mod son para cuando no hay voz configurada, o mientras Fish Audio falla (sin crédito o sin conexión).

### Arreglado
- **Rocco no hablaba en voz alta:** desde la 1.3.0, buscar si la frase estaba grabada fallaba con casi cualquier respuesta y cortaba la voz entera (el texto salía igual). Ahora suena con tu voz de Fish Audio.
- **Nadando no te seguía:** en el agua lo movía la navegación de tierra, que avanza a menos de un bloque por segundo. Ahora nada a tu ritmo, a tu costado, y si te seguía con "Evita el agua" prendido se mete igual. Con vos quieto se queda flotando a un par de bloques: girar la cámara para mirarlo ya no lo hace nadar alrededor tuyo ni chocarte, y al salir del agua frena en vez de seguir pataleando.
- **Guardar se dejaba cosas:** miraba si en el cofre entraba una sola unidad y mandaba todo ahí; lo que no entraba volvía con él y decía que el cofre estaba lleno, aunque al lado hubiera otros vacíos. Ahora reparte contando el lugar real de cada cofre (una misma cosa puede ir a varios), y si alguno se llenó mientras iba o no llega, prueba en otro.
- **"Los cofres están llenos" sin cofres llenos:** lo decía también cuando no encontraba ningún cofre cerca. Ahora busca alrededor suyo y alrededor tuyo, y si no hay ninguno te lo dice así.
- **La tierra del modo planta parpadeaba:** las capas de tierra (y la tapa) tenían varias caras de arriba a la misma altura con texturas distintas, y la placa de video las alternaba al mover la cámara. Ahora no se pisan.
- **El mate no le llegaba a la boca:** mientras tomaba se le giraba la cabeza hacia un costado y la bombilla quedaba lejos. Ahora la cabeza la mueve solo la animación.
- **El juego no arrancaba con la 1.3.0:** el archivo de animaciones disparaba dos partículas en el mismo instante (al encantar, despegar, volar con cohete, festejar y bailar), algo que GeckoLib no acepta. Ahora salen una detrás de la otra, con medio tick de diferencia.

## 1.3.0

### Nuevo
- **Modelo nuevo:** textura de 1024, seis caras nuevas (viento, mewing, bostezo, pensando, festejo y tarareo) y 50 animaciones.
- **Vuela con vos:** si planeás con elytra (o volás en creativo), agarra sus hojitas, las convierte en alas y vuela a tu derecha, inclinándose con vos. Con cohete va a toda velocidad (con el pelo al viento y lagrimitas), en picada pliega las alas y al aterrizar se pone las hojas de nuevo.
- **En el agua:** flota con su flotador de patito y, si buceás, se pone antiparras y snorkel y bucea con vos. Al salir se sacude como un perrito. Nunca se ahoga.
- **Su propio barquito:** si vas en bote, aparece al costado en su barquito de calabaza y rema con vos.
- **Pensando:** si la IA tarda, se lleva la mano al mentón hasta que llega la respuesta.
- **Festejos:** aplaude tus logros chicos (un logro común, cada 5 niveles, domesticar un animal) y salta con confeti en los grandes (logros de desafío, matar a un jefe). Con "bailá", "aplaudí" o "festejá" lo hace cuando se lo pedís, y cuando estás tranquilo a veces baila tarareando.
- **Mucho más sin IA:** cientos de frases nuevas para charlar (cómo está, chistes, datos, consejos, el clima, los bichos, si estás triste o aburrido...), comentarios por su cuenta según lo que pasa (la noche, una cueva, poca vida, bichos cerca) y reacciones al volar, nadar, subirse al barquito o cuando te morís.
- **Ahorro de IA:** con IA configurada, las órdenes simples y claras ("seguime", "guardá todo", "crafteame un pico") las resuelve Rocco solo, sin gastar un pedido. Se apaga en **IA (cerebro) > Ahorrar IA en órdenes simples**.
- **Frases grabadas:** el mod está listo para que sus frases de siempre suenen con audios grabados, sin clave y sin gastar crédito de voz. Lo que no esté grabado sigue saliendo con Fish Audio.

### Cambiado
- **Configuración más clara:** arriba de todo, juntos, lo que hay que conectar (IA, Voz y Micrófono), cada uno con una luz que dice si ya está listo. Abajo, el resto de los ajustes.
- Los sonidos de mate, magia, ronquido y golpes se rehicieron, y se suman 11 sonidos nuevos (hojas, patito, aplausos, festejo, tarareo, pensar, suspiro del mate).
- Al dormirse y al despertarse bosteza con la boquita en "O".

### Arreglado
- **El mate y el termo** a veces no aparecían en la animación del mate: ahora se muestran según la animación que se está viendo.
- **Que nunca se pierda:**
  - si te alejás mucho mientras está sentado, dormido o con un encargo, lo deja y te alcanza;
  - si cae al vacío, aparece al lado tuyo;
  - si estás nadando lejos de la costa, cae al agua junto a vos;
  - si morís o cruzás un portal con su zona descargada, lo va a buscar igual.

## 1.2.1

### Arreglado
- **Puertas:** Rocco ahora cabe por una puerta y la abre (y la cierra) solo, en vez de quedarse trabado hasta teletransportarse.
- **Construir al lado de Rocco:** es más angosto, y si vas a poner un bloque justo donde está parado, se corre a un costado sin pararse delante tuyo. Mientras construís te deja un poco más de espacio.
- **Una sola respuesta por orden:** ya no salen dos mensajes encimados ni contradictorios. Rocco primero intenta lo que le pediste y después contesta una vez, con lo que va a hacer o con lo que le falta.
- Si le mandás otra orden mientras todavía piensa la anterior, hace caso a la última. "Seguime", "esperá" y similares se cumplen al instante.

### Cambiado
- **Craftear:** si los materiales están en los cofres de alrededor, va a buscarlos directamente. Si no están pero se consiguen (madera, piedra), va a talar o picar. Si no hay forma, te dice qué le falta.
- "Haceme un pico" (sin decir de qué) hace el mejor pico que pueda con lo que hay. Lo mismo con hachas, palas, azadas, espadas y armaduras.
- **Traer:** si le pedís algo que no está en ningún cofre, sale a conseguirlo. Si un solo cofre tiene todo lo que pediste, va a ese en vez de recorrer varios.
- **Guardar:** cada cosa va a un cofre que ya la tenga, si no a uno de su tipo (minerales, comida, madera, piedra, cultivos, cosas de mobs, redstone, herramientas...), si no a un cofre vacío que desde ahí queda para ese tipo, y recién al final a cualquiera con lugar.
- **Ordenar:** cada cofre queda de un tema, en el que ya tenía más de ese tipo (así lo que ya tenías acomodado casi no se mueve), con los stacks completos y todo agrupado adentro.
- Entiende órdenes también en "tú": sígueme, ven aquí, despiértate, hazme, constrúyeme.

## 1.2.0

### Nuevo
- **Personalidad nueva:** Rocco es un compañero de aventuras irónico y sarcástico, que se burla con cariño y lo demuestra ayudando.
  - **Memoria:** se acuerda de lo que le contás (tus gustos, tus planes, tus promesas, tus metidas de pata) y te lo recuerda más adelante. Lo de este mundo se guarda con el mundo; lo tuyo como persona (tu bebida favorita, tu apodo) vale en todos los mundos.
  - **Comentarios por su cuenta:** cuando hay silencio un rato, comenta lo que estás haciendo (o lo que no estás haciendo), lo que llevás encima, lo que hay alrededor, un dato curioso o te pregunta por tus planes. Con "callate un rato" se calla.
  - **Menos repeticiones:** lleva la cuenta de las frases que viene usando y las evita.
  - **Confianza:** de desconocidos a mejores amigos, con el tiempo juntos y las charlas. Con más confianza se anima a más.
- **Tres modos:** Chill (de fábrica), Sin filtro y Personalizado, donde se ajusta cada rasgo. Se cambian con el juego abierto, desde la config o con `/rocco personalidad`.
- **Imitaciones:** habla como personajes y famosos si se lo pedís. La voz de cada uno se puede elegir en la config.
- **Voz con emociones:** con Fish Audio (modelo s1), la voz cambia de tono según lo que dice.
- **Pantalla de bienvenida:** claves de IA, voz y micrófono, cada una con su botón para probarla, más la tecla para hablar y la personalidad. `/rocco setup` la vuelve a abrir.
- Si al entrar a un mundo Rocco no tiene IA, avisa una vez con un cartelito.

### Cambiado
- Si un proveedor bloquea una respuesta, Rocco lo dice en personaje en vez de mostrar el error. Los problemas de la IA se avisan una sola vez.
- `recordar` y `olvidar` ahora son para la memoria; los lugares siguen con `recordar_lugar` y `olvidar_lugar`.
- Quien tenía la personalidad vieja editada no la pierde: se le ofrece usarla como archivo propio.

## 1.1.0

### Nuevo
- **Configuración por temas:** el menú del juego ahora se divide en General, IA y claves, IA avanzada, Charla, Voz, Micrófono, Movimiento, Vida y rutina, Trabajo y Magia, cada uno con su ícono.
  - Buscador que encuentra cualquier opción por nombre o por lo que hace, sin importar tildes.
  - Nombres en castellano, explicación y valor por defecto al pasar el mouse, y un botón ↺ para volver cada opción a su valor original.
  - Acceso directo a las teclas (la del micrófono incluida).
- **Pesca con boya y cuerda:** al pescar se ve la boya volar desde la punta de la caña, flotar en el agua y hundirse cuando pica, con la estela del pez acercándose. Al recoger, la boya vuelve y Rocco muestra lo que sacó.

### Arreglado
- Mientras pescaba, Rocco giraba hacia el jugador en vez de mirar el agua.
- Al comerciar con Rocco, los aldeanos no subían de nivel. Ahora el trato cuenta igual que uno hecho por el jugador: el aldeano gana experiencia, sube de nivel y recuerda al jugador.

## 1.0.0: primera versión pública

### Nuevo
- **Crafteo real:** Rocco lee la receta del juego, te dice exactamente qué falta, busca los materiales en los cofres cercanos y fabrica el objeto.
- **Huerta sin semillas:** planta, cosecha lo maduro, replanta y guarda la cosecha en su cofre. También puede cuidar la huerta solo.
- **Cocina:** usa el mejor horno disponible (horno, ahumador o alto horno) con el combustible justo.
- **Encantamientos, una vez por día, en dos modos:**
  - *Al azar* ("encantame este pico"): como una mesa de encantamientos a nivel 30.
  - *A pedido* ("encantame el pico con eficiencia 5"): pone **solo** ese encantamiento. Entiende nombres en castellano, ids del juego, números romanos y errores de tipeo. Avisa si el encantamiento no sirve para ese objeto, si es incompatible con otro que ya tiene, si ya lo tiene o si el nivel pedido supera el máximo.
- **Mate:** ceba y se toma unos mates, a pedido o solo cuando estás tranquilo en tu casa.
- **Lugares:** recuerda lugares ("esta es mi casa") y te guía hasta ellos. `/rocco lugares` los lista.
- **Animales, pesca y aldeanos:** cría animales, pesca y comercia con los aldeanos.
- **Cofres:** trae objetos, guarda todo y ordena los cofres.
- **Comer bichos:** si se lo pedís, se come monstruos o animales cercanos ("comete a todos" va por los monstruos de alrededor).
- **Inventario de 36 lugares** (clic derecho sobre Rocco).
- **Comportamientos de reposo:** cuando no tiene nada que hacer, pasea cerca (radio de 8 bloques), mira alrededor y se entretiene.
- **Voz con Fish Audio:** habla en voz alta desde su boca y respeta el volumen "Voz/Habla" del juego.
- **Micrófono con Deepgram:** tecla para hablar configurable (por defecto V), manteniendo la tecla o con detección de voz.
- **Varios proveedores de IA gratuitos** (Groq, Gemini, OpenRouter, Ollama) con cambio automático si uno se queda sin cupo. `/rocco ia` muestra el estado de cada uno.
- **Sin IA también funciona:** frases propias con su personalidad y un intérprete de órdenes en castellano.
- Logo, descripción y enlaces en la lista de mods.

### Arreglado
- Rocco a veces se trababa: no obedecía, no te seguía o no se quedaba quieto. Pasaba cuando la IA respondía sin la acción a ejecutar. Ahora:
  - entiende la acción aunque la IA la escriba con otro formato;
  - si la IA la olvida, usa el intérprete de órdenes;
  - las órdenes claras ("seguime", "quedate quieto") se cumplen al instante aunque la IA esté pensando;
  - si una respuesta de la IA tarda más de 2 minutos, se descarta y Rocco vuelve a escuchar.
- "Estás sentado, levantate" ya no lo hace sentarse. "Levantate" y "seguime" lo paran y lo ponen a seguirte.
- Después de "vení", se queda cerca tuyo un rato en vez de volver enseguida a lo que hacía.
- Sonidos sincronizados con las animaciones (sorbos del mate, masticar, plantar, cosechar, encantar, tapa, ronquidos).
- Teletransporte más confiable cuando quedás lejos o cambiás de dimensión.
- Nombres con tilde ("araña") y animales nombrados en castellano se reconocen bien.
- El micrófono funciona con más modelos de micrófono (prueba varios formatos de audio).
- Ya no impide entrar a servidores que no tienen el mod.

### Limpieza
- Se quitaron opciones de configuración que no hacían nada.
- Se quitaron archivos de la plantilla de Forge y código sin uso.
