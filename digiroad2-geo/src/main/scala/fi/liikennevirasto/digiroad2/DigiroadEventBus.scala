package fi.liikennevirasto.digiroad2

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.LookupClassification
import akka.event.EventBus
import akka.actor.ActorRef

case class Message(s: String, payload: Any)

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

  def publish(msg: Message): Unit = {
    publish(Event(msg.s, msg))
  }

  override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int =
    a.compareTo(b)

  override protected def mapSize: Int = 128
}
