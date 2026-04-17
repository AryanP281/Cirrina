package at.ac.uibk.dps.cirrina

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory
import at.ac.uibk.dps.cirrina.execution.util.Serializer
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.parallel.ResourceAccessMode
import org.junit.jupiter.api.parallel.ResourceLock

@TestMethodOrder(MethodOrderer.DisplayName::class)
@ResourceLock(value = "port-6000", mode = ResourceAccessMode.READ_WRITE)
class BeltTests {

  @Test
  fun testIsUnloadingStatusChangeOnTransitioningToUnloadingState() {
    assertTimeoutPreemptively(Duration.ofSeconds(20)) {
      assertDoesNotThrow {
        val httpServer = HttpServer.create(InetSocketAddress(6000), 0)
        httpServer.createContext("/detectbeam/start") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("interrupted", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/detectbeam/end") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("interrupted", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/takephoto") { exchange ->
          exchange.use { exchange -> exchange.sendResponseHeaders(200, -1) }
        }
        httpServer.createContext("/scanphoto") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("validObject", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/movebelt") { exchange ->
          exchange.use { exchange -> exchange.sendResponseHeaders(200, -1) }
        }
        httpServer.createContext("/stopbelt") { exchange ->
          exchange.use { exchange -> exchange.sendResponseHeaders(200, -1) }
        }

        httpServer.start()
        var runtimeJob: Job? = null

        try {
          val context = ContextInMemory()

          val runtime =
            DaggerTestComponent.builder()
              .testModule(
                TestModule(context, DefaultDescriptions.smartFactory, listOf("conveyorBelt"))
              )
              .build()
              .runtime()

          val cs = CoroutineScope(Dispatchers.Default)
          runtimeJob = cs.launch(Dispatchers.Default) { runtime.run() }

          Thread.sleep(5000)
          assertEquals(true, context.get("isUnloading"))
        } finally {
          httpServer.stop(0)
          runtimeJob?.cancel()
        }
      }
    }
  }

  @Test
  fun testIsUnloadingStatusChangeOnTransitioningToJobDoneState() {
    assertTimeoutPreemptively(Duration.ofSeconds(20)) {
      assertDoesNotThrow {
        val httpServer = HttpServer.create(InetSocketAddress(6000), 0)
        httpServer.createContext("/detectbeam/start") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("interrupted", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/detectbeam/end") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("interrupted", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/takephoto") { exchange ->
          exchange.use { exchange -> exchange.sendResponseHeaders(200, -1) }
        }
        httpServer.createContext("/scanphoto") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("validObject", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/movebelt") { exchange ->
          exchange.use { exchange -> exchange.sendResponseHeaders(200, -1) }
        }
        httpServer.createContext("/stopbelt") { exchange ->
          exchange.use { exchange -> exchange.sendResponseHeaders(200, -1) }
        }

        httpServer.start()
        var runtimeJob: Job? = null

        try {
          val context = ContextInMemory()

          val runtime =
            DaggerTestComponent.builder()
              .testModule(
                TestModule(
                  context,
                  DefaultDescriptions.smartFactory,
                  listOf("conveyorBelt", "jobController", "arm"),
                )
              )
              .build()
              .runtime()

          val cs = CoroutineScope(Dispatchers.Default)
          runtimeJob = cs.launch(Dispatchers.Default) { runtime.run() }

          Thread.sleep(5000)
          assertEquals(true, context.get("isUnloading"))
          runtime.eventHandler.emit(
            Event(
              "eProductComplete",
              Csml.EventChannel.EXTERNAL,
              listOf(ContextVariable("cv", "test")),
              target = "jobController",
              source = "arm",
            )
          )

          Thread.sleep(5000)
          assertEquals(false, context.get("isUnloading"))
        } finally {
          httpServer.stop(0)
          runtimeJob?.cancel()
        }
      }
    }
  }

