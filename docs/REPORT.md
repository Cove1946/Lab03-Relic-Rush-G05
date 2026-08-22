# ARSW Lab 3 - Relic Rush - Delivery Report

## Team

| Student | ID | GitHub        |
|---|---|---------------|
|Cristian Ronaldo Guerrero Buitrago |1000101455 | Cove1946      |
|Juan Esteban Tellez Valencia | 1000098939 | JuanTellez125 |
|Andrea Mariana Parra Urrego  |1000101817 | Mar9793       |

Repository: `https://github.com/Cove1946/Lab03-Relic-Rush-G05.git`

Final commit: `SHA`

## 1. Baseline observations

- Command(s) executed:


```text
java -cp target/classes edu.eci.arsw.relicrush.app.RelicRushMain
```

```text
java -cp target/classes edu.eci.arsw.relicrush.app.LedgerRaceProbe
```

```text
java -cp target/classes edu.eci.arsw.relicrush.app.DeadlockProbe
```


- What happened?
```text
El juego parece iniciar con normalidad, pero casi que de inmediato los hilos de los aventureros se quedan bloqueados ya que estan compitiendo por las estaciones de forja. El mecanismo que los vigila es decir (Watchdog) detecta este interbloqueo (deadlock) y termina finalizando el proceso con el mensaje de error que aparece como salida.
```

- Was the round invariant always preserved?
```text
En la corrida del juego completo, el invariante de ronda no pudo verificarse: el deadlock congela a los aventureros antes de que se complete la primera ronda, así que nunca se llega a calcular ni imprimir el resultado de esa ronda. Sin embargo, al aislar el estado compartido con la prueba de la bitácora, sí se obtiene evidencia directa de que el invariante se rompe: la cantidad de escrituras que quedaron registradas en el contador y en la lista de eventos terminó muy por debajo de lo esperado, y además esos dos valores no coincidieron entre sí. Esto confirma que el problema no es solo de coordinación (deadlock), sino también de estado compartido inseguro.
```

- Did the game stop unexpectedly?
```text
Si el juego termina deteniendose de forma inesperada debido al deadlockk detectado por Watchdog, el cual termina finalizando el proceso con el mensaje de error.

```

```text
Starting Relic Rush: adventurers=8, stations=6, rounds=25

*** DEADLOCK DETECTED BY GAME WATCHDOG ***
Run DeadlockProbe or jcmd <PID> Thread.print for a focused diagnosis.
The starter exits here so you do not have to kill a frozen process manually.
```

```text
expected=64000 totalCrafted=3593 eventCount=53660 invariant=BROKEN
```

```text
DEADLOCK DETECTED
- probe-A-anvil-then-furnace waiting on edu.eci.arsw.relicrush.model.ForgeStation@79fc0f2f owned by probe-B-furnace-then-anvil
- probe-B-furnace-then-anvil waiting on edu.eci.arsw.relicrush.model.ForgeStation@17a7cec2 owned by probe-A-anvil-then-furnace
```

## 2. Coordination analysis

Explain the responsibility of both barriers:

- `roundStart`: Su responsabilidad es sincronizar el arranque de la ronda: ningún aventurero empieza a jugar su turno 
(playTurn) hasta que todos los hilos (los N aventureros + el hilo del GameEngine) hayan llegado a ese punto.
- `roundEnd`: Su responsabilidad es sincronizar el cierre de la ronda: el GameEngine no puede tomar el snapshot 
(printRoundSnapshot) hasta que todos los aventureros hayan terminado su playTurn de esa ronda.

Why is `Thread.sleep(...)` not a valid replacement for a barrier?
Thread.sleep fija un tiempo arbitrario y fijo, no una condición real de sincronización: no sabe cuántos aventureros ya terminaron ni si alguno tardó más de lo esperado

## 3. Thread-safety problems

