package fi.liikennevirasto.digiroad2

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.GenTraversableOnce
import fi.liikennevirasto.digiroad2.asset.{Property, PropertyValue}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries
import fi.liikennevirasto.digiroad2.util.{RoadAddress, Track}
import org.apache.http.client.methods.{HttpGet, HttpPost, HttpPut, HttpDelete}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClientBuilder
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Formats, StreamInput}

sealed trait StopType {
  def value: String
  def propertyValues: Set[Int]
}
object StopType {
  val values = Set[StopType](Commuter, LongDistance, Combined, Virtual, Unknown)

  def apply(value: String): StopType = {
    values.find(_.value == value).getOrElse(Unknown)
  }

//  def propertyValues() : Unit = {
    def propertyValues() : Set[Int] = {
    values.flatMap(_.propertyValues)
//    values.map(_.propertyValues)
  }

  case object Commuter extends StopType { def value = "paikallis"; def propertyValues = Set(2); }
  case object LongDistance extends StopType { def value = "kauko"; def propertyValues = Set(3); }
  case object Combined extends StopType { def value = "molemmat"; def propertyValues = Set(2,3); }
  case object Virtual extends StopType { def value = "virtuaali"; def propertyValues = Set(5); }
  case object Unknown extends StopType { def value = "tuntematon"; def propertyValues = Set(99); }  // Should not be passed on interface
}

sealed trait Existence {
  def value: String
  def propertyValue: Int
}
object Existence {
  val values = Set(Yes, No, Unknown)

  def apply(value: String): Existence = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def fromPropertyValue(value: String): Existence = {
    value match {
      case "1" => No
      case "2" => Yes
      case _ => Unknown
    }
  }
  //TODO CONFIRM THE PROPERTY VALUES
  case object Yes extends Existence { def value = "on"; def propertyValue = 1; }
  case object No extends Existence { def value = "ei"; def propertyValue = 1; }
  case object Unknown extends Existence { def value = "ei_tietoa"; def propertyValue = 1; }
}

sealed trait Equipment {
  def value: String
  def publicId: String
}
object Equipment {
  val values = Set[Equipment](Timetable, TrashBin, BikeStand, Lighting, Seat, Roof, RoofMaintainedByAdvertiser, ElectronicTimetables, CarParkForTakingPassengers, RaisedBusStop)

  def apply(value: String): Equipment = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def fromPublicId(value: String): Equipment = {
    values.find(_.publicId == value).getOrElse(Unknown)
  }

  case object Timetable extends Equipment { def value = "aikataulu"; def publicId = "aikataulu"; }
  case object TrashBin extends Equipment { def value = "roskis"; def publicId = "roska-astia"; }
  case object BikeStand extends Equipment { def value = "pyorateline"; def publicId = "pyorateline"; }
  case object Lighting extends Equipment { def value = "valaistus"; def publicId = "valaistus"; }
  case object Seat extends Equipment { def value = "penkki"; def publicId = "penkki"; }
  case object Roof extends Equipment { def value = "katos"; def publicId = "katos"; }
  case object RoofMaintainedByAdvertiser extends Equipment { def value = "mainoskatos"; def publicId = "mainoskatos"; }
  case object ElectronicTimetables extends Equipment { def value = "sahkoinen_aikataulunaytto"; def publicId = "sahkoinen_aikataulunaytto"; }
  case object CarParkForTakingPassengers extends Equipment { def value = "saattomahd"; def publicId = "saattomahdollisuus_henkiloautolla"; }
  case object RaisedBusStop extends Equipment { def value = "korotus"; def publicId = "roska-astia"; }
  case object Unknown extends Equipment { def value = "UNKNOWN"; def publicId = "tuntematon"; }
}

sealed trait RoadSide {
  def value: String
  def propertyValues: Set[Int]
}
object RoadSide {
  val values = Set(Right, Left, Off, Unknown)

  def apply(value: String): RoadSide = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def propertyValues() : Set[Int] = {
    values.flatMap(_.propertyValues)
  }

  case object Right extends RoadSide { def value = "oikea"; def propertyValues = Set(1) }
  case object Left extends RoadSide { def value = "vasen"; def propertyValues = Set(2) }
  case object Off extends RoadSide { def value = "paassa"; def propertyValues = Set(99) } // Not supported by OTH
  case object Unknown extends RoadSide { def value = "ei_tietoa"; def propertyValues = Set(0) }
}

