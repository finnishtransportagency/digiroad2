package fi.liikennevirasto.digiroad2.csvDataExporter

import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, DateParser, TrafficSigns}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, OracleUserProvider}
import fi.liikennevirasto.digiroad2.dao.csvexporter.{AssetReport, AssetReporterDAO}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.{CsvDataExporter, DigiroadEventBus, Status}
import org.joda.time.DateTime


case class ExportAssetReport(municipalityName: String, assetTypeId: Int, assetNameFI: String, assetGeomType: String,
                             operatorUser: String, operatorModifiedDate: Option[DateTime],
                             municipalityUser: String, municipalityModifiedDate: Option[DateTime] )

sealed trait SpecialAssetsTypeValues {
  val id: Int
}
case object AllAssets extends SpecialAssetsTypeValues {val id = 1}
case object PointAssets extends SpecialAssetsTypeValues {val id = 2}
case object LinearAssets extends SpecialAssetsTypeValues {val id = 3}



class AssetReportCsvExporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus, userProviderImpl: UserProvider) extends CsvDataExporter(eventBusImpl: DigiroadEventBus){
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  val assetReporterDAO = new AssetReporterDAO()
  val municipalityDao = new MunicipalityDao()
  val userProvider: UserProvider = userProviderImpl

  val mainHeaders = "Kunta;Tietolaji;kohteiden määrä (pistemäinen);Kohteita (viivamainen);Muokattu pvm (operaattori);Muokattu pvm (kuntakäyttäjä);Käyttäjä (operaattori);Käyttäjä (kuntakäyttäjä);*Liikennemerkit kpl lisätty 1.6.2020 jälkeen\r\n"


  def exportAssetsByMunicipality( assetTypesList: List[Int], municipalitiesList: List[Int], extractPointsAsset: Boolean = false): List[ExportAssetReport] = {

    def extractUserAndDateTime( assetReports: List[AssetReport], userNameList: Seq[String]): (String, Option[DateTime]) = {

      val validUsers = assetReports.filter(asset => userNameList.contains(asset.modifiedBy))
                                   .sortBy(_.modifiedDate.getMillis)(Ordering.Long.reverse)

      if (validUsers.nonEmpty) {
        val elem = validUsers.head
        ( elem.modifiedBy , Some(elem.modifiedDate) )
      }
      else
        ("", None)
    }

    def extractData(municipality: Int): List[AssetReport] = {
      withDynTransaction {

        if(extractPointsAsset) {
          assetReporterDAO.pointAssetQuery(Seq(municipality), assetTypesList)
        }
        else {
          val linkIds = roadLinkServiceImpl.getVVHRoadLinksF(municipality)
                                            .map(_.linkId)

          assetReporterDAO.linearAssetQuery(linkIds, assetTypesList)
        }
      }
    }


    val (users, municipalitiesInfo) = withDynSession {
      val userResults = userProvider.getUsers()
      val municResults = municipalityDao.getMunicipalitiesNameAndIdByCode(municipalitiesList.toSet)

      (userResults, municResults)
    }

    val operatorUsers = users.filter(_.isOperator())
    val operatorUsersUsername = operatorUsers.map(_.username)

    municipalitiesList.flatMap{ municipality =>

      val municipalityUsers = users.filter( _.isAuthorizedToWrite(municipality) )
                                    .filterNot( u => operatorUsersUsername.contains(u.username) )
                                    .map( _.username )

      val linearAssets = extractData(municipality)

      if ( linearAssets != null && linearAssets.nonEmpty) {
        val linearAssetsGroupedBy = linearAssets.groupBy(_.assetType)

        assetTypesList.flatMap { assetType =>
          if (linearAssetsGroupedBy.contains(assetType)) {
            val currentAssetType = linearAssetsGroupedBy(assetType)

            val (operatorUser, operatorDate) = extractUserAndDateTime(currentAssetType, operatorUsersUsername)
            val (municipalityUser, municipalityDate) = extractUserAndDateTime(currentAssetType, municipalityUsers)

            val firstElem = currentAssetType.head

            Some( ExportAssetReport(municipalitiesInfo.filter(_.id == municipality).head.name, firstElem.assetType, firstElem.assetNameFI,
                              firstElem.assetGeometryType, operatorUser, operatorDate, municipalityUser, municipalityDate) )
          }
          else
            None
        }

      }
      else
        None

    }
  }


