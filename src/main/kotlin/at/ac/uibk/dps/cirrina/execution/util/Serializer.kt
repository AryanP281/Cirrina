package at.ac.uibk.dps.cirrina.execution.util

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.fory.exchange.ContextVariableFdl
import at.ac.uibk.dps.cirrina.fory.exchange.EventFdl
import org.apache.fory.Fory
import org.apache.fory.ThreadSafeFory
import org.apache.fory.config.Language
import org.apache.fory.memory.MemoryBuffer

object Serializer {
  private val fory: ThreadSafeFory =
    Fory.builder().withLanguage(Language.JAVA).buildThreadSafeFory().apply {
      register(ContextVariableFdl.ContextVariable::class.java)
      register(EventFdl.Event::class.java)
      register(EventFdl.EventChannel::class.java)
      register(ContextVariableFdl.Value::class.java)
    }

  private val threadBuffer = ThreadLocal.withInitial { MemoryBuffer.newHeapBuffer(1024) }

  fun serializeValues(obj: Any): ByteArray {

    // Parsing Cirrina data types into Fory-generated data types
    var objToSerialize: Any? =
      when {
        obj is ContextVariable -> parseCirrinaContextVariable(obj)
        obj is Event -> parseCirrinaEvent(obj)
        obj is EventChannel -> parseCirrinaEventChannel(obj)
        else -> parseCirrinaValue(obj)
      }

    val buffer = threadBuffer.get()
    buffer.writerIndex(0)

    fory.serialize(buffer, objToSerialize)

    return buffer.getBytes(0, buffer.writerIndex())
  }

