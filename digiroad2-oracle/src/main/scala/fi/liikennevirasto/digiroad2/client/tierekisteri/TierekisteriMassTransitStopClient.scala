package fi.liikennevirasto.digiroad2.client.tierekisteri

import java.text.SimpleDateFormat
import java.util.Date

import fi.liikennevirasto.digiroad2.asset.{Property, PropertyValue}
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.PersistedMassTransitStop
import fi.liikennevirasto.digiroad2.util.{RoadAddress, RoadSide, Track}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.CloseableHttpClient
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s.jackson.Serialization

/**
  * Values for Existence (Olemassaolo) enumeration
  */
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

  case object Yes extends Existence { def value = "on"; def propertyValue = 2; }
  case object No extends Existence { def value = "ei"; def propertyValue = 1; }
  case object Unknown extends Existence { def value = "ei_tietoa"; def propertyValue = 99; }
}


/**
  * Values for Equipment (Varuste) enumeration
  */
sealed trait Equipment {
  def value: String
  def publicId: String
  def isMaster: Boolean
}
object Equipment {
  val values = Set[Equipment](Timetable, TrashBin, BikeStand, Lighting, Seat, Roof, RoofMaintainedByAdvertiser, ElectronicTimetables, CarParkForTakingPassengers, RaisedBusStop)

  def apply(value: String): Equipment = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def fromPublicId(value: String): Equipment = {
    values.find(_.publicId == value).getOrElse(Unknown)
  }

  case object Timetable extends Equipment { def value = "aikataulu"; def publicId = "aikataulu"; def isMaster = true; }
  case object TrashBin extends Equipment { def value = "roskis"; def publicId = "roska_astia"; def isMaster = true; }
  case object BikeStand extends Equipment { def value = "pyorateline"; def publicId = "pyorateline"; def isMaster = true; }
  case object Lighting extends Equipment { def value = "valaistus"; def publicId = "valaistus"; def isMaster = true; }
  case object Seat extends Equipment { def value = "penkki"; def publicId = "penkki"; def isMaster = true; }
  case object Roof extends Equipment { def value = "katos"; def publicId = "katos"; def isMaster = false; }
  case object RoofMaintainedByAdvertiser extends Equipment { def value = "mainoskatos"; def publicId = "mainoskatos"; def isMaster = false; }
  case object ElectronicTimetables extends Equipment { def value = "sahk_aikataulu"; def publicId = "sahkoinen_aikataulunaytto"; def isMaster = false; }
  case object CarParkForTakingPassengers extends Equipment { def value = "saattomahd"; def publicId = "saattomahdollisuus_henkiloautolla"; def isMaster = false; }
  case object RaisedBusStop extends Equipment { def value = "korotus"; def publicId = "korotettu"; def isMaster = false; }
  case object Unknown extends Equipment { def value = "UNKNOWN"; def publicId = "tuntematon"; def isMaster = false; }
}

/**
  * Values for Stop type (Pysäkin tyyppi) enumeration
  */
sealed trait StopType {
  def value: String
  def propertyValues: Set[Int]
}
object StopType {
  val values = Set[StopType](Commuter, LongDistance, Combined, Virtual, Unknown)

  def apply(value: String): StopType = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def propertyValues() : Set[Int] = {
    values.flatMap(_.propertyValues)
  }

  case object Commuter extends StopType { def value = "paikallis"; def propertyValues = Set(2); }
  case object LongDistance extends StopType { def value = "kauko"; def propertyValues = Set(3); }
  case object Combined extends StopType { def value = "molemmat"; def propertyValues = Set(2,3); }
  case object Virtual extends StopType { def value = "virtuaali"; def propertyValues = Set(5); }
  case object Unknown extends StopType { def value = "tuntematon"; def propertyValues = Set(99); }  // Should not be passed on interface
}

case class TierekisteriMassTransitStop(nationalId: Long,
                                       liviId: String,
                                       roadAddress: RoadAddress,
                                       roadSide: TRRoadSide,
                                       stopType: StopType,
                                       express: Boolean,
                                       equipments: Map[Equipment, Existence] = Map(),
                                       stopCode: Option[String],
                                       nameFi: Option[String],
                                       nameSe: Option[String],
                                       modifiedBy: String,
                                       operatingFrom: Option[Date],
                                       operatingTo: Option[Date],
                                       removalDate: Option[Date],
                                       inventoryDate: Date)

