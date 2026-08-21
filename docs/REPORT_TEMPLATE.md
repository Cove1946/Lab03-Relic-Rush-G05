# ARSW Lab 3 - Relic Rush - Delivery Report

## Team

| Student | ID | GitHub |
|---|---|---|
| | | |
| | | |
| | | |

Repository: `URL`

Final commit: `SHA`

## 1. Baseline observations

- Command(s) executed:
- What happened?
- Was the round invariant always preserved?
- Did the game stop unexpectedly?

Evidence:

```text
PASTE RELEVANT OUTPUT
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
