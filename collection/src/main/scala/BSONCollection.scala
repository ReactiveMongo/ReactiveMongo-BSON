package reactivemongo.api.bson.collection

//import scala.concurrent.{ ExecutionContext, Future }

import com.github.ghik.silencer.silent

import reactivemongo.api.{
  CollectionMetaCommands,
  DB,
  FailoverStrategy,
  ReadPreference
}
import reactivemongo.api.collections.{
  //BatchCommands,
  GenericCollection
  //GenericCollectionProducer
  //GenericQueryBuilder
}
//import reactivemongo.api.commands.WriteConcern
//import reactivemongo.util.option

object `package` {
  /*
  implicit object JSONCollectionProducer extends GenericCollectionProducer[JSONSerializationPack.type, JSONCollection] {
    def apply(db: DB, name: String, failoverStrategy: FailoverStrategy) = new JSONCollection(db, name, failoverStrategy, db.defaultReadPreference)
  }
   */
}

/**
 * A Collection that interacts with the BSON library.
 */
final class BSONCollection(
  val db: DB,
  val name: String,
  val failoverStrategy: FailoverStrategy,
  override val readPreference: ReadPreference) extends GenericCollection[BSONSerializationPack.type]
  with CollectionMetaCommands {

  val pack = BSONSerializationPack

  @silent
  val BatchCommands = BSONBatchCommands

  def withReadPreference(pref: ReadPreference): BSONCollection =
    new BSONCollection(db, name, failoverStrategy, pref)
}

// BSON extension for cursors

/*
import reactivemongo.api.{
  Cursor,
  FlattenedCursor,
  WrappedCursor
}

/** Implicits of the JSON extensions for cursors. */
object JsCursor {
  import reactivemongo.api.{ CursorFlattener, CursorProducer }

  /** Provides JSON instances for CursorProducer typeclass. */
  implicit def cursorProducer[T: Writes] = new CursorProducer[T] {
    type ProducedCursor = JsCursor[T]

    // Returns a cursor with JSON operations.
    def produce(base: Cursor.WithOps[T]): JsCursor[T] =
      new JsCursorImpl[T](base)
  }

  /** Provides flattener for JSON cursor. */
  implicit object cursorFlattener extends CursorFlattener[JsCursor] {
    def flatten[T](future: Future[JsCursor[T]]): JsCursor[T] =
      new JsFlattenedCursor(future)
  }
}
 */
