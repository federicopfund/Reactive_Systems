# Checklist Reactivo — Validación de Componentes

Usar este checklist para revisar cualquier Actor, Service o módulo del proyecto
antes de considerarlo listo para merge.

---

## Pilar 1 — Responsive (Responsivo)

El sistema responde en tiempo acotado bajo cualquier condición.

- [ ] Todos los endpoints `async` tienen timeout explícito en el `ask` pattern
- [ ] No hay `Thread.sleep` ni `Await.result` en el código de producción
- [ ] Los timeouts están externalizados en `application.conf`, no hardcodeados
- [ ] Las respuestas de error tienen el mismo tiempo de respuesta que las exitosas
- [ ] Back-pressure: el actor descarta o encola mensajes si el buzón supera el límite

**Cómo verificar:**
```bash
grep -rn "Await\.result\|Thread\.sleep\|\.wait()" app/
# Debe retornar vacío
```

---

## Pilar 2 — Resilient (Resiliente)

El sistema se recupera de fallos sin propagar errores al caller.

- [ ] Todos los actores tienen `SupervisorStrategy` definida (o heredan la del guardian)
- [ ] Los errores internos se convierten en mensajes `InternalError(msg)` con `pipeToSelf`
- [ ] Los adapters capturan `AskTimeoutException` y retornan `ServiceUnavailable`
- [ ] No hay `throw` en el cuerpo de un `Behavior` (usar `Behaviors.same` + log)
- [ ] El `CrossCutGuardian` usa `context.watch` sobre todos los actores hijos
- [ ] Las fallas de DB se modelan como `Try/Either`, nunca como excepciones no capturadas

**Patrón correcto:**
```scala
ctx.pipeToSelf(db.run(action)) {
  case Success(r) => InternalOk(r)
  case Failure(e) => InternalError(e.getMessage)  // no relanza la excepción
}
```

---

## Pilar 3 — Elastic (Elástico)

El sistema se adapta a la carga sin cambios de arquitectura.

- [ ] Sin estado mutable compartido entre actores (no `var` globales, no singletons con estado)
- [ ] El estado del actor se pasa por parámetro en la recursión (`active(state.copy(...))`)
- [ ] Los actores de alta carga usan `DistributedPubSub` (no broadcast O(n) manual)
- [ ] Los router pools (`RoundRobinPool`, `ConsistentHashingPool`) se usan para workers costosos
- [ ] Las dependencias se inyectan, no se crean dentro del `Behavior`

**Anti-pattern:**
```scala
// ❌ Estado compartido entre actores
object SharedCounter { var count = 0 }

// ✓ Estado encapsulado en el Behavior
private def active(count: Int): Behavior[Cmd] = Behaviors.receive { (_, msg) =>
  msg match {
    case Increment => active(count + 1)
  }
}
```

---

## Pilar 4 — Message-Driven (Orientado a Mensajes)

Toda comunicación entre componentes es asíncrona vía mensajes.

- [ ] Los actores se comunican solo via `!` (tell) o `ask` — nunca llamadas directas a métodos
- [ ] Los adapters exponen `Future[Response]`, nunca `ActorRef` directamente
- [ ] Los eventos de dominio se publican al `EventBusEngine` vía `PublishEvent`
- [ ] El protocolo del actor está completamente modelado como ADT sellado (`sealed trait`)
- [ ] Los mensajes son immutables (`case class`, `case object`)
- [ ] No hay llamadas HTTP síncronas dentro de un actor (usar `ctx.pipeToSelf` + client async)

---

## Checklist de integración Play ↔ Akka

- [ ] El adapter está registrado como binding en `app/Module.scala`
- [ ] El controller usa `Action.async { ... }` (nunca `Action { ... }` para operaciones con actor)
- [ ] La ruta está registrada en `conf/routes`
- [ ] El Scheduler implícito proviene de `actorSystem.scheduler`, no creado manualmente
- [ ] Los timeouts de `ask` no superan el timeout del request HTTP de Play (default 60s)

---

## Checklist de testing

- [ ] Existe un test en `test/` usando `ScalaTestWithActorTestKit`
- [ ] Se testea el comportamiento ante mensajes desconocidos (dead letters)
- [ ] Se testea la respuesta ante falla de dependencia (mock que lanza excepción)
- [ ] Los timeouts del test kit son menores a los de producción

```scala
// Estructura de test recomendada
class <Nombre>ActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "<Nombre>Actor" when {
    "receives DoSomething" should {
      "reply with StateSnapshot" in {
        val actor = spawn(<Nombre>Actor())
        val probe = createTestProbe[<Nombre>Response]()
        actor ! QueryState(probe.ref)
        probe.expectMessageType[StateSnapshot]
      }
    }
  }
}
```
