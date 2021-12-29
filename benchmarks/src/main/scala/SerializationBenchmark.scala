package reactivemongo
package api.bson

import reactivemongo.api.bson.buffer.{
  DefaultBufferHandler,
  ReadableBuffer,
  WritableBuffer
}

import org.openjdk.jmh.annotations._

sealed trait SerializationBenchmark {
  protected var value: BSONValue = _
  private var written: WritableBuffer = _

  protected var input: ReadableBuffer = _
  private var output: WritableBuffer = _

  protected def setupSerialization[B <: BSONValue](values: Seq[B]): Unit =
    values.headOption.foreach { v =>
      value = v

      written = DefaultBufferHandler.serialize(
        v,
        buffer =
          WritableBuffer.empty.writeBsonString("field").writeByte(value.code)
      )

    }

  @Setup(Level.Invocation)
  def setupInvocation(): Unit = {
    output = WritableBuffer.empty
    input = written.toReadableBuffer()
  }

  @Benchmark
  def write(): WritableBuffer =
    DefaultBufferHandler.serialize(value, output)

  @Benchmark
  @SuppressWarnings(Array("VarClosure" /*value*/ ))
  def read(): Unit = {
    val name = input.readBsonString()
    val result = DefaultBufferHandler.deserialize(input)

    assert(name == "field")
    assert(result.filter(_ == value).isSuccess)
  }
}

// ---

@State(Scope.Benchmark)
class BSONBooleanSerializationBenchmark extends SerializationBenchmark {

  @Setup(Level.Iteration)
  def setup(): Unit = setupSerialization(BSONValueFixtures.bsonBoolFixtures)

}

@State(Scope.Benchmark)
class BSONDateTimeSerializationBenchmark extends SerializationBenchmark {

  @Setup(Level.Iteration)
  def setup(): Unit = setupSerialization(BSONValueFixtures.bsonDateTimeFixtures)

}

@State(Scope.Benchmark)
class BSONDecimalSerializationBenchmark extends SerializationBenchmark {

  @Setup(Level.Iteration)
  def setup(): Unit = setupSerialization(BSONValueFixtures.bsonDecimalFixtures)

}

@State(Scope.Benchmark)
class BSONDocumentSerializationBenchmark extends SerializationBenchmark {

  @Setup(Level.Iteration)
  def setup(): Unit = setupSerialization(BSONValueFixtures.bsonDocFixtures)

}

@State(Scope.Benchmark)
class BSONDoubleSerializationBenchmark extends SerializationBenchmark {

  @Setup(Level.Iteration)
  def setup(): Unit = setupSerialization(BSONValueFixtures.bsonDoubleFixtures)

}

@State(Scope.Benchmark)
class BSONIntegerSerializationBenchmark extends SerializationBenchmark {

  @Setup(Level.Iteration)
  def setup(): Unit = setupSerialization(BSONValueFixtures.bsonIntFixtures)
}

@State(Scope.Benchmark)
class BSONLongSerializationBenchmark extends SerializationBenchmark {

  @Setup(Level.Iteration)
  def setup(): Unit = setupSerialization(BSONValueFixtures.bsonLongFixtures)
}

@State(Scope.Benchmark)
class BSONStringSerializationBenchmark extends SerializationBenchmark {

  @Setup(Level.Iteration)
  def setup(): Unit = setupSerialization(BSONValueFixtures.bsonStrFixtures)
}

@State(Scope.Benchmark)
class BSONObjectIDSerializationBenchmark extends SerializationBenchmark {

  @Setup(Level.Iteration)
  def setup(): Unit = setupSerialization(BSONValueFixtures.bsonOidFixtures)
}

@State(Scope.Benchmark)
class BSONJavascriptSerializationBenchmark extends SerializationBenchmark {

  @Setup(Level.Iteration)
  def setup(): Unit = setupSerialization(BSONValueFixtures.bsonJSFixtures)
}

@State(Scope.Benchmark)
class BSONJavascriptWsSerializationBenchmark extends SerializationBenchmark {

  @Setup(Level.Iteration)
  def setup(): Unit = setupSerialization(BSONValueFixtures.bsonJSWsFixtures)
}

@State(Scope.Benchmark)
class BSONTimestampSerializationBenchmark extends SerializationBenchmark {

  @Setup(Level.Iteration)
  def setup(): Unit = setupSerialization(BSONValueFixtures.bsonTsFixtures)
}

@State(Scope.Benchmark)
class BSONBinarySerializationBenchmark extends SerializationBenchmark {

  @Setup(Level.Iteration)
  def setup(): Unit = setupSerialization(BSONValueFixtures.bsonBinFixtures)
}

@State(Scope.Benchmark)
class BSONRegexSerializationBenchmark extends SerializationBenchmark {

  @Setup(Level.Iteration)
  def setup(): Unit = setupSerialization(BSONValueFixtures.bsonRegexFixtures)
}
