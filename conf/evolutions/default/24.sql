# --- Editorial articles: piezas fundacionales del equipo de la edicion.
# --- Migracion de los 6 articulos hardcoded en app/views/articulos/*.scala.html
# --- a una tabla DB-driven. Las vistas estaticas se eliminan en este mismo sprint.

# --- !Ups

-- ============================================================
-- Tabla: editorial_articles
--
-- Diferencia con publications:
--   - publications  : contenido enviado por usuarios, con workflow
--                     editorial (pitch -> draft -> review -> published)
--   - editorial_articles : piezas escritas por el equipo de la edicion,
--                          siempre publicadas, sin ciclo de revision.
--
-- Se almacenan ambos en el mismo listado publico (HomeController.publicaciones)
-- y comparten taxonomia (publication_categories).
-- ============================================================

CREATE TABLE IF NOT EXISTS editorial_articles (
    id              BIGSERIAL    PRIMARY KEY,
    slug            VARCHAR(150) NOT NULL UNIQUE,
    title           VARCHAR(255) NOT NULL,
    excerpt         VARCHAR(500),
    body_html       TEXT         NOT NULL,
    category_id     BIGINT       REFERENCES publication_categories(id) ON DELETE SET NULL,
    category_label  VARCHAR(100) NOT NULL,            -- snapshot legible
    tags_pipe       TEXT         NOT NULL DEFAULT '', -- "tag1|tag2|tag3"
    published_label VARCHAR(40)  NOT NULL,            -- ej: "15 Dic 2025" (fecha visible)
    published_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cover_image     VARCHAR(500),
    is_published    BOOLEAN      NOT NULL DEFAULT TRUE,
    view_count      INT          NOT NULL DEFAULT 0,
    order_index     INT          NOT NULL DEFAULT 100,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_editorial_articles_pub_order
    ON editorial_articles(is_published, order_index, published_at DESC);

CREATE INDEX IF NOT EXISTS idx_editorial_articles_category
    ON editorial_articles(category_id);

-- ============================================================
-- Seed: 6 articulos fundacionales migrados literalmente desde
-- app/views/articulos/*.scala.html. Se usa dollar-quoting de
-- PostgreSQL para evitar escape hell del HTML.
-- ============================================================

-- 1) Akka Actors
INSERT INTO editorial_articles
    (slug, title, excerpt, body_html, category_id, category_label,
     tags_pipe, published_label, published_at, order_index)
VALUES (
    'akka-actors',
    'Introducción a Akka Actors',
    'El modelo de actores como abstracción de alto nivel para sistemas concurrentes y distribuidos. Mensajes inmutables, supervisión y let it crash.',
    $body$<div class="article-body">
<section class="article-section">
  <h2>El Modelo de Actores</h2>
  <p>El modelo de actores es un paradigma de programación concurrente que proporciona una abstracción de alto nivel para construir sistemas distribuidos y concurrentes. En Akka, los actores son objetos ligeros que procesan mensajes de forma asíncrona.</p>
</section>
<section class="article-section">
  <h2>Principios de Diseño Reactivo</h2>
  <div class="pattern-card">
    <h3>🎯 Actor Model Pattern</h3>
    <p>El patrón de modelo de actores es fundamental en el diseño reactivo. Cada actor es una entidad independiente que:</p>
    <ul>
      <li><strong>Encapsula estado</strong>: El estado interno del actor está protegido y solo puede ser modificado por el propio actor</li>
      <li><strong>Procesa mensajes secuencialmente</strong>: Garantiza thread-safety sin locks</li>
      <li><strong>Comunicación asíncrona</strong>: Los actores se comunican mediante el envío de mensajes inmutables</li>
      <li><strong>Location transparency</strong>: Los actores pueden estar en diferentes nodos sin cambiar el código</li>
    </ul>
  </div>
  <div class="pattern-card">
    <h3>📦 Let It Crash Philosophy</h3>
    <p>En lugar de programación defensiva, Akka promueve la filosofía "let it crash":</p>
    <ul>
      <li>Los actores supervisor gestionan fallos de sus subordinados</li>
      <li>Las estrategias de supervisión definen cómo recuperarse de errores</li>
      <li>Permite código más simple y limpio</li>
      <li>Aumenta la resiliencia del sistema</li>
    </ul>
  </div>
</section>
<section class="article-section">
  <h2>Implementación Básica</h2>
  <div class="code-block"><pre><code>import akka.actor.{Actor, ActorSystem, Props}

case class Greeting(message: String)
case object GetGreeting

class GreeterActor extends Actor {
  var greeting = ""

  def receive = {
    case Greeting(message) =>
      greeting = message
      println(s"Greeting received: $message")

    case GetGreeting =>
      sender() ! greeting
  }
}

val system = ActorSystem("GreeterSystem")
val greeter = system.actorOf(Props[GreeterActor], "greeter")

greeter ! Greeting("Hello, Akka!")
greeter ! GetGreeting</code></pre></div>
</section>
<section class="article-section">
  <h2>Patrones de Supervisión</h2>
  <div class="pattern-card">
    <h3>🔄 Supervision Strategies</h3>
    <p>Akka proporciona estrategias de supervisión predefinidas:</p>
    <ul>
      <li><strong>Resume</strong>: Continuar procesando con el estado actual</li>
      <li><strong>Restart</strong>: Reiniciar el actor con estado limpio</li>
      <li><strong>Stop</strong>: Detener permanentemente el actor</li>
      <li><strong>Escalate</strong>: Pasar el error al supervisor padre</li>
    </ul>
  </div>
  <div class="code-block"><pre><code>import akka.actor.SupervisorStrategy._
import akka.actor.{OneForOneStrategy, SupervisorStrategy}
import scala.concurrent.duration._

override val supervisorStrategy: SupervisorStrategy =
  OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
    case _: ArithmeticException => Resume
    case _: NullPointerException => Restart
    case _: IllegalArgumentException => Stop
    case _: Exception => Escalate
  }</code></pre></div>
</section>
<section class="article-section">
  <h2>Beneficios del Modelo de Actores</h2>
  <div class="benefits-grid">
    <div class="benefit-box"><h4>🚀 Alta Concurrencia</h4><p>Millones de actores pueden ejecutarse concurrentemente sin degradación de rendimiento</p></div>
    <div class="benefit-box"><h4>🔒 Thread-Safe</h4><p>Sin necesidad de locks, mutexes o sincronización manual</p></div>
    <div class="benefit-box"><h4>🌐 Distribución</h4><p>Escalado horizontal transparente a través de múltiples nodos</p></div>
    <div class="benefit-box"><h4>💪 Resiliencia</h4><p>Recuperación automática de fallos mediante supervisión</p></div>
  </div>
</section>
<section class="article-section">
  <h2>Recursos Adicionales</h2>
  <ul class="resource-list">
    <li><a href="https://www.reactivedesignpatterns.com/" target="_blank">Reactive Design Patterns Book</a></li>
    <li><a href="https://doc.akka.io/docs/akka/current/" target="_blank">Akka Documentation</a></li>
    <li><a href="https://www.manning.com/books/akka-in-action" target="_blank">Akka in Action</a></li>
  </ul>
</section>
</div>$body$,
    (SELECT id FROM publication_categories WHERE slug = 'tutorial'),
    'Tutorial',
    'Akka|Scala|Concurrencia',
    '15 Dic 2025',
    TIMESTAMP '2025-12-15 09:00:00',
    10
)
ON CONFLICT (slug) DO NOTHING;

-- 2) Patrones de Resiliencia
INSERT INTO editorial_articles
    (slug, title, excerpt, body_html, category_id, category_label,
     tags_pipe, published_label, published_at, order_index)
VALUES (
    'patrones-resiliencia',
    'Patrones de Resiliencia',
    'Circuit Breaker, Bulkhead, Timeout y Retry: cuatro patrones complementarios para construir sistemas que sobreviven al fallo.',
    $body$<div class="article-body">
<section class="article-section">
  <h2>Diseñando Sistemas Resilientes</h2>
  <p>La resiliencia es un pilar fundamental del manifiesto reactivo. Un sistema resiliente permanece responsivo ante fallos, se recupera rápidamente y minimiza el impacto en los usuarios. Exploraremos tres patrones clave de resiliencia.</p>
</section>
<section class="article-section">
  <h2>Circuit Breaker Pattern</h2>
  <div class="pattern-card">
    <h3>⚡ ¿Qué es un Circuit Breaker?</h3>
    <p>El patrón Circuit Breaker previene que una aplicación intente ejecutar operaciones que probablemente fallarán. Funciona como un interruptor eléctrico que se "abre" cuando detecta muchos fallos.</p>
    <h4>Estados del Circuit Breaker:</h4>
    <ul>
      <li><strong>Closed</strong>: Operación normal, las peticiones pasan</li>
      <li><strong>Open</strong>: Se detectaron fallos, las peticiones fallan inmediatamente</li>
      <li><strong>Half-Open</strong>: Prueba si el servicio se recuperó</li>
    </ul>
  </div>
  <div class="code-block"><pre><code>import akka.pattern.CircuitBreaker
import scala.concurrent.duration._

val breaker = new CircuitBreaker(
  scheduler = system.scheduler,
  maxFailures = 5,
  callTimeout = 10.seconds,
  resetTimeout = 1.minute
)

val future = breaker.withCircuitBreaker {
  externalService.call()
}

breaker.onOpen { println("Circuit breaker opened!") }
breaker.onHalfOpen { println("Circuit breaker half-open, testing...") }
breaker.onClose { println("Circuit breaker closed, service recovered") }</code></pre></div>
  <div class="pattern-card">
    <h3>📊 Beneficios del Circuit Breaker</h3>
    <ul>
      <li>Previene cascadas de fallos en sistemas distribuidos</li>
      <li>Mejora el tiempo de respuesta al fallar rápido</li>
      <li>Permite que servicios degradados se recuperen</li>
      <li>Proporciona fallbacks y respuestas por defecto</li>
    </ul>
  </div>
</section>
<section class="article-section">
  <h2>Bulkhead Pattern</h2>
  <div class="pattern-card">
    <h3>🚢 Aislamiento de Recursos</h3>
    <p>El patrón Bulkhead aísla recursos en "compartimentos" separados, similar a los compartimentos estancos de un barco. Si un compartimento falla, los otros continúan funcionando.</p>
    <h4>Tipos de Bulkheads:</h4>
    <ul>
      <li><strong>Thread Pool Bulkheads</strong>: Pools de threads separados por servicio</li>
      <li><strong>Semaphore Bulkheads</strong>: Límites de concurrencia por recurso</li>
      <li><strong>Actor Bulkheads</strong>: Grupos de actores aislados</li>
    </ul>
  </div>
  <div class="code-block"><pre><code>import scala.concurrent.{ExecutionContext, Future}
import java.util.concurrent.Executors

val databaseEC    = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))
val externalApiEC = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))

def queryDatabase(): Future[Result]    = Future { /* ... */ }(databaseEC)
def callExternalApi(): Future[Response] = Future { /* ... */ }(externalApiEC)</code></pre></div>
</section>
<section class="article-section">
  <h2>Timeout Pattern</h2>
  <div class="pattern-card">
    <h3>⏱️ Gestión de Tiempos de Espera</h3>
    <p>Los timeouts previenen que operaciones lentas o bloqueadas consuman recursos indefinidamente. Son esenciales para mantener la responsividad del sistema.</p>
  </div>
  <div class="code-block"><pre><code>import akka.pattern.after
