# Arquitectura de Capas — Guardian Hierarchy

Mapa completo del árbol de actores en `app/infrastructure/guardian/`.

---

## Árbol de guardians

```
ActorSystem("reactive-manifesto")          ← único ActorSystem del proceso
└── RootGuardian  [Nothing]                ← solo hace setup, luego Behaviors.empty
    │
    ├── DomainGuardian                     ← capa 1: lógica de negocio
    │   ├── contact-engine      [ContactCommand]
    │   ├── message-engine      [MessageCommand]
    │   ├── publication-engine  [PublicationCommand]
    │   └── gamification-engine [GamificationCommand]
    │
    ├── CrossCutGuardian                   ← capa 2: cross-cutting concerns
    │   ├── notification-engine [NotificationCommand]
    │   ├── moderation-engine   [ModerationCommand]  ← tiene circuit-breaker
    │   └── analytics-engine    [AnalyticsCommand]
    │
    └── InfraGuardian                      ← capa 3: infraestructura
        └── eventbus-engine     [EventBusCommand]    ← DistributedPubSub
```

---

## Reglas de ubicación

| Tipo de dominio | Guardian | Criterio |
|-----------------|----------|---------|
| Negocio (CRUD, lógica de dominio) | `DomainGuardian` | tiene repository, modela una entidad |
| Transversal (aplica a todos los dominios) | `CrossCutGuardian` | no tiene entidad propia, reacciona a eventos |
| Infraestructura (mensajería, cluster) | `InfraGuardian` | no tiene lógica de negocio, gestiona recursos |

### Ejemplos de clasificación

| Dominio | Guardian correcto | Razón |
|---------|------------------|-------|
| `reviews` | Domain | tiene entidad `Review`, CRUD |
| `subscriptions` | Domain | tiene entidad `Subscription`, ciclo de vida |
| `audit-log` | CrossCut | no tiene entidad propia, registra eventos de otros |
| `rate-limiter` | CrossCut | aplica a todas las peticiones, no tiene modelo |
| `search-index` | Infra | gestiona un recurso externo (Elasticsearch) |

---

## Flujo de mensajes

```
HTTP Request
    │
    ▼
Play Controller (Action.async)
    │  inject
    ▼
Reactive*Adapter              ← ask/tell con timeout
    │  Forward* message
    ▼
*Guardian (Domain/CrossCut)   ← enruta al hijo correcto
    │
    ▼
*Engine (Akka Typed Actor)    ← procesa el mensaje, accede al repo
    │  pipeToSelf
    ▼
Repository (Slick Future)     ← acceso no bloqueante a DB
```

---

## Ciclo de vida y supervisión

Cada guardian aplica `SupervisorStrategy.restartWithBackoff` a sus hijos:

```
min  = 200ms
max  = 5s
jitter = 0.2    ← evita thundering herd en restart simultáneo
```

El guardian detecta terminación de hijos vía `ctx.watchWith(ref, ChildTerminated(name))`
y actualiza el estado de salud interno (`Healthy | Degraded | Dead`).

Si un hijo está `Dead`, el guardian responde inmediatamente al `replyTo`
con un error (fast-fail), evitando que el `ask` timeout del adapter agote innecesariamente.

---

## Interfaces de salud

Cada guardian expone un comando `GetHealth` que retorna:

```scala
case class GuardianHealth(
  status:   HealthStatus,  // Healthy | Degraded | Dead
  children: Map[String, ChildHealth],
  uptime:   Duration
)
```

El `InfraGuardian` agrega la salud de las 3 capas y la expone vía
`GET /health` en el controller de administración.

---

## Agregar un nuevo dominio — checklist de guardians

1. [ ] Elegir el guardian correcto (Domain / CrossCut / Infra)
2. [ ] Agregar `final case class Forward<Nombre>(cmd: <Nombre>Command)` al sealed trait
3. [ ] Agregar `<nombre>: ActorRef[<Nombre>Command]` al case class `*Refs`
4. [ ] Hacer `spawn` + `watchWith` en el `Behaviors.setup` del guardian
5. [ ] Agregar caso en el pattern match: `case Forward<Nombre>(cmd) => children.<nombre> ! cmd`
6. [ ] Actualizar `GetHealth` / `Get*Refs` para incluir el nuevo hijo
7. [ ] En `Module.scala`, construir el adapter con la ref del guardian correspondiente
