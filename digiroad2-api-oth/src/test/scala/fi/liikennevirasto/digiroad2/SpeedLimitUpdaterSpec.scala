package fi.liikennevirasto.digiroad2

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestActorRef
import fi.liikennevirasto.digiroad2.asset.Municipality
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import fi.liikennevirasto.digiroad2.linearasset.UnknownSpeedLimit
import fi.liikennevirasto.digiroad2.service.linearasset.SpeedLimitService
import fi.liikennevirasto.digiroad2.util.LinkIdGenerator
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class SpeedLimitUpdaterSpec extends FunSuite with Matchers {
  val linkId = LinkIdGenerator.generateRandom()

  test("should purge unknown speed limits") {
    val system = ActorSystem("TestActorSystem")
    val mockProvider = MockitoSugar.mock[SpeedLimitService]
    val eventBus = new DigiroadEventBus()
    val updater = TestActorRef[SpeedLimitUpdater[Long, UnknownSpeedLimit, ChangeSet]](Props(classOf[SpeedLimitUpdater[Long, UnknownSpeedLimit, ChangeSet]], mockProvider), name = "testSpeedLimitUpdater")(system)
    eventBus.subscribe(updater, "testSpeedLimits:purgeUnknownSpeedLimits")
    eventBus.publish("testSpeedLimits:purgeUnknownSpeedLimits", (Set(linkId), Seq()))

    verify(mockProvider, times(1)).purgeUnknown(Set(linkId), Seq())
  }

  test("should persist unknown speed limits") {
    val system = ActorSystem("TestActorSystem")
    val mockProvider = MockitoSugar.mock[SpeedLimitService]
    val eventBus = new DigiroadEventBus()
    val updater = TestActorRef[SpeedLimitUpdater[Long, UnknownSpeedLimit, ChangeSet]](Props(classOf[SpeedLimitUpdater[Long, UnknownSpeedLimit, ChangeSet]], mockProvider), name = "testSpeedLimitUpdater")(system)
    eventBus.subscribe(updater, "testSpeedLimits:persistUnknownSpeedLimit")
    eventBus.publish("testSpeedLimits:persistUnknownSpeedLimit", Seq(UnknownSpeedLimit(linkId, 235, Municipality)))

    verify(mockProvider, times(1)).persistUnknown(Seq(UnknownSpeedLimit(linkId, 235, Municipality)))
  }

  test("should persist update speed limits") {
    val system = ActorSystem("TestActorSystem")
    val mockProvider = MockitoSugar.mock[SpeedLimitService]
    val eventBus = new DigiroadEventBus()
    val updater = TestActorRef[SpeedLimitUpdater[Long, UnknownSpeedLimit, ChangeSet]](Props(classOf[SpeedLimitUpdater[Long, UnknownSpeedLimit, ChangeSet]], mockProvider), name = "testSpeedLimitUpdater")(system)
    eventBus.subscribe(updater, "testSpeedLimits:update")
    eventBus.publish("testSpeedLimits:update", ChangeSet(Set.empty[Long], Nil, Nil, Nil, Set.empty[Long], Nil))

    verify(mockProvider, times(1)).updateChangeSet(ChangeSet(Set.empty[Long], Nil, Nil, Nil, Set.empty[Long], Nil))
  }
}
