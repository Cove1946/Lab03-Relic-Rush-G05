# ARSW Lab 3 - Relic Rush - Delivery Report

## Team

| Student | ID | GitHub |
|---|---|---|
|Cristian Ronaldo Guerrero Buitrago |1000101455 |Cove1946 |
|Juan Esteban Tellez Valencia | 1000098939 |JuanTellez125 |
|Andrea Mariana Parra Urrego  |1000101817 |marianaparraurrego-oss |

Repository: `URL`

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

| Shared state | Problem | Invariant at risk | Solution | Why this solution? |
|---|---|---|---|---|
| | | | | |
| | | | | |

## 4. Deadlock diagnosis

### 4.1 Evidence

```text
PASTE DeadlockProbe OR jcmd/jstack EVIDENCE
```

### 4.2 Coffman conditions in Relic Rush

- Mutual exclusion:
- Hold and wait:
- No preemption:
- Circular wait:

### 4.3 Wait-for graph

Describe or add a diagram.

### 4.4 Fix

What condition did you break?

How did you preserve concurrency between independent forge operations?

## 5. Verification

| Players | Stations | Rounds | Deadlock? | Invariant result |
|---:|---:|---:|---|---|
| 8 | 6 | 50 | | |
| 32 | 8 | 100 | | |
| 128 | 8 | 100 | | |

## 6. Architectural trade-offs

Discuss:

- Correctness / reliability
- Performance / throughput
- Contention
- Maintainability
- Scalability

## 7. Mini ADR

### Context

### Decision

### Alternatives considered

### Consequences

### Evidence

## 8. Conclusions

1.
2.
3.