  def extractDateToString(dateTime: Option[DateTime]): String = {
    dateTime match {
      case Some(date) =>
        DateParser.dateToString(date, DateParser.DateTimePropertyFormat)
      case _ => ""
    }
  }


  def generateCSVContent(assetTypesList: List[Int], municipalitiesList: List[Int], extractPointsAsset: Boolean = false): String = {

    def extractRecords( exportAssetReports: List[ExportAssetReport]): List[String] = {
      if (exportAssetReports.isEmpty) {
        List()
      }
      else {
        val allInfoGroupedByMunicipality = exportAssetReports.groupBy(_.municipalityName)

        val result = allInfoGroupedByMunicipality.map { case (municipality, records) =>
          val output = municipality + ";\r\n"
          val totalRecords = records.size

          val rows = records.map { elem =>
                val (totalPoints, isLinear) = if (elem.assetGeomType.trim == "linear") ("", "Kyllä")
                                              else (totalRecords.toString, "")

                val operatorDate = extractDateToString(elem.operatorModifiedDate)
                val municipalityDate = extractDateToString(elem.municipalityModifiedDate)

                val baseRow = s";${elem.assetNameFI};$totalPoints;$isLinear;${operatorDate};${municipalityDate};${elem.operatorUser};${elem.municipalityUser}"

                if (elem.assetTypeId == TrafficSigns.typeId) {
                  withDynSession {
                    val municipalityInfo = municipalityDao.getMunicipalityIdByName(municipality)
                    val totalNewLawTrafficSigns = assetReporterDAO.getTotalTrafficSignNewLaw(municipalityInfo.head.id)

                    baseRow.concat(s";${totalNewLawTrafficSigns}")
                  }
                }
                else
                  baseRow
          }

          output + rows.mkString("\r\n")
        }

        result.toList
      }

    }

    val allInfo = exportAssetsByMunicipality(assetTypesList, municipalitiesList, extractPointsAsset).sortBy(_.assetNameFI)
    val allRecords = extractRecords(allInfo)

    allRecords.mkString("\r\n")
  }

  def decodeAssetsToProcess(assetTypesList: List[Int]): List[Int] = {

    val ( pointAssets, linearAssets ) = ( assetTypesList.contains(PointAssets.id), assetTypesList.contains(LinearAssets.id) )

    if ( assetTypesList.contains(AllAssets.id) ) {
      AssetTypeInfo.values.map(_.typeId).toList
    }
    else if ( pointAssets || linearAssets ) {

      val allPoints = AssetTypeInfo.values
                                  .filter(_.geometryType == "point")
                                  .map(_.typeId)

      val allLinears = AssetTypeInfo.values
                                    .filter(_.geometryType == "linear")
                                    .map(_.typeId)

      (pointAssets, linearAssets) match {
        case (true, false) =>
          (allPoints ++ assetTypesList).filterNot(_ == PointAssets.id).toList

        case (false, true) =>
          (allLinears ++ assetTypesList).filterNot(_ == LinearAssets.id).toList

        case (true, true) =>
          (allPoints ++ allLinears).filterNot(id => id == PointAssets.id || id == LinearAssets.id).toList

        case _ =>
          List()
      }

    }
    else
      assetTypesList
  }


  def decodeMunicipalitiesToProcess(municipalities: List[Int]): List[Int] = {
    if (municipalities.isEmpty || municipalities.contains(1000)) { /* 1000 = All municipalities */
      withDynSession {
        municipalityDao.getMunicipalitiesInfo.map(_._1).toList
      }
    }
    else
      municipalities
  }


  def exportAssetsByMunicipalityCSVGenerator(assetTypesList: List[Int], municipalitiesList: List[Int], logId: Long): Long = {

    try {

      val finalAssetList = decodeAssetsToProcess(assetTypesList)
      val (linearAssets, pointAssets) = finalAssetList.partition( AssetTypeInfo(_).geometryType == "linear")

      val linearContent = if (linearAssets.nonEmpty) generateCSVContent(assetTypesList, municipalitiesList)
                        else ""

      val pointContent = if (pointAssets.nonEmpty) generateCSVContent(assetTypesList, municipalitiesList, true)
                        else ""

      val fileContent = mainHeaders ++ linearContent ++ pointContent
      update(logId, Status.OK, Some(fileContent) )

    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Tapahtui odottamaton virhe: " + e.toString))
    }
  }


}