import scala.concurrent.duration._

val futureWithTimeout = Future.firstCompletedOf(Seq(
  slowOperation(),
  after(5.seconds, system.scheduler)(
    Future.failed(new TimeoutException("Operation timed out"))
  )
))

futureWithTimeout.recover {
  case _: TimeoutException => defaultValue
}</code></pre></div>
</section>
<section class="article-section">
  <h2>Retry Pattern</h2>
  <div class="pattern-card">
    <h3>🔄 Reintentos Inteligentes</h3>
    <p>El patrón Retry maneja fallos transitorios reintentando operaciones con estrategias de backoff para evitar sobrecargar servicios.</p>
    <h4>Estrategias de Retry:</h4>
    <ul>
      <li><strong>Exponential Backoff</strong>: Aumenta el tiempo entre reintentos</li>
      <li><strong>Jitter</strong>: Añade aleatoriedad para evitar tormentas de reintentos</li>
      <li><strong>Max Attempts</strong>: Límite de intentos antes de fallar</li>
    </ul>
  </div>
</section>
<section class="article-section">
  <h2>Combinando Patrones</h2>
  <div class="pattern-card">
    <h3>🔗 Estrategia Completa de Resiliencia</h3>
    <p>Los patrones de resiliencia son más efectivos cuando se combinan:</p>
    <ul>
      <li>Circuit Breaker + Retry: Previene reintentos innecesarios</li>
      <li>Bulkhead + Timeout: Aísla y limita recursos bloqueados</li>
      <li>Circuit Breaker + Fallback: Proporciona respuestas alternativas</li>
    </ul>
  </div>
  <div class="benefits-grid">
    <div class="benefit-box"><h4>🛡️ Fault Tolerance</h4><p>El sistema continúa funcionando ante fallos parciales</p></div>
    <div class="benefit-box"><h4>⚡ Fast Fail</h4><p>Respuestas rápidas incluso cuando servicios fallan</p></div>
    <div class="benefit-box"><h4>🔍 Observabilidad</h4><p>Métricas y monitoreo de salud del sistema</p></div>
    <div class="benefit-box"><h4>🌊 Cascading Prevention</h4><p>Evita que fallos se propaguen a todo el sistema</p></div>
  </div>
