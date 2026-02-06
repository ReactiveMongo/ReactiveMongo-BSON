package reactivemongo.api.bson.builder

import java.time.OffsetDateTime

final case class Assignee(name: String)

final case class Assignation(assignee: Assignee)

final case class Details(
    shortDescription: String,
    longDescription: String)

final case class Status(
    name: String,
    updated: OffsetDateTime,
    details: Details)

final case class Commiter(
    username: String,
    email: Option[String],
    accountCreated: OffsetDateTime,
    lastActivity: Option[Status])

final case class Tracker(
    id: Long,
    lastOk: Option[OffsetDateTime],
    commiter: Commiter,
    value: Long)

final case class Pos(
    stopped: Boolean,
    current: String,
    previous: Option[String])

final case class DetectedAt(
    updated: OffsetDateTime,
    position: Option[Pos])

final case class Location(
    updated: OffsetDateTime,
    detectedAt: Option[DetectedAt])

final case class Foo(
    id: String,
    status: Option[Status],
    location: Location,
    tracker: Tracker,
    counter: Int,
    quantity: Long,
    score: Double,
    tags: Seq[String],
    categories: Set[String],
    extraTags: Option[Seq[String]])