| Shared state | Problem                                                                                                                                                                                         | Invariant at risk | Solution | Why this solution? |
|---|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---|---|---|
| totalCrafted, el número que cuenta cuántas relics se han forjado en total.| Varios jugadores pueden sumarle 1 al mismo tiempo. Como sumar no es una sola operación instantánea (primero se lee el valor, luego se escribe el nuevo), puede pasar que dos jugadores lean el mismo número al mismo tiempo y uno de los dos incrementos "se pierda". | que el total contado sea igual a la cantidad real de relics forjadas.|usar un contador especial pensado para que varios hilos lo usen a la vez (AtomicInteger), o hacer que solo un jugador a la vez pueda sumarle (con synchronized) |así cada suma se hace completa, sin que otro jugador interfiera a la mitad, y no se pierden conteos |
| la lista donde se guarda cada evento de forja|varios jugadores intentan agregar algo a la lista al mismo tiempo, pero la lista que se está usando (ArrayList) no está preparada para eso, entonces pueden perderse elementos o incluso fallar  |que la cantidad de eventos guardados coincida con la cantidad real de relics forjadas. |usar una lista pensada para varios hilos a la vez, o proteger el add() igual que el contador (solo uno a la vez puede agregar) |evita que dos jugadores escriban en la lista al mismo tiempo y se pisen entre sí, sin necesidad de bloquear todo el juego |

## 4. Deadlock diagnosis

### 4.1 Evidence

```text
DEADLOCK DETECTED
- probe-A-anvil-then-furnace waiting on edu.eci.arsw.relicrush.model.ForgeStation@79fc0f2f owned by probe-B-furnace-then-anvil
- probe-B-furnace-then-anvil waiting on edu.eci.arsw.relicrush.model.ForgeStation@17a7cec2 owned by probe-A-anvil-then-furnace
```

### 4.2 Coffman conditions in Relic Rush

- **Mutual exclusion:** cualesquiera dos aventureros que elijan pares de estaciones con intersección no vacía. En la evidencia, probe-A-anvil-then-furnace y probe-B-furnace-then-anvil. \
LockPair.withBoth entra a la sección crítica con synchronized (first) (LockPair.java:20) y synchronized (second) (LockPair.java:23). Un monitor de Java admite exactamente un hilo propietario a la vez; cualquier otro hilo que ejecute monitorenter sobre el mismo objeto pasa a estado BLOCKED hasta que el propietario libere.

- **Hold and wait:** Los mismos dos hilos en conflicto; cada uno retiene un monitor y solicita el otro. \
**LockPair.java**

```bash
    synchronized (first) {       // adquiere y RETIENE first
    sleepQuietly(2);            
    synchronized (second) {     // Solicita second, sin haber soltado first
        action.run();           
    }
}
```

- **No preemption:** El hilo bloqueado en **LockPair.java:23**, que no tiene forma de arrebatar el monitor a su propietario. /
El watchdog (GameEngine.java:66-84) detecta el ciclo pero no puede repararlo: su única acción posible es System.exit(2) (GameEngine.java:76). Matar el proceso entero es exactamente lo que se hace cuando no hay expropiación disponible. El propio comentario del starter lo dice: "so you do not have to kill a frozen process manually".

- **Circular wait:** LockPair.withBoth adquiere los monitores en el orden en que el llamador pasó los parámetros. No hay normalización, ordenamiento ni comparación de ningún tipo antes de synchronized (first). Y ese orden lo produce el azar: \

```bash
int firstIndex  = random.nextInt(stations.size());   // Adventurer.java:68
do { secondIndex = random.nextInt(stations.size()); }
while (secondIndex == firstIndex);                    // Adventurer.java:69-72

ForgeStation first  = stations.get(firstIndex);       // Adventurer.java:74
ForgeStation second = stations.get(secondIndex);      // Adventurer.java:75

LockPair.withBoth(first, second, ...);                // Adventurer.java:77
```

DeadlockProbe no descubre este comportamiento por azar: lo construye deliberadamente, pasando los mismos dos objetos en orden invertido a cada hilo (DeadlockProbe.java:24-25), que es la razón de los nombres anvil-then-furnace y furnace-then-anvil.

### 4.3 Wait-for graph
![alt text](images/DiagramaDeadlock1.png)

![alt text](images/DiagramaDeadlock2.png)

![Diagrama Deadlock](images/DiagramaDeadlock.png)

### 4.4 Fix 

**What condition did you break?**

Se rompió **circular wait**. Las otras tres condiciones de Coffman siguen presentes de forma deliberada, y eso es suficiente: las cuatro deben cumplirse simultáneamente para que exista interbloqueo, así que eliminar una sola basta.

