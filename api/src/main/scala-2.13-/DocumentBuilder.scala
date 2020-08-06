package reactivemongo.api.bson

import scala.collection.mutable.{ Builder => MBuilder }

private[bson] final class DocumentBuilder
  extends MBuilder[ElementProducer, BSONDocument] {

  private val elms = Seq.newBuilder[BSONElement]
  private val fs = Map.newBuilder[String, BSONValue]

  def +=(elem: ElementProducer): this.type = {
    val es = elem.generate()

    elms ++= es
    fs ++= es.map(e => e.name -> e.value)

    this
  }

  override def ++=(xs: TraversableOnce[ElementProducer]): this.type = {
    xs.foreach { producer =>
      val es = producer.generate()

      elms ++= es
      fs ++= es.map(e => e.name -> e.value)
    }

    this
  }

  def clear(): Unit = {
    elms.clear()
    fs.clear()
  }

  def result(): BSONDocument =
    BSONDocument(elms.result(), fs.result())
}

