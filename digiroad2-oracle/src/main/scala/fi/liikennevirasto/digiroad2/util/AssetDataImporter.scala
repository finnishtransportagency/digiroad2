package fi.liikennevirasto.digiroad2.dataimport

import javax.sql.DataSource
import com.jolbox.bonecp.{BoneCPDataSource, BoneCPConfig}
import java.util.Properties
import scala.slick.driver.JdbcDriver.backend.{Database, DatabaseDef}
import scala.slick.jdbc.{StaticQuery => Q, GetResult, PositionedParameters, SetParameter}
import Database.dynamicSession
import Q.interpolation
import java.io.{ByteArrayOutputStream, BufferedInputStream}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.LocalDate
import com.github.tototoshi.slick.MySQLJodaSupport._
import oracle.sql.STRUCT
import org.slf4j.LoggerFactory
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetProvider
import fi.liikennevirasto.digiroad2.util.GeometryUtils
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.dataimport.AssetDataImporter._
import scala.io.Source

object AssetDataImporter {
  case class SimpleBusStop(shelterType: Int, busStopType: Seq[Int], lrmPositionId: Long, validFrom: LocalDate = LocalDate.now, validTo: Option[LocalDate] = None, externalId: Option[Long] = None)
  case class SimpleLRMPosition(id: Long, roadLinkId: Long, laneCode: Int, sideCode: Int, startMeasure: Int, endMeasure: Int)
  case class SimpleRoadLink(id: Long, roadType: Int, roadNumber: Int, roadPartNumber: Int, functionalClass: Int, rStartHn: Int, lStartHn: Int,
                            rEndHn: Int, lEndHn: Int, municipalityNumber: Int, geom: STRUCT)

  sealed trait ImportDataSet {
    def database(): DatabaseDef
    val roadLinkTable: String
    val busStopTable: String
  }

  case object TemporaryTables extends ImportDataSet {
    lazy val dataSource: DataSource = {
      val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/import.bonecp.properties"))
      new BoneCPDataSource(cfg)
    }

    def database() = Database.forDataSource(dataSource)
    val roadLinkTable: String = "temp2_tielinkki"
    val busStopTable: String = "temp2_lineaarilokaatio"
  }

  case object Conversion extends ImportDataSet {
    lazy val dataSource: DataSource = {
      val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/conversion.bonecp.properties"))
      new BoneCPDataSource(cfg)
    }

    def database() = Database.forDataSource(dataSource)
    val roadLinkTable: String = "tielinkki"
    val busStopTable: String = "lineaarilokaatio"
  }
}

class AssetDataImporter {
  val logger = LoggerFactory.getLogger(getClass)
  lazy val ds: DataSource = initDataSource
  lazy val assetProvider = new OracleSpatialAssetProvider(new OracleUserProvider)

  val shelterTypes = Map[Int, Int](1 -> 1, 2 -> 2, 0 -> 99, 99 -> 99)
  val busStopTypes = Map[Int, Seq[Int]](1 -> Seq(1), 2 -> Seq(2), 3 -> Seq(3), 4 -> Seq(2, 3), 5 -> Seq(3, 4), 6 -> Seq(2, 3, 4), 7 -> Seq(99), 99 -> Seq(99),  0 -> Seq(99))
  val imagesForBusStopTypes = Map[String, String] ("1" -> "/raitiovaunu.png", "2" -> "/paikallisliikenne.png", "3" -> "/kaukoliikenne.png", "4" -> "/pikavuoro.png", "99" -> "/pysakki_ei_tiedossa.png")
  val Modifier = "automatic_import"

  implicit val getSimpleBusStop = GetResult[(SimpleBusStop, SimpleLRMPosition)] { r =>
    val bs = SimpleBusStop(shelterTypes(r.<<), busStopTypes(r.<<), r.<<)
    val lrm = SimpleLRMPosition(bs.lrmPositionId, r.<<, r.<<, r.<<, r.<<, r.<<)
    (bs, lrm)
  }
  implicit val getSimpleRoadLink = GetResult[SimpleRoadLink](r => SimpleRoadLink(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.nextObject().asInstanceOf[STRUCT]))

  implicit object SetByteArray extends SetParameter[Array[Byte]] {
    def apply(v: Array[Byte], pp: PositionedParameters) {
      pp.setBytes(v)
    }
  }