Por qué esa condición y no otra:**

- **Mutual exclusion** no puede eliminarse: el invariante del dominio exige que una estación no sea usada por dos forjas incompatibles a la vez (README §4). Quitarla destruiría justo lo que hay que proteger.
- **No preemption** es inherente a synchronized: la JVM no ofrece ninguna forma de revocar un monitor a su propietario.
- **Hold and wait** sería atacable, pero exigiría sustituir los monitores de ForgeStation por ReentrantLock, añadir tryLock con política de reintento y asumir riesgo de livelock: un cambio estructural en varias clases.
- **Circular wait** es la única que dependía de una decisión de diseño de este código, no del dominio ni de la JVM.

**Cambio concreto:** `LockPair.withBoth` ya no adquiere los monitores en el orden en que el llamador pasó los parámetros. Antes de tomar el primer monitor, normaliza el orden usando ForgeStation.id(), que ya existía en el proyecto (ForgeStation.java:14-16) y es único y estable durante toda la partida, porque GameEngine.createStations los asigna como 1..N (GameEngine.java:120). Eso constituye un orden total sobre los recursos.

**Por qué esto soluciona el deadlock:** con la normalización, `withBoth(X, Y)` y `withBoth(Y, X)` producen **la mi
sma secuencia de bloqueo**. Todos los hilos adquieren siempre de menor a mayor `id`. Para que existiera un ciclo `
T1 → T2 → ... → T1`, al menos un hilo tendría que estar esperando una estación de rango **inferior** a una que ya
retiene; el orden monotónico lo hace imposible por construcción. No es una reducción de la probabilidad del deadlo
ck: es una **imposibilidad estructural**.

**How did you preserve concurrency between independent forge operations?**

el fix cambia el **orden** de adquisición, no qué se bloquea ni durante cuánto tiempo. Se siguen tomando exactamente los mismos dos monitores de estación, durante el mismo intervalo. No se introdujo ningún lock nuevo, ni compartido, ni global. Comparado con el código defectuoso,**no se elimina ni una sola ejecución concurrente**; solo se eliminan las secuencias de adquisición que formaban ciclo.

**Qué operaciones pueden ejecutarse simultáneamente:**

- **Pares disjuntos: paralelismo total.** Un aventurero forjando en (S1, S2) y otro en (S3, S4) no comparten ningún monitor y jamás se bloquean entre sí.
- **Pares solapados: serialización mínima.** (S1, S2) y (S2, S5) compiten únicamente por S2. Esa espera es la exclusión mutua que el invariante exige, no un efecto colateral del arreglo — existía igual a
ntes del fix.
- **Paralelismo máximo:** ⌊S/2⌋ forjas simultáneas, idéntico a la versión con el defecto. El fix no reduce el techo de concurrencia.

**Qué recursos siguen protegidos:** cada `ForgeStation` conserva su monitor exclusivo. El invariante *"una estación no puede ser usada simultáneamente por dos forjas incompatibles"* nombrado en el README sigue garantizado, porque la exclusión mutua se mantuvo intacta.

## 5. Verification

| Players | Stations | Rounds | Deadlock? | Invariant result |
|---:|---:|---:|---|---|
| 8 | 6 | 50 | No | OK (scoreSum == totalCrafted == eventCount en todas las rondas) |
| 32 | 8 | 100 | No | OK (scoreSum == totalCrafted == eventCount en todas las rondas) |
| 128 | 8 | 100 | No | OK (scoreSum == totalCrafted == eventCount en todas las rondas) |

