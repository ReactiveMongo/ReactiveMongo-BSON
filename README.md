# ReactiveMongo Biːsən

BSON libraries for ReactiveMongo

## Motivation

These libraries are intended to replace (at some point after release 1.0) the BSON library currently shipped along with ReactiveMongo driver.

It will fix some issues, bring multiple API and performance improvements (simpler & better).

> TODO: Details & publish benchmarks

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

This refactoring also includes the following optional libraries.

**compat:**

The compatibility library, that provides conversions between the current and the future APIs. It can be configured in the `build.sbt` as below.

```ocaml
libraryDependencies += "org.reactivemongo" %% "reactivemongo-bson-compat" % VERSION
```

Then the conversions can be imported:

```ocaml
import reactivemongo.api.bson.compat._
```

> See [examples](compat/src/test/scala/ValueConverterSpec.scala)

**collection:**

The library providing collection references, with operations using the BSON values from the new API.

> See [examples](collection/src/test/scala/CollectionSpec.scala)

**msb-compat:**

The compatiblity library for `org.bson`, that provides conversions between this package and the future BSON API. It can be configured in the `build.sbt` as below.

```ocaml
libraryDependencies += "org.reactivemongo" %% "reactivemongo-bson-msb-compat" % VERSION
```

Then the conversions can be imported:

```ocaml
import reactivemongo.api.bson.msb._
```

> See [examples](msb-compat/src/test/scala/ValueConverterSpec.scala)

## Build manually

ReactiveMongo BSON libraries can be built from this source repository.

    sbt publishLocal

To run the tests, use:

    sbt test

> Integration tests in the `collection` module requires a local MongoDB instance on port 27017 .

[![CircleCI](https://circleci.com/gh/ReactiveMongo/ReactiveMongo-BSON.svg?style=svg)](https://circleci.com/gh/ReactiveMongo/ReactiveMongo-BSON)

[Travis](https://travis-ci.org/ReactiveMongo/ReactiveMongo-BSON): ![Travis build status](https://travis-ci.org/ReactiveMongo/ReactiveMongo-BSON.png?branch=master)
