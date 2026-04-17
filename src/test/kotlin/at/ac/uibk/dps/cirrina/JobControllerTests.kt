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
import kotlin.time.measureTime
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.parallel.ResourceAccessMode
import org.junit.jupiter.api.parallel.ResourceLock

@TestMethodOrder(MethodOrderer.DisplayName::class)
@ResourceLock(value = "port-6000", mode = ResourceAccessMode.READ_WRITE)
class JobControllerTests {

  @Test
  fun testJobDoneTransition() {
    assertTimeoutPreemptively(Duration.ofSeconds(10)) {
      assertDoesNotThrow {
        val httpServer = HttpServer.create(InetSocketAddress(6000), 0)
        httpServer.createContext(
          "/process/email",
          { exchange ->
            try {
              exchange.sendResponseHeaders(200, 0)
            } finally {
              exchange.close()
            }
          },
        )
        httpServer.start()

        try {
          val context = ContextInMemory()

          val runtime =
            DaggerTestComponent.builder()
              .testModule(
                TestModule(
                  context,
                  DefaultDescriptions.smartFactory,
                  listOf("jobController", "arm", "messageProcessor"),
                )
              )
              .build()
              .runtime()

          runtime.eventHandler.emit(
            Event(
              "eProductComplete",
              Csml.EventChannel.EXTERNAL,
              listOf(ContextVariable("cv", "test")),
              target = "jobController",
              source = "arm",
            )
          )

          val duration = measureTime { runtime.run() }
          println("Job controller test 1 execution: $duration")

          assertEquals(true, context.get("isJobDone"))
        } finally {
          httpServer.stop(0)
        }
      }
    }
  }

  @Test
  fun testJobDoneMessageEventRaising() {
    assertTimeoutPreemptively(Duration.ofSeconds(10)) {
      assertDoesNotThrow {
        val msgFuture: CompletableFuture<String?> = CompletableFuture()
        val httpServer = HttpServer.create(InetSocketAddress(6000), 0)
        httpServer.createContext(
          "/process/email",
          { exchange ->
            try {
              val cv =
                Serializer.deserialize<List<ContextVariable>>(exchange.requestBody.readAllBytes())[
                    0]
              msgFuture.complete(cv.value as String?)
              exchange.sendResponseHeaders(200, 0)
            } finally {
              exchange.close()
            }
          },
        )
        httpServer.start()

        try {
          val context = ContextInMemory()

          val runtime =
            DaggerTestComponent.builder()
              .testModule(
                TestModule(
                  context,
                  DefaultDescriptions.smartFactory,
                  listOf("jobController", "arm", "messageProcessor"),
                )
              )
              .build()
              .runtime()

          Thread.sleep(2000)
          runtime.eventHandler.emit(
            Event(
              "eProductComplete",
              Csml.EventChannel.EXTERNAL,
              listOf(ContextVariable("cv", "test")),
              target = "jobController",
              source = "arm",
            )
          )

          val duration = measureTime { runtime.run() }
          println("Job controller test 2 execution: $duration")

          assertEquals("Job done...", msgFuture.get(5, TimeUnit.SECONDS))
        } finally {
          httpServer.stop(0)
        }
      }
    }
  }

  @Test
  fun testJobDoneEventRaise() {
    assertTimeoutPreemptively(Duration.ofSeconds(10)) {
      assertDoesNotThrow {
        val msgFuture: CompletableFuture<String?> = CompletableFuture()
        val httpServer = HttpServer.create(InetSocketAddress(6000), 0)
        httpServer.createContext(
          "/process/email",
          { exchange ->
            try {
              val cv =
                Serializer.deserialize<List<ContextVariable>>(exchange.requestBody.readAllBytes())[
                    0]
              msgFuture.complete(cv.value as String?)
              exchange.sendResponseHeaders(200, 0)
            } finally {
              exchange.close()
            }
          },
        )
        httpServer.start()

        try {
          val context = ContextInMemory()

          val runtime =
            DaggerTestComponent.builder()
              .testModule(
                TestModule(
                  context,
                  DefaultDescriptions.smartFactory,
                  listOf("jobController", "arm", "messageProcessor", "stub"),
                )
              )
              .build()
              .runtime()

          runtime.eventHandler.emit(
            Event(
              "eProductComplete",
              Csml.EventChannel.EXTERNAL,
              listOf(ContextVariable("cv", "test")),
              target = "jobController",
              source = "arm",
            )
          )

          val duration = measureTime { runtime.run() }
          println("Job controller test 3 execution: $duration")

          assertEquals(true, context.get("stub_job_done_received"))
        } finally {
          httpServer.stop(0)
        }
      }
    }
  }
}