```java
java -cp target/classes edu.eci.arsw.relicrush.app.InvariantProbe 8 6 50
```
```text

=== RELIC RUSH - FINAL SCORE ===
adventurer-1       50 relics
adventurer-2       50 relics
adventurer-3       50 relics
adventurer-4       50 relics
adventurer-5       50 relics
adventurer-6       50 relics
adventurer-7       50 relics
adventurer-8       50 relics
Total by players : 400
Ledger total     : 400
Ledger events    : 400
```
```java
 java -cp target/classes edu.eci.arsw.relicrush.app.InvariantProbe 32 8 100
```
```text

=== RELIC RUSH - FINAL SCORE ===
adventurer-1      100 relics
adventurer-2      100 relics
adventurer-3      100 relics
adventurer-4      100 relics
adventurer-5      100 relics
adventurer-6      100 relics
adventurer-7      100 relics
adventurer-8      100 relics
adventurer-9      100 relics
adventurer-10     100 relics
adventurer-11     100 relics
adventurer-12     100 relics
adventurer-13     100 relics
adventurer-14     100 relics
adventurer-15     100 relics
adventurer-16     100 relics
adventurer-17     100 relics
adventurer-18     100 relics
adventurer-19     100 relics
adventurer-20     100 relics
adventurer-21     100 relics
adventurer-22     100 relics
adventurer-23     100 relics
adventurer-24     100 relics
adventurer-25     100 relics
adventurer-26     100 relics
adventurer-27     100 relics
adventurer-28     100 relics
adventurer-29     100 relics
adventurer-30     100 relics
adventurer-31     100 relics
adventurer-32     100 relics
Total by players : 3200
Ledger total     : 3200
Ledger events    : 3200
```
```java
java -cp target/classes edu.eci.arsw.relicrush.app.InvariantProbe 128 8 100
```
```text
=== RELIC RUSH - FINAL SCORE ===
adventurer-1      100 relics
adventurer-2      100 relics
adventurer-3      100 relics
adventurer-4      100 relics
adventurer-5      100 relics
adventurer-6      100 relics
adventurer-7      100 relics
adventurer-8      100 relics
adventurer-9      100 relics
adventurer-10     100 relics
adventurer-11     100 relics
adventurer-12     100 relics
adventurer-13     100 relics
adventurer-14     100 relics
.....
adventurer-126    100 relics
adventurer-127    100 relics
adventurer-128    100 relics
Total by players : 12800
Ledger total     : 12800
Ledger events    : 12800
```

```java
 java -cp target/classes edu.eci.arsw.relicrush.app.DeadlockProbe
```
```text
NO DEADLOCK DETECTED within 2 seconds.
```
## 6. Architectural trade-offs

### 6.1 Correctness / Reliability (Correctitud y Confiabilidad)

- **Invariantes protegidos:**
    1. *Invariante de consistencia global:* sumatoria de puntajes individuales == ForgeLedger.totalCrafted == total de eventos registrados. Cada reliquia forjada exitosamente se refleja exactamente una vez en todos los registros.
    2. *Invariante de exclusión mutua de recursos:* Ninguna estación de forja (`ForgeStation`) es utilizada de manera simultánea por dos aventureros.
    3. *Invariante de liveness (ausencia de Deadlock):* El juego nunca se congela ni entra en interbloqueo por adquisición circular de recursos.
    4. *Invariante de fases y memoria:* Ningún hilo inicia la ronda N+1 antes de que todos completen la ronda N, garantizando visibilidad coherente del estado al tomar el snapshot.

- **Evidencia empírica:**
    - Las pruebas de aislamiento (`DeadlockProbe` y `LedgerRaceProbe`) superaron los diagnósticos sin detectar ciclos ni pérdidas de conteo.
    - Las pruebas de estrés con carga masiva (`InvariantProbe` con 8, 32 y 128 hilos en 100 rondas) confirmaron `invariant=OK` en el 100% de las rondas ejecutadas, con sumas finales idénticas (12800 = 12800 = 12800).

---

### 6.2 Performance / Throughput (Rendimiento y Paralelismo)

- **¿Por qué se evitó un Lock Global?**
    - Implementar un candado global para toda la operación de forja habría convertido el juego en un sistema estrictamente secuencial (1 sola forja concurrente a la vez), desperdiciando por completo los núcleos de procesamiento de la CPU.
- **Operaciones que se ejecutan en paralelo:**
    - Gracias al bloqueo granular por estación en `LockPair`, todas las forjas sobre **pares disjuntos de estaciones** se ejecutan en paralelo al 100% (por ejemplo, un hilo usando las estaciones 1 y 2, y otro usando las estaciones 3 y 4).
    - El paralelismo máximo alcanzable en el juego equivale a la mitad de las estaciones totales disponibles (por ejemplo, con 8 estaciones se pueden realizar hasta 4 forjas simultáneas).
    - La sección crítica de la bitácora (`ForgeLedger`) tiene una duración de unos pocos nanosegundos (incremento y adición en memoria), dejando prácticamente todo el tiempo de CPU libre para el crafteo concurrente.

