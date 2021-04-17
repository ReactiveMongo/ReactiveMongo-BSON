package reactivemongo.api.bson

import scala.collection.mutable.{ Builder => MBuilder }

private[bson] final class DocumentBuilder
    extends MBuilder[ElementProducer, BSONDocument] {

  private val elms = Seq.newBuilder[BSONElement]
  private val fs = Map.newBuilder[String, BSONValue]

  def addOne(elem: ElementProducer): this.type = {
    val es = elem.generate()

    elms ++= es
    fs ++= es.map(e => e.name -> e.value)

    this
  }

  override def addAll(xs: IterableOnce[ElementProducer]): this.type = {
    xs.iterator.foreach { producer =>
      val es = producer.generate()

      elms ++= es
      fs ++= es.map(e => e.name -> e.value)
    }

    this
  }

  override def knownSize: Int = elms.knownSize

  override def sizeHint(size: Int): Unit = {
    elms.sizeHint(size)
    fs.sizeHint(size)
  }

  def clear(): Unit = {
    elms.clear()
    fs.clear()
  }

  def result(): BSONDocument =
    BSONDocument(elms.result(), fs.result())
}
