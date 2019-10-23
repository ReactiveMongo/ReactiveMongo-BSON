package reactivemongo.api.bson.collection

import com.github.ghik.silencer.silent

import reactivemongo.api.{
  CollectionMetaCommands,
  DB,
  FailoverStrategy,
  ReadPreference
}
import reactivemongo.api.collections.{
  GenericCollection,
  GenericCollectionProducer
}

object `package` {
  implicit object CollectionProducer extends GenericCollectionProducer[BSONSerializationPack.type, BSONCollection] {
    def apply(db: DB, name: String, failoverStrategy: FailoverStrategy) = new BSONCollection(db, name, failoverStrategy, db.defaultReadPreference)
  }
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
