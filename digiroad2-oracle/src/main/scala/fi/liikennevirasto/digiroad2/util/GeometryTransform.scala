package fi.liikennevirasto.digiroad2.util

import java.util.Properties

import fi.liikennevirasto.digiroad2.Point
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.json4s._
import org.json4s.jackson.JsonMethods._
/**
  * A road consists of 1-2 tracks (fi: "ajorata"). 2 tracks are separated by a fence or grass for example.
  * Left and Right are relative to the advancing direction (direction of growing m values)
  */
sealed trait Track {
  def value: Int
}
object Track {
  val values = Set(Combined, RightSide, LeftSide, Unknown)

  def apply(intValue: Int): Track = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Combined extends Track { def value = 0 }
  case object RightSide extends Track { def value = 1 }
  case object LeftSide extends Track { def value = 2 }
  case object Unknown extends Track { def value = 99 }
}

/**
  * Road Side (fi: "puoli") tells the relation of an object and the track. For example, the side
  * where the bus stop is.
  */
sealed trait RoadSide {
  def value: Int
}
object RoadSide {
  val values = Set(Right, Left, Between, End, Middle, Across, Unknown)

  def apply(intValue: Int): RoadSide = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Right extends RoadSide { def value = 1 }
  case object Left extends RoadSide { def value = 2 }
  case object Between extends RoadSide { def value = 3 }
  case object End extends RoadSide { def value = 7 }
  case object Middle extends RoadSide { def value = 8 }
  case object Across extends RoadSide { def value = 9 }
  case object Unknown extends RoadSide { def value = 0 }
}

/**
  * Lanes 1-* are on a track.
  */
sealed trait Lane {
  def value: Int
}
object Lane {
  val values = Set(Main, ByPassOnLeft, AdditionalOnRight, Other)

  def apply(intValue: Int): Lane = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Main extends Lane { def value = 1 }
  case object ByPassOnLeft extends Lane { def value = 2 }
  case object AdditionalOnRight extends Lane { def value = 3 }
  case object Other extends Lane { def value = 4 }
  case object Unknown extends Lane { def value = 99 }
}

case class VKMError(content: Map[String, Any], url: String)
case class RoadAddress(municipalityCode: Option[String], road: Int, roadPart: Int, track: Track, mValue: Int, deviation: Option[Double])

class GeometryTransform {
  class VKMClientException(response: String) extends RuntimeException(response)
  protected implicit val jsonFormats: Formats = DefaultFormats

  private def vkmUrl = {
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream("/digiroad2.properties"))
    properties.getProperty("digiroad2.VKMUrl")
  }
  private def urlParams(paramMap: Map[String, Option[Any]]) = {
    paramMap.filter(entry => entry._2.nonEmpty).map(entry => entry._1 + "=" + entry._2.get).mkString("&")
  }

  private def request(url: String): Either[List[Map[String, Any]], VKMError] = {
    val request = new HttpGet(url)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
      val content: Map[String, Any] = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]]
      content.get("tieosoitteet").map(_.asInstanceOf[List[Map[String, Any]]])
        .map(Left(_)).getOrElse(Right(VKMError(content, url)))
    } finally {
      response.close()
    }
  }

  def coordToAddress(coord: Point, road: Option[Int] = None, roadPart: Option[Int] = None,
                     distance: Option[Int] = None, track: Option[Track]) = {
    val params = Map("tie" -> road, "osa" -> roadPart, "etaisyys" -> distance, "ajorata" -> track.map(_.value),
    "x" -> Option(coord.x), "y" -> Option(coord.y))
    request(vkmUrl + urlParams(params)) match {
      case Left(address) => address.map(mapFields)
      case Right(error) => throw new VKMClientException(error.toString)
    }
  }

  private def extractRoadAddresses(data: List[Map[String, Any]]) = {
    data.map(x => )
  }

  private def mapFields(data: Map[String, Any]) = {
    val municipalityCode = data.get("kuntanro")
    val road = validateAndConvertToInt("tie", data)
    val roadPart = validateAndConvertToInt("osa", data)
    val track = validateAndConvertToInt("ajorata", data)
    val mValue = validateAndConvertToInt("etaisyys", data)
    val deviation = convertToDouble(data.get("valimatka")).get
    if (Track.apply(track).eq(Track.Unknown)) {
      throw new VKMClientException("Invalid value for Track (ajorata): %d".format(track))
    }
    RoadAddress(municipalityCode.map(_.toString), road, roadPart, Track.apply(track), mValue, deviation)
  }

  private def validateAndConvertToInt(fieldName: String, map: Map[String, Any]) = {
    def value = map.get(fieldName)
    if (value.isEmpty)
      throw new VKMClientException(
        "Missing mandatory field in response: %s".format(
          fieldName))
    value.get match {
      case Some(x: Int) => x
      case _ => throw new VKMClientException("Invalid value in response: %s, Int expected, got '%s'".format(
        fieldName, value.get))
    }
  }

  private def convertToDouble(value: Option[Any]) = {
    value.map {
      case Some(x: Double) => Option(x)
      case _ => None
    }
  }
}