  fun serializeList(obj: List<*>): ByteArray {
    val objToSerialize =
      when (obj.firstOrNull()) {
        is ContextVariable -> obj.map { parseCirrinaContextVariable(it as ContextVariable) }
        is Event -> obj.map { parseCirrinaEvent(it as Event) }
        is EventChannel -> obj.map { parseCirrinaEventChannel(it as EventChannel) }
        null -> obj
        else -> error("value type could not be converted to fory")
      }

    val buffer = threadBuffer.get()
    buffer.writerIndex(0)

    fory.serialize(buffer, objToSerialize)

    return buffer.getBytes(0, buffer.writerIndex())
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> deserializeValues(bytes: ByteArray, deserializationClass: Class<T>): T {
    val deserializedObj: T =
      when (deserializationClass) {
        ContextVariable::class.java ->
          parseFdlContextVariable(fory.deserialize(bytes) as ContextVariableFdl.ContextVariable)
        Event::class.java -> parseFdlEvent(fory.deserialize(bytes) as EventFdl.Event)
        EventChannel::class.java ->
          parseFdlEventChannel(fory.deserialize(bytes) as EventFdl.EventChannel)
        else -> parseFdlValue(fory.deserialize(bytes) as ContextVariableFdl.Value)
      }
        as T

    return deserializedObj
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> deserializeList(bytes: ByteArray, deserializationClass: Class<T>): List<T> {
    val deserializedObj = fory.deserialize(bytes) ?: return emptyList() // Handling empty lists

    val deserializedCollection: List<T> =
      when (deserializationClass) {
        ContextVariable::class.java -> {
          val fdlList = deserializedObj as List<ContextVariableFdl.ContextVariable>
          fdlList.map { parseFdlContextVariable(it) }
        }
        Event::class.java -> {
          val fdlList = deserializedObj as List<EventFdl.Event>
          fdlList.map { parseFdlEvent(it) }
        }
        else -> {
          val fdlList = deserializedObj as List<EventFdl.EventChannel>
          fdlList.map { parseFdlEventChannel(it) }
        }
      }
        as List<T>

    return deserializedCollection
  }

  private fun parseCirrinaValue(cirrinaValue: Any?): ContextVariableFdl.Value? {
    return when (cirrinaValue) {
      is Int -> ContextVariableFdl.Value.ofInteger(cirrinaValue)
      is Float -> ContextVariableFdl.Value.ofFloat(cirrinaValue)
      is Long -> ContextVariableFdl.Value.ofLong(cirrinaValue)
      is Double -> ContextVariableFdl.Value.ofDouble(cirrinaValue)
      is String -> ContextVariableFdl.Value.ofString(cirrinaValue)
      is Boolean -> ContextVariableFdl.Value.ofBool(cirrinaValue)
      is ByteArray -> ContextVariableFdl.Value.ofBytes(cirrinaValue)
      is Array<*> -> ContextVariableFdl.Value.ofArray(cirrinaValue.map { parseCirrinaValue(it!!) })
      is List<*> ->
        ContextVariableFdl.Value.ofValueList(cirrinaValue.map { parseCirrinaValue(it!!) })
      is Map<*, *> -> {
        val fdlMap = HashMap<ContextVariableFdl.Value?, ContextVariableFdl.Value?>()
        val cirrinaMap = cirrinaValue as Map<Any?, Any?>
        cirrinaMap.forEach { entry ->
          fdlMap[parseCirrinaValue(entry.key)] = parseCirrinaValue(entry.value)
        }
        ContextVariableFdl.Value.ofValueMap(fdlMap)
      }
      null -> null
      else -> error("value type could not be converted to fory")
    }
      as ContextVariableFdl.Value?
  }

  private fun parseCirrinaContextVariable(
    cirrinaContextVariable: ContextVariable
  ): ContextVariableFdl.ContextVariable {
    val fdlContextVariable: ContextVariableFdl.ContextVariable =
      ContextVariableFdl.ContextVariable()
    fdlContextVariable.name = cirrinaContextVariable.name
    fdlContextVariable.value = parseCirrinaValue(cirrinaContextVariable.value)

    return fdlContextVariable
  }

  private fun parseCirrinaEventChannel(cirrinaEventChannel: EventChannel): EventFdl.EventChannel {
    return when (cirrinaEventChannel) {
      EventChannel.INTERNAL -> EventFdl.EventChannel.INTERNAL
      EventChannel.EXTERNAL -> EventFdl.EventChannel.EXTERNAL
      EventChannel.PERIPHERAL -> EventFdl.EventChannel.PERIPHERAL
    }
  }

  private fun parseCirrinaEvent(cirrinaEvent: Event): EventFdl.Event {
    // Checking if the event is valid
    cirrinaEvent.data.forEach {
      if (it.isLazy) error("event '${cirrinaEvent.topic}' has unevaluated data")
    }

    val fdlEvent: EventFdl.Event = EventFdl.Event()
    fdlEvent.topic = cirrinaEvent.topic
    fdlEvent.channel = parseCirrinaEventChannel(cirrinaEvent.channel)
    fdlEvent.data = cirrinaEvent.data.map { parseCirrinaContextVariable(it) }
    fdlEvent.target = cirrinaEvent.target
    fdlEvent.source = cirrinaEvent.source
    fdlEvent.id = cirrinaEvent.id
    fdlEvent.emittedTime = cirrinaEvent.emittedTime

    return fdlEvent
  }

  private fun parseFdlValue(fdlValue: ContextVariableFdl.Value): Any {
    return when (fdlValue.valueCase) {
      ContextVariableFdl.Value.ValueCase.INTEGER -> fdlValue.integer
      ContextVariableFdl.Value.ValueCase.FLOAT -> fdlValue.float
      ContextVariableFdl.Value.ValueCase.LONG -> fdlValue.long
      ContextVariableFdl.Value.ValueCase.DOUBLE -> fdlValue.double
      ContextVariableFdl.Value.ValueCase.STRING -> fdlValue.string
      ContextVariableFdl.Value.ValueCase.BOOL -> fdlValue.bool
      ContextVariableFdl.Value.ValueCase.BYTES -> fdlValue.bytes
      ContextVariableFdl.Value.ValueCase.ARRAY ->
        fdlValue.array.map { parseFdlValue(it) }.toTypedArray()
      ContextVariableFdl.Value.ValueCase.VALUE_LIST -> fdlValue.valueList.map { parseFdlValue(it) }
      ContextVariableFdl.Value.ValueCase.VALUE_MAP -> {
        val fdlMap: Map<ContextVariableFdl.Value, ContextVariableFdl.Value> = fdlValue.valueMap
        val cirrinaMap = HashMap<Any, Any>()
        fdlMap.forEach { entry ->
          cirrinaMap[parseFdlValue(entry.key)] = parseFdlValue(entry.value)
        }
        cirrinaMap
      }
    }
  }

  private fun parseFdlContextVariable(
    fdlContextVariable: ContextVariableFdl.ContextVariable
  ): ContextVariable {
    return ContextVariable(fdlContextVariable.name, parseFdlValue(fdlContextVariable.value))
  }

  private fun parseFdlEventChannel(fdlEventChannel: EventFdl.EventChannel): EventChannel {
    return when (fdlEventChannel) {
      EventFdl.EventChannel.INTERNAL -> EventChannel.INTERNAL
      EventFdl.EventChannel.EXTERNAL -> EventChannel.EXTERNAL
      EventFdl.EventChannel.PERIPHERAL -> EventChannel.PERIPHERAL
    }
  }

  private fun parseFdlEvent(fdlEvent: EventFdl.Event): Event {
    return Event(
      fdlEvent.topic,
      parseFdlEventChannel(fdlEvent.channel),
      fdlEvent.data.map { parseFdlContextVariable(it) },
      fdlEvent.target,
      fdlEvent.source,
      fdlEvent.id,
      fdlEvent.emittedTime,
    )
  }
}