</section>
<section class="article-section">
  <h2>Recursos Adicionales</h2>
  <ul class="resource-list">
    <li><a href="https://www.reactivedesignpatterns.com/" target="_blank">Reactive Design Patterns - Resilience Patterns</a></li>
    <li><a href="https://martinfowler.com/bliki/CircuitBreaker.html" target="_blank">Martin Fowler - Circuit Breaker</a></li>
    <li><a href="https://github.com/resilience4j/resilience4j" target="_blank">Resilience4j Library</a></li>
  </ul>
</section>
</div>$body$,
    (SELECT id FROM publication_categories WHERE slug = 'articulo'),
    'Artículo',
    'Patrones|Resiliencia|Arquitectura',
    '8 Dic 2025',
    TIMESTAMP '2025-12-08 09:00:00',
    20
)
ON CONFLICT (slug) DO NOTHING;

-- 3) Akka Streams
INSERT INTO editorial_articles
    (slug, title, excerpt, body_html, category_id, category_label,
     tags_pipe, published_label, published_at, order_index)
VALUES (
    'akka-streams',
    'Streams Reactivos con Akka Streams',
    'Reactive Streams, backpressure y composición de pipelines type-safe para procesar flujos de datos asíncronos.',
    $body$<div class="article-body">
<section class="article-section">
  <h2>Introducción a Akka Streams</h2>
  <p>Akka Streams es una implementación de Reactive Streams que proporciona una API de alto nivel para procesar flujos de datos con backpressure automático. Permite construir pipelines de procesamiento complejos de manera declarativa y type-safe.</p>
</section>
<section class="article-section">
  <h2>Reactive Streams Specification</h2>
  <div class="pattern-card">
    <h3>📊 Los Cuatro Componentes</h3>
    <p>La especificación Reactive Streams define cuatro interfaces clave:</p>
    <ul>
      <li><strong>Publisher</strong>: Produce elementos y los envía a Subscribers</li>
      <li><strong>Subscriber</strong>: Consume elementos de un Publisher</li>
      <li><strong>Subscription</strong>: Representa la conexión entre Publisher y Subscriber</li>
      <li><strong>Processor</strong>: Actúa como Publisher y Subscriber simultáneamente</li>
    </ul>
  </div>
  <div class="pattern-card">
    <h3>🔄 Backpressure</h3>
    <p>Backpressure es el mecanismo que permite a los consumidores controlar la velocidad de producción de datos. Previene:</p>
    <ul>
      <li>Desbordamiento de buffers</li>
      <li>OutOfMemoryErrors</li>
      <li>Degradación del rendimiento</li>
      <li>Pérdida de datos</li>
    </ul>
  </div>
</section>
<section class="article-section">
  <h2>Componentes de Akka Streams</h2>
  <div class="code-block"><pre><code>import akka.stream._
import akka.stream.scaladsl._

implicit val system = ActorSystem("StreamsSystem")
implicit val materializer = ActorMaterializer()

val source: Source[Int, NotUsed] = Source(1 to 100)
val flow: Flow[Int, String, NotUsed] = Flow[Int].map(i => s"Number: $i")
val sink: Sink[String, Future[Done]] = Sink.foreach[String](println)

val graph = source.via(flow).to(sink)
graph.run()</code></pre></div>
</section>
<section class="article-section">
  <h2>Manejo de Backpressure</h2>
  <div class="code-block"><pre><code>val bufferedFlow  = Flow[Int].buffer(100, OverflowStrategy.backpressure)
val droppingFlow  = Flow[Int].buffer(100, OverflowStrategy.dropHead)
val throttledSrc  = Source(1 to 1000).throttle(10, 1.second, 100, ThrottleMode.Shaping)</code></pre></div>
  <div class="pattern-card">
    <h3>⚖️ Estrategias de Buffer</h3>
    <ul>
      <li><strong>Backpressure</strong>: Ralentiza el productor (predeterminado)</li>
      <li><strong>DropHead</strong>: Descarta elementos más antiguos</li>
      <li><strong>DropTail</strong>: Descarta elementos más recientes</li>
      <li><strong>DropBuffer</strong>: Descarta todo el buffer</li>
      <li><strong>DropNew</strong>: Rechaza elementos nuevos</li>
      <li><strong>Fail</strong>: Falla el stream</li>
    </ul>
  </div>
</section>
<section class="article-section">
  <h2>Procesamiento Asíncrono</h2>
  <div class="code-block"><pre><code>val asyncFlow     = Flow[Int].mapAsync(4) { i => Future { expensiveComputation(i) } }
val unorderedFlow = Flow[Int].mapAsyncUnordered(10) { i => callExternalService(i) }</code></pre></div>
</section>
<section class="article-section">
  <h2>Beneficios de Akka Streams</h2>
  <div class="benefits-grid">
    <div class="benefit-box"><h4>🔄 Backpressure Automático</h4><p>Gestión automática del flujo de datos sin desbordamientos</p></div>
    <div class="benefit-box"><h4>📐 Type-Safe</h4><p>Composición type-safe verificada en tiempo de compilación</p></div>
    <div class="benefit-box"><h4>⚡ Alto Rendimiento</h4><p>Procesamiento eficiente con mínima overhead</p></div>
    <div class="benefit-box"><h4>🔌 Interoperabilidad</h4><p>Compatible con especificación Reactive Streams</p></div>
  </div>
</section>
<section class="article-section">
  <h2>Recursos Adicionales</h2>
  <ul class="resource-list">
    <li><a href="https://www.reactivedesignpatterns.com/" target="_blank">Reactive Design Patterns - Stream Processing</a></li>
    <li><a href="https://doc.akka.io/docs/akka/current/stream/index.html" target="_blank">Akka Streams Documentation</a></li>
    <li><a href="https://www.reactive-streams.org/" target="_blank">Reactive Streams Specification</a></li>
  </ul>
</section>
</div>$body$,
    (SELECT id FROM publication_categories WHERE slug = 'guia'),
    'Guía',
    'Akka Streams|Reactive Streams|Backpressure',
    '1 Dic 2025',
    TIMESTAMP '2025-12-01 09:00:00',
    30
)
ON CONFLICT (slug) DO NOTHING;

