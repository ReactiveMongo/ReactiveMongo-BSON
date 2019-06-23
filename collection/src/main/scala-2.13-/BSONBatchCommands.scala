package reactivemongo.api.bson.collection

import scala.util.{ Try, Failure }

import reactivemongo.api.collections.BatchCommands

import reactivemongo.api.commands.{
  CountCommand => CC,
  DeleteCommand => DC,
  InsertCommand => IC,
  DistinctCommand => DistC,
  ResolvedCollectionCommand,
  UpdateCommand => UC
}

import reactivemongo.api.bson._ // TODO

@deprecated("BatchCommands will be removed from the API", "0.17.0")
object BSONBatchCommands
  extends BatchCommands[BSONSerializationPack.type] { commands =>

  val pack = BSONSerializationPack

  object BSONCountCommand extends CC[BSONSerializationPack.type] {
    val pack = commands.pack
  }
  val CountCommand = BSONCountCommand

  private def deprecated[T]: Try[T] =
    Failure(new UnsupportedOperationException("Deprecated"))

  object CountWriter extends BSONDocumentWriter[ResolvedCollectionCommand[CountCommand.Count]] {
    def writeTry(cmd: ResolvedCollectionCommand[CountCommand.Count]) = deprecated
  }

  object CountResultReader
    extends BSONDocumentReader[CountCommand.CountResult] {
    def readDocument(doc: BSONDocument) = deprecated
  }

  object BSONDistinctCommand extends DistC[BSONSerializationPack.type] {
    val pack = commands.pack
  }
  val DistinctCommand = BSONDistinctCommand

  object DistinctWriter
    extends BSONDocumentWriter[ResolvedCollectionCommand[DistinctCommand.Distinct]] {
    def writeTry(cmd: ResolvedCollectionCommand[DistinctCommand.Distinct]) = deprecated
  }

  object DistinctResultReader extends BSONDocumentReader[DistinctCommand.DistinctResult] {
    def readDocument(doc: BSONDocument) = deprecated
  }

  object BSONInsertCommand extends IC[BSONSerializationPack.type] {
    val pack = commands.pack
  }
  val InsertCommand = BSONInsertCommand

  object InsertWriter extends BSONDocumentWriter[ResolvedCollectionCommand[InsertCommand.Insert]] {
    def writeTry(cmd: ResolvedCollectionCommand[InsertCommand.Insert]) = deprecated
  }

  object BSONUpdateCommand extends UC[BSONSerializationPack.type] {
    val pack = commands.pack
  }
  val UpdateCommand = BSONUpdateCommand

  object UpdateWriter extends BSONDocumentWriter[ResolvedCollectionCommand[UpdateCommand.Update]] {
    def writeTry(cmd: ResolvedCollectionCommand[UpdateCommand.Update]) = deprecated
  }

  object UpdateReader extends BSONDocumentReader[UpdateCommand.UpdateResult] {
    def readDocument(doc: BSONDocument) = deprecated
  }

  object BSONDeleteCommand extends DC[BSONSerializationPack.type] {
    val pack = commands.pack
  }
  val DeleteCommand = BSONDeleteCommand

  object DeleteWriter extends BSONDocumentWriter[ResolvedCollectionCommand[DeleteCommand.Delete]] {
    def writeTry(cmd: ResolvedCollectionCommand[DeleteCommand.Delete]) = deprecated
  }

  import reactivemongo.api.commands.{
    AggregationFramework => AggFramework,
    FindAndModifyCommand => FindAndModifyCmd
  }

  object BSONFindAndModifyCommand
    extends FindAndModifyCmd[BSONSerializationPack.type] {
    val pack: BSONSerializationPack.type = BSONSerializationPack
  }
  val FindAndModifyCommand = BSONFindAndModifyCommand

  object FindAndModifyWriter extends BSONDocumentWriter[ResolvedCollectionCommand[FindAndModifyCommand.FindAndModify]] {
    def writeTry(cmd: ResolvedCollectionCommand[FindAndModifyCommand.FindAndModify]) = deprecated
  }

  object FindAndModifyReader
    extends BSONDocumentReader[FindAndModifyCommand.FindAndModifyResult] {
    def readDocument(doc: BSONDocument) = deprecated
  }

  object BSONAggregationFramework extends AggFramework[BSONSerializationPack.type] {
    val pack: BSONSerializationPack.type = BSONSerializationPack
  }

  val AggregationFramework = BSONAggregationFramework

  object AggregateWriter extends BSONDocumentWriter[ResolvedCollectionCommand[AggregationFramework.Aggregate]] {
    def writeTry(cmd: ResolvedCollectionCommand[AggregationFramework.Aggregate]) = deprecated
  }

  object AggregateReader extends BSONDocumentReader[AggregationFramework.AggregationResult] {
    def readDocument(doc: BSONDocument) = deprecated
  }
}
