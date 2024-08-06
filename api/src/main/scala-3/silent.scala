package com.github.ghik.silencer

class silent(s: String = "") extends scala.annotation.StaticAnnotation {
  val _ = s
}