-- 4) Play Async
INSERT INTO editorial_articles
    (slug, title, excerpt, body_html, category_id, category_label,
     tags_pipe, published_label, published_at, order_index)
VALUES (
    'play-async',
    'Play Framework y Programación Asíncrona',
    'Por qué Play es asíncrono por diseño: actions no bloqueantes, streaming, WebSockets y composición de Futures.',
    $body$<div class="article-body">
<section class="article-section">
  <h2>Play Framework: Asíncrono por Diseño</h2>
  <p>Play Framework está construido desde cero para ser completamente asíncrono y no bloqueante. Aprovecha Scala Futures, Akka y el modelo reactivo para proporcionar alta escalabilidad y rendimiento en aplicaciones web.</p>
</section>
<section class="article-section">
  <h2>Non-Blocking I/O</h2>
  <div class="pattern-card">
    <h3>🚀 Arquitectura Asíncrona</h3>
    <p>A diferencia de frameworks tradicionales que usan un thread por request, Play usa:</p>
    <ul>
      <li><strong>Event Loop</strong>: Procesa múltiples requests con pocos threads</li>
      <li><strong>Non-blocking I/O</strong>: No bloquea threads esperando operaciones I/O</li>
      <li><strong>Future-based</strong>: Todas las operaciones asíncronas retornan Futures</li>
      <li><strong>Akka HTTP</strong>: Servidor HTTP reactivo y escalable</li>
    </ul>
  </div>
</section>
<section class="article-section">
  <h2>Actions Asíncronas</h2>
  <div class="code-block"><pre><code>import play.api.mvc._
import scala.concurrent.{Future, ExecutionContext}
import javax.inject.Inject

class AsyncController @Inject()(cc: ControllerComponents)
                               (implicit ec: ExecutionContext) extends AbstractController(cc) {

  def syncAction = Action { implicit request =>
    val result = blockingDatabaseCall()
    Ok(result)
  }

  def asyncAction = Action.async { implicit request =>
    asyncDatabaseCall().map { result => Ok(result) }
  }

  def parallelAction = Action.async { implicit request =>
    val f1 = service1.getData()
    val f2 = service2.getData()
    val f3 = service3.getData()
    for {
      d1 <- f1
      d2 <- f2
      d3 <- f3
    } yield Ok(combineResults(d1, d2, d3))
  }
}</code></pre></div>
</section>
<section class="article-section">
  <h2>Streaming de Respuestas</h2>
  <div class="pattern-card">
    <h3>📡 Respuestas Chunked</h3>
    <p>Play permite streaming de respuestas grandes sin cargar todo en memoria.</p>
  </div>
  <div class="code-block"><pre><code>def downloadLargeFile = Action {
  val source = FileIO.fromPath(new java.io.File("/path/file.dat").toPath)
  Ok.chunked(source)
}

def eventStream = Action {
  val source = Source.tick(1.second, 1.second, "tick").map(ServerSentEvent(_))
  Ok.chunked(source via EventSource.flow).as(ContentTypes.EVENT_STREAM)
}</code></pre></div>
</section>
<section class="article-section">
  <h2>WebSockets</h2>
  <div class="code-block"><pre><code>def echoSocket = WebSocket.accept[String, String] { request =>
  Flow[String].map(msg => s"Echo: $msg")
}

def throttledSocket = WebSocket.accept[String, String] { request =>
  Flow[String].throttle(10, 1.second).map(processMessage)
}</code></pre></div>
</section>
<section class="article-section">
  <h2>Best Practices</h2>
  <div class="pattern-card">
    <h3>✅ Recomendaciones</h3>
    <ul>
      <li>Usa <code>Action.async</code> para todas las operaciones I/O</li>
      <li>Evita bloquear threads con operaciones síncronas</li>
      <li>Usa execution contexts apropiados para diferentes tipos de trabajo</li>
      <li>Implementa timeouts en todas las operaciones externas</li>
      <li>Maneja errores con <code>recover</code> y <code>recoverWith</code></li>
      <li>Usa streaming para respuestas grandes</li>
      <li>Aprovecha composición de Futures para paralelizar operaciones</li>
    </ul>
  </div>
</section>
<section class="article-section">
  <h2>Beneficios</h2>
  <div class="benefits-grid">
    <div class="benefit-box"><h4>📈 Escalabilidad</h4><p>Maneja miles de conexiones concurrentes con pocos recursos</p></div>
    <div class="benefit-box"><h4>⚡ Performance</h4><p>Menor latencia y mayor throughput que frameworks bloqueantes</p></div>
    <div class="benefit-box"><h4>💪 Resiliencia</h4><p>Mejor manejo de fallos y timeouts</p></div>
    <div class="benefit-box"><h4>🔄 Reactive</h4><p>Cumple con los principios del Manifiesto Reactivo</p></div>
  </div>
</section>
<section class="article-section">
  <h2>Recursos Adicionales</h2>
  <ul class="resource-list">
    <li><a href="https://www.playframework.com/documentation/latest/ScalaAsync" target="_blank">Play Framework - Asynchronous Programming</a></li>
    <li><a href="https://www.reactivedesignpatterns.com/" target="_blank">Reactive Design Patterns</a></li>
    <li><a href="https://doc.akka.io/docs/akka-http/current/" target="_blank">Akka HTTP Documentation</a></li>
  </ul>
</section>
</div>$body$,
    (SELECT id FROM publication_categories WHERE slug = 'tutorial'),
    'Tutorial',
    'Play Framework|Async|Web',
    '25 Nov 2025',
    TIMESTAMP '2025-11-25 09:00:00',
    40
)
ON CONFLICT (slug) DO NOTHING;

