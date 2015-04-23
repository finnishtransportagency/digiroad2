package fi.liikennevirasto.digiroad2.util

import scala.collection.immutable
import fi.liikennevirasto.digiroad2.asset._
import scala.language.postfixOps
import org.joda.time.format.DateTimeFormat
import fi.liikennevirasto.digiroad2.vallu.ValluTransformer._
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.asset.Modification
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties
import fi.liikennevirasto.digiroad2.asset.PropertyValue

object AssetValluCsvFormatter extends AssetCsvFormatter with AssetPropertiesReader {
  case class ValluCsvMassTransitStop(nationalId: Long, propertyData: Seq[Property], lon: Double, lat: Double,
                                     bearing: Option[Int], validityDirection: Option[Int],
                                     created: Modification, modified: Modification,
                                     administrativeClass: AdministrativeClass) extends NationalStop

  val OutputDateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss")

  def valluCsvRowsFromAssets(municipalityId: Long, municipalityName: String,
                             assets: immutable.Iterable[ValluCsvMassTransitStop],
                             complementaryBusStopNames: Map[Long, String]): Iterable[String] = {
    assets.map(fetchNameFromValluImport(complementaryBusStopNames, _))
      .filterNot(x => isTramStop(x))
      .map(formatFromAssetWithPropertiesValluCsv(municipalityId, municipalityName, _))
  }

  def formatAssetsWithProperties(municipalityId: Long, municipalityName: String, assets: Iterable[ValluCsvMassTransitStop]): Iterable[String] = {
    assets.map(formatFromAssetWithPropertiesValluCsv(municipalityId, municipalityName, _))
  }

  def formatFromAssetWithPropertiesValluCsv(municipalityId: Long, municipalityName: String, asset: ValluCsvMassTransitStop): String = {
    (addStopId[ValluCsvMassTransitStop] _)
      .andThen (addAdminStopId)
      .andThen (addStopCode)
      .andThen ((addName _ curried)("nimi_suomeksi")(_))
      .andThen ((addName _ curried)("nimi_ruotsiksi")(_))
      .andThen (addXCoord)
      .andThen (addYCoord)
      .andThen (addAddress)
      .andThen (addRoadNumber)
      .andThen (addBearing)
      .andThen (addBearingDescription)
      .andThen (addValidityDirection)
      .andThen (addBusStopTypes)
      .andThen (addEquipment)
      .andThen (addReachability)
      .andThen (addSpecialNeeds)
      .andThen (addModifiedInfo)
      .andThen (addValidityPeriods)
      .andThen (addMaintainerId)
      .andThen (addMunicipalityInfo(municipalityId, municipalityName, _))
      .andThen (addComments)
      .andThen (addContactEmail)
      .andThen (addLiviId)
      .andThen (addRoadType)
      .apply(asset, List())._2.reverse.mkString(";")
  }

  private def fetchNameFromValluImport(complementaryBusStopNames: Map[Long, String], asset: ValluCsvMassTransitStop): ValluCsvMassTransitStop = {
    asset.copy(propertyData = asset.propertyData.map { property =>
      if (property.publicId != "nimi_suomeksi") {
        property
      } else {
        val complementaryName = complementaryBusStopNames.get(asset.nationalId)
        if (property.values.isEmpty && complementaryName.isDefined) {
          property.copy(values = List(PropertyValue(propertyValue = complementaryName.get, propertyDisplayValue = Some(complementaryName.get))))
        } else {
          property
        }
      }
    })
  }

  private def addXCoord(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    (asset, asset.lon.toString :: result)
  }

  private def addYCoord(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    (asset, asset.lat.toString :: result)
  }

  private def addName(language: String, params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    val name = getPropertyValuesByPublicId(language, asset.propertyData)
    (asset, name.headOption.fold("")(_.propertyDisplayValue.getOrElse("")) :: result)
  }

  private def addAdminStopId(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    val id = getPropertyValuesByPublicId("yllapitajan_tunnus", asset.propertyData)
    (asset, id.headOption.fold("")(x => x.propertyDisplayValue.getOrElse("")) :: result)
  }

  private def addStopCode(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    val id = getPropertyValuesByPublicId("matkustajatunnus", asset.propertyData)
    (asset, id.headOption.fold("")(x => x.propertyDisplayValue.getOrElse("")) :: result)
  }

  private def addContactEmail(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    val email = getPropertyValuesByPublicId("palauteosoite", asset.propertyData)
    (asset, email.headOption.fold("")(x => x.propertyDisplayValue.getOrElse("")) :: result)
  }

