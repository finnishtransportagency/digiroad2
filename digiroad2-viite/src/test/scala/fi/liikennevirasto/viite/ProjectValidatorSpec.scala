package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.util.Track.Combined
import fi.liikennevirasto.viite.dao._
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class ProjectValidatorSpec extends FunSuite with Matchers{
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def runWithRollback[T](f: => T): T = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val t = f
      dynamicSession.rollback()
      t
    }
  }

  def projectLink(startAddrM: Long, endAddrM: Long, track: Track, projectId: Long) = {
    ProjectLink(startAddrM, 19999L, 1L, track, Discontinuity.Continuous, startAddrM, endAddrM, None, None,
      None, 0L, startAddrM, 0.0, (endAddrM - startAddrM).toDouble, SideCode.TowardsDigitizing, (None, None),
      false, Seq(Point(0.0, startAddrM), Point(0.0, endAddrM)), projectId, LinkStatus.NotHandled, RoadType.PublicRoad,
      LinkGeomSource.NormalLinkInterface, (endAddrM - startAddrM).toDouble, 0L, 8L, false, None, 0L)
  }

  test("Project Links should be continuous if geometry is continuous") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val project = RoadAddressProject(id, ProjectState.Incomplete, "f", "s", DateTime.now(), "", DateTime.now(), DateTime.now(),
        "", Seq(), None, Some(8), None)
      ProjectDAO.createRoadAddressProject(project)
      val projectLinks = Seq(projectLink(0, 0, Combined, id))
      ProjectValidator.checkOrdinaryRoadContinuityCodes(project, projectLinks)
      // In progress
    }
  }
}