-- 5) Message Passing
INSERT INTO editorial_articles
    (slug, title, excerpt, body_html, category_id, category_label,
     tags_pipe, published_label, published_at, order_index)
VALUES (
    'message-passing',
    'Escalabilidad con Message Passing',
    'Pub-Sub, Sharding y Cluster en Akka: cómo escalar horizontalmente con paso de mensajes asíncrono e inmutable.',
    $body$<div class="article-body">
<section class="article-section">
  <h2>Message Passing en Sistemas Distribuidos</h2>
  <p>El paso de mensajes es un patrón fundamental para construir sistemas distribuidos escalables. En lugar de comunicación directa mediante llamadas de método, los componentes se comunican enviando mensajes inmutables de forma asíncrona.</p>
</section>
<section class="article-section">
  <h2>Principios del Message Passing</h2>
  <div class="pattern-card">
    <h3>📨 Características Clave</h3>
    <ul>
      <li><strong>Asíncrono</strong>: El emisor no espera respuesta inmediata</li>
      <li><strong>Desacoplamiento</strong>: Emisor y receptor no se conocen directamente</li>
      <li><strong>Inmutabilidad</strong>: Los mensajes son inmutables</li>
      <li><strong>Location Transparency</strong>: Funciona igual local o remotamente</li>
      <li><strong>Fire and Forget</strong>: Patrón de envío sin respuesta</li>
    </ul>
  </div>
</section>
<section class="article-section">
  <h2>Patrones de Mensajería</h2>
  <div class="code-block"><pre><code>// Point-to-Point (1-to-1)
actor ! Message("direct message")

// Publish-Subscribe (1-to-many)
mediator ! Publish("topic", Event("broadcast"))

// Request-Response
actor ? Request("query") // retorna Future[Response]

// Message Routing
router ! Message("routed to one of many")</code></pre></div>
</section>
<section class="article-section">
  <h2>Distributed Pub-Sub</h2>
  <div class="code-block"><pre><code>import akka.cluster.pubsub._
import akka.cluster.pubsub.DistributedPubSubMediator._

class PublisherActor extends Actor {
  val mediator = DistributedPubSub(context.system).mediator
  def receive = {
    case msg: String => mediator ! Publish("content", msg)
  }
}

class SubscriberActor extends Actor {
  val mediator = DistributedPubSub(context.system).mediator
  override def preStart() = mediator ! Subscribe("content", self)
  def receive = { case msg: String => println(s"Received: $msg") }
}</code></pre></div>
</section>
<section class="article-section">
  <h2>Sharding para Escalabilidad Masiva</h2>
  <div class="pattern-card">
    <h3>🔀 Cluster Sharding</h3>
    <p>Cluster Sharding distribuye automáticamente actores a través del cluster y gestiona su localización y ciclo de vida.</p>
  </div>
  <div class="pattern-card">
    <h3>💎 Beneficios de Sharding</h3>
    <ul>
      <li>Distribución automática de carga</li>
      <li>Rebalanceo dinámico cuando nodos se unen/salen</li>
      <li>Location transparency completa</li>
      <li>Escalado a millones de entidades</li>
      <li>Persistencia opcional con Event Sourcing</li>
    </ul>
  </div>
</section>
<section class="article-section">
  <h2>Métricas y Resultados</h2>
  <div class="benefits-grid">
    <div class="benefit-box"><h4>📊 10x Throughput</h4><p>De 1,000 a 10,000 ordenes/seg con mismo hardware</p></div>
    <div class="benefit-box"><h4>⚡ Latencia p99: 50ms</h4><p>Respuesta rápida incluso bajo carga alta</p></div>
    <div class="benefit-box"><h4>🌍 Multi-Region</h4><p>Despliegue en 3 regiones sin cambios de código</p></div>
    <div class="benefit-box"><h4>💰 70% Cost Saving</h4><p>Reducción de costos de infraestructura</p></div>
  </div>
</section>
<section class="article-section">
  <h2>Recursos Adicionales</h2>
  <ul class="resource-list">
    <li><a href="https://www.reactivedesignpatterns.com/" target="_blank">Reactive Design Patterns - Messaging Patterns</a></li>
    <li><a href="https://doc.akka.io/docs/akka/current/typed/cluster.html" target="_blank">Akka Cluster Documentation</a></li>
    <li><a href="https://www.enterpriseintegrationpatterns.com/" target="_blank">Enterprise Integration Patterns</a></li>
  </ul>
</section>
</div>$body$,
    (SELECT id FROM publication_categories WHERE slug = 'caso-de-estudio'),
    'Caso de Estudio',
    'Mensajería|Distribución|Escalabilidad',
    '18 Nov 2025',
    TIMESTAMP '2025-11-18 09:00:00',
    50
)
ON CONFLICT (slug) DO NOTHING;

