package fi.liikennevirasto.digiroad2.util

import java.io.{File, FileReader, FileWriter}
import java.nio.file.Paths

import java.nio.file.Files.copy

import fi.liikennevirasto.digiroad2.ChangeInfo
import fi.liikennevirasto.digiroad2.asset.{LinkType, TrafficDirection, _}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, ValidityPeriodDayOfWeek}
import org.json4s.JsonAST.{JInt, JString}
import org.json4s.jackson.Serialization.{read, write}
import org.json4s._
import org.slf4j.LoggerFactory

class JsonSerializer extends VVHSerializer {
  val logger = LoggerFactory.getLogger(getClass)
  case object SideCodeSerializer extends CustomSerializer[SideCode](format => ( {
    null
  }, {
    case s: SideCode => JInt(s.value)
  }))

  case object TrafficDirectionSerializer extends CustomSerializer[TrafficDirection](format => ( {
    case JString(direction) => TrafficDirection(direction)
  }, {
    case t: TrafficDirection => JString(t.toString)
  }))

  case object DayofWeekSerializer extends CustomSerializer[ValidityPeriodDayOfWeek](format => ( {
    case JString(dayOfWeek) => ValidityPeriodDayOfWeek(dayOfWeek)
  }, {
    case d: ValidityPeriodDayOfWeek => JString(d.toString)
  }))

  case object LinkTypeSerializer extends CustomSerializer[LinkType](format => ( {
    case JInt(linkType) => LinkType(linkType.toInt)
  }, {
    case lt: LinkType => JInt(BigInt(lt.value))
  }))

  case object AdministrativeClassSerializer extends CustomSerializer[AdministrativeClass](format => ( {
    case JInt(typeInt) => AdministrativeClass(typeInt.toInt)
  }, {
    case ac: AdministrativeClass =>
      JInt(BigInt(ac.value))
  }))

  protected implicit val jsonFormats: Formats = DefaultFormats + SideCodeSerializer + TrafficDirectionSerializer + LinkTypeSerializer + DayofWeekSerializer + AdministrativeClassSerializer

  override def readCachedGeometry(file: File): Seq[RoadLink] = {
    val json = new FileReader(file)
    read[Seq[RoadLink]](json)
  }

  override def readCachedChanges(file: File): Seq[ChangeInfo] = {
    val json = new FileReader(file)
    read[Seq[ChangeInfo]](json)
  }
  override def writeCache(file: File, objects: Seq[Object]): Boolean = {

    val tmpFile = File.createTempFile("tmp", "cached")
    tmpFile.deleteOnExit()

    write(objects, new FileWriter(tmpFile))

    if (file.exists())
      file.delete()

    try {
      copy(Paths.get(tmpFile.getAbsolutePath), Paths.get(file.getAbsolutePath))
      return true
    } catch {
      case ex: Exception => logger.warn("Copy failed", ex)
    } finally {
      try {
        if (tmpFile != null) tmpFile.delete()
      } catch {
        case ex: Exception => logger.info("Deleting tmp file failed", ex)
      }
    }
    false

  }
}
