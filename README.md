# ReactiveMongo Biːsən

BSON libraries for ReactiveMongo

## Motivation

These libraries are intended to replace (at some point after release 1.0) the BSON library currently shipped along with ReactiveMongo driver.

It will fix some issues, bring multiple API and performance improvements (simpler & better).

## Usage

The main API library migrates both the BSON values types (with same names; see [example](api/src/test/scala/BSONValueFixtures.scala)) and the handler typeclasses (reader/writer; see [example](api/src/test/scala/HandlerSpec.scala)).

> *Note:* The package of the new library is `reactivemongo.api.bson` (instead of `reactivemongo.bson`).

It can already be used in your `build.sbt`:

```ocaml
libraryDependencies += "org.reactivemongo" %% "reactivemongo-bson-api" % VERSION)
```

[![Maven](https://img.shields.io/maven-central/v/org.reactivemongo/reactivemongo-bson-api_2.12.svg)](http://search.maven.org/#search%7Cga%7C1%7Creactivemongo-bson-api) [![Javadocs](https://javadoc.io/badge/org.reactivemongo/reactivemongo-bson-api_2.12.svg)](https://javadoc.io/doc/org.reactivemongo/reactivemongo-bson-api_2.12)

```ocaml
import reactivemongo.api.bson._
```

- [Documentation](http://reactivemongo.org/releases/0.1x/documentation/tutorial/bison.html)
- [Examples](api/src/test/scala/BSONValueFixtures.scala)

This refactoring also includes the following optional libraries.

**compat:**

The compatibility library, that provides conversions between the previous and the new APIs. It can be configured in the `build.sbt` as below.

```ocaml
libraryDependencies += "org.reactivemongo" %% "reactivemongo-bson-compat" % VERSION
```

Then the conversions can be imported:

```ocaml
import reactivemongo.api.bson.compat._
```

- [Documentation](https://oss.sonatype.org/service/local/repositories/releases/archive/org/reactivemongo/reactivemongo-bson-compat_2.12/0.18.5/reactivemongo-bson-compat_2.12-0.18.5-javadoc.jar/!/reactivemongo/api/bson/compat/index.html)
- [Examples](compat/src/test/scala/ValueConverterSpec.scala)

**msb-compat:**

The compatiblity library for `org.bson`, that provides conversions between this package and the new BSON API. It can be configured in the `build.sbt` as below.

```ocaml
libraryDependencies += "org.reactivemongo" %% "reactivemongo-bson-msb-compat" % VERSION
```

Then the conversions can be imported:

```ocaml
import reactivemongo.api.bson.msb._
```

- [Documentation](https://oss.sonatype.org/service/local/repositories/releases/archive/org/reactivemongo/reactivemongo-bson-msb_2.12/0.18.5/reactivemongo-bson-msb_2.12-0.18.5-javadoc.jar/!/reactivemongo/api/bson/msb/index.html)
- [Examples](msb-compat/src/test/scala/ValueConverterSpec.scala)

**geo:**

The [GeoJSON](https://docs.mongodb.com/manual/reference/geojson/) library, that provides the geometry types and the handlers to read from and write to appropriate BSON representation.

It can be configured in the `build.sbt` as below.

```ocaml
libraryDependencies += "org.reactivemongo" %% "reactivemongo-bson-geo" % VERSION
```

- [Documentation](https://oss.sonatype.org/service/local/repositories/releases/archive/org/reactivemongo/reactivemongo-bson-geo_2.12/0.18.5/reactivemongo-bson-geo_2.12-0.18.5-javadoc.jar/!/reactivemongo/api/bson/geo/index.html)
- [Examples](geo/src/test/scala/GeometrySpec.scala)

**specs2:**

The Specs2 library provides utilities to write tests using [specs2](https://etorreborre.github.io/specs2/) with BSON values.

It can be configured in the `build.sbt` as below.

```ocaml
libraryDependencies += "org.reactivemongo" %% "reactivemongo-bson-specs2" % VERSION
```

```scala
import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.specs2._

final class MySpec extends org.specs2.mutable.Specification {
  "Foo" title

  "Bar" should {
    "lorem" in {
      BSONDocument("ipsum" -> 1) must_=== BSONDocument("dolor" -> 2)
      // Use provided Diffable to display difference
      // between actual and expected documents
    }
  }
}
```

- [Documentation](https://oss.sonatype.org/service/local/repositories/releases/archive/org/reactivemongo/reactivemongo-bson-geo_2.12/0.18.5/reactivemongo-bson-geo_2.12-0.18.5-javadoc.jar/!/reactivemongo/api/bson/geo/index.html)
- [Examples](specs2/src/test/scala/DiffableSpec.scala)

**monocle:** EXPERIMENTAL

The library that provides [Monocle](http://julien-truffaut.github.io/Monocle/) utilities for BSON values. It can be configured in the `build.sbt` as below.

```ocaml
libraryDependencies += "org.reactivemongo" %% "reactivemongo-bson-monocle" % VERSION
```

Then the utilities can be imported:

```ocaml
import reactivemongo.api.bson.monocle._
```

- [Documentation](https://oss.sonatype.org/service/local/repositories/releases/archive/org/reactivemongo/reactivemongo-bson-monocle_2.12/0.18.5/reactivemongo-bson-monocle_2.12-0.18.5-javadoc.jar/!/reactivemongo/api/bson/monocle/index.html)
- [Examples](monocle/src/test/scala/MonocleSpec.scala)

## Build manually

ReactiveMongo BSON libraries can be built from this source repository.

    sbt publishLocal

To run the tests, use:

    sbt test

[Travis](https://travis-ci.org/ReactiveMongo/ReactiveMongo-BSON): ![Travis build status](https://travis-ci.org/ReactiveMongo/ReactiveMongo-BSON.png?branch=master)