---

### 6.3 Contention (Contención de Candados)

- **Puntos donde ocurre contención:**
    1. *Contención en estaciones de forja:* Se produce cuando dos aventureros eligen una misma estación compartida (por ejemplo, los pares `(S1, S2)` y `(S1, S5)` compiten por `S1`). Esta contención es inevitable y correcta, pues modela la exclusión física del recurso.
    2. *Contención en la bitácora (`ForgeLedger`):* Al final de la forja, los hilos compiten por el lock privado de la bitácora para registrar su evento.
- **Mitigación y diseño:**
    - El orden estricto de adquisición en `LockPair` (`lower.id()` a `higher.id()`) asegura que la contención en estaciones compartidas resuelva la espera de forma determinista y sin riesgo de ciclos.
    - La contención en `ForgeLedger` no se convierte en cuello de botella porque el tiempo de retención del lock es mínimo y está desacoplado del tiempo que toma forjar en las estaciones.

---

### 6.4 Maintainability (Mantenibilidad)

- **Encapsulamiento del ordenamiento de bloqueos:**
    - La regla de adquisición ordenada no está dispersa en la clase `Adventurer` ni en `GameEngine`; reside de forma exclusiva en `LockPair.withBoth(...)`.
    - La regla es explícita y se basa en una propiedad intrínseca e inmutable del recurso (`ForgeStation.id()`). Si se añaden nuevas estaciones dinámicamente, el orden total sigue garantizado sin cambios de código.
- **Protección del estado interno:**
    - El uso del patrón *Private Lock Object* (`private final Object lock`) en `ForgeLedger` asegura que ninguna clase externa pueda interferir con el monitor interno de la bitácora, evitando bloqueos accidentales o acoplamientos indeseados.

---

### 6.5 Scalability (Escalabilidad)

- **Comportamiento cuando el número de jugadores crece y las estaciones permanecen constantes:**
    - El techo de concurrencia física está acotado por las estaciones disponibles (la mitad del número de estaciones). Al aumentar el número de jugadores (por ejemplo, de 8 a 128 hilos con 8 estaciones fijas), la probabilidad de colisión en las estaciones aumenta significativamente.
    - El tiempo por ronda escala proporcionalmente a la contención en las estaciones, ya que más hilos quedan en estado `BLOCKED` esperando que se liberen las estaciones.
    - Sin embargo, **la estabilidad y confiabilidad del sistema es del 100%**: no ocurren condiciones de carrera, no se desborda memoria y la ausencia de deadlocks se mantiene invariable sin importar cuántos jugadores compitan.

## 7. Mini ADR: Estrategia de prevención de Deadlock

### Context
En Relic Rush, múltiples hilos de aventureros compiten concurrentemente por adquirir dos estaciones de forja (ForgeStation) exclusivas para crear reliquias. En la versión inicial, cada hilo adquiría los monitores de las estaciones en el orden en que eran seleccionadas al azar. Esto generaba un interbloqueo (deadlock) cuando dos hilos intentaban adquirir los mismos recursos en orden inverso (por ejemplo, el Hilo 1 retenía la Estación A y solicitaba la Estación B, mientras el Hilo 2 retenía la Estación B y solicitaba la Estación A), cumpliendo las cuatro condiciones de Coffman y congelando el juego.

El requerimiento arquitectónico exigía eliminar el interbloqueo garantizando la liveness del sistema, sin recurrir a un bloqueo global que destruyera el paralelismo y preservando el invariante de exclusión mutua por estación.

### Decision
Se decidió adoptar una estrategia de *adquisición ordenada global de recursos (Ordered Locking / Resource Hierarchy)* basada en un orden total estricto:
- Se utiliza el identificador inmutable y único ForgeStation.id() (asignado como 1..N).
- En LockPair.withBoth(...), antes de ejecutar cualquier bloqueo, se normaliza el orden de adquisición: *todos los hilos adquieren siempre primero el monitor con menor id (lower) y luego el de mayor id (higher)*, sin importar el orden en que el aventurero solicitó las estaciones.
- Esta decisión rompe estructuralmente la condición de *Espera Circular (Circular Wait)* de Coffman, haciendo que los ciclos de espera sean matemáticamente imposibles en el grafo de recursos.