  implicit object SetStruct extends SetParameter[STRUCT] {
    def apply(v: STRUCT, pp: PositionedParameters) {
      pp.setObject(v, java.sql.Types.STRUCT)
    }
  }

  def importRoadlinks(dataSet: ImportDataSet) = insertRoadLinks(roadLinksToImport(dataSet))

  private def roadLinksToImport(dataSet: ImportDataSet) = {
    val table = dataSet.roadLinkTable
    dataSet.database().withDynSession {
      sql"""
        select tunnus, nvl(formofway,99), tienro, tieosanro, functionalroadclass, ens_talo_o, ens_talo_v,
        viim_talo_o, viim_talo_v, kunta_nro, shape from #$table
      """.as[SimpleRoadLink].list
    }
  }

  def insertRoadLinks(roadLinks: Seq[SimpleRoadLink]) {
    Database.forDataSource(ds).withDynTransaction {
      roadLinks.foreach { rl =>
        println("ROADLINK: " + rl)
        try {
          sqlu"""
            insert into road_link (id, road_type, road_number, road_part_number, functional_class, r_start_hn,
              l_start_hn, r_end_hn, l_end_hn, municipality_number, geom)
            values (${rl.id}, ${rl.roadType}, ${rl.roadNumber}, ${rl.roadPartNumber}, ${rl.functionalClass}, ${rl.rStartHn},
              ${rl.lStartHn}, ${rl.rEndHn}, ${rl.lEndHn}, ${rl.municipalityNumber}, ${rl.geom})
          """.execute
        } catch {
          // TODO: current import data does not contain validity dates to resolve duplicate IDs (tunnus) so just disregard individual errors (duplicates) for now
          // TODO: should tunnus be mapped to unique road_link.id as it is now (ie could there be several road links with same tunnus)?
          case e: Exception => logger.error("Can't import roadlink", e)
        }
      }
    }
  }

  def importBusStops(dataSet: ImportDataSet) = {
    val busStopsAndPositions = busStopsToImport(dataSet)
    insertLrmPositions(busStopsAndPositions.map(_._2).toList)
    insertBusStops(busStopsAndPositions.map(_._1).toList)
  }

  private def busStopsToImport(dataSet: ImportDataSet) = {
    val table = dataSet.busStopTable
    dataSet.database().withDynSession {
      sql"""
        select katos, pysakkityyppi, objectid, tielinkkitunnus, kaista, puoli, alkum, loppum from #$table
      """.as[(SimpleBusStop, SimpleLRMPosition)].list
    }
  }

  def insertLrmPositions(lrmPositions: Seq[SimpleLRMPosition]) {
    Database.forDataSource(ds).withDynTransaction {
      lrmPositions.foreach { lrm =>
        println("LRM POSITION: " + lrm)
        try {
          sqlu"""
          insert into lrm_position (id, road_link_id, event_type, lane_code, side_code, start_measure, end_measure)
          values (${lrm.id}, ${lrm.roadLinkId}, 1, ${lrm.laneCode}, ${lrm.sideCode}, ${lrm.startMeasure}, ${lrm.endMeasure})
        """.execute
        } catch {
          case e: Exception => logger.error("Can't insert lrm position", e)
        }
      }
    }
  }

