package reactivemongo.api.bson

/**
 * Implicit conversions for handler & values types
 * between `org.bson` and `reactivemongo.api.bson` .
 *
 * {{{
 * import scala.reflect.ClassTag
 *
 * import reactivemongo.api.bson.BSONWriter
 * import reactivemongo.api.bson.msb._
 *
 * def writerToEncoder[T](w: BSONWriter[T])(implicit ct: ClassTag[T]) = {
 *   // ClassTag required for generics
 *
 *   val enc: org.bson.codecs.Encoder[T] = w
 *   enc
 * }
 *
 * // From org.bson
 * import reactivemongo.api.bson.{ BSONDouble, BSONString, BSONValue }
 *
 * val newStr: BSONString = new org.bson.BsonString("foo")
 * val newVal: BSONValue = new org.bson.BsonInt32(2)
 *
 * // To org.bson
 * val oldStr: org.bson.BsonString = BSONString("bar")
 * val oldVal: org.bson.BsonValue = BSONDouble(1.2D)
 * }}}
 *
 * For more specific imports, see [[ValueConverters]]
 * and [[HandlerConverters]] .
 */
package object msb extends ValueConverters with HandlerConverters
