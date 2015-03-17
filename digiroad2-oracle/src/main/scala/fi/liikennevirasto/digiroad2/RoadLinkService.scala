package fi.liikennevirasto.digiroad2

import _root_.oracle.spatial.geometry.JGeometry
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.oracle.AssetPropertyConfiguration.DateTimePropertyFormat
import fi.liikennevirasto.digiroad2.asset.oracle.Queries
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.ConversionDatabase._
import fi.liikennevirasto.digiroad2.oracle.collections.OracleArray
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}

import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.json4s._
import org.json4s.jackson.JsonMethods._

object RoadLinkService {
  type BasicRoadLink = (Long, Long, Seq[Point], Double, AdministrativeClass, Int, TrafficDirection, Int)
  type KalpaRoadLink = (Long, Long, Seq[Point], AdministrativeClass, Int, TrafficDirection, Int)
  type AdjustedRoadLink = (Long, Long, Seq[Point], Double, AdministrativeClass, Int, TrafficDirection, Option[String], Option[String], Int)

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

  def getRoadLinkMiddlePointByMMLId(mmlId: Long): Option[(Long, Point)] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
        select dr1_id, to_2d(sdo_lrs.dynamic_segment(shape, sdo_lrs.geom_segment_length(shape) / 2, sdo_lrs.geom_segment_length(shape) / 2))
          from tielinkki_ctas
          where mml_id = $mmlId
        """
      query.as[(Long, Seq[Point])].firstOption.map {
        case(id, geometry) => (id, geometry.head)
      }
    }
  }

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

  private def getRoadLinkProperties(id: Long): BasicRoadLink = {
    sql"""select dr1_id, mml_id, to_2d(shape), sdo_lrs.geom_segment_length(shape) as length, omistaja, toiminnallinen_luokka, liikennevirran_suunta, linkkityyppi
            from tielinkki_ctas where dr1_id = $id"""
      .as[BasicRoadLink].first()
  }

  private def addAdjustment(adjustmentTable: String, adjustmentColumn: String, adjustment: Int, unadjustedValue: Int, mmlId: Long, username: String) = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val optionalAdjustment: Option[Int] = sql"""select #$adjustmentColumn from #$adjustmentTable where mml_id = $mmlId""".as[Int].firstOption
      optionalAdjustment match {
        case Some(existingAdjustment) =>
          if (existingAdjustment != adjustment) {
            sqlu"""update #$adjustmentTable
                     set #$adjustmentColumn = $adjustment,
                         modified_date = current_timestamp,
                         modified_by = $username
                     where mml_id = $mmlId""".execute()
          }
        case None =>
          if (unadjustedValue != adjustment) {
            sqlu"""insert into #$adjustmentTable (mml_id, #$adjustmentColumn, modified_by) values ($mmlId, $adjustment, $username)""".execute()
          }
      }
    }
  }

  def adjustTrafficDirection(id: Long, trafficDirection: TrafficDirection, username: String): Unit = {
    val unadjustedRoadLink: BasicRoadLink = Database.forDataSource(dataSource).withDynTransaction { getRoadLinkProperties(id) }
    val (mmlId, unadjustedTrafficDirection) = (unadjustedRoadLink._2, unadjustedRoadLink._7)
    addAdjustment("adjusted_traffic_direction", "traffic_direction", trafficDirection.value, unadjustedTrafficDirection.value, mmlId, username)
  }

  def adjustFunctionalClass(id: Long, functionalClass: Int, username: String): Unit = {
    val unadjustedRoadLink: BasicRoadLink = Database.forDataSource(dataSource).withDynTransaction { getRoadLinkProperties(id) }
    val (mmlId, unadjustedFunctionalClass) = (unadjustedRoadLink._2, unadjustedRoadLink._6)
    addAdjustment("adjusted_functional_class", "functional_class", functionalClass, unadjustedFunctionalClass, mmlId, username)
  }

  def adjustLinkType(id: Long, linkType: Int, username: String): Unit = {
    val unadjustedRoadLink: BasicRoadLink = Database.forDataSource(dataSource).withDynTransaction { getRoadLinkProperties(id) }
    val (mmlId, unadjustedLinkType) = (unadjustedRoadLink._2, unadjustedRoadLink._8)
    addAdjustment("adjusted_link_type", "link_type", linkType, unadjustedLinkType, mmlId, username)
  }

  private def basicToAdjusted(basic: BasicRoadLink, modification: Option[(DateTime, String)]): AdjustedRoadLink = {
    val (modifiedAt, modifiedBy) = (modification.map(_._1), modification.map(_._2))
    (basic._1, basic._2, basic._3, basic._4,
     basic._5, basic._6, basic._7, modifiedAt.map(DateTimePropertyFormat.print), modifiedBy, basic._8)
  }

  private def adjustedRoadLinks(basicRoadLinks: Seq[BasicRoadLink]): Seq[AdjustedRoadLink] = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val adjustedTrafficDirections: Map[Long, Seq[(Long, Int, DateTime, String)]] = OracleArray.fetchAdjustedTrafficDirectionsByMMLId(basicRoadLinks.map(_._2), Queries.bonecpToInternalConnection(dynamicSession.conn)).groupBy(_._1)
      val adjustedFunctionalClasses: Map[Long, Seq[(Long, Int, DateTime, String)]] = OracleArray.fetchAdjustedFunctionalClassesByMMLId(basicRoadLinks.map(_._2), Queries.bonecpToInternalConnection(dynamicSession.conn)).groupBy(_._1)
      val adjustedLinkTypes: Map[Long, Seq[(Long, Int, DateTime, String)]] = OracleArray.fetchAdjustedLinkTypesMMLId(basicRoadLinks.map(_._2), Queries.bonecpToInternalConnection(dynamicSession.conn)).groupBy(_._1)

      basicRoadLinks.map { basicRoadLink =>
        val mmlId = basicRoadLink._2
        val functionalClass = adjustedFunctionalClasses.get(mmlId).flatMap(_.headOption)
        val adjustedLinkType = adjustedLinkTypes.get(mmlId).flatMap(_.headOption)
        val trafficDirection = adjustedTrafficDirections.get(mmlId).flatMap(_.headOption)

        val functionalClassValue = functionalClass.map(_._2).getOrElse(basicRoadLink._6)
        val adjustedLinkTypeValue = adjustedLinkType.map(_._2).getOrElse(basicRoadLink._8)
        val trafficDirectionValue = trafficDirection.map( trafficDirection =>
          TrafficDirection(trafficDirection._2)
        ).getOrElse(basicRoadLink._7)

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

        basicToAdjusted(basicRoadLink.copy(_6 = functionalClassValue, _7 = trafficDirectionValue, _8 = adjustedLinkTypeValue), modifications.reduce(latestModifications))
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
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds)
      val query =
      s"""
            select dr1_id, mml_id, to_2d(shape), sdo_lrs.geom_segment_length(shape) as length, omistaja, toiminnallinen_luokka, liikennevirran_suunta, linkkityyppi
              from tielinkki_ctas
              where $municipalityFilter $boundingBoxFilter
      """
      Q.queryNA[BasicRoadLink](query).iterator().toSeq
    }
    adjustedRoadLinks(roadLinks)
  }

  protected implicit val jsonFormats: Formats = DefaultFormats
  def getRoadLinksFromVVH(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[AdjustedRoadLink] = {
    val url = "http://10.129.47.146:6080/arcgis/rest/services/VVH_OTH/Basic_data/FeatureServer/query?" +
      "layerDefs=0&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&returnGeometry=true&geometryPrecision=3&f=pjson"
    println(url)
    val request = new HttpGet(url)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    val content = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]]
    val layers = content("layers").asInstanceOf[List[Map[String, Any]]]
    val featureMap: Map[String, Any] = layers.find(map => {map.contains("features")}).get
    val features = featureMap("features").asInstanceOf[List[Map[String, Any]]]
    val basicRoadLinks: Seq[BasicRoadLink] = features.map(feature => {
      val geometry = feature("geometry").asInstanceOf[Map[String, Any]]
      val paths = geometry("paths").asInstanceOf[List[List[List[Double]]]]
      val path: List[List[Double]] = paths.head
      val linkGeometry: Seq[Point] = path.map(point => {
        Point(point(0), point(1))
      })
      val attributes = feature("attributes").asInstanceOf[Map[String, Any]]
      val id = attributes("OBJECTID").asInstanceOf[BigInt].longValue()
      val mmlId = attributes("MTK_ID").asInstanceOf[BigInt].longValue()
      (id, mmlId, linkGeometry, 0.0, Unknown, 4, BothDirections, 3)
    })
    adjustedRoadLinks(basicRoadLinks)
  }

  def getByMunicipality(municipality: Int): Seq[(Long, Seq[Point])] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = s"""select dr1_id, to_2d(shape) from tielinkki_ctas where kunta_nro = $municipality"""
      Q.queryNA[(Long, Seq[Point])](query).iterator().toSeq
    }
  }

  def getByMunicipalityWithProperties(municipality: Int): Seq[Map[String, Any]] = {
    val kalpaRoadLinks = Database.forDataSource(dataSource).withDynTransaction {
      sql"""
        select dr1_id, mml_id, to_2d(shape), omistaja, toiminnallinen_luokka, liikennevirran_suunta, linkkityyppi
          from tielinkki_ctas
          where kunta_nro = $municipality
        """.as[KalpaRoadLink].list()
    }
    val roadLinks: Seq[BasicRoadLink] = kalpaRoadLinks.map { k => (k._1, k._2, k._3, 0.0, k._4, k._5, k._6, k._7) }
    adjustedRoadLinks(roadLinks).map { roadLink =>
      Map("id" -> roadLink._1, "mmlId" -> roadLink._2, "points" -> roadLink._3, "administrativeClass" -> roadLink._5.value,
          "functionalClass" -> roadLink._6, "trafficDirection" -> roadLink._7.value, "linkType" -> roadLink._8)
    }
  }

  def getAdjacent(id: Long): Seq[Map[String, Any]] = {
    val endpoints = getRoadLinkGeometry(id).map(GeometryUtils.geometryEndpoints)
    endpoints.map(endpoint => {
      val roadLinks = Database.forDataSource(dataSource).withDynTransaction {
        val delta: Vector3d = Vector3d(0.1, 0.1, 0)
        val bounds = BoundingRectangle(endpoint._1 - delta, endpoint._1 + delta)
        val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds)

        val bounds2 = BoundingRectangle(endpoint._2 - delta, endpoint._2 + delta)
        val boundingBoxFilter2 = OracleDatabase.boundingBoxFilter(bounds2)

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