-- 6) Testing Reactivo
INSERT INTO editorial_articles
    (slug, title, excerpt, body_html, category_id, category_label,
     tags_pipe, published_label, published_at, order_index)
VALUES (
    'testing-reactivo',
    'Testing de Sistemas Reactivos',
    'Akka TestKit, TestProbe, property-based testing y testing de streams: el toolbox para verificar sistemas asíncronos.',
    $body$<div class="article-body">
<section class="article-section">
  <h2>Desafíos del Testing Reactivo</h2>
  <p>Los sistemas reactivos presentan desafíos únicos para testing debido a su naturaleza asíncrona, concurrente y distribuida. Sin embargo, existen herramientas y patrones específicos que facilitan escribir tests confiables y mantenibles.</p>
</section>
<section class="article-section">
  <h2>Akka TestKit</h2>
  <div class="pattern-card">
    <h3>🧪 Herramienta de Testing para Actores</h3>
    <p>Akka TestKit proporciona utilidades para probar actores de forma síncrona y determinística, a pesar de su naturaleza asíncrona.</p>
  </div>
  <div class="code-block"><pre><code>class GreeterActorSpec extends TestKit(ActorSystem("TestSystem"))
  with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "A Greeter actor" should {
    "respond with greeting" in {
      val greeter = system.actorOf(Props[GreeterActor])
      greeter ! Greeting("Hello")
      greeter ! GetGreeting
      expectMsg(500.millis, "Hello")
    }
  }
}</code></pre></div>
</section>
<section class="article-section">
  <h2>TestProbe: Actores de Prueba</h2>
  <div class="pattern-card">
    <h3>🔍 Métodos Útiles de TestProbe</h3>
    <ul>
      <li><code>expectMsg</code>: Espera mensaje específico</li>
      <li><code>expectMsgType[T]</code>: Espera mensaje de tipo T</li>
      <li><code>expectNoMessage</code>: Verifica que no lleguen mensajes</li>
      <li><code>expectMsgAnyOf</code>: Espera cualquiera de varios mensajes</li>
      <li><code>fishForMessage</code>: Busca mensaje que cumple condición</li>
      <li><code>receiveN</code>: Recibe N mensajes</li>
    </ul>
  </div>
