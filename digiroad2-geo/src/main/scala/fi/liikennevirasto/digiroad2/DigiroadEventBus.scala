package fi.liikennevirasto.digiroad2

import akka.actor.ActorRef
import akka.event.{EventBus, LookupClassification}
import fi.liikennevirasto.digiroad2.asset.{Property, Modification}

case class EventBusMassTransitStop(municipalityNumber: Int, municipalityName: String, nationalId: Long,
                                   lon: Double, lat: Double, bearing: Option[Int], validityDirection: Option[Int],
                                   created: Modification, modified: Modification, propertyData: Seq[Property] = List())

class DigiroadEventBus extends EventBus with LookupClassification {
  case class Event(name: String, value: Any)
  type Classifier = String
  type Subscriber = ActorRef

  override protected def classify(event: Event): Classifier = event.name

  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
    subscriber ! event.value
  }

  def publish(name: String, value: Any): Unit = {
    publish(Event(name, value))
  }

  override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int =
    a.compareTo(b)

  override protected def mapSize: Int = 128
}
