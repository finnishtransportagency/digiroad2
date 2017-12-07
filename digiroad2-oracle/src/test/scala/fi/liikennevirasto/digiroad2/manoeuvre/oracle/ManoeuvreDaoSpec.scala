package fi.liikennevirasto.digiroad2.manoeuvre.oracle

import fi.liikennevirasto.digiroad2.linearasset.{ValidityPeriod, ValidityPeriodDayOfWeek}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadLinkClient, VVHRoadlink}
import org.scalatest.{FunSuite, Matchers, Tag}
import org.mockito.Mockito._
import org.scalatest.{FunSuite, Matchers, Tag}
import org.scalatest.mock.MockitoSugar

/**
  * Created by venholat on 3.5.2016.
  */
class ManoeuvreDaoSpec extends  FunSuite with Matchers {

  private def daoWithRoadLinks(roadLinks: Seq[VVHRoadlink]): ManoeuvreDao = {
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]

    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    when(mockVVHRoadLinkClient.fetchByLinkIds(roadLinks.map(_.linkId).toSet))
      .thenReturn(roadLinks)

    roadLinks.foreach { roadLink =>
      when(mockVVHRoadLinkClient.fetchByLinkId(roadLink.linkId)).thenReturn(Some(roadLink))
    }

    new ManoeuvreDao(mockVVHClient)
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("test setManoeuvreExceptions") {
    runWithRollback {
      val dao = new ManoeuvreDao(MockitoSugar.mock[VVHClient])
      val mano = NewManoeuvre(Set(), Seq(), None, Seq(1, 2, 3))
      val id = dao.createManoeuvre("user", mano)
      id > 0 should be (true)
      val persisted = dao.find(id).get
      persisted.exceptions should be(Seq())
      val exceptions = Seq(1, 2, 3, 4)
      dao.addManoeuvreExceptions(id, exceptions)
      val updated = dao.find(id).get
      updated shouldNot be(persisted)
      updated.exceptions should be(exceptions)
    }
  }

  test("test create Manoeuvre For Update") {
    runWithRollback {
      val dao = new ManoeuvreDao(MockitoSugar.mock[VVHClient])
      val validityPeriod = Set(ValidityPeriod(12, 13, ValidityPeriodDayOfWeek("Sunday"), 30, 15), ValidityPeriod(8, 12, ValidityPeriodDayOfWeek("Saturday"), 0, 10))
      val exceptions = List(4,5)
      val mano = NewManoeuvre(validityPeriod, exceptions, None, Seq(4, 7))
      val id = dao.createManoeuvre("user", mano)
      id > 0 should be (true)
      val persisted = dao.find(id).get
          val manoeuvreRowOld = dao.fetchManoeuvreById(id).head
      val newId = dao.createManoeuvreForUpdate("updater", manoeuvreRowOld, Option("Additional Info"))

      val newManoeuvre = dao.find(newId).get
      newManoeuvre shouldNot be(persisted)
      newManoeuvre.additionalInfo should be("Additional Info")
      newManoeuvre.validityPeriods should be(validityPeriod)
      newManoeuvre.exceptions should be(exceptions)
    }
  }

  test("test getByRoadLinks") {
    runWithRollback {
      val dao = new ManoeuvreDao(MockitoSugar.mock[VVHClient])
      val mano = NewManoeuvre(Set(), Seq(), None, Seq(4, 7))
      val id = dao.createManoeuvre("user", mano)
      id > 0 should be (true)
      val retrieved = dao.getByRoadLinks(Seq(4, 7))
      retrieved should have size 1L
      val persisted = retrieved.head
      persisted.id should be(id)
      val retrieved4 = dao.getByRoadLinks(Seq(4))
      retrieved4 should have size 1L
      val retrieved7 = dao.getByRoadLinks(Seq(7))
      retrieved7 should have size 1L
      val retrievedN = dao.getByRoadLinks(Seq(546786765L))
      retrievedN should have size 0L
    }
  }

  test("test addManoeuvreValidityPeriods") {
    runWithRollback {
      val dao = new ManoeuvreDao(MockitoSugar.mock[VVHClient])
      val mano = NewManoeuvre(Set(), Seq(), None, Seq(1, 2, 3))
      val id = dao.createManoeuvre("user", mano)
      id > 0 should be (true)
      val persisted = dao.find(id).get
      persisted.validityPeriods should be(Set())
      val validityPeriod = Set(ValidityPeriod(12, 13, ValidityPeriodDayOfWeek("Sunday"), 30, 45))
      dao.addManoeuvreValidityPeriods(id, validityPeriod)
      val updated = dao.find(id).get
      updated shouldNot be(persisted)
      updated.validityPeriods should be(validityPeriod)
      val validityPeriod2 = Set(ValidityPeriod(9, 15, ValidityPeriodDayOfWeek("Weekday"), 0, 55))
      dao.addManoeuvreValidityPeriods(id, validityPeriod2)
      val updated2 = dao.find(id).get
      updated2.validityPeriods should be(validityPeriod ++ validityPeriod2)
    }
  }

  test("test addManoeuvreExceptions") {
    runWithRollback {
      val dao = new ManoeuvreDao(MockitoSugar.mock[VVHClient])
      val mano = NewManoeuvre(Set(), Seq(1, 2), None, Seq(4, 7))
      val id = dao.createManoeuvre("user", mano)
      id > 0 should be (true)
      val persisted = dao.find(id).get
      persisted.exceptions should have size 2
      val exceptions = Seq(3, 4)
      dao.addManoeuvreExceptions(id, exceptions)
      val updated = dao.find(id).get
      updated shouldNot be(persisted)
      updated.exceptions should have size 4
    }
  }

  test("test deleteManoeuvre") {
    runWithRollback {
      val dao = new ManoeuvreDao(MockitoSugar.mock[VVHClient])
      val mano = NewManoeuvre(Set(), Seq(1, 2), Option("added"), Seq(4, 7))
      val id = dao.createManoeuvre("user", mano)
      id > 0 should be (true)
      val persisted = dao.find(id).get
      dao.deleteManoeuvre("deleter", id)
      val updated = dao.find(id)
      updated.isEmpty should be (true)
    }
  }

  test("test createManoeuvre") {
    val dao = new ManoeuvreDao(MockitoSugar.mock[VVHClient])
    val elements = Seq(ManoeuvreElement(1, 123, 124, ElementTypes.FirstElement),
      ManoeuvreElement(1, 124, 125, ElementTypes.IntermediateElement),
      ManoeuvreElement(1, 125, 0, ElementTypes.LastElement))
    val mano = NewManoeuvre(Set(), Seq(), None, elements.map(_.sourceLinkId))
    runWithRollback {
      val id = dao.createManoeuvre("user", mano)
      (id > 0) should be (true)
      val created = dao.find(id)
      created shouldNot be (None)
      val saved = created.get
      saved.id should be (id)
      saved.elements should have length 3
      elements.map(el => ManoeuvreElement(id, el.sourceLinkId, el.destLinkId, el.elementType)).foreach {
        el => saved.elements should contain (el)
      }
    }
  }

}
