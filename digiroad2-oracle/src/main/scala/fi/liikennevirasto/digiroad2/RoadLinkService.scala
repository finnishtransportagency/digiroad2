package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.ConversionDatabase._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.oracle.AssetPropertyConfiguration.DateTimePropertyFormat
import fi.liikennevirasto.digiroad2.asset.oracle.Queries
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.oracle.collections.OracleArray
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}

trait RoadLinkService {
  case class BasicRoadLink(id: Long, mmlId: Long, geometry: Seq[Point], length: Double, administrativeClass: AdministrativeClass)
  type AdjustedRoadLink = (Long, Long, Seq[Point], Double, AdministrativeClass, Int, TrafficDirection, Option[String], Option[String], Int)
  case class VVHRoadLink(mmlId: Long, geometry: Seq[Point], administrativeClass: AdministrativeClass, functionalClass: Int, trafficDirection: TrafficDirection, linkType: LinkType, modifiedAt: Option[String], modifiedBy: Option[String])

  def getByIdAndMeasure(id: Long, measure: Double): Option[(Long, Int, Option[Point], AdministrativeClass)] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
         select dr1_id, kunta_nro, to_2d(sdo_lrs.dynamic_segment(shape, $measure, $measure)), omistaja
           from tielinkki_ctas
           where dr1_id = $id
        """
      query.as[(Long, Int, Seq[Point], AdministrativeClass)].firstOption().map {
        case (roadLinkId, municipalityNumber, geometry, roadLinkType) => (roadLinkId, municipalityNumber, geometry.headOption, roadLinkType)
      }
    }
  }

  def getByTestIdAndMeasure(testId: Long, measure: Double): Option[(Long, Int, Option[Point], AdministrativeClass)] = {
    Database.forDataSource(dataSource).withDynTransaction {
       val query = sql"""
         select prod.dr1_id, prod.kunta_nro, to_2d(sdo_lrs.dynamic_segment(prod.shape, $measure, $measure)), prod.omistaja
           from tielinkki_ctas prod
           join tielinkki test
           on prod.mml_id = test.mml_id
           where test.objectid = $testId
        """
      query.as[(Long, Int, Seq[Point], AdministrativeClass)].list() match {
        case List(productionLink) => Some((productionLink._1, productionLink._2, productionLink._3.headOption, productionLink._4))
        case _ => None
      }
    }
  }

  def getMunicipalityCode(roadLinkId: Long): Option[Int] = {
    Database.forDataSource(dataSource).withDynTransaction {
       val query = sql"""
         select prod.kunta_nro
           from tielinkki_ctas prod
           where prod.dr1_id = $roadLinkId
        """
      query.as[Int].firstOption
    }
  }

  def getRoadLinkGeometry(id: Long, startMeasure: Double, endMeasure: Double): Seq[Point] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
        select to_2d(sdo_lrs.dynamic_segment(shape, $startMeasure, $endMeasure))
          from tielinkki_ctas
          where dr1_id = $id
        """
      query.as[Seq[Point]].first
    }
  }

  def getRoadLinkGeometry(id: Long): Option[Seq[Point]] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
        select to_2d(shape)
          from tielinkki_ctas
          where dr1_id = $id
        """
      query.as[Seq[Point]].firstOption
    }
  }

  def getRoadLinkGeometryByTestId(testId: Long): Option[Seq[Point]] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
        select to_2d(prod.shape)
           from tielinkki_ctas prod
           join tielinkki test
           on prod.mml_id = test.mml_id
           where test.objectid = $testId
        """
      query.as[Seq[Point]].firstOption
    }
  }

  def getTestId(id: Long): Option[Long] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
        select test.objectid
           from tielinkki_ctas prod
           join tielinkki test
           on prod.mml_id = test.mml_id
           where prod.dr1_id = $id
        """
      query.as[Long].firstOption
    }
  }

  def getPointLRMeasure(roadLinkId: Long, point: Point): BigDecimal = {
    Database.forDataSource(dataSource).withDynTransaction {
      val x = point.x
      val y = point.y
      val query =
        s"""
          SELECT
            SDO_LRS.GET_MEASURE(
              SDO_LRS.PROJECT_PT(
                tl.shape,
                MDSYS.SDO_GEOMETRY(2001,
                                   3067,
                                   NULL,
                                   MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                   MDSYS.SDO_ORDINATE_ARRAY($x, $y)
                                  )
                ))
            FROM tielinkki_ctas tl
            WHERE tl.dr1_id = $roadLinkId
        """
      Q.queryNA[BigDecimal](query).first
    }
  }

  implicit val getAdministrativeClass = new GetResult[AdministrativeClass] {
    def apply(r: PositionedResult) = {
      AdministrativeClass(r.nextInt())
    }
  }

  implicit val getTrafficDirection = new GetResult[TrafficDirection] {
    def apply(r: PositionedResult) = {
      TrafficDirection(r.nextIntOption())
    }
  }

  implicit val getBasicRoadLink = GetResult( r => BasicRoadLink(r.<<, r.<<, r.<<, r.<<,r.<<) )

  def getRoadLinkMiddlePointByMMLId(mmlId: Long): Option[(Long, Point)]

  def getRoadLinkLength(id: Long): Option[Double] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
        select sdo_lrs.geom_segment_length(shape) as length
          from tielinkki_ctas
          where dr1_id = $id
        """
      query.as[Double].firstOption
    }
  }

  def withDynTransaction[T](f: => T): T

  private def getRoadLinkProperties(id: Long): BasicRoadLink = {
    sql"""select dr1_id, mml_id, to_2d(shape), sdo_lrs.geom_segment_length(shape) as length, omistaja
            from tielinkki_ctas where dr1_id = $id"""
      .as[BasicRoadLink].first()
  }

  protected def setLinkProperty(table: String, column: String, value: Int, mmlId: Long, username: String) = {
    withDynTransaction {
      val optionalExistingValue: Option[Int] = sql"""select #$column from #$table where mml_id = $mmlId""".as[Int].firstOption
      optionalExistingValue match {
        case Some(existingValue) =>
          if (existingValue != value) {
            sqlu"""update #$table
                     set #$column = $value,
                         modified_date = current_timestamp,
                         modified_by = $username
                     where mml_id = $mmlId""".execute()
          }
        case None => sqlu"""insert into #$table (mml_id, #$column, modified_by) values ($mmlId, $value, $username)""".execute()
      }
    }
  }

  def updateProperties(id: Long, functionalClass: Int, linkType: LinkType,
                       direction: TrafficDirection, username: String, municipalityValidation: Int => Unit): Option[VVHRoadLink]

  private def basicToAdjusted(basic: BasicRoadLink, modification: Option[(DateTime, String)], functionalClass: Int, linkType: Int, trafficDirection: TrafficDirection): AdjustedRoadLink = {
    val (modifiedAt, modifiedBy) = (modification.map(_._1), modification.map(_._2))
    (basic.id, basic.mmlId, basic.geometry, basic.length,
     basic.administrativeClass, functionalClass, trafficDirection, modifiedAt.map(DateTimePropertyFormat.print), modifiedBy, linkType)
  }

  private def adjustedRoadLinks(basicRoadLinks: Seq[BasicRoadLink]): Seq[AdjustedRoadLink] = {
    withDynTransaction {
      val adjustedTrafficDirections: Map[Long, Seq[(Long, Int, DateTime, String)]] = OracleArray.fetchTrafficDirections(basicRoadLinks.map(_.mmlId), Queries.bonecpToInternalConnection(dynamicSession.conn)).groupBy(_._1)
      val adjustedFunctionalClasses: Map[Long, Seq[(Long, Int, DateTime, String)]] = OracleArray.fetchFunctionalClasses(basicRoadLinks.map(_.mmlId), Queries.bonecpToInternalConnection(dynamicSession.conn)).groupBy(_._1)
      val adjustedLinkTypes: Map[Long, Seq[(Long, Int, DateTime, String)]] = OracleArray.fetchLinkTypes(basicRoadLinks.map(_.mmlId), Queries.bonecpToInternalConnection(dynamicSession.conn)).groupBy(_._1)

      basicRoadLinks.map { basicRoadLink =>
        val mmlId = basicRoadLink.mmlId
        val functionalClass = adjustedFunctionalClasses.get(mmlId).flatMap(_.headOption)
        val adjustedLinkType = adjustedLinkTypes.get(mmlId).flatMap(_.headOption)
        val trafficDirection = adjustedTrafficDirections.get(mmlId).flatMap(_.headOption)

        val functionalClassValue = functionalClass.map(_._2).getOrElse(FunctionalClass.Unknown)
        val adjustedLinkTypeValue = adjustedLinkType.map(_._2).getOrElse(UnknownLinkType.value)
        val trafficDirectionValue = trafficDirection.map( trafficDirection =>
          TrafficDirection(trafficDirection._2)
        ).getOrElse(UnknownDirection)

        def latestModifications(a: Option[(DateTime, String)], b: Option[(DateTime, String)]) = {
          (a, b) match {
            case (Some((firstModifiedAt, firstModifiedBy)), Some((secondModifiedAt, secondModifiedBy))) =>
              if (firstModifiedAt.isAfter(secondModifiedAt))
                Some((firstModifiedAt, firstModifiedBy))
              else
                Some((secondModifiedAt, secondModifiedBy))
            case (Some((firstModifiedAt, firstModifiedBy)), None) => Some((firstModifiedAt, firstModifiedBy))
            case (None, Some((secondModifiedAt, secondModifiedBy))) => Some((secondModifiedAt, secondModifiedBy))
            case (None, None) => None
          }
        }
        val modifications = List(functionalClass, trafficDirection, adjustedLinkType).map {
          case Some((_, _, at, by)) => Some((at, by))
          case _ => None
        }

        basicToAdjusted(basicRoadLink, modifications.reduce(latestModifications), functionalClassValue, adjustedLinkTypeValue, trafficDirectionValue)
      }
    }
  }

  def getRoadLink(id: Long): AdjustedRoadLink = {
    val roadLink = Database.forDataSource(dataSource).withDynTransaction { getRoadLinkProperties(id) }
    adjustedRoadLinks(Seq(roadLink)).head
  }

  def getRoadLinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[AdjustedRoadLink] = {
    val roadLinks = Database.forDataSource(dataSource).withDynTransaction {
      val municipalityFilter = if (municipalities.nonEmpty) "kunta_nro in (" + municipalities.mkString(",") + ") and" else ""
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "shape")
      val query =
      s"""
            select dr1_id, mml_id, to_2d(shape), sdo_lrs.geom_segment_length(shape) as length, omistaja, liikennevirran_suunta, linkkityyppi
              from tielinkki_ctas
              where $municipalityFilter $boundingBoxFilter
      """
      Q.queryNA[BasicRoadLink](query).iterator().toSeq
    }
    adjustedRoadLinks(roadLinks)
  }

  def getRoadLinksFromVVH(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadLink] = {
    val vvhRoadLinks = fetchVVHRoadlinks(bounds, municipalities)
    enrichRoadLinksFromVVH(vvhRoadLinks)
  }

  protected def enrichRoadLinksFromVVH(vvhRoadLinks: Seq[(Long, Int, Seq[Point], AdministrativeClass, TrafficDirection)]): Seq[VVHRoadLink] = {
    val roadLinkDataByMmlId = getRoadLinkDataByMmlIds(vvhRoadLinks)
    roadLinkDataByMmlId.map { roadLink =>
      VVHRoadLink(roadLink._2, roadLink._3, roadLink._5, roadLink._6, roadLink._7, LinkType(roadLink._10), roadLink._8, roadLink._9)
    }
  }

  def fetchVVHRoadlinks(municipalityCode: Int):  Seq[(Long, Int, Seq[Point])]

  def fetchVVHRoadlinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[(Long, Int, Seq[Point], AdministrativeClass, TrafficDirection)]

  def fetchVVHRoadlink(mmlId: Long): Option[(Int, Seq[Point], AdministrativeClass)]

  def getRoadLinkDataByMmlIds(vvhRoadLinks: Seq[(Long, Int, Seq[Point], AdministrativeClass, TrafficDirection)]): Seq[AdjustedRoadLink] = {
    val basicRoadLinks = vvhRoadLinks.map { roadLink =>
      val (mmlId, _, geometry, administrativeClass, _) = roadLink
      BasicRoadLink(0, mmlId, geometry, 0.0, administrativeClass)
    }
    adjustedRoadLinks(basicRoadLinks)
  }

  def getByMunicipality(municipality: Int): Seq[(Long, Seq[Point])] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = s"""select dr1_id, to_2d(shape) from tielinkki_ctas where kunta_nro = $municipality"""
      Q.queryNA[(Long, Seq[Point])](query).iterator().toSeq
    }
  }

  def getByMunicipalityWithProperties(municipality: Int): Seq[Map[String, Any]] = {
    val roadLinks = Database.forDataSource(dataSource).withDynTransaction {
      sql"""
        select dr1_id, mml_id, to_2d(shape), sdo_lrs.geom_segment_length(shape) as length, omistaja
          from tielinkki_ctas
          where kunta_nro = $municipality
        """.as[BasicRoadLink].list()
    }
    adjustedRoadLinks(roadLinks).map { roadLink =>
      Map("id" -> roadLink._1, "mmlId" -> roadLink._2, "points" -> roadLink._3, "administrativeClass" -> roadLink._5.value,
          "functionalClass" -> roadLink._6, "trafficDirection" -> roadLink._7.value, "linkType" -> roadLink._10)
    }
  }

  def getAdjacent(id: Long): Seq[Map[String, Any]] = {
    val endpoints = getRoadLinkGeometry(id).map(GeometryUtils.geometryEndpoints)
    endpoints.map(endpoint => {
      val roadLinks = Database.forDataSource(dataSource).withDynTransaction {
        val delta: Vector3d = Vector3d(0.1, 0.1, 0)
        val bounds = BoundingRectangle(endpoint._1 - delta, endpoint._1 + delta)
        val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "shape")

        val bounds2 = BoundingRectangle(endpoint._2 - delta, endpoint._2 + delta)
        val boundingBoxFilter2 = OracleDatabase.boundingBoxFilter(bounds2, "shape")

        sql"""
        select dr1_id, mml_id, to_2d(shape)
        from tielinkki_ctas
        where (#$boundingBoxFilter or #$boundingBoxFilter2) and linkkityyppi not in (8, 9, 21)
      """.as[(Long, Long, Seq[Point])].iterator().toSeq
      }
      roadLinks.filterNot(_._1 == id).filter(roadLink => {
        val (_, _, geometry) = roadLink
        val epsilon = 0.01
        val rlEndpoints = GeometryUtils.geometryEndpoints(geometry)
        rlEndpoints._1.distanceTo(endpoint._1) < epsilon ||
          rlEndpoints._2.distanceTo(endpoint._1) < epsilon ||
          rlEndpoints._1.distanceTo(endpoint._2) < epsilon ||
          rlEndpoints._2.distanceTo(endpoint._2) < epsilon
      }).map(roadLink => Map("id" -> roadLink._1, "mmlId" -> roadLink._2))
    }).getOrElse(Nil)
  }
}

