package reactivemongo.api.bson

import scala.annotation.{ StaticAnnotation, meta }

private[bson] trait MacroAnnotations { macros: Macros.type =>

  /** Annotations to use on case classes that are being processed by macros. */
  object Annotations {
    /**
     * Specify a key different from field name in your case class.
     * Convenient to use when you'd like to leverage mongo's `_id` index
     * but don't want to actually use `_id` in your code.
     *
     * {{{
     * import reactivemongo.api.bson.Macros.Annotations.Key
     *
     * case class Website(@Key("_id") url: String)
     * }}}
     *
     * Generated handler will map the `url` field in your code
     * to `_id` field in BSON
     *
     * @param key the desired key to use in BSON
     */
    @meta.param
    final class Key(val key: String) extends StaticAnnotation {
      override def equals(that: Any): Boolean = that match {
        case other: this.type => this.key == other.key
        case _ => false
      }

      override def hashCode: Int = key.hashCode
    }

    /**
     * Indicates that the annotated field must not be serialized to BSON.
     * Annotation `@transient` can also be used to achieve the same purpose.
     *
     * If the annotate field must be read, a default value must be defined,
     * either from the field default value,
     * or using the annotation [[DefaultValue]] (specific to BSON).
     */
    @meta.param
    final class Ignore extends StaticAnnotation {
      override def hashCode: Int = 1278101060

      override def equals(that: Any): Boolean = that match {
        case _: this.type => true
        case _ => false
      }
    }

    /**
     * Indicates that if a property is represented as a document itself,
     * the document fields are directly included in top document,
     * rather than nesting it.
     *
     * {{{
     * import reactivemongo.api.bson.Macros.Annotations.Flatten
     *
     * case class Range(start: Int, end: Int)
     *
     * case class LabelledRange(
     *   name: String,
     *   @Flatten range: Range)
     *
     * val flattened = reactivemongo.api.bson.BSONDocument(
     *   "name" -> "foo", "start" -> 0, "end" -> 1)
     *
     * // Rather than:
     * // BSONDocument("name" -> "foo", "range" -> BSONDocument(
     * //   "start" -> 0, "end" -> 1))
     * }}}
     */
    @meta.param
    final class Flatten extends StaticAnnotation {
      override def hashCode: Int = 488571557

      override def equals(that: Any): Boolean = that match {
        case _: this.type => true
        case _ => false
      }
    }

    /**
     * Indicates that if an `Option` property is empty,
     * it will be represented by `BSONNull` rather than being omitted.
     *
     * {{{
     * import reactivemongo.api.bson.Macros.Annotations.NoneAsNull
     *
     * case class Foo(
     *   title: String,
     *   @NoneAsNull description: Option[String])
     * }}}
     */
    @meta.param
    final class NoneAsNull extends StaticAnnotation {
      override def hashCode: Int = 1667526726

      override def equals(that: Any): Boolean = that match {
        case _: this.type => true
        case _ => false
      }
    }

    /**
     * Indicates a default value for a class property,
     * when there is no corresponding BSON value when reading.
     *
     * It enables a behaviour similar to [[MacroOptions.ReadDefaultValues]],
     * without requiring the define a global default value for the property.
     *
     * {{{
     * import reactivemongo.api.bson.BSONDocument
     * import reactivemongo.api.bson.Macros,
     *   Macros.Annotations.DefaultValue
     *
     * case class Foo(
     *   title: String,
     *   @DefaultValue(1.23D) score: Double)
     *
     * val reader = Macros.reader[Foo]
     *
     * reader.readTry(BSONDocument("title" -> "Bar")) // No BSON 'score'
     * // Success: Foo(title = "Bar", score = 1.23D)
     * }}}
     */
    @meta.param
    final class DefaultValue[T](val value: T) extends StaticAnnotation {
      @inline override def hashCode: Int = value.hashCode

      @SuppressWarnings(Array("ComparingUnrelatedTypes"))
      override def equals(that: Any): Boolean = that match {
        case other: this.type =>
          this.value == other.value

        case _ => false
      }
    }

    /**
     * Indicates a BSON reader to be used for a specific property,
     * possibly overriding the default one from the implicit scope.
     *
     * {{{
     * import reactivemongo.api.bson.{
     *   BSONDocument, BSONDouble, BSONString, BSONReader
     * }
     * import reactivemongo.api.bson.Macros,
     *   Macros.Annotations.Reader
     *
     * val scoreReader: BSONReader[Double] = BSONReader.collect[Double] {
     *   case BSONString(v) => v.toDouble
     *   case BSONDouble(b) => b
     * }
     *
     * case class Foo(
     *   title: String,
     *   @Reader(scoreReader) score: Double)
     *
     * val reader = Macros.reader[Foo]
     *
     * reader.readTry(BSONDocument(
     *   "title" -> "Bar",
     *   "score" -> "1.23" // accepted by annotated scoreReader
     * ))
     * // Success: Foo(title = "Bar", score = 1.23D)
     * }}}
     */
    @meta.param
    final class Reader[T](val reader: BSONReader[T]) extends StaticAnnotation {
      @inline override def hashCode: Int = reader.hashCode

      @SuppressWarnings(Array("ComparingUnrelatedTypes"))
      override def equals(that: Any): Boolean = that match {
        case other: this.type =>
          this.reader == other.reader

        case _ => false
      }
    }

    /**
     * Indicates a BSON writer to be used for a specific property,
     * possibly overriding the default one from the implicit scope.
     *
     * {{{
     * import reactivemongo.api.bson.{ BSONString, BSONWriter }
     * import reactivemongo.api.bson.Macros,
     *   Macros.Annotations.Writer
     *
     * val scoreWriter: BSONWriter[Double] = BSONWriter[Double] { d =>
     *   BSONString(d.toString) // write double as string
     * }
     *
     * case class Foo(
     *   title: String,
     *   @Writer(scoreWriter) score: Double)
     *
     * val writer = Macros.writer[Foo]
     *
     * writer.writeTry(Foo(title = "Bar", score = 1.23D))
     * // Success: BSONDocument("title" -> "Bar", "score" -> "1.23")
     * }}}
     */
    @meta.param
    final class Writer[T](val writer: BSONWriter[T]) extends StaticAnnotation {
      @inline override def hashCode: Int = writer.hashCode

      @SuppressWarnings(Array("ComparingUnrelatedTypes"))
      override def equals(that: Any): Boolean = that match {
        case other: this.type =>
          this.writer == other.writer

        case _ => false
      }
    }
  }
}
