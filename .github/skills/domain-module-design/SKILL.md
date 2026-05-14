---
name: domain-module-design
description: 'Diseño y scaffolding de nuevos módulos de dominio en este proyecto siguiendo la arquitectura de 3 capas (Domain → CrossCut → Infra). Usar esta skill en los siguientes casos específicos: crear un dominio nuevo desde cero, agregar un engine (Actor) a un dominio existente, agregar un repository Slick a un dominio, registrar un dominio en el guardian correspondiente, enlazar un nuevo adapter en Module.scala, revisar si la estructura de un dominio sigue las convenciones del proyecto.'
argument-hint: 'Nombre del dominio a crear o extender (ej: "reviews", "subscriptions")'
---

# Domain Module Design — Skill

Guía canónica para crear y extender módulos de dominio en este proyecto
(**Play 3 + Akka Typed 2.8 + Slick 6 + Guice**), siguiendo la arquitectura
de 3 capas de guardians definida en `app/infrastructure/guardian/`.

---

## Arquitectura de capas

```
ActorSystem "reactive-manifesto"
└── RootGuardian                         ← singleton, no recibe mensajes post-boot
    ├── DomainGuardian                   ← lógica de negocio (Contact, Message, Publication, Gamification)
    ├── CrossCutGuardian                 ← transversales (Notification, Moderation, Analytics)
    └── InfraGuardian                    ← infraestructura (EventBus, Health)
```

Referencia completa de capas: [./references/domain-layers.md](./references/domain-layers.md)

---

## Cuándo usar esta skill

| Situación | Prompt de ejemplo |
|-----------|-------------------|
| Dominio nuevo de negocio | `"Crea el dominio reviews con modelo, repo y engine"` |
| Nuevo engine en dominio existente | `"Agrega un RecommendationEngine al dominio publications"` |
| Nuevo repository Slick | `"Crea el repository para ReviewRepository en el dominio reviews"` |
| Registrar dominio en guardian | `"Registra ReviewEngine en el DomainGuardian"` |
| Binding Guice | `"Enlaza ReactiveReviewAdapter en Module.scala"` |
| Auditar estructura de un dominio | `"Revisa si el dominio messaging sigue las convenciones del proyecto"` |

---

## Procedimiento — Dominio nuevo completo

### 1. Crear la estructura de carpetas

```
app/domains/<dominio>/
├── models/          ← case classes + ADTs inmutables
├── repositories/    ← acceso a DB con Slick (no bloquea)
├── engines/         ← Akka Typed Actors (lógica de negocio)
├── services/        ← Reactive Adapters para Play controllers
└── policies/        ← (opcional) reglas de negocio puras sin efectos
```

Usar la plantilla: [./assets/domain-folder-init.sh](./assets/domain-folder-init.sh)

---

### 2. Definir los modelos

Ubicación: `app/domains/<dominio>/models/<Nombre>.scala`

```scala
package domains.<dominio>.models

import java.time.Instant

// Entidad principal — siempre case class inmutable
case class <Nombre>(
  id:        Long,
  createdAt: Instant,
  updatedAt: Instant
)

// DTO para creación (sin id ni timestamps)
case class Create<Nombre>Request(
  /* campos obligatorios */
)

// DTO de respuesta pública (puede omitir campos internos)
case class <Nombre>View(
  id:   Long,
  /* campos públicos */
)
```

Plantilla completa: [./assets/models-template.scala](./assets/models-template.scala)

---

### 3. Crear el Repository (Slick)

Ubicación: `app/domains/<dominio>/repositories/<Nombre>Repository.scala`

- Extender `play.api.db.slick.DatabaseConfigProvider`
- Todos los métodos retornan `Future[_]` — nunca bloquear con `Await`
- Usar `dbConfig.db.run(...)` para ejecutar queries
- Las evoluciones SQL van en `conf/evolutions/default/<N>.sql`

Plantilla: [./assets/repository-template.scala](./assets/repository-template.scala)

---

### 4. Crear el Engine (Actor de dominio)

Ubicación: `app/domains/<dominio>/engines/<Nombre>Engine.scala`

- Seguir el patrón `active(state)` del skill **akka-reactive-play**
- El engine NO debe importar clases de Play ni de HTTP
- El protocolo (commands/responses) se define en el mismo archivo
- Usar `ctx.pipeToSelf` para operaciones async del repository

```scala
package domains.<dominio>.engines

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import domains.<dominio>.models._
import domains.<dominio>.repositories.<Nombre>Repository
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

// ── Protocol ──
sealed trait <Nombre>Command
sealed trait <Nombre>Response

// ── Engine ──
object <Nombre>Engine {
  private case class State(/* estado mínimo, el resto en DB */)

  def apply(repo: <Nombre>Repository)(implicit ec: ExecutionContext): Behavior[<Nombre>Command] =
    active(State(), repo)

  private def active(state: State, repo: <Nombre>Repository)
                    (implicit ec: ExecutionContext): Behavior[<Nombre>Command] =
    Behaviors.receive { (ctx, msg) =>
      // ... pattern match sobre msg
      Behaviors.same
    }
}
```

Plantilla completa: [./assets/engine-template.scala](./assets/engine-template.scala)

---

