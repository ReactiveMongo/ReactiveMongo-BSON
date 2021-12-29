import scala.deriving.Mirror

trait MacroTestCompat { _self: MacroTest.type =>
  given Conversion[Union.UB, Tuple1[String]] = (ub: Union.UB) => Tuple(ub.s)

  implicit object ProductOfUB extends Mirror.Product {
    type MirroredType = Union.UB
    type MirroredElemTypes = Tuple1[String]
    type MirroredMonoType = Union.UB
    type MirroredLabel = "UB"
    type MirroredElemLabels = Tuple1["s"]

    def fromProduct(p: Product): MirroredMonoType =
      new Union.UB(p.productElement(0).asInstanceOf[String])
  }
}
