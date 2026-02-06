# Builder API Documentation

This comprehensive guide covers the type-safe builder API for MongoDB operations with ReactiveMongo-BSON.

## Table of Contents

1. [**Getting Started**](get-started.md) - Introduction, motivation, installation, and your first queries.
2. [**Filter Operations**](filters.md) - Building complex queries with type-safe filters.
3. [**Projection Operations**](projection.md) - Selecting and excluding fields with compile-time validation.
4. [**Update Operations**](update.md) - Type-safe update documents with field operations.

## Quick Start

If you're already familiar with MongoDB and Scala, you can jump straight to the [Getting Started](get-started.md) guide to start building type-safe queries immediately.

## What You'll Learn

This documentation will teach you how to:

- **Build type-safe MongoDB queries** with compile-time field validation.
- **Create projections** to select or hide specific fields.
- **Construct update documents** with operators like `$set`, `$inc`, `$push`, and more.
- **Work with nested fields** using type-safe path navigation.
- **Handle optional fields** and conditional operations.
- **Leverage Shapeless** for compile-time type checking.

## Prerequisites

This guide assumes basic familiarity with:

- Scala programming language
- MongoDB concepts (documents, collections, queries)
- ReactiveMongo for MongoDB connectivity
- Basic understanding of type-level programming (helpful but not required)

## Key Features

The builder API provides:

- ✅ **Compile-time field validation** - Catch typos and type errors before runtime.
- ✅ **Type-safe operations** - Field types are verified at compile time.
- ✅ **Fluent API** - Readable, chainable method calls.
- ✅ **ReactiveMongo integration** - Seamless integration with ReactiveMongo collections.
- ✅ **Nested field support** - Type-safe updates to nested objects.
- ✅ **Zero runtime overhead** - All validation happens at compile time.

## Example Usage

Here's a quick example showing the builder API in action:

```scala
import reactivemongo.api.bson.builder._

case class User(
  name: String,
  age: Int,
  email: String,
  tags: Seq[String]
)

// Type-safe filter
val filter = FilterBuilder.empty[User]
  .eq(Symbol("name"), "John")
  .gt(Symbol("age"), 18)
  .result()

// Type-safe projection
val projection = ProjectionBuilder.empty[User]
  .includes(Symbol("name"))
  .includes(Symbol("age"))
  .excludes(Symbol("email"))
  .result()

// Type-safe update
val update = UpdateBuilder.empty[User]
  .set(Symbol("name"), "Jane")
  .inc(Symbol("age"), 1)
  .push(Symbol("tags"), "verified")
  .result()
```

Let's get started with understanding [why the builder API exists](get-started.md) and how it can improve your MongoDB applications.