### 5. Crear el Reactive Adapter (servicio Play)

Ubicación: `app/domains/<dominio>/services/Reactive<Nombre>Adapter.scala`

- Recibe `ActorRef[DomainGuardianCommand]` (no el engine directamente)
- Expone `Future[_]` al controller — nunca `ActorRef`
- Inyectable vía Guice (`@Singleton class`)

```scala
package domains.<dominio>.services

import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import infrastructure.guardian.{DomainGuardianCommand, Forward<Nombre>}
import domains.<dominio>.engines._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class Reactive<Nombre>Adapter(
  domain:    ActorRef[DomainGuardianCommand],
  scheduler: Scheduler,
  ec:        ExecutionContext
) {
  implicit private val timeout: Timeout    = 5.seconds
  implicit private val s: Scheduler       = scheduler
  implicit private val e: ExecutionContext = ec

  def create(req: Create<Nombre>Request): Future[<Nombre>View] =
    domain.ask(ref => Forward<Nombre>(Create<Nombre>(req, ref)))
}
```

Plantilla completa: [./assets/adapter-template.scala](./assets/adapter-template.scala)

---

### 6. Registrar en el Guardian

Si el dominio es de **negocio** → `DomainGuardian`.
Si es **transversal** (audit, metrics, moderation) → `CrossCutGuardian`.

En `app/infrastructure/guardian/DomainGuardian.scala`:

```scala
// 1. Agregar al sealed trait de comandos
final case class Forward<Nombre>(cmd: <Nombre>Command) extends DomainGuardianCommand

// 2. Agregar al case class DomainRefs
final case class DomainRefs(
  // ... existentes ...
  <nombre>: ActorRef[<Nombre>Command]
)

// 3. En el Behaviors.setup, hacer spawn
val <nombre>Ref = context.spawn(
  Behaviors.supervise(<Nombre>Engine(repo))
    .onFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.2)),
  "<nombre>-engine"
)
context.watchWith(<nombre>Ref, DomainChildTerminated("<nombre>-engine"))

// 4. En el pattern match de mensajes, agregar:
case Forward<Nombre>(cmd) => children.<nombre> ! cmd
```

---

### 7. Enlazar en Module.scala

En `app/Module.scala`, dentro del método `configure()` o en un `@Provides @Singleton`:

```scala
// Instanciar repository
val <nombre>Repo = injector.instanceOf[<Nombre>Repository]

// Crear adapter usando los refs del RootGuardian
bind(classOf[Reactive<Nombre>Adapter]).toInstance(
  new Reactive<Nombre>Adapter(refs.domain, system.scheduler, ec)
)
```

---

### 8. Registrar rutas

En `conf/routes`, siguiendo la convención del proyecto:

```
# <Nombre> domain
GET     /api/<nombre>s              controllers.web.<Nombre>Controller.list
POST    /api/<nombre>s              controllers.web.<Nombre>Controller.create
GET     /api/<nombre>s/:id          controllers.web.<Nombre>Controller.get(id: Long)
PUT     /api/<nombre>s/:id          controllers.web.<Nombre>Controller.update(id: Long)
DELETE  /api/<nombre>s/:id          controllers.web.<Nombre>Controller.delete(id: Long)
```

---

### 9. Crear evolución SQL

En `conf/evolutions/default/<N>.sql` (N = siguiente número):

```sql
# --- !Ups
CREATE TABLE <nombre>s (
  id          BIGSERIAL PRIMARY KEY,
  created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

# --- !Downs
DROP TABLE IF EXISTS <nombre>s;
```

---

## Convenciones de nomenclatura

| Elemento | Convención | Ejemplo |
|----------|-----------|---------|
| Paquete dominio | `domains.<nombre>` | `domains.reviews` |
| Actor engine | `<Nombre>Engine` | `ReviewEngine` |
| Repository | `<Nombre>Repository` | `ReviewRepository` |
| Adapter | `Reactive<Nombre>Adapter` | `ReactiveReviewAdapter` |
| Mensaje forward | `Forward<Nombre>` | `ForwardReview` |
| Carpeta | snake_case plural | `reviews/` |
| Tabla SQL | snake_case plural | `reviews` |

---

## Skills relacionadas

- [akka-reactive-play](..//akka-reactive-play/SKILL.md) — Diseño interno del Engine (Behavior, supervision, EventBus)
- Usar ambas skills en conjunto para un dominio completo: primero **domain-module-design** para el scaffold, luego **akka-reactive-play** para la lógica interna del engine.

---

## Assets del skill

| Archivo | Propósito |
|---------|-----------|
| [./references/domain-layers.md](./references/domain-layers.md) | Mapa completo de la arquitectura de guardians |
| [./assets/domain-folder-init.sh](./assets/domain-folder-init.sh) | Script para crear la estructura de carpetas |
| [./assets/models-template.scala](./assets/models-template.scala) | Plantilla de modelos y DTOs |
| [./assets/repository-template.scala](./assets/repository-template.scala) | Plantilla de Repository Slick |
| [./assets/engine-template.scala](./assets/engine-template.scala) | Plantilla de Engine (Actor Typed) |
| [./assets/adapter-template.scala](./assets/adapter-template.scala) | Plantilla de Reactive Adapter |
