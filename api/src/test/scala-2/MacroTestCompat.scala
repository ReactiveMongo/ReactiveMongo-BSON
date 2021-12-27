trait MacroTestCompat { _: MacroTest.type =>

  case class WithImplicit1(pos: Int, text: String)(implicit x: Numeric[Int]) {
    def test = x
  }

  @com.github.ghik.silencer.silent
  case class WithImplicit2[N: Numeric](ident: String, value: N)
}
