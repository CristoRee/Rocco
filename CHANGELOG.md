# Cambios

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