case class TierekisteriMassTransitStop(nationalId: Long, liViId: String, roadAddress: RoadAddress,
                                       roadSide: RoadSide, stopType: StopType, express: Boolean,
                                       equipments: Map[Equipment, Existence] = Map(),
                                       stopCode: String, nameFi: String, nameSe: String, modifiedBy: String,
                                       operatingFrom: Date, operatingTo: Date, removalDate: Date)

case class TierekisteriError(content: Map[String, Any], url: String)
class TierekisteriClientException(response: String) extends RuntimeException(response)

/**
  * Utility for using Tierekisteri mass transit stop interface in OTH.
  *
  * @param tierekisteriRestApiEndPoint
  */
class TierekisteriClient(tierekisteriRestApiEndPoint: String) {

  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dateFormat = "yyyy-MM-dd"
  private val serviceName = "pysakit"

  private val trNationalId = "valtakunnallinen_id"
  private val trRoadNumber = "tienumero"
  private val trRoadPartNumber = "tieosanumero"
  private val trLane = "ajorata"
  private val trDistance = "etaisyys"
  private val trSide = "puoli"
  private val trStopCode = "pysakin_tunnus"
  private val trNameFi = "nimi_fi"
  private val trStopType = "pysakin_tyyppi"
  private val trIsExpress = "pikavuoro"
  private val trOperatingFrom = "alkupvm"
  private val trOperatingTo = "loppupvm"
  private val trRemovalDate = "lakkautuspvm"
  private val trLiviId = "livitunnus"
  private val trNameSe = "nimi_se"
  private val trEquipment = "varusteet"
  private val trUser = "kayttajatunnus"

  private def serviceUrl() : String = tierekisteriRestApiEndPoint + serviceName
  private def serviceUrl(id: String) : String = serviceUrl + "/" + id

  private def booleanCodeToBoolean: Map[String, Boolean] = Map("on" -> true, "ei" -> false)
  private def booleanToBooleanCode: Map[Boolean, String] = Map(true -> "on", false -> "ei")