object RoadLinkService extends RoadLinkService {
  override def withDynTransaction[T](f: => T): T = Database.forDataSource(OracleDatabase.ds).withDynTransaction(f)

  override def fetchVVHRoadlinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[(Long, Int, Seq[Point], AdministrativeClass, TrafficDirection)] = {
    throw new NotImplementedError()
  }

  override def fetchVVHRoadlink(mmlId: Long): Option[(Int, Seq[Point], AdministrativeClass)] = {
    throw new NotImplementedError()
  }

  override def fetchVVHRoadlinks(municipalityCode: Int) = throw new NotImplementedError()

  override def getRoadLinkMiddlePointByMMLId(mmlId: Long): Option[(Long, Point)] = throw new NotImplementedError()

  override def updateProperties(id: Long, functionalClass: Int, linkType: LinkType,
                                direction: TrafficDirection, username: String, municipalityValidation: Int => Unit): Option[VVHRoadLink] = throw new NotImplementedError()
}

class VVHRoadLinkService(vvhClient: VVHClient) extends RoadLinkService {
  override def withDynTransaction[T](f: => T): T = Database.forDataSource(OracleDatabase.ds).withDynTransaction(f)

  override def fetchVVHRoadlinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[(Long, Int, Seq[Point], AdministrativeClass, TrafficDirection)] = {
    vvhClient.fetchVVHRoadlinks(bounds, municipalities)
  }

