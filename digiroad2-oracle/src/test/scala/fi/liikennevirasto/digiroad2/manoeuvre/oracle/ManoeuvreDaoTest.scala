package fi.liikennevirasto.digiroad2.manoeuvre.oracle

import fi.liikennevirasto.digiroad2.util.TestTransactions
import fi.liikennevirasto.digiroad2._
import org.scalatest.{FunSuite, Matchers, Tag}
import org.mockito.Mockito._
import org.scalatest.{FunSuite, Matchers, Tag}
import org.scalatest.mock.MockitoSugar

/**
  * Created by venholat on 3.5.2016.
  */
class ManoeuvreDaoTest extends  FunSuite with Matchers {

  private def daoWithRoadLinks(roadLinks: Seq[VVHRoadlink]): ManoeuvreDao = {
    val mockVVHClient = MockitoSugar.mock[VVHClient]

    when(mockVVHClient.fetchVVHRoadlinks(roadLinks.map(_.linkId).toSet))
      .thenReturn(roadLinks)

    roadLinks.foreach { roadLink =>
      when(mockVVHClient.fetchVVHRoadlink(roadLink.linkId)).thenReturn(Some(roadLink))
    }

    new ManoeuvreDao(mockVVHClient)
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("test setManoeuvreExceptions") {

  }

  test("test updateManoueuvre") {

  }

  test("test updateModifiedData") {

  }

  test("test getByRoadLinks") {

  }

  test("test addManoeuvreValidityPeriods") {

  }

  test("test setManoeuvreAdditionalInfo") {

  }

  test("test fetchManoeuvresByLinkIds") {

  }

  test("test addManoeuvreExceptions") {

  }

  test("test deleteManoeuvre") {

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