</section>
<section class="article-section">
  <h2>Testing de Streams</h2>
  <div class="code-block"><pre><code>val (pub, sub) = TestSource.probe[Int]
  .via(processingFlow)
  .toMat(TestSink.probe[String])(Keep.both)
  .run()

sub.request(3)
pub.sendNext(1);; pub.sendNext(2);; pub.sendNext(3)
sub.expectNext("1", "2", "3")
pub.sendComplete()
sub.expectComplete()</code></pre></div>
</section>
<section class="article-section">
  <h2>Property-Based Testing</h2>
  <div class="pattern-card">
    <h3>🎲 ScalaCheck</h3>
    <p>Property-based testing genera casos de prueba automáticamente, ideal para sistemas con comportamiento complejo o muchos estados posibles.</p>
  </div>
</section>
<section class="article-section">
  <h2>Best Practices</h2>
  <div class="pattern-card">
    <h3>✅ Patrones Recomendados</h3>
    <ul>
      <li><strong>Test Isolation</strong>: Cada test debe ser independiente</li>
      <li><strong>Deterministic Tests</strong>: Evitar timing dependencies</li>
      <li><strong>Fast Feedback</strong>: Tests unitarios rápidos</li>
      <li><strong>Pyramid Structure</strong>: Muchos unit, menos integration</li>
      <li><strong>Meaningful Assertions</strong>: Verificar comportamiento, no implementación</li>
    </ul>
  </div>
