package at.ac.uibk.dps.cirrina.execution.util

import at.ac.uibk.dps.cirrina.csm.Csml.EventChannel
import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.`object`.Event
import at.ac.uibk.dps.cirrina.fory.exchange.ForyDescriptorProtos
import org.apache.fory.Fory
import org.apache.fory.ThreadSafeFory
import org.apache.fory.config.Language
import org.apache.fory.memory.MemoryBuffer

object Serializer {
  private val fory: ThreadSafeFory =
    Fory.builder().withLanguage(Language.XLANG).buildThreadSafeFory().apply {
      register(ForyDescriptorProtos.ContextVariable::class.java)
      register(ForyDescriptorProtos.Event::class.java)
      register(ForyDescriptorProtos.EventChannel::class.java)
      register(ForyDescriptorProtos.Value::class.java)
    }

  private val threadBuffer = ThreadLocal.withInitial { MemoryBuffer.newHeapBuffer(1024) }

  fun serializeValues(obj: Any?): ByteArray {

    // Parsing Cirrina data types into Fory-generated data types
    val objToSerialize: Any? =
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
  fun <T> deserializeValues(bytes: ByteArray, deserializationClass: Class<T>): T? {
    val deserializedObj: T =
      when (deserializationClass) {
        ContextVariable::class.java ->
          parseFdlContextVariable(fory.deserialize(bytes) as ForyDescriptorProtos.ContextVariable)
        Event::class.java -> parseFdlEvent(fory.deserialize(bytes) as ForyDescriptorProtos.Event)
        EventChannel::class.java ->
          parseFdlEventChannel(fory.deserialize(bytes) as ForyDescriptorProtos.EventChannel)
        else -> parseFdlValue(fory.deserialize(bytes) as ForyDescriptorProtos.Value?)
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
          val fdlList = deserializedObj as List<ForyDescriptorProtos.ContextVariable>
          fdlList.map { parseFdlContextVariable(it) }
        }
        Event::class.java -> {
          val fdlList = deserializedObj as List<ForyDescriptorProtos.Event>
          fdlList.map { parseFdlEvent(it) }
        }
        else -> {
          val fdlList = deserializedObj as List<ForyDescriptorProtos.EventChannel>
          fdlList.map { parseFdlEventChannel(it) }
        }
      }
        as List<T>

    return deserializedCollection
  }

  @Suppress("UNCHECKED_CAST")
  private fun parseCirrinaValue(cirrinaValue: Any?): ForyDescriptorProtos.Value? {
    return when (cirrinaValue) {
      is Int -> ForyDescriptorProtos.Value.ofInteger(cirrinaValue)
      is Float -> ForyDescriptorProtos.Value.ofFloat(cirrinaValue)
      is Long -> ForyDescriptorProtos.Value.ofLong(cirrinaValue)
      is Double -> ForyDescriptorProtos.Value.ofDouble(cirrinaValue)
      is String -> ForyDescriptorProtos.Value.ofString(cirrinaValue)
      is Boolean -> ForyDescriptorProtos.Value.ofBool(cirrinaValue)
      is ByteArray -> ForyDescriptorProtos.Value.ofBytes(cirrinaValue)
      is Array<*> ->
        ForyDescriptorProtos.Value.ofArray(cirrinaValue.map { parseCirrinaValue(it!!) })
      is List<*> ->
        ForyDescriptorProtos.Value.ofValueList(cirrinaValue.map { parseCirrinaValue(it!!) })
      is Map<*, *> -> {
        val fdlMap = HashMap<ForyDescriptorProtos.Value?, ForyDescriptorProtos.Value?>()
        val cirrinaMap = cirrinaValue as Map<Any?, Any?>
        cirrinaMap.forEach { entry ->
          fdlMap[parseCirrinaValue(entry.key)] = parseCirrinaValue(entry.value)
        }
        ForyDescriptorProtos.Value.ofValueMap(fdlMap)
      }
      null -> null
      else -> error("value type could not be converted to fory")
    }
  }