  def insertBusStops(busStops: Seq[SimpleBusStop]) {
    Database.forDataSource(ds).withDynSession {
      val busStopTypePropertyId = sql"select id from property where name_fi = 'Pysäkin tyyppi'".as[Long].first
      val shelterTypePropertyId = sql"select id from property where name_fi = 'Pysäkin katos'".as[Long].first
      val reachabilityPropertyId = sql"select id from property where name_fi = 'Pysäkin saavutettavuus'".as[Long].first
      val accessibilityPropertyId = sql"select id from property where name_fi = 'Esteettömyystiedot'".as[Long].first
      val administratorPropertyId = sql"select id from property where name_fi = 'Ylläpitäjä'".as[Long].first
      val busStopAssetTypeId = sql"select id from asset_type where name = 'Bussipysäkit'".as[Long].first

      importMunicipalityCodes

      insertImages(busStopTypePropertyId)

      busStops.foreach { busStop =>
        try {
          println("BUS STOP: " + busStop)
          val assetId = sql"select primary_key_seq.nextval from dual".as[Long].first

          sqlu"""
            insert into asset(id, external_id, asset_type_id, lrm_position_id, created_by, valid_from, valid_to)
            values($assetId, ${busStop.externalId}, $busStopAssetTypeId, ${busStop.lrmPositionId}, $Modifier, ${busStop.validFrom}, ${busStop.validTo.getOrElse(null)})
          """.execute

          val bearing = assetProvider.getAssetById(assetId) match {
            case Some(a) => {
              assetProvider.getRoadLinkById(a.roadLinkId) match {
                case Some(rl) => GeometryUtils.calculateBearing(a, rl)
                case None => 0.0 // TODO log/throw error?
              }
            }
            case None => 0.0 // TODO log/throw error?
          }
          sqlu"update asset set bearing = $bearing where id = $assetId".execute
          busStop.busStopType.foreach { busStopType =>
            insertMultipleChoiceValue(busStopTypePropertyId, assetId, busStopType)
          }

          insertTextPropertyData(reachabilityPropertyId, assetId, "Ei tiedossa")

          insertTextPropertyData(accessibilityPropertyId, assetId, "Ei tiedossa")

          insertSingleChoiceValue(administratorPropertyId, assetId, 4)

          insertSingleChoiceValue(shelterTypePropertyId, assetId, busStop.shelterType)
        } catch {
          case e: Exception => logger.error("Cannot insert " + busStop, e)
        }
      }
    }
  }

  def insertTextPropertyData(propertyId: Long, assetId: Long, text:String) {
    sqlu"""
      insert into text_property_value(id, property_id, asset_id, value_fi, value_sv, created_by)
      values (primary_key_seq.nextval, $propertyId, $assetId, $text, ' ', $Modifier)
    """.execute
  }

  def insertMultipleChoiceValue(propertyId: Long, assetId: Long, value: Int) {
    sqlu"""
      insert into multiple_choice_value(id, property_id, asset_id, enumerated_value_id, modified_by)
      values (primary_key_seq.nextval, $propertyId, $assetId,
        (select id from enumerated_value where value = $value and property_id = $propertyId), $Modifier)
    """.execute
  }

  def insertSingleChoiceValue(propertyId: Long, assetId: Long, value: Int) {
    sqlu"""
      insert into single_choice_value(property_id, asset_id, enumerated_value_id, modified_by)
      values ($propertyId, $assetId, (select id from enumerated_value where value = $value and property_id = $propertyId), $Modifier)
    """.execute
  }

  def insertImages(busStopTypePropertyId: Long) {
    imagesForBusStopTypes.foreach { keyVal =>
      val s = getClass.getResourceAsStream(keyVal._2)
      val bis = new BufferedInputStream(s)
      val fos = new ByteArrayOutputStream(65535)
      val buf = new Array[Byte](1024)
      Stream.continually(bis.read(buf)).takeWhile(_ != -1).foreach(fos.write(buf, 0, _))
      val byteArray = fos.toByteArray

      sqlu"""
        insert into image (id, created_by, modified_date, file_name, image_data)
        values (${keyVal._1}, $Modifier, current_timestamp, ${keyVal._2.tail}, $byteArray)
      """.execute

      sqlu"""
        update enumerated_value set image_id = ${keyVal._1} where property_id = $busStopTypePropertyId and value = ${keyVal._1}
      """.execute
    }
  }

  def importMunicipalityCodes() = {
    val src = Source.fromInputStream(getClass.getResourceAsStream("/kunnat_ja_elyt_2014.csv"))
    src.getLines().toList.drop(1).map(row => {
      var elems = row.replace("\"", "").split(";");
      sqlu"""
        insert into municipality(id, name_fi, name_sv) values( ${elems(0).toInt}, ${elems(1)}, ${elems(2)} )
      """.execute
      sqlu"""
        insert into ely(id, name_fi, municipality_id) values( ${elems(3).toInt}, ${elems(4)}, ${elems(0).toInt} )
      """.execute
    })
  }

  private[this] def initDataSource: DataSource = {
    Class.forName("oracle.jdbc.driver.OracleDriver")
    val cfg = new BoneCPConfig(localProperties)
    new BoneCPDataSource(cfg)
  }

  lazy val localProperties: Properties = {
    val props = new Properties()
    try {
      props.load(getClass.getResourceAsStream("/bonecp.properties"))
    } catch {
      case e: Exception => throw new RuntimeException("Can't load local.properties for env: " + System.getProperty("env"), e)
    }
    props
  }
}