class TierekisteriMassTransitStopClient(trEndPoint: String, trEnabled: Boolean, httpClient: CloseableHttpClient) extends TierekisteriClient{

  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnabled
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriMassTransitStop

  private val serviceName = "pysakit/"

  private val trNationalId = "valtakunnallinen_id"
  private val trRoadNumber = "tie"        // tienumero
  private val trRoadPartNumber = "aosa"   // tieosanumero
  private val trLane = "ajr"              // ajorata
  private val trDistance = "aet"          // etaisyys
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
  private val trInventoryDate = "inventointipvm"
  private val serviceUrl : String = tierekisteriRestApiEndPoint + serviceName

  private def serviceUrl(id: String) : String = serviceUrl + id
  private def serviceUrl(replaceIdOption: Option[String]) : String = {
    replaceIdOption match {
      case Some(replaceId) => serviceUrl + "?replace=" + replaceId
      case _ => serviceUrl
    }
  }

  private def booleanCodeToBoolean: Map[String, Boolean] = Map("on" -> true, "ei" -> false)
  private def booleanToBooleanCode: Map[Boolean, String] = Map(true -> "on", false -> "ei")


  private val toIso8601 = DateTimeFormat.forPattern("yyyy-MM-dd")

  /**
    * Return all bus stops currently active from Tierekisteri
    * Tierekisteri REST API endpoint: GET /pysakit/
    *
    * @return
    */
  def fetchActiveMassTransitStops(): Seq[TierekisteriMassTransitStop] = {
    request[List[Map[String, Any]]](serviceUrl) match {
      case Left(content) =>
        content.flatMap{
          stopAsset =>
            mapFields(stopAsset)
        }
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  /**
    * Returns the anwser to the question "Is Tierekisteri Enabled?".
    *
    * @return Type: Boolean - If TR client is enabled
    */
  def isTREnabled : Boolean = {
    tierekisteriEnabled
  }

  /**
    * Returns a bus stop based on OTH "yllapitajan_koodi" id
    * Tierekisteri REST API endpoint: GET /pysakit/{livitunn}
    *
    * @param id
    * @return
    */
  def fetchMassTransitStop(id: String): Option[TierekisteriMassTransitStop] = {
    logger.info("Requesting stop %s from Tierekisteri".format(id))
    request[Map[String, Any]](serviceUrl(id)) match {
      case Left(content) =>
        mapFields(content)
      case Right(null) =>
        None
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  /**
    * Creates a new bus stop to Tierekisteri.
    * Tierekisteri REST API endpoint: POST /pysakit/
    *
    * @param trMassTransitStop
    */
  def createMassTransitStop(trMassTransitStop: TierekisteriMassTransitStop, replaceLiviId: Option[String] = None): Unit ={
    logger.info("Creating stop %s in Tierekisteri".format(trMassTransitStop.liviId))
    post(serviceUrl(replaceLiviId), trMassTransitStop, createJson) match {
      case Some(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
      case _ => ; // do nothing
    }
  }

  /**
    * Updates and/or invalidates a stop. (If valid_to is set, the stop is invalidated. Other data may be updated at the time, too)
    * Tierekisteri REST API endpoint: PUT /pysakit/{livitunn}
    *
    * @param trMassTransitStop
    */
  def updateMassTransitStop(trMassTransitStop: TierekisteriMassTransitStop, overrideLiviIdOption: Option[String], overrideUserNameOption: Option[String] = None): Unit ={
    val liviId = overrideLiviIdOption.getOrElse(trMassTransitStop.liviId)
    val trStop = trMassTransitStop.copy(modifiedBy = overrideUserNameOption.getOrElse(trMassTransitStop.modifiedBy))
    logger.info("Updating stop %s in Tierekisteri".format(liviId))
    put(serviceUrl(liviId), trStop, createJson) match {
      case Some(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
      case _ => ;
    }
  }

  /**
    * Marks a bus stop to be removed. Only for error correcting purposes, for example when bus stop was accidentally added.
    * Tierekisteri REST API endpoint: DELETE /pysakit/{livitunn}
    *
    * @param id
    */
  def deleteMassTransitStop(id: String): Unit ={
    logger.info("REMOVING stop %s in Tierekisteri".format(id))
    delete(serviceUrl(id)) match {
      case Some(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
      case _ => ;
    }
  }


  protected def createJson(trMassTransitStop: TierekisteriType) = {

    val jsonObj = Map(
      trNationalId -> trMassTransitStop.nationalId,
      trLiviId -> trMassTransitStop.liviId,
      trRoadNumber -> trMassTransitStop.roadAddress.road,
      trRoadPartNumber -> trMassTransitStop.roadAddress.roadPart,
      trSide -> trMassTransitStop.roadSide.value,
      trLane -> trMassTransitStop.roadAddress.track.value,
      trDistance -> trMassTransitStop.roadAddress.mValue,
      trStopCode -> trMassTransitStop.stopCode,
      trIsExpress -> booleanToBooleanCode.get(trMassTransitStop.express),
      trNameFi -> trMassTransitStop.nameFi,
      trNameSe -> trMassTransitStop.nameSe,
      trUser -> trMassTransitStop.modifiedBy,
      trOperatingFrom -> convertDateToString(trMassTransitStop.operatingFrom),
      trOperatingTo -> convertDateToString(trMassTransitStop.operatingTo),
      trRemovalDate -> convertDateToString(trMassTransitStop.removalDate),
      trInventoryDate -> convertDateToString(trMassTransitStop.inventoryDate),
      trEquipment -> trMassTransitStop.equipments.map{
        case (equipment, existence) =>
          equipment.value -> existence.value
      }
    )

    val stopType: Map[String, Any] = trMassTransitStop.stopType match {
      case StopType.Unknown => Map()
      case _ => Map(trStopType -> trMassTransitStop.stopType.value)
    }

    val json = Serialization.write(jsonObj ++ stopType)
    // Print JSON sent to Tierekisteri for testing purposes
    logger.info("Stop in JSON: %s".format(json))

    new StringEntity(json, ContentType.APPLICATION_JSON)
  }

  override def mapFields(data: Map[String, Any]): Option[TierekisteriMassTransitStop] = {

    //Mandatory fields
    val nationalId = convertToLong(getMandatoryFieldValue(data, trNationalId)).get
    val roadSide = TRRoadSide.apply(getMandatoryFieldValue(data, trSide).get)
    val express = booleanCodeToBoolean.getOrElse(getMandatoryFieldValue(data, trIsExpress).get, throw new TierekisteriClientException("The boolean code '%s' is not supported".format(getFieldValue(data, trIsExpress))))
    val liviId = getMandatoryFieldValue(data, trLiviId).get
    val stopType = StopType.apply(getMandatoryFieldValue(data, trStopType).get)
    val modifiedBy = getMandatoryFieldValue(data, trUser).get
    val roadAddress = RoadAddress(None, convertToInt(getMandatoryFieldValue(data, trRoadNumber)).get,
      convertToInt(getMandatoryFieldValue(data, trRoadPartNumber)).get,Track.Combined,convertToInt(getMandatoryFieldValue(data, trDistance)).get,None)

    //Not mandatory fields
    val equipments = extractEquipment(data)
    val stopCode = getFieldValue(data, trStopCode)
    val nameFi = getFieldValue(data, trNameFi)
    val nameSe = getFieldValue(data, trNameSe)
    val operatingFrom = convertToDate(getFieldValue(data, trOperatingFrom))
    val operatingTo = convertToDate(getFieldValue(data, trOperatingTo))
    val removalDate = convertToDate(getFieldValue(data, trRemovalDate))
    val inventoryDate = convertToDate(Some(getFieldValue(data, trInventoryDate).getOrElse(toIso8601.print(DateTime.now())))).get

    Some(TierekisteriMassTransitStop(nationalId,liviId, roadAddress, roadSide, stopType, express, equipments,
      stopCode, nameFi, nameSe, modifiedBy, operatingFrom, operatingTo, removalDate, inventoryDate))
  }

  private def extractEquipment(data: Map[String, Any]) : Map[Equipment, Existence] = {
    val equipmentData: Map[String, String] = data.get(trEquipment).nonEmpty match {
      case true => data.get(trEquipment).get.asInstanceOf[Map[String, String]]
      case false => Map()
    }

    Equipment.values.flatMap{ equipment =>
      equipmentData.get(equipment.value) match{
        case Some(value) =>
          Some(equipment -> Existence.apply(value))
        case None =>
          None
      }
    }.toMap
  }
}

/**
  * A class to transform data between the interface bus stop format and OTH internal bus stop format
  */
object TierekisteriBusStopMarshaller {

  private val liviIdPublicId = "yllapitajan_koodi"
  private val stopTypePublicId = "pysakin_tyyppi"
  private val nameFiPublicId = "nimi_suomeksi"
  private val nameSePublicId = "nimi_ruotsiksi"
  private val stopCode = "matkustajatunnus"
  private val InventoryDatePublicId = "inventointipaiva"
  private val FirstDayValidPublicId = "ensimmainen_voimassaolopaiva"
  private val LastDayValidPublicId = "viimeinen_voimassaolopaiva"
  private val expressPropertyValue = 4
  private val typeId: Int = 10
  private val dateFormat = "yyyy-MM-dd"

  private def convertDateToString(date: Option[Date]): Option[String] = {
    date.map(dv => new SimpleDateFormat(dateFormat).format(dv))
  }

  private def convertStringToDate(str: Option[String]): Option[Date] = {
    if(str.exists(_.trim.nonEmpty))
      Some(new SimpleDateFormat(dateFormat).parse(str.get))
    else
      None
  }

  def getAllPropertiesAvailable(AssetTypeId : Int): Seq[Property] = {
    Queries.availableProperties(AssetTypeId)
  }


  def getPropertyOption(propertyData: Seq[Property], publicId: String): Option[String] = {
    propertyData.find(p => p.publicId == publicId).flatMap(_.values.headOption.map(_.propertyValue))
  }
  def getPropertyDateOption(propertyData: Seq[Property], publicId: String): Option[Date] = {
    convertStringToDate(propertyData.find(p => p.publicId == publicId).flatMap(_.values.headOption.map(_.propertyValue)))
  }

  def toTRRoadSide(roadSide: RoadSide) = {
    roadSide match {
      case RoadSide.Right => TRRoadSide.Right
      case RoadSide.Left => TRRoadSide.Left
      case RoadSide.End => TRRoadSide.Off
      case _ => TRRoadSide.Unknown
    }
  }
  def toTierekisteriMassTransitStop(massTransitStop: PersistedMassTransitStop, roadAddress: RoadAddress,
                                    roadSideOption: Option[RoadSide], expireDate: Option[Date] = None, overrideLiviId: Option[String] = None): TierekisteriMassTransitStop = {
    val inventoryDate = convertStringToDate(getPropertyOption(massTransitStop.propertyData, InventoryDatePublicId)).getOrElse(new Date)
    val startingDate = convertStringToDate(getPropertyOption(massTransitStop.propertyData, FirstDayValidPublicId))
    val lastDate = if (expireDate.nonEmpty) expireDate else convertStringToDate(getPropertyOption(massTransitStop.propertyData, LastDayValidPublicId))
    TierekisteriMassTransitStop(massTransitStop.nationalId, findLiViId(massTransitStop.propertyData).getOrElse(overrideLiviId.getOrElse("")),
      roadAddress, roadSideOption.map(toTRRoadSide).getOrElse(TRRoadSide.Unknown), findStopType(massTransitStop.stopTypes),
      massTransitStop.stopTypes.contains(expressPropertyValue), mapEquipments(massTransitStop.propertyData),
      getPropertyOption(massTransitStop.propertyData, stopCode),
      getPropertyOption(massTransitStop.propertyData, nameFiPublicId),
      getPropertyOption(massTransitStop.propertyData, nameSePublicId),
      massTransitStop.modified.modifier.getOrElse(massTransitStop.created.modifier.get),
      startingDate, lastDate, None, inventoryDate)
  }

  // Map Seq(2) => local, Seq(2,3) => Combined, Seq(3) => Long distance, Seq(5) => Virtual
  def findStopType(stopTypes: Seq[Int]): StopType = {
    // remove from OTH stoptypes the values that are not supported by tierekisteri
    val codesOfInterest = StopType.values.flatMap(st => st.propertyValues)
    val availableStopTypes = StopType.values.map(st => st.propertyValues -> st ).toMap
    availableStopTypes.get(stopTypes.toSet.intersect(codesOfInterest)) match {
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
    properties.find(p =>
      p.publicId.equals(liviIdPublicId) && p.values.nonEmpty).map(_.values.head.propertyValue)
  }
}
