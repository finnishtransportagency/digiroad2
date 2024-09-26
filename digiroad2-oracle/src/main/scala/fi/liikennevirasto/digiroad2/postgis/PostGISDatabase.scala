package fi.liikennevirasto.digiroad2.postgis

import java.sql.Date
import javax.sql.DataSource
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import org.joda.time.LocalDate
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import org.locationtech.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.postgresql.util.PGobject
import net.postgis.jdbc.geometry.GeometryBuilder
import scala.collection.mutable.ListBuffer

object PostGISDatabase {
  lazy val ds: DataSource = initDataSource

  private val transactionOpen = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = { false }
  }

  def isLocalDbConnection: Boolean = {
    ds.getConnection.getMetaData.getURL == "jdbc:postgresql://localhost:5432/digiroad2" && ds.getConnection.getMetaData.getUserName == "digiroad2"
  }

  def isAwsUnitTestConnection: Boolean = {
    ds.getConnection.getMetaData.getURL == "jdbc:postgresql://ddw6gldo8fiqt4.c8sq5c8rj3gu.eu-west-1.rds.amazonaws.com:5432/digiroad2" &&
      ds.getConnection.getMetaData.getUserName == "digiroaduserfortest"
  }

  def isTransactionOpen: Boolean = transactionOpen.get()

  /**
    * Opens new dynSession only if there is not connection open
    */
  def withDbConnection[T](f: => T): T = {
    if (isTransactionOpen) f else withDynSession{ f }
  }
  
  def withDynTransaction[T](f: => T): T = {
    if (transactionOpen.get())
      throw new IllegalThreadStateException("Attempted to open nested transaction")
    else {
      try {
        transactionOpen.set(true)
        Database.forDataSource(PostGISDatabase.ds).withDynTransaction {
          setSessionLanguage()
          f
        }
      } finally {
        transactionOpen.set(false)
      }
    }
  }

  def withDynSession[T](f: => T): T = {
    if (transactionOpen.get())
      throw new IllegalThreadStateException("Attempted to open nested session")
    else {
      try {
        transactionOpen.set(true)
        Database.forDataSource(PostGISDatabase.ds).withDynSession {
          setSessionLanguage()
          f
        }
      } finally {
        transactionOpen.set(false)
      }
    }
  }

  def isWithinSession: Boolean = {
    transactionOpen.get()
  }

  def setSessionLanguage() {
    //sqlu"""alter session set nls_language = 'american'""".execute
  }

  def jodaToSqlDate(jodaDate: LocalDate): Date = {
    new Date(jodaDate.toDate.getTime)
  }

  def initDataSource: DataSource = {
    Class.forName("org.postgresql.Driver")
    val cfg = new BoneCPConfig(Digiroad2Properties.bonecpProperties)
    new BoneCPDataSource(cfg)
  }

  def boundingBoxFilter(bounds: BoundingRectangle, geometryColumn: String): String = {
    val leftBottomX = bounds.leftBottom.x
    val leftBottomY = bounds.leftBottom.y
    val rightTopX = bounds.rightTop.x
    val rightTopY = bounds.rightTop.y
    s"""
      $geometryColumn && ST_MakeEnvelope($leftBottomX,
                                         $leftBottomY,
                                         $rightTopX,
                                         $rightTopY,
                                         3067)
    """
  }

  def polygonFilter(polygon: Polygon, geometryColumn: String): String = {
    val geom = polygon.getCoordinates.map(point => s"${point.x} ${point.y}")
    val lineString = s"'LINESTRING(${geom.mkString(",")})'"

    s"$geometryColumn && ST_MakePolygon(ST_GeomFromText($lineString, 3067))"
  }

  def extractGeometry(data: Object): List[List[Double]] = {
    val geometry = data.asInstanceOf[PGobject]
    if (geometry == null) Nil
    else {
      val geomValue = geometry.getValue
      val geom = GeometryBuilder.geomFromString(geomValue)
      val listOfPoint = ListBuffer[List[Double]]()
      for (i <- 0 until geom.numPoints()) {
        val point = geom.getPoint(i)
        listOfPoint += List(point.x, point.y, point.z, point.m)
      }
      listOfPoint.toList
    }
  }

  def processGeometry(path: List[List[Double]]): (Seq[Map[String, Double]], String) = {
    val geometryForApi = path.map(point => Map("x" -> point(0), "y" -> point(1), "z" -> point(2), "m" -> point(3)))
    val geometryWKT = s"LINESTRING ZM (${path.map(point => s"${point(0)} ${point(1)} ${point(2)} ${point(3)}").mkString(", ")})"
    (geometryForApi, geometryWKT)
  }
}