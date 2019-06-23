# ReactiveMongo Biːsən

BSON libraries for ReactiveMongo

## Usage

The main library is intended to replace (at some point after release 1.0) the BSON library currently shipped along with ReactiveMongo driver.

It can already be used in your `build.sbt`:

```ocaml
libraryDependencies ++= Seq(
  "org.reactivemongo" %% "reactivemongo-bson-api" % VERSION,
  "org.reactivemongo" %% "reactivemongo-bson-compat" % VERSION)
```

[![Maven](https://img.shields.io/maven-central/v/org.reactivemongo/reactivemongo-bson-api_2.12.svg)](http://search.maven.org/#search%7Cga%7C1%7Creactivemongo-bson-api) [![Javadocs](https://javadoc.io/badge/org.reactivemongo/reactivemongo-bson-api_2.12.svg)](https://javadoc.io/doc/org.reactivemongo/reactivemongo-bson-api_2.12)

The package of the new library is `reactivemongo.api.bson` (instead of `reactivemongo.bson`).

> More [examples](collection/src/test/scala/CollectionSpec.scala)

## Build manually

ReactiveMongo for Play2 can be built from this source repository.

    sbt publishLocal

To run the tests, use:

    sbt test

> Integration tests in the `collection` module requires a local MongoDB instance on port 27017 .

[Travis](https://travis-ci.org/ReactiveMongo/ReactiveMongo-BSON): ![Travis build status](https://travis-ci.org/ReactiveMongo/ReactiveMongo-BSON.png?branch=master)