  override def fetchVVHRoadlink(mmlId: Long): Option[(Int, Seq[Point], AdministrativeClass)] = {
    vvhClient.fetchVVHRoadlink(mmlId)
  }

  override def fetchVVHRoadlinks(municipalityCode: Int): Seq[(Long, Int, Seq[Point])] = {
     vvhClient.fetchByMunicipality(municipalityCode)
  }

  override def getRoadLinkMiddlePointByMMLId(mmlId: Long): Option[(Long, Point)] = {
    val middlePoint: Option[Point] = vvhClient.fetchVVHRoadlink(mmlId)
      .map(_._2)
      .flatMap { geometry =>
      GeometryUtils.calculatePointFromLinearReference(geometry, GeometryUtils.geometryLength(geometry) / 2.0)
    }
    middlePoint.map((mmlId, _))
  }

  override def updateProperties(mmlId: Long, functionalClass: Int, linkType: LinkType,
                                direction: TrafficDirection, username: String, municipalityValidation: Int => Unit): Option[VVHRoadLink] = {
    val vvhRoadLink = fetchVVHRoadlink(mmlId)
    vvhRoadLink.map { case (municipalityCode, geometry, administrativeClass) =>
      municipalityValidation(municipalityCode)
      setLinkProperty("traffic_direction", "traffic_direction", direction.value, mmlId, username)
      setLinkProperty("functional_class", "functional_class", functionalClass, mmlId, username)
      setLinkProperty("link_type", "link_type", linkType.value, mmlId, username)
      // TODO: Fetch traffic direction from VVH
      enrichRoadLinksFromVVH(Seq((mmlId, municipalityCode, geometry, administrativeClass, UnknownDirection))).head
    }
  }
}