### Alternatives considered
1. *Candado global único (Coarse-Grained Lock):*
  - Descripción: Sincronizar toda la operación de forja bajo un único monitor central.
  - Descarte: Aunque evita el deadlock, serializa por completo el juego a 1 sola forja concurrente a la vez, destruyendo el rendimiento y desaprovechando los núcleos de la CPU.
2. *Adquisición no bloqueante con ReentrantLock y tryLock (Eliminación de Hold and Wait):*
  - Descripción: Reemplazar monitores intrínsecos por ReentrantLock. Si no se puede adquirir la segunda estación de inmediato, se libera la primera, se espera un tiempo aleatorio y se reintenta.
  - Descarte: Introduce alta complejidad, sobrecarga de CPU por reintentos activos y riesgo de Livelock o inanición (starvation) bajo alta concurrencia.
3. *Ordenamiento jerárquico por ID (Seleccionada):*
  - Justificación: Mantiene los monitores intrínsecos de Java (synchronized), es liviana, no requiere reintentos y maximiza el paralelismo en pares disjuntos.

### Consequences
- *Consecuencias positivas:*
  - *Ausencia total de deadlocks por diseño:* Ningún hilo puede quedar bloqueado en un ciclo de espera circular.
  - *Paralelismo óptimo:* Hasta la mitad de las estaciones totales disponibles pueden forjar de manera simultánea en pares disjuntos.
  - *Alta mantenibilidad:* La lógica queda 100% encapsulada en LockPair sin afectar a Adventurer ni a GameEngine.
- *Compromisos / Trade-offs:*
  - Requiere como precondición obligatoria que cada recurso posea un identificador único y comparable.
  - Existe contención natural (espera bloqueante) cuando dos hilos comparten una estación, lo cual es el comportamiento correcto exigido por el dominio.

### Evidence
- *Prueba de diagnóstico (DeadlockProbe):* Dos hilos ejecutando accesos cruzados deliberados ((A, B) y (B, A)) completaron su ejecución sin bloqueos, reportando: NO DEADLOCK DETECTED within 2 seconds.
- *Pruebas de estrés (InvariantProbe):* En ejecuciones con 8, 32 y hasta 128 hilos en 100 rondas continuas, se completaron 12,800 transacciones con 0 bloqueos y 100% de invariantes cumplidos (invariant=OK).

## 8. Conclusions

1. El juego tenía dos problemas de concurrencia distintos y separados: un deadlock por
   adquisición circular de monitores en LockPair (coordinación de recursos), y una
   condición de carrera en ForgeLedger por operaciones de lectura-escritura no atómicas
   sobre el contador y la lista de eventos (estado compartido inseguro). Arreglar uno
   no arreglaba el otro; ambos se confirmaron con evidencia empírica separada
   (DeadlockProbe y LedgerRaceProbe) antes de corregirlos.
2. El deadlock se eliminó rompiendo una sola condición de Coffman (circular wait),
   imponiendo un orden total de adquisición por ForgeStation.id() en LockPair, sin
   introducir ningún lock global ni reducir el paralelismo entre forjas en estaciones
   disjuntas. Esto se confirmó con pruebas de estrés de hasta 128 hilos y 100 rondas
   (InvariantProbe), donde el invariante se cumplió en el 100% de las rondas
   (scoreSum == totalCrafted == eventCount) y DeadlockProbe no volvió a detectar ciclos.
3. La coordinación por rondas (roundStart/roundEnd con CyclicBarrier) fue igual de
   importante que la corrección del deadlock: sin esas barreras no habría forma de
   garantizar que el snapshot de cada ronda se toma con un estado consistente entre
   todos los aventureros. Esto demuestra que en sistemas concurrentes la corrección
   depende tanto de proteger el estado compartido (thread-safety) como de sincronizar
   correctamente las fases de ejecución (coordination), y que ninguna de las dos
   por separado es suficiente.
