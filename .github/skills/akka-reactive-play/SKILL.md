---
name: akka-reactive-play
description: 'Diseño e implementación de componentes reactivos con Akka Typed + Play Framework siguiendo los 4 pilares del Reactive Manifesto (Responsive, Resilient, Elastic, Message-Driven). Usar esta skill en los siguientes casos específicos: crear nuevos Actors, diseñar un EventBus/PubSub, integrar actors con controllers de Play, modelar dominios como sistemas de mensajes, revisar que un componente cumple los pilares reactivos, migrar código bloqueante a un modelo message-driven.'
argument-hint: 'Nombre del componente o dominio a implementar (ej: "NotificationActor para el dominio messaging")'
---

# Akka Reactive Play — Skill

Guía paso a paso para diseñar, implementar y validar componentes reactivos en este proyecto
(**Play Framework 3 + Akka Typed 2.8 + Scala 2.13**), asegurando adhesión al
[Reactive Manifesto](https://www.reactivemanifesto.org/).

---

## Cuándo usar esta skill

| Situación | Ejemplo de prompt |
|-----------|-------------------|
| Crear un Actor nuevo | "Crea un `NotificationActor` para el dominio `messaging`" |
| Modelar un dominio reactivo | "Diseña el dominio `newsletter` siguiendo el Reactive Manifesto" |
| Integrar Actor con Play Controller | "Conecta `GamificationActor` al endpoint POST `/events/rsvp`" |
| Revisar adhesión a los pilares reactivos | "Revisa si `AnalyticsEngine` es realmente Elastic" |
| Migrar código bloqueante | "Convierte este service síncrono a message-driven" |
| Diseñar pub/sub entre dominios | "Propaga `PublicationPublished` a los dominios `gamification` y `newsletter`" |

---

## Pilares del Reactive Manifesto — Checklist rápido

Antes de escribir código, validar los 4 pilares **en este orden de prioridad**:

1. [ ] **Message-Driven** _(base)_ — toda comunicación via mensajes asíncronos; sin llamadas síncronas inter-actor
2. [ ] **Responsive** _(sobre Message-Driven)_ — responde en tiempo acotado; usa `ask` con timeout o `tell` fire-and-forget
3. [ ] **Resilient** _(sobre Responsive)_ — maneja fallos con supervision (`SupervisorStrategy`), no propaga excepciones al caller
4. [ ] **Elastic** _(sobre Resilient)_ — sin estado compartido mutable; escala horizontalmente vía `akka-cluster`

---

## Procedimiento de implementación

### 1. Modelar el protocolo de mensajes

Define el ADT del actor en su propio objeto:

```scala
// app/domains/<dominio>/<NombreActor>.scala
sealed trait <Nombre>Command
sealed trait <Nombre>Response

// Comandos mutantes (fire-and-forget o con replyTo)
case class DoSomething(payload: String)               extends <Nombre>Command
case class QueryState(replyTo: ActorRef[<Nombre>Response]) extends <Nombre>Command

// Respuestas
case class StateSnapshot(data: Map[String, Long])     extends <Nombre>Response
```

**Regla:** Nunca exponer el `ActorRef` directamente fuera del dominio. Usar un *adapter* (ver paso 4).

---

### 2. Implementar el `Behavior`

Usar el patrón **functional stateful** con `Behaviors.receive` + recursión de cola:

```scala
object <Nombre>Actor {

  private case class State(/* campos inmutables */)

  def apply()(implicit ec: ExecutionContext): Behavior[<Nombre>Command] =
    active(State())

  private def active(state: State)(implicit ec: ExecutionContext): Behavior[<Nombre>Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case DoSomething(payload) =>
          // Efecto asíncrono sin bloquear
          ctx.pipeToSelf(someAsyncOp(payload)) {
            case Success(r) => InternalResult(r)
            case Failure(e) => InternalError(e.getMessage)
          }
          Behaviors.same

        case QueryState(replyTo) =>
          replyTo ! StateSnapshot(state.data)
          Behaviors.same
      }
    }
}
```

**Anti-patterns a evitar:**

```scala
// ❌ NUNCA: bloquear dentro de un actor
Await.result(db.run(query), 5.seconds)

// ❌ NUNCA: estado mutable compartido
var counter = 0

// ✓ CORRECTO: pasar estado como parámetro (immutable recursion)
active(state.copy(counter = state.counter + 1))
```

---

### 3. Registrar el Actor en el Guardian

Todo actor del sistema es hijo del `CrossCutGuardian` ubicado en
`app/infrastructure/guardian/`. Agregar un `spawn` en el guardian:

```scala
// En CrossCutGuardianActor.scala
val <nombre>Ref: ActorRef[<Nombre>Command] =
  context.spawn(<Nombre>Actor(), "<nombre>-actor")
context.watch(<nombre>Ref) // supervision automática via DeathWatch
```

Referenciar el archivo del guardian: [CrossCutGuardian](../../../app/infrastructure/guardian/).

---

### 4. Crear el Adapter para Play

El controller NO debe conocer el `ActorRef` directamente. Crear un adapter inyectable:

```scala
// app/domains/<dominio>/Reactive<Nombre>Adapter.scala
import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class Reactive<Nombre>Adapter(
  guardian: ActorRef[CrossCutGuardianCommand],
  scheduler: Scheduler,
  ec: ExecutionContext
) {
  implicit val timeout: Timeout = 5.seconds
  implicit val s: Scheduler     = scheduler
  implicit val e: ExecutionContext = ec

  def queryState(): Future[StateSnapshot] =
    guardian.ask(ref => Forward<Nombre>Query(QueryState(ref)))
}
```

Patrón de referencia en el proyecto: [ReactiveAnalyticsAdapter](../../../app/shared/analytics/ReactiveAnalyticsAdapter.scala).

---

### 5. Integrar con el Play Controller

```scala
class <Nombre>Controller @Inject()(
  cc: ControllerComponents,
  adapter: Reactive<Nombre>Adapter
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def getState: Action[AnyContent] = Action.async {
    adapter.queryState().map { snapshot =>
      Ok(Json.toJson(snapshot))
    }.recover {
      case _: AskTimeoutException => ServiceUnavailable("Actor timeout")
      case e: Exception           => InternalServerError(e.getMessage)
    }
  }
}
```

Registrar en `conf/routes` y enlazar el módulo en `app/Module.scala`.

---

### 6. Publicar eventos de dominio en el EventBus

Si el actor produce eventos de dominio que otros dominios deben consumir, publicar
al `EventBusEngine` (ver [EventBusEngine](../../../app/shared/eventbus/EventBusEngine.scala)):

```scala
// Dentro del Behavior, después de una mutación exitosa:
eventBusRef ! PublishEvent(DomainEvent(
  eventType  = "<dominio>.<accion>",   // ej: "gamification.badge_earned"
  entityId   = entityId.toString,
  entityType = "<Entidad>",
  data       = Map("key" -> value.toString),
  occurredAt = Instant.now()
))
```

**Topics automáticos:** el EventBus extrae el prefijo antes del `.` como topic,
y publica también a `"*"` para suscriptores globales.

---

### 7. Validar con el checklist reactivo

Usar la tabla de referencia completa en [./references/reactive-checklist.md](./references/reactive-checklist.md).

---

## Convenciones del proyecto

| Aspecto | Convención |
|---------|-----------|
| Nombres de paquete | `domains.<dominio>`, `shared.<cross-cutting>`, `infrastructure.<infra>` |
| Sufijo de Actor | `*Engine` para lógica de dominio, `*Adapter` para integración Play |
| Versión Akka | `2.8.5` (Typed API únicamente, no Classic salvo interop forzado) |
| Serialización | Jackson (`akka-serialization-jackson`) para mensajes de cluster |
| Timeouts | `5.seconds` por defecto en `ask`; configurar en `application.conf` para producción |
| Tests de Actor | `akka-actor-testkit-typed`, clase `ScalaTestWithActorTestKit` |

---

## Recursos del skill

- [Checklist Reactivo completo](./references/reactive-checklist.md)
- [Plantilla de Actor](./assets/actor-template.scala)
- [Plantilla de Adapter](./assets/adapter-template.scala)

---

## Skill relacionada

Para crear la estructura completa de un dominio (carpetas, repository Slick, engine, adapter, guardian, Module.scala), usar primero **domain-module-design** y luego esta skill para la lógica interna del engine:

```
/domain-module-design reviews    ← scaffold completo del dominio
/akka-reactive-play              ← lógica reactiva interna del engine
```
