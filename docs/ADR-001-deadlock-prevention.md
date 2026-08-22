# ADR-001: Deadlock prevention strategy

## Context

En el juego concurrente Relic Rush, múltiples aventureros (cada uno en un hilo de plataforma) compiten por forjar reliquias. Para completar una forja, cada jugador debe adquirir de forma exclusiva dos estaciones de forja (ForgeStation).

En la implementación inicial del starter:
- Los hilos adquirían los monitores de las estaciones en el orden en que eran seleccionadas aleatoriamente por cada aventurero.
- Si el hilo A intentaba adquirir la estación 1 y luego la estación 2, mientras el hilo B intentaba adquirir la estación 2 y luego la estación 1, ambos hilos quedaban reteniendo un monitor mientras esperaban indefinidamente por el otro.
- Esta adquisición anidada e inconsistente cumplía las cuatro condiciones de Coffman (Mutual Exclusion, Hold and Wait, No Preemption y Circular Wait), produciendo un interbloqueo (deadlock) que obligaba al Watchdog del juego a abortar el proceso.

Requerimiento: Prevenir el interbloqueo garantizando la propiedad de liveness (el juego debe progresar y terminar), preservando la exclusión mutua por estación y sin serializar el juego detrás de un candado global.

## Decision

Se adoptó una estrategia de adquisición ordenada global de recursos (Ordered Resource Locking / Resource Hierarchy) basada en un orden total estricto:

1. Se utiliza el identificador único e inmutable ForgeStation.id() (generado como enteros consecutivos 1..N) para definir la jerarquía global de recursos.
2. Toda adquisición de estaciones se centraliza en LockPair.withBoth(first, second, action). Antes de realizar cualquier bloqueo, se comparan los identificadores:
    - Siempre se adquiere primero el monitor de la estación con menor ID (lower = first.id() <= second.id() ? first : second).
    - Posteriormente se adquiere el monitor de la estación con mayor ID (higher).
3. Esta normalización rompe por construcción la condición de *Espera Circular (Circular Wait), haciendo estructuralmente imposible la existencia de ciclos en el grafo de espera (*Wait-For Graph).

## Alternatives considered

1. Candado Global Único (Coarse-Grained Locking)
- Descripción: Proteger toda la operación de forja con un único monitor global (synchronized(globalLock)).
- Descarte: Aunque elimina el deadlock, reduce la concurrencia a 1 sola forja a la vez en todo el sistema. Desperdicia los núcleos del procesador y contradice los requisitos arquitectónicos del laboratorio.

2. Adquisición no bloqueante con Reintento (Eliminación de Hold and Wait)
- Descripción: Utilizar ReentrantLock con tryLock(). Si el segundo recurso no está disponible de inmediato, el hilo libera el primero, duerme un tiempo aleatorio (backoff) y vuelve a intentar.
- Descarte: Incrementa innecesariamente la complejidad del código, introduce sobrecarga de consumo de CPU por reintentos constantes y expone al sistema a riesgos de Livelock o inanición (starvation) bajo alta contención.

3. Adquisición Jerárquica por ID (Alternativa Seleccionada)
- Justificación: Mantiene los monitores intrínsecos de Java (synchronized), no requiere estructuras complejas ni consumo de CPU en reintentos, y maximiza el paralelismo en pares disjuntos.

## Quality attributes affected

- Correctness / Reliability: Garantiza que los invariantes del juego se mantengan intactos en todo momento, eliminando de raíz las condiciones de carrera y los interbloqueos.
- Performance / Throughput: Permite paralelismo real en pares de estaciones disjuntas, alcanzando hasta la mitad del total de estaciones disponibles en forjas simultáneas.
- Maintainability: La regla de sincronización queda totalmente aislada y centralizada en una única clase utilitaria (LockPair), desacoplada de la lógica de negocio de los aventureros.
- Scalability: El sistema permanece estable y libre de deadlocks incluso bajo cargas extremas (probado con 128 hilos en 100 rondas).

## Evidence

1. Diagnóstico focalizado (DeadlockProbe):
    - Ejecución de dos hilos compitiendo con secuencias deliberadamente invertidas (anvil-then-furnace vs furnace-then-anvil).
    - Resultado: NO DEADLOCK DETECTED within 2 seconds.
2. Pruebas de estrés y verificación de invariantes (InvariantProbe):
    - Escenario 8 jugadores / 6 estaciones / 50 rondas: 400 relics procesadas $\rightarrow$ invariant=OK.
    - Escenario 32 jugadores / 8 estaciones / 100 rondas: 3200 relics procesadas $\rightarrow$ invariant=OK.
    - Escenario 128 jugadores / 8 estaciones / 100 rondas: 12800 relics procesadas $\rightarrow$ invariant=OK.
    - Cero congelamientos y cero activaciones del Watchdog en todas las ejecuciones.

## Consequences

- Positivas:
    - Imposibilidad matemática de formar ciclos de espera en el sistema.
    - No hay sobrecarga de memoria ni uso innecesario de CPU.
    - La solución no requirió modificar la API pública de ForgeStation ni de Adventurer.
- Compromisos (Trade-offs):
    - Si dos aventureros necesitan una misma estación compartida, uno de ellos debe esperar a que el otro termine (serialización requerida por la exclusión mutua).

## Risks

1. Colisión o duplicación de IDs: La estrategia asume que cada ForgeStation tiene un id() estrictamente único. Si una futura modificación en GameEngine.createStations() asignara IDs duplicados, el orden total se rompería y se reintroduciría el riesgo de ciclos.
2. Evasión de LockPair: Si un desarrollador futuro intenta sincronizar sobre instancias de ForgeStation directamente en otra parte del código sin pasar por LockPair.withBoth(...), podría reintroducir un bloqueo con orden inconsistente.
