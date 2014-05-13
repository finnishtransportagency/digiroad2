package fi.liikennevirasto.digiroad2

class DummyEventBus extends DigiroadEventBus {
  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
  }

  override def publish(name: String, value: Any): Unit = {
  }
}