  private fun parseCirrinaContextVariable(
    cirrinaContextVariable: ContextVariable
  ): ForyDescriptorProtos.ContextVariable {
    val fdlContextVariable: ForyDescriptorProtos.ContextVariable =
      ForyDescriptorProtos.ContextVariable()
    fdlContextVariable.name = cirrinaContextVariable.name
    fdlContextVariable.value = parseCirrinaValue(cirrinaContextVariable.value)

    return fdlContextVariable
  }

  private fun parseCirrinaEventChannel(
    cirrinaEventChannel: EventChannel
  ): ForyDescriptorProtos.EventChannel {
    return when (cirrinaEventChannel) {
      EventChannel.INTERNAL -> ForyDescriptorProtos.EventChannel.INTERNAL
      EventChannel.EXTERNAL -> ForyDescriptorProtos.EventChannel.EXTERNAL
      EventChannel.PERIPHERAL -> ForyDescriptorProtos.EventChannel.PERIPHERAL
    }
  }

  private fun parseCirrinaEvent(cirrinaEvent: Event): ForyDescriptorProtos.Event {
    // Checking if the event is valid
    cirrinaEvent.data.forEach {
      if (it.isLazy) error("event '${cirrinaEvent.topic}' has unevaluated data")
    }

    val fdlEvent: ForyDescriptorProtos.Event = ForyDescriptorProtos.Event()
    fdlEvent.topic = cirrinaEvent.topic
    fdlEvent.channel = parseCirrinaEventChannel(cirrinaEvent.channel)
    fdlEvent.data = cirrinaEvent.data.map { parseCirrinaContextVariable(it) }
    fdlEvent.target = cirrinaEvent.target
    fdlEvent.source = cirrinaEvent.source
    fdlEvent.id = cirrinaEvent.id
    fdlEvent.emittedTime = cirrinaEvent.emittedTime

    return fdlEvent
  }

  private fun parseFdlValue(fdlValue: ForyDescriptorProtos.Value?): Any? {
    if (fdlValue == null) return null

    return when (fdlValue.valueCase) {
      ForyDescriptorProtos.Value.ValueCase.INTEGER -> fdlValue.integer
      ForyDescriptorProtos.Value.ValueCase.FLOAT -> fdlValue.float
      ForyDescriptorProtos.Value.ValueCase.LONG -> fdlValue.long
      ForyDescriptorProtos.Value.ValueCase.DOUBLE -> fdlValue.double
      ForyDescriptorProtos.Value.ValueCase.STRING -> fdlValue.string
      ForyDescriptorProtos.Value.ValueCase.BOOL -> fdlValue.bool
      ForyDescriptorProtos.Value.ValueCase.BYTES -> fdlValue.bytes
      ForyDescriptorProtos.Value.ValueCase.ARRAY ->
        fdlValue.array.map { parseFdlValue(it) }.toTypedArray()
      ForyDescriptorProtos.Value.ValueCase.VALUE_LIST ->
        fdlValue.valueList.map { parseFdlValue(it) }
      ForyDescriptorProtos.Value.ValueCase.VALUE_MAP -> {
        val fdlMap: Map<ForyDescriptorProtos.Value, ForyDescriptorProtos.Value> = fdlValue.valueMap
        val cirrinaMap = HashMap<Any?, Any?>()
        fdlMap.forEach { entry ->
          cirrinaMap[parseFdlValue(entry.key)] = parseFdlValue(entry.value)
        }
        cirrinaMap
      }
    }
  }

  private fun parseFdlContextVariable(
    fdlContextVariable: ForyDescriptorProtos.ContextVariable
  ): ContextVariable {
    return ContextVariable(fdlContextVariable.name, parseFdlValue(fdlContextVariable.value))
  }

  private fun parseFdlEventChannel(
    fdlEventChannel: ForyDescriptorProtos.EventChannel
  ): EventChannel {
    return when (fdlEventChannel) {
      ForyDescriptorProtos.EventChannel.INTERNAL -> EventChannel.INTERNAL
      ForyDescriptorProtos.EventChannel.EXTERNAL -> EventChannel.EXTERNAL
      ForyDescriptorProtos.EventChannel.PERIPHERAL -> EventChannel.PERIPHERAL
    }
  }

  private fun parseFdlEvent(fdlEvent: ForyDescriptorProtos.Event): Event {
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
