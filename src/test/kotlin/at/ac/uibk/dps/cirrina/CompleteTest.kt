package at.ac.uibk.dps.cirrina

import at.ac.uibk.dps.cirrina.csm.Csml
import at.ac.uibk.dps.cirrina.csm.Csml.*
import at.ac.uibk.dps.cirrina.data.DefaultDescriptions
import at.ac.uibk.dps.cirrina.di.DaggerTestComponent
import at.ac.uibk.dps.cirrina.di.TestModule
import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.execution.`object`.Stdlib
import at.ac.uibk.dps.cirrina.execution.provider.ContextInMemory
import at.ac.uibk.dps.cirrina.execution.util.Serializer
import at.ac.uibk.dps.cirrina.util.TestUtils.mockHttpServer
import java.time.Duration
import kotlin.time.measureTime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CompleteTest {
  @Test
  fun testCompleteExecute() {
    assertTimeout(Duration.ofSeconds(10)) {
      assertDoesNotThrow {
        val context = ContextInMemory()
        val server = mockHttpServer { input ->
          val v = input.firstOrNull { it.name == "v" } ?: error("variable 'v' not found")
          listOf(ContextVariable("v", (v.value as Int) + 1))
        }

        try {
          val runtime =
            DaggerTestComponent.builder()
              .testModule(TestModule(context, DefaultDescriptions.complete, listOf("complete")))
              .build()
              .runtime()

          val duration = measureTime { runtime.run() }
          println("complete execution: $duration")

          assertEquals(100, context.get("v"))
          assertEquals(true, context.get("b"))
          assertEquals(true, context.get("e"))
        } finally {
          server.stop(1)
        }
      }
    }
  }

  @Test
  fun testStdlib() {
    val sizes = intArrayOf(10, 50, 100, 200, 500)
    val sizeSet = HashSet<Int>()

    repeat(100) {
      val payload = Stdlib.randomPayload(sizes)
      assertNotNull(payload)
      assertTrue(sizes.any { it == payload.size })
      sizeSet.add(payload.size)
    }

    assertTrue(sizeSet.size > 1)
    assertTrue(sizeSet.first() in sizes)

    assertTrue(Stdlib.takeRandom(sizeSet) in sizeSet)
  }

  @Test
  fun testSerialization() {
    val intVal = 123
    assertEquals(
      intVal,
      Serializer.deserializeValues(Serializer.serializeValues(intVal), Int::class.java),
    )

    val stringVal = "test"
    assertEquals(
      stringVal,
      Serializer.deserializeValues<String>(
        Serializer.serializeValues(stringVal),
        String::class.java,
      ),
    )

    val cv = ContextVariable("testVar", 456)
    val cvParsed =
      Serializer.deserializeValues<ContextVariable>(
        Serializer.serializeValues(cv),
        ContextVariable::class.java,
      )
    assertEquals(cv.name, cvParsed.name)
    assertEquals(cv.value, cvParsed.value)

    val event =
      Event(
        topic = "testEvent",
        channel = Csml.EventChannel.PERIPHERAL,
        data = listOf(cv),
        target = "target",
        source = "source",
        id = "someId",
        emittedTime = 1L,
      )

    val eventParsed =
      Serializer.deserializeValues<Event>(Serializer.serializeValues(event), Event::class.java)
    assertEquals(event.topic, eventParsed.topic)
    assertEquals(event.channel, eventParsed.channel)
    assertEquals(1, eventParsed.data.size)
    assertEquals(456, eventParsed.data[0].value)
    assertEquals("source", eventParsed.source)

    val complexMap = mapOf("key" to listOf(1, 2, 3), "active" to true)
    val mapParsed =
      Serializer.deserializeValues<Map<String, Any>>(
        Serializer.serializeValues(complexMap),
        Map::class.java as Class<Map<String, Any>>,
      )
    assertEquals(complexMap, mapParsed)
    assertEquals(3, (mapParsed["key"] as List<*>).size)

    val cvList: List<ContextVariable> =
      listOf(ContextVariable("testVar2", 456), ContextVariable("testVar2", 457))
    val cvListParsed =
      Serializer.deserializeList(Serializer.serializeList(cvList), ContextVariable::class.java)
    assertEquals(cvList.size, cvListParsed.size)
    for (i in cvListParsed.indices) {
      assertEquals(cvList[i].name, cvListParsed[i].name)
      assertEquals(cvList[i].value, cvListParsed[i].value)
      assertEquals(cvList[i].isLazy, cvListParsed[i].isLazy)
    }

    val eventList: List<Event> =
      listOf(
        Event(
          topic = "testEvent1",
          channel = Csml.EventChannel.PERIPHERAL,
          data = listOf(ContextVariable("testVar1", 100)),
          target = "target",
          source = "source",
          id = "someId1",
          emittedTime = 1L,
        ),
        Event(
          topic = "testEvent2",
          channel = Csml.EventChannel.PERIPHERAL,
          data = listOf(ContextVariable("testVar2", 200)),
          target = "target",
          source = "source",
          id = "someId2",
          emittedTime = 2L,
        ),
      )
    val eventListParsed =
      Serializer.deserializeList(Serializer.serializeList(eventList), Event::class.java)
    assertEquals(eventList.size, eventListParsed.size)
    for (i in eventList.indices) {
      assertEquals(eventList[i].topic, eventListParsed[i].topic)
      assertEquals(eventList[i].channel, eventListParsed[i].channel)
      assertEquals(eventList[i].data.size, eventListParsed[i].data.size)
      assertEquals(eventList[i].data[0].value, eventListParsed[i].data[0].value)
      assertEquals(eventList[i].target, eventListParsed[i].target)
      assertEquals(eventList[i].source, eventListParsed[i].source)
      assertEquals(eventList[i].emittedTime, eventListParsed[i].emittedTime)
    }

    val emptyList: List<ContextVariable> = ArrayList()
    val emptyListParsed =
      Serializer.deserializeList(Serializer.serializeList(emptyList), ContextVariable::class.java)
    assertEquals(0, emptyListParsed.size)

    val testArray = arrayOf(1, 2, 3)
    val testArrayParsed =
      Serializer.deserializeValues(Serializer.serializeValues(testArray), Array::class.java)
    assertEquals(testArray.size, testArrayParsed.size)
    for (i in testArray.indices) {
      assertEquals(testArray[i], testArrayParsed[i])
    }

    val listOfLists = listOf(listOf(1, 2, 3), listOf("a", "b", "c"))
    val listOfListsParsed =
      Serializer.deserializeValues(Serializer.serializeValues(listOfLists), List::class.java)
    assertEquals(listOfLists.size, listOfListsParsed.size)
    for (i in listOfLists.indices) {
      assertEquals(listOfLists[i], listOfListsParsed[i])
    }
  }

  @Test
  fun testServiceImplementationBindings() {
    val service =
      HttpServiceImplementationBinding(
        "increment",
        true,
        Type.HTTP,
        "http",
        "localhost",
        8000,
        "/increment",
        HttpMethod.GET,
      )

    assertEquals("copy", service.withName("copy").name)
    assertEquals(Type.HTTP, service.withType(Type.HTTP).type)
    assertFalse(service.withLocal(false).isLocal)
    assertEquals("otherScheme", service.withScheme("otherScheme").scheme)
    assertEquals("otherHost", service.withHost("otherHost").host)
    assertEquals(8080, service.withPort(8080).port)
    assertEquals("/somethingElse", service.withEndPoint("/somethingElse").endPoint)
    assertEquals(HttpMethod.POST, service.withMethod(HttpMethod.POST).method)

    val otherService = service.withName("otherService")
    assertEquals(service, service)
    assertEquals(service.hashCode(), service.hashCode())
    assertNotEquals(service, otherService)
  }
}