  /**
    * Returns all active mass transit stops.
    * Tierekisteri GET /pysakit/
    *
    * @return
    */
  def fetchActiveMassTransitStops(): Seq[TierekisteriMassTransitStop] = {
    request[List[Map[String, Any]]](serviceUrl) match {
      case Left(content) =>
        content.map{
          stopAsset =>
            mapFields(stopAsset)
        }
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  /**
    * Returns mass transit stop data by livitunnus.
    * Tierekisteri GET /pysakit/{livitunn}
    *
    * @param id   livitunnus
    * @return
    */
  def fetchMassTransitStop(id: String): TierekisteriMassTransitStop = {
    request[Map[String, Any]](serviceUrl(id)) match {
      case Left(content) =>
        mapFields(content)
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  /**
    * Creates a new mass transit stop to Tierekisteri from OTH document.
    * Tierekisteri POST /pysakit/
    *
    * @param trMassTransitStop
    */
  def createMassTransitStop(trMassTransitStop: TierekisteriMassTransitStop): Unit ={
    post(serviceUrl(trMassTransitStop.liViId), trMassTransitStop) match {
      case Some(error) => throw new TierekisteriClientException(error.toString)
      case _ => ; // do nothing
    }
  }

  /**
    * Updates mass transit stop data and ends it, if there is valid to date in OTH JSON document.
    * Tierekisteri PUT /pysakit/{livitunn}
    *
    * @param trMassTransitStop
    */
  def updateMassTransitStop(trMassTransitStop: TierekisteriMassTransitStop): Unit ={
    put(serviceUrl(trMassTransitStop.liViId), trMassTransitStop) match {
      case Some(error) => throw new TierekisteriClientException(error.toString)
      case _ => ;
    }
  }

  /**
    * Deletes mass transit stop from Tierekisteri by livitunnus.
    * Tierekisteri DELETE /pysakit/{livitunn}
    *
    * @param id livitunnus
    */
  def deleteMassTransitStop(id: String): Unit ={
    delete(serviceUrl(id)) match {
      case Some(error) => throw new TierekisteriClientException(error.toString)
      case _ => ;
    }
  }

  private def request[T](url: String): Either[T, TierekisteriError] = {
    val request = new HttpGet(url)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)

    try {
      val statusCode = response.getStatusLine.getStatusCode
      if (statusCode >= 400)
        return Right(TierekisteriError(Map("error" -> "Request returned HTTP Error %d".format(statusCode)), url))
      Left(parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[T])
    } catch {
      case e: Exception => Right(TierekisteriError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  private def post(url: String, trMassTransitStop: TierekisteriMassTransitStop): Option[TierekisteriError] = {
    val request = new HttpPost(url)
    request.setEntity(createJson(trMassTransitStop))
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      if (statusCode >= 400)
        return Some(TierekisteriError(Map("error" -> "Request returned HTTP Error %d".format(statusCode)), url))
     None
    } catch {
      case e: Exception => Some(TierekisteriError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  private def put(url: String, tnMassTransitStop: TierekisteriMassTransitStop): Option[TierekisteriError] = {
    val request = new HttpPut(url)
    request.setEntity(createJson(tnMassTransitStop))
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      if (statusCode >= 400)
        return Some(TierekisteriError(Map("error" -> "Request returned HTTP Error %d".format(statusCode)), url))
      None
    } catch {
      case e: Exception => Some(TierekisteriError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  private def delete(url: String): Option[TierekisteriError] = {
    val request = new HttpDelete(url)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      if (statusCode >= 400)
        return Some(TierekisteriError(Map("error" -> "Request returned HTTP Error %d".format(statusCode)), url))
      None
    } catch {
      case e: Exception => Some(TierekisteriError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  private def createJson(trMassTransitStop: TierekisteriMassTransitStop) = {

    val jsonObj = Map(
      trNationalId -> trMassTransitStop.nationalId,
      trLiviId -> trMassTransitStop.liViId,
      trRoadNumber -> trMassTransitStop.roadAddress.road,
      trRoadPartNumber -> trMassTransitStop.roadAddress.roadPart,
      trSide -> trMassTransitStop.roadSide.value,
      trLane -> trMassTransitStop.roadAddress.track.value,
      trDistance -> trMassTransitStop.roadAddress.mValue,
      trStopCode -> trMassTransitStop.stopCode,
      trIsExpress -> trMassTransitStop.express,
      trNameFi -> trMassTransitStop.nameFi,
      trNameSe -> trMassTransitStop.nameSe,
      trUser -> trMassTransitStop.modifiedBy,
      trOperatingFrom -> convertDateToString(trMassTransitStop.operatingFrom),
      trOperatingTo -> convertDateToString(trMassTransitStop.operatingTo),
      trRemovalDate -> convertDateToString(trMassTransitStop.removalDate),
      trEquipment -> trMassTransitStop.equipments.map{
        case (equipment, existence) =>
          equipment.value -> existence.value
      }
    )

    if(trMassTransitStop.stopType == StopType.Unknown)
      jsonObj++trStopType -> trMassTransitStop.stopType

    new StringEntity(Serialization.write(jsonObj), ContentType.APPLICATION_JSON)
  }

  private def mapFields(data: Map[String, Any]): TierekisteriMassTransitStop = {
    def getMandatoryFieldValue(field: String): String = {
      data.
        get(field).
        getOrElse(throw new TierekisteriClientException("The field '%s' is mandatory".format(field))).
        toString
    }

    val nationalId = convertToLong(data.get(trNationalId)).get
    val liviId = data.get(trLiviId).get.toString
    val roadAddress = RoadAddress(None, convertToInt(data.get(trRoadNumber)).get, convertToInt(data.get(trRoadPartNumber)).get,Track.Combined,1,None)
    val roadSide = RoadSide.apply(data.get(trSide).get.toString)
    val stopType = StopType.apply(data.get(trStopType).get.toString)
    //Not support exception
    val express = booleanCodeToBoolean.get(getMandatoryFieldValue(trIsExpress)).get
    val equipments = extractEquipment(data)
    val stopCode = data.get(trStopCode).get.toString
    val nameFi = data.get(trNameFi).get.toString
    val nameSe = data.get(trNameSe).get.toString
    val modifiedBy = data.get(trUser).get.toString
    val operatingFrom = convertToDate(data.get(trOperatingFrom)).get
    val operatingTo = convertToDate(data.get(trOperatingTo)).get
    val removalDate = convertToDate(data.get(trRemovalDate)).get
    TierekisteriMassTransitStop(nationalId,liviId, roadAddress, roadSide, stopType, express, equipments,
      stopCode, nameFi, nameSe, modifiedBy, operatingFrom, operatingTo, removalDate)
  }

  private def extractEquipment(data: Map[String, Any]) : Map[Equipment, Existence] = {
    val equipmentData = data.get(trEquipment).get.asInstanceOf[Map[String, String]]

    Equipment.values.flatMap{ equipment =>
      equipmentData.get(equipment.value) match{
        case Some(value) =>
          Some(equipment -> Existence.apply(value))
        case None =>
          None
      }
    }.toMap
  }

  private def convertToLong(value: Option[Any]): Option[Long] = {
    value.map {
      case x: Object =>
        try {
          x.toString.toLong
        } catch {
          case e: NumberFormatException =>
            throw new TierekisteriClientException("Invalid value in response: Long expected, got '%s'".format(x))
        }
      case _ => throw new TierekisteriClientException("Invalid value in response: Long expected, got '%s'".format(value.get))
    }
  }

  private def convertToInt(value: Option[Any]): Option[Int] = {
    value.map {
      case x: Object =>
        try {
          x.toString.toInt
        } catch {
          case e: NumberFormatException =>
            throw new TierekisteriClientException("Invalid value in response: Int expected, got '%s'".format(x))
        }
      case _ => throw new TierekisteriClientException("Invalid value in response: Int expected, got '%s'".format(value.get))
    }
  }

  private def convertToDouble(value: Option[Any]): Option[Double] = {
    value.map {
      case x: Object =>
        try {
          x.toString.toDouble
        } catch {
          case e: NumberFormatException =>
            throw new TierekisteriClientException("Invalid value in response: Double expected, got '%s'".format(x))
        }
      case _ => throw new TierekisteriClientException("Invalid value in response: Double expected, got '%s'".format(value.get))
    }
  }

  private def convertToDate(value: Option[Any]): Option[Date] = {
    value.map {
      case x: Object =>
        try {
          new SimpleDateFormat(dateFormat).parse(x.toString)
        } catch {
          case e: ParseException =>
            throw new TierekisteriClientException("Invalid value in response: Date expected, got '%s'".format(x))
        }
      case _ => throw new TierekisteriClientException("Invalid value in response: Date expected, got '%s'".format(value.get))
    }
  }

  private def convertDateToString(date: Date): String = {
    new SimpleDateFormat(dateFormat).format(date);
  }
}

/**
  * A class to transform data between the interface bus stop format and OTH internal bus stop format
  */
class TierekisteriBusStopMarshaller {

  private val liviIdPublicId = "yllapitajan_koodi"
  private val stopTypePublicId = "pysakin_tyyppi"
  private val nameFiPublicId = "nimi suomeksi"
  private val nameSePublicId = "nimi ruotsiksi"
  private val expressPropertyValue = 4
  private val typeId: Int = 10

  def getAllPropertiesAvailable(AssetTypeId : Int): Seq[Property] = {
    Queries.availableProperties(AssetTypeId)
  }

  // TODO: Or variable type: persisted mass transit stop?
  def toTierekisteriMassTransitStop(massTransitStop: MassTransitStopWithProperties): TierekisteriMassTransitStop = {
    TierekisteriMassTransitStop(massTransitStop.nationalId, findLiViId(massTransitStop.propertyData).getOrElse(""),
      RoadAddress(None, 1,1,Track.Combined,1,None), RoadSide.Right, findStopType(massTransitStop.stopTypes),
      massTransitStop.stopTypes.contains(expressPropertyValue), mapEquipments(massTransitStop.propertyData),
      "", "", "", "", new Date, new Date, new Date)
  }


  private def findStopType(stopTypes: Seq[Int]): StopType = {
    // remove from OTH stoptypes the values that are not supported by tierekisteri
    val avaibleStopTypes = StopType.values.flatMap(_.propertyValues).intersect(stopTypes.toSet)
    //TODO try to improve that. Maybe just use a match
    val stopTypeOption = StopType.values.find(st => st.propertyValues.size == avaibleStopTypes.size && avaibleStopTypes.diff(st.propertyValues).isEmpty)

    stopTypeOption match {
      case Some(stopType) =>
        stopType
      case None =>
        StopType.Unknown
    }
  }

  private def mapEquipments(properties: Seq[Property]): Map[Equipment, Existence] = {
    properties.map(p => mapPropertyToEquipment(p) -> mapPropertyValueToExistence(p.values)).
      filterNot(p => p._1.equals(Equipment.Unknown)).toMap
  }

  private def mapPropertyToEquipment(p: Property) = {
    Equipment.fromPublicId(p.publicId)
  }

  private def mapPropertyValueToExistence(values: Seq[PropertyValue]) = {
    val v = values.map(pv => Existence.fromPropertyValue(pv.propertyValue)).distinct
    v.size match {
      case 1 => v.head
      case _ => Existence.Unknown // none or mismatching
    }
  }

  private def findLiViId(properties: Seq[Property]) = {
    properties.find(p => p.publicId.equals(liviIdPublicId)).map(_.values.head.propertyValue)
  }

  // TODO: FROM: Guilherme Pedrosa - Please confirm if this is it
  def fromTierekisteriMassTransitStop(massTransitStop: TierekisteriMassTransitStop): MassTransitStopWithProperties = {
    val allPropertiesAvailable = getAllPropertiesAvailable(typeId)

    val nationalId = massTransitStop.nationalId
    val stopTypes = massTransitStop.stopType.propertyValues
    val validityDirection = massTransitStop.roadSide.propertyValues
    val bearing = massTransitStop.roadAddress.track.value
    val validityPeriod = massTransitStop.operatingFrom.toString() + " " + massTransitStop.operatingTo.toString()
    val floating = false

    val equipmentsProperty = mapEquipmentProperties(massTransitStop.equipments, allPropertiesAvailable)
    val stopTypeProperty = mapStopTypeProperties(massTransitStop.stopType, massTransitStop.express, allPropertiesAvailable)
    val liViIdProperty = mapLiViIdProperties(massTransitStop.liViId, allPropertiesAvailable)
    val nameFiProperty = mapNameFiProperties(massTransitStop.nameFi, allPropertiesAvailable)
    val nameSeProperty = mapNameSeProperties(massTransitStop.nameSe, allPropertiesAvailable)
    val allProperties = liViIdProperty++equipmentsProperty++stopTypeProperty++nameFiProperty++nameSeProperty

    MassTransitStopWithProperties(0L, nationalId, stopTypes.toSeq, 0.0, 0.0, Option(validityDirection.head), Option(bearing), Option(validityPeriod), floating, allProperties)
  }

  private def mapEquipmentProperties(equipments: Map[Equipment, Existence], allProperties: Seq[Property]): Seq[Property] = {
    equipments.map {
      case (equipment, existence) =>
        val equipmentProperties = allProperties.find(p => p.publicId.equals(equipment.publicId)).get
        Property(equipmentProperties.id, equipment.publicId, equipmentProperties.propertyType, equipmentProperties.required, Seq(PropertyValue(existence.propertyValue.toString)))
    }.toSeq
  }

  private def mapStopTypeProperties(stopType: StopType, isExpress: Boolean, allProperties: Seq[Property]): Seq[Property] = {
    var propertyValues = stopType.propertyValues.map { value =>
      PropertyValue(value.toString)
    }
    if (isExpress)
      propertyValues += PropertyValue(expressPropertyValue.toString)

    val stopTypeProperties = allProperties.find(p => p.publicId.equals(stopTypePublicId)).get

    Seq (Property(stopTypeProperties.id, stopTypePublicId, stopTypeProperties.propertyType, stopTypeProperties.required, propertyValues.toSeq))
  }

  private def mapLiViIdProperties(liViId: String, allProperties: Seq[Property]): Seq[Property] = {
    val liViIdProperties = allProperties.find(p => p.publicId.equals(liviIdPublicId)).get

    Seq(Property(liViIdProperties.id, liviIdPublicId, liViIdProperties.propertyType, liViIdProperties.required, Seq(PropertyValue(liViId))))
  }

  private def mapNameFiProperties(nameFi: String, allProperties: Seq[Property]): Seq[Property] = {
    val nameFiProperties = allProperties.find(p => p.publicId.equals(nameFiPublicId)).get

    Seq(Property(nameFiProperties.id, nameFiPublicId, nameFiProperties.propertyType, nameFiProperties.required, Seq(PropertyValue(nameFi))))
  }

  private def mapNameSeProperties(nameSe: String, allProperties: Seq[Property]): Seq[Property] = {
    val nameSeProperties = allProperties.find(p => p.publicId.equals(nameSePublicId)).get

    Seq(Property(nameSeProperties.id, nameSePublicId, nameSeProperties.propertyType, nameSeProperties.required, Seq(PropertyValue(nameSe))))
  }
}