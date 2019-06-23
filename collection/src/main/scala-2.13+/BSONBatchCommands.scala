package reactivemongo.api.bson.collection

import scala.util.{ Try, Failure }

import reactivemongo.api.collections.BatchCommands

import reactivemongo.api.commands.{
  CountCommand => CC,
  DeleteCommand => DC,
  InsertCommand => IC,
  ResolvedCollectionCommand,
  UpdateCommand => UC
}

import reactivemongo.api.bson._ // TODO

@deprecated("BatchCommands will be removed from the API", "0.17.0")
object BSONBatchCommands
  extends BatchCommands[BSONSerializationPack.type] { commands =>

  val pack = BSONSerializationPack

  object CountCommand extends CC[BSONSerializationPack.type] {
    val pack = commands.pack
  }

  private def deprecated[T]: Try[T] =
    Failure(new UnsupportedOperationException("Deprecated"))

  object CountWriter extends BSONDocumentWriter[ResolvedCollectionCommand[CountCommand.Count]] {
    def writeTry(cmd: ResolvedCollectionCommand[CountCommand.Count]) = deprecated
  }

  object CountResultReader
    extends BSONDocumentReader[CountCommand.CountResult] {
    def readDocument(doc: BSONDocument) = deprecated
  }

  object InsertCommand extends IC[BSONSerializationPack.type] {
    val pack = commands.pack
  }

  object InsertWriter extends BSONDocumentWriter[ResolvedCollectionCommand[InsertCommand.Insert]] {
    def writeTry(cmd: ResolvedCollectionCommand[InsertCommand.Insert]) = deprecated
  }

  object UpdateCommand extends UC[BSONSerializationPack.type] {
    val pack = commands.pack
  }

  object UpdateWriter extends BSONDocumentWriter[ResolvedCollectionCommand[UpdateCommand.Update]] {
    def writeTry(cmd: ResolvedCollectionCommand[UpdateCommand.Update]) = deprecated
  }

  object UpdateReader extends BSONDocumentReader[UpdateCommand.UpdateResult] {
    def readDocument(doc: BSONDocument) = deprecated
  }

  object DeleteCommand extends DC[BSONSerializationPack.type] {
    val pack = commands.pack
  }

  object DeleteWriter extends BSONDocumentWriter[ResolvedCollectionCommand[DeleteCommand.Delete]] {
    def writeTry(cmd: ResolvedCollectionCommand[DeleteCommand.Delete]) = deprecated
  }

  import reactivemongo.api.commands.{
    AggregationFramework => AggFramework,
    FindAndModifyCommand => FindAndModifyCmd
  }

  object FindAndModifyCommand
    extends FindAndModifyCmd[BSONSerializationPack.type] {
    val pack: BSONSerializationPack.type = BSONSerializationPack
  }

  object FindAndModifyWriter extends BSONDocumentWriter[ResolvedCollectionCommand[FindAndModifyCommand.FindAndModify]] {
    def writeTry(cmd: ResolvedCollectionCommand[FindAndModifyCommand.FindAndModify]) = deprecated
  }

  object FindAndModifyReader
    extends BSONDocumentReader[FindAndModifyCommand.FindAndModifyResult] {
    def readDocument(doc: BSONDocument) = deprecated
  }

  object AggregationFramework extends AggFramework[BSONSerializationPack.type] {
    val pack: BSONSerializationPack.type = BSONSerializationPack
  }

  object AggregateWriter extends BSONDocumentWriter[ResolvedCollectionCommand[AggregationFramework.Aggregate]] {
    def writeTry(cmd: ResolvedCollectionCommand[AggregationFramework.Aggregate]) = deprecated
  }

  object AggregateReader extends BSONDocumentReader[AggregationFramework.AggregationResult] {
    def readDocument(doc: BSONDocument) = deprecated
  }
}