  @Test
  fun testArmPickupEventRaising() {
    assertTimeoutPreemptively(Duration.ofSeconds(20)) {
      assertDoesNotThrow {
        val httpServer = HttpServer.create(InetSocketAddress(6000), 0)
        httpServer.createContext("/detectbeam/start") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("interrupted", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/detectbeam/end") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("interrupted", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/takephoto") { exchange ->
          exchange.use { exchange -> exchange.sendResponseHeaders(200, -1) }
        }
        httpServer.createContext("/scanphoto") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("validObject", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/movebelt") { exchange ->
          exchange.use { exchange -> exchange.sendResponseHeaders(200, -1) }
        }
        httpServer.createContext("/stopbelt") { exchange ->
          exchange.use { exchange -> exchange.sendResponseHeaders(200, -1) }
        }

        val armPickupRequestStatus: CompletableFuture<Boolean> = CompletableFuture()
        httpServer.createContext("/pickup") { exchange ->
          exchange.use {
            val respData = listOf<ContextVariable>(ContextVariable("success", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
          armPickupRequestStatus.complete(true)
        }

        httpServer.start()
        var runtimeJob: Job? = null

        try {
          val context = ContextInMemory()

          val runtime =
            DaggerTestComponent.builder()
              .testModule(
                TestModule(context, DefaultDescriptions.smartFactory, listOf("conveyorBelt", "arm"))
              )
              .build()
              .runtime()

          val cs = CoroutineScope(Dispatchers.Default)
          runtimeJob = cs.launch(Dispatchers.Default) { runtime.run() }

          Thread.sleep(5000)
          assertEquals(true, armPickupRequestStatus.get())
        } finally {
          httpServer.stop(0)
          runtimeJob?.cancel()
        }
      }
    }
  }

  @Test
  fun testUnloadingStatusOnArmPickup() {
    assertTimeoutPreemptively(Duration.ofSeconds(20)) {
      assertDoesNotThrow {
        val httpServer = HttpServer.create(InetSocketAddress(6000), 0)

        httpServer.createContext("/detectbeam/start") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("interrupted", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/detectbeam/end") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("interrupted", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/takephoto") { exchange ->
          exchange.use { exchange -> exchange.sendResponseHeaders(200, -1) }
        }

        val validObject: AtomicBoolean = AtomicBoolean(true)
        httpServer.createContext("/scanphoto") { exchange ->
          exchange.use { exchange ->
            val respData =
              listOf<ContextVariable>(ContextVariable("validObject", validObject.getAndSet(false)))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/movebelt") { exchange ->
          exchange.use { exchange -> exchange.sendResponseHeaders(200, -1) }
        }
        httpServer.createContext("/stopbelt") { exchange ->
          exchange.use { exchange -> exchange.sendResponseHeaders(200, -1) }
        }

        val armPickupRequestStatus: CompletableFuture<Boolean> = CompletableFuture()
        httpServer.createContext("/pickup") { exchange ->
          exchange.use {
            val respData = listOf<ContextVariable>(ContextVariable("success", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
          armPickupRequestStatus.complete(true)
        }

        httpServer.start()
        var runtimeJob: Job? = null

        try {
          val context = ContextInMemory()

          val runtime =
            DaggerTestComponent.builder()
              .testModule(
                TestModule(context, DefaultDescriptions.smartFactory, listOf("conveyorBelt", "arm"))
              )
              .build()
              .runtime()

          val cs = CoroutineScope(Dispatchers.Default)
          runtimeJob = cs.launch(Dispatchers.Default) { runtime.run() }

          Thread.sleep(5000)
          assertEquals(true, armPickupRequestStatus.get())
          assertEquals(false, context.get("isUnloading"))
        } finally {
          httpServer.stop(0)
          runtimeJob?.cancel()
        }
      }
    }
  }

  @Test
  fun testErrorMessageRaising() {
    assertTimeoutPreemptively(Duration.ofSeconds(20)) {
      assertDoesNotThrow {
        val httpServer = HttpServer.create(InetSocketAddress(6000), 0)

        httpServer.createContext("/detectbeam/start") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("interrupted", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/detectbeam/end") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("interrupted", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/takephoto") { exchange ->
          exchange.use { exchange -> exchange.sendResponseHeaders(200, -1) }
        }

        httpServer.createContext("/scanphoto") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("validObject", false))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }

        val msgFuture: CompletableFuture<String> = CompletableFuture()
        httpServer.createContext("/process/email") { exchange ->
          exchange.use { exchange ->
            val cv =
              Serializer.deserialize<List<ContextVariable>>(exchange.requestBody.readAllBytes())[0]
            msgFuture.complete(cv.value as String?)
            exchange.sendResponseHeaders(200, 0)
          }
        }

        httpServer.start()
        var runtimeJob: Job? = null

        try {
          val context = ContextInMemory()

          val runtime =
            DaggerTestComponent.builder()
              .testModule(
                TestModule(
                  context,
                  DefaultDescriptions.smartFactory,
                  listOf("conveyorBelt", "messageProcessor"),
                )
              )
              .build()
              .runtime()

          val cs = CoroutineScope(Dispatchers.Default)
          runtimeJob = cs.launch(Dispatchers.Default) { runtime.run() }

          Thread.sleep(5000)
          assertEquals("Belt error: Invalid object detected", msgFuture.get(5, TimeUnit.SECONDS))
        } finally {
          httpServer.stop(0)
          runtimeJob?.cancel()
        }
      }
    }
  }

  @Test
  fun testEScannedEventRaising() {
    assertTimeoutPreemptively(Duration.ofSeconds(20)) {
      assertDoesNotThrow {
        val httpServer = HttpServer.create(InetSocketAddress(6000), 0)

        httpServer.createContext("/detectbeam/start") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("interrupted", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/detectbeam/end") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("interrupted", true))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }
        httpServer.createContext("/takephoto") { exchange ->
          exchange.use { exchange -> exchange.sendResponseHeaders(200, -1) }
        }

        httpServer.createContext("/scanphoto") { exchange ->
          exchange.use { exchange ->
            val respData = listOf<ContextVariable>(ContextVariable("validObject", false))
            val serializedResp = Serializer.serialize(respData)
            exchange.sendResponseHeaders(200, serializedResp.size.toLong())
            exchange.responseBody.use { stream -> stream.write(serializedResp) }
          }
        }

        val scansCount: CompletableFuture<Int> = CompletableFuture()
        httpServer.createContext("/statistics") { exchange ->
          exchange.use { exchange ->
            val reqData: List<ContextVariable> =
              Serializer.deserialize(exchange.requestBody.readAllBytes())
            reqData.forEach { if (it.name == "nScans") scansCount.complete(it.value as Int) }
          }
        }

        httpServer.start()
        var runtimeJob: Job? = null

        try {
          val context = ContextInMemory()

          val runtime =
            DaggerTestComponent.builder()
              .testModule(
                TestModule(
                  context,
                  DefaultDescriptions.smartFactory,
                  listOf("conveyorBelt", "monitor"),
                )
              )
              .build()
              .runtime()

          val cs = CoroutineScope(Dispatchers.Default)
          runtimeJob = cs.launch(Dispatchers.Default) { runtime.run() }

          Thread.sleep(5000)
          assertEquals(1, scansCount.get(5, TimeUnit.SECONDS))
        } finally {
          httpServer.stop(0)
          runtimeJob?.cancel()
        }
      }
    }
  }
}
