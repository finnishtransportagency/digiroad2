package fi.liikennevirasto.digiroad2

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestActorRef
import fi.liikennevirasto.digiroad2.asset.Municipality
import fi.liikennevirasto.digiroad2.linearasset.UnknownSpeedLimit
import fi.liikennevirasto.digiroad2.service.linearasset.SpeedLimitService
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class SpeedLimitUpdaterSpec extends FunSuite with Matchers {
  test("should purge unknown speed limits") {
    val system = ActorSystem("TestActorSystem")
    val mockProvider = MockitoSugar.mock[SpeedLimitService]
    val eventBus = new DigiroadEventBus()
    val updater = TestActorRef[SpeedLimitUpdater[Long, UnknownSpeedLimit]](Props(classOf[SpeedLimitUpdater[Long, UnknownSpeedLimit]], mockProvider), name = "testSpeedLimitUpdater")(system)
    eventBus.subscribe(updater, "testSpeedLimits:purgeUnknownSpeedLimits")
    eventBus.publish("testSpeedLimits:purgeUnknownSpeedLimits", Set(1l))

    verify(mockProvider, times(1)).purgeUnknown(Set(1l))
  }

  test("should persist unknown speed limits") {
    val system = ActorSystem("TestActorSystem")
    val mockProvider = MockitoSugar.mock[SpeedLimitService]
    val eventBus = new DigiroadEventBus()
    val updater = TestActorRef[SpeedLimitUpdater[Long, UnknownSpeedLimit]](Props(classOf[SpeedLimitUpdater[Long, UnknownSpeedLimit]], mockProvider), name = "testSpeedLimitUpdater")(system)
    eventBus.subscribe(updater, "testSpeedLimits:persistUnknownSpeedLimit")
    eventBus.publish("testSpeedLimits:persistUnknownSpeedLimit", Seq(UnknownSpeedLimit(1l, 235, Municipality)))

    verify(mockProvider, times(1)).persistUnknown(Seq(UnknownSpeedLimit(1l, 235, Municipality)))
  }
}