</section>
<section class="article-section">
  <h2>Herramientas Recomendadas</h2>
  <div class="benefits-grid">
    <div class="benefit-box"><h4>🧪 ScalaTest</h4><p>Framework de testing versátil y expresivo</p></div>
    <div class="benefit-box"><h4>🔍 Akka TestKit</h4><p>Testing específico para actores</p></div>
    <div class="benefit-box"><h4>🎲 ScalaCheck</h4><p>Property-based testing automático</p></div>
    <div class="benefit-box"><h4>🐳 TestContainers</h4><p>Dependencias en contenedores Docker</p></div>
  </div>
</section>
<section class="article-section">
  <h2>Recursos Adicionales</h2>
  <ul class="resource-list">
    <li><a href="https://doc.akka.io/docs/akka/current/testing.html" target="_blank">Akka Testing Documentation</a></li>
    <li><a href="https://www.scalatest.org/" target="_blank">ScalaTest Official Site</a></li>
    <li><a href="https://www.scalacheck.org/" target="_blank">ScalaCheck Documentation</a></li>
    <li><a href="https://www.reactivedesignpatterns.com/" target="_blank">Reactive Design Patterns - Testing Patterns</a></li>
  </ul>
</section>
</div>$body$,
    (SELECT id FROM publication_categories WHERE slug = 'recurso'),
    'Recurso',
    'Testing|Akka TestKit|QA',
    '10 Nov 2025',
    TIMESTAMP '2025-11-10 09:00:00',
    60
)
ON CONFLICT (slug) DO NOTHING;

# --- !Downs

DROP INDEX IF EXISTS idx_editorial_articles_category;
DROP INDEX IF EXISTS idx_editorial_articles_pub_order;
DROP TABLE IF EXISTS editorial_articles;