  private def addComments(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    val comments = getPropertyValuesByPublicId("lisatiedot", asset.propertyData)
    (asset, comments.headOption.fold("")(x => x.propertyDisplayValue.getOrElse("")) :: result)
  }

  private def addMunicipalityInfo(municipalityId: Long, municipalityName: String, params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    (asset, municipalityName :: municipalityId.toString :: result)
  }

  private def addAddress(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    // address not known
    (asset, "" :: result)
  }

  private def addRoadNumber(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    // roadnumber not known
    (asset, "" :: result)
  }

  private def addMaintainerId(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    val maintainer = getPropertyValuesByPublicId("tietojen_yllapitaja", asset.propertyData)
    (asset, maintainer.headOption.fold("")(x => x.propertyDisplayValue.getOrElse("")) :: result)
  }

  private def addValidityPeriods(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    val validFrom = getPropertyValuesByPublicId("ensimmainen_voimassaolopaiva", asset.propertyData)
    val validTo = getPropertyValuesByPublicId("viimeinen_voimassaolopaiva", asset.propertyData)
    (asset, transformToISODate(validTo.head.propertyDisplayValue) ::
      transformToISODate(validFrom.head.propertyDisplayValue) ::
      result)
  }

  private def addSpecialNeeds(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    val specialNeeds = getPropertyValuesByPublicId("esteettomyys_liikuntarajoitteiselle", asset.propertyData)
    (asset, specialNeeds.headOption.fold("")(x => x.propertyDisplayValue.get) :: result)
  }

  private def addModifiedInfo(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params

    def creationTimeOrEmpty(time: Option[DateTime]): String = {
      time.fold("")(OutputDateTimeFormat.print)
    }

    asset.modified match {
      case Modification(Some(modificationTime), Some(modifier)) => (asset, modifier :: OutputDateTimeFormat.print(modificationTime) :: result)
      case Modification(Some(modificationTime), None)           => (asset, asset.created.modifier.getOrElse("") :: OutputDateTimeFormat.print(modificationTime) :: result)
      case Modification(None, Some(modifier))                   => (asset, modifier :: creationTimeOrEmpty(asset.created.modificationTime) :: result)
      case _                                                    => (asset, asset.created.modifier.getOrElse("") :: creationTimeOrEmpty(asset.created.modificationTime) :: result)
    }
  }

  private[util] def addBearing(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    val validityDirection = asset.validityDirection.getOrElse(1)
    val actualBearing = asset.bearing.map { bearing =>
      calculateActualBearing(validityDirection, bearing)
    }.getOrElse("").toString
    (asset, actualBearing :: result)
  }

  private[util] def addBearingDescription(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    val bearingDescription = getPropertyValuesByPublicId("liikennointisuuntima", asset.propertyData)
    (asset, bearingDescription.headOption.fold("")(_.propertyDisplayValue.getOrElse("")) :: result)
  }

  private def addValidityDirection(params: (ValluCsvMassTransitStop, List[String])) = {
    val (asset, result) = params
    val id = getPropertyValuesByPublicId("liikennointisuunta", asset.propertyData)
    (asset, id.headOption.fold("")(_.propertyDisplayValue.getOrElse("")) :: result)
  }

  private def addReachability(params: (ValluCsvMassTransitStop, List[String])): (ValluCsvMassTransitStop, List[String]) = {
    val (asset, result) = params
    val reachability = describeReachability(asset)
    (asset, reachability :: result)
  }

  private def addEquipment(params: (ValluCsvMassTransitStop, List[String])): (ValluCsvMassTransitStop, List[String]) = {
    val (asset, result) = params
    val equipments = describeEquipments(asset)
    (asset, equipments :: result)
  }

  private def addBusStopTypes(params: (ValluCsvMassTransitStop, List[String])): (ValluCsvMassTransitStop, List[String]) = {
    val (asset, result) = params
    val (local, express, nonStopExpress, virtual) = describeBusStopTypes(asset)
    (asset, virtual :: nonStopExpress :: express :: local :: result)
  }

  private def addLiviId(params: (ValluCsvMassTransitStop, List[String])): (ValluCsvMassTransitStop, List[String]) = {
    val (asset, result) = params
    val liviId = getPropertyValuesByPublicId("yllapitajan_koodi", asset.propertyData)
    (asset, liviId.flatMap(_.propertyDisplayValue).headOption.getOrElse("") :: result)
  }

  private def addRoadType(params: (ValluCsvMassTransitStop, List[String])): (ValluCsvMassTransitStop, List[String]) = {
    val (asset, result) = params
    (asset, asset.administrativeClass.value.toString :: result)
  }
}
