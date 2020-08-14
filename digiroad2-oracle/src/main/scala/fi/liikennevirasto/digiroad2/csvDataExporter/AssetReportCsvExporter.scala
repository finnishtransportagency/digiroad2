package fi.liikennevirasto.digiroad2.csvDataExporter

import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, DateParser, TrafficSigns}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, MunicipalityInfo}
import fi.liikennevirasto.digiroad2.dao.csvexporter.{AssetReport, AssetReporterDAO}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}
import fi.liikennevirasto.digiroad2.{CsvDataExporter, DigiroadEventBus, Status}
import org.joda.time.DateTime


case class ExportAssetReport(municipalityName: String, assetTypeId: Int, totalAssets: Int, assetNameFI: String, assetGeomType: String,
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


  def exportAssetsByMunicipality( assetTypesList: List[Int], municipalitiesList: List[Int], extractPointsAsset: Boolean = false,
                                  withTransaction: Boolean = true): List[ExportAssetReport] = {

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

    def executeQuery(municipality: Int): List[AssetReport] = {
      if (extractPointsAsset) {
        assetReporterDAO.pointAssetQuery(Seq(municipality), assetTypesList)
      }
      else {
        val linkIds = roadLinkServiceImpl.getVVHRoadLinksF(municipality)
                                         .map(_.linkId)

        assetReporterDAO.linearAssetQuery(linkIds, assetTypesList)
      }
    }

    def extractData(municipality: Int): List[AssetReport] = {
      if (withTransaction) {
        withDynTransaction {
          executeQuery(municipality)
        }
      }
      else {
        executeQuery(municipality)
      }
    }


    def getUserAndMunicipalitiesInfo(): (Seq[User], List[MunicipalityInfo]) = {

      def getUserAndMunicipalitiesFromDB(): (Seq[User], List[MunicipalityInfo]) = {
        val userResults = userProvider.getUsers()
        val municResults = municipalityDao.getMunicipalitiesNameAndIdByCode(municipalitiesList.toSet)

        (userResults, municResults)
      }

      if(withTransaction) {
        withDynSession {
          getUserAndMunicipalitiesFromDB()
        }
      }
      else{
        getUserAndMunicipalitiesFromDB()
      }
    }


    val (users, municipalitiesInfo) = getUserAndMunicipalitiesInfo()

    val operatorUsers = users.filter(_.isOperator())
    val operatorUsersUsername = operatorUsers.map(_.username)

    municipalitiesList.flatMap{ municipality =>

      val municipalityUsers = users.filter( _.isAuthorizedToWrite(municipality) )
                                    .filterNot( user => operatorUsersUsername.contains(user.username) )
                                    .map( _.username )

      val extractedAssets = extractData(municipality)

      val linearAssetsGroupedBy = extractedAssets.groupBy(_.assetType)

      assetTypesList.flatMap { assetType =>
        if (linearAssetsGroupedBy.contains(assetType)) {
          val currentAssetType = linearAssetsGroupedBy(assetType)

          val (operatorUser, operatorDate) = extractUserAndDateTime(currentAssetType, operatorUsersUsername)
          val (municipalityUser, municipalityDate) = extractUserAndDateTime(currentAssetType, municipalityUsers)

          val firstElem = currentAssetType.head

          Some( ExportAssetReport(municipalitiesInfo.filter(_.id == municipality).head.name, firstElem.assetType, currentAssetType.size,
                            firstElem.assetNameFI, firstElem.assetGeometryType, operatorUser, operatorDate, municipalityUser, municipalityDate) )
        }
        else
          None
      }

    }
  }


  def extractDateToString(dateTime: Option[DateTime]): String = {
    dateTime match {
      case Some(date) =>
        DateParser.dateToString(date, DateParser.DatePropertyFormat)
      case _ => ""
    }
  }


  def extractRecords( exportAssetReports: List[ExportAssetReport], withTransaction: Boolean = true): String = {

    def getTotalNewLawTrafficSigns(municipalityName: String): Int = {
      if(withTransaction) {
        withDynSession {
          val municipalityInfo = municipalityDao.getMunicipalityIdByName(municipalityName)
          assetReporterDAO.getTotalTrafficSignNewLaw(municipalityInfo.head.id)
        }
      }
      else{
        val municipalityInfo = municipalityDao.getMunicipalityIdByName(municipalityName)
        assetReporterDAO.getTotalTrafficSignNewLaw(municipalityInfo.head.id)
      }
    }


    val allInfoGroupedByMunicipality = exportAssetReports.groupBy(_.municipalityName)

    val output = allInfoGroupedByMunicipality.map { case (municipality, records) =>
          val line = municipality + ";\r\n"

          val rows = records.map { elem =>
                val (totalPoints, isLinear) = if (elem.assetGeomType.trim == "linear") ("", "Kyllä")
                                              else (elem.totalAssets, "")

                val operatorDate = extractDateToString(elem.operatorModifiedDate)
                val municipalityDate = extractDateToString(elem.municipalityModifiedDate)

                val baseRow = s";${elem.assetNameFI};$totalPoints;$isLinear;$operatorDate;$municipalityDate;${elem.operatorUser};${elem.municipalityUser}"

                if (elem.assetTypeId == TrafficSigns.typeId) {
                  val totalNewLawTrafficSigns = getTotalNewLawTrafficSigns(municipality)
                  baseRow.concat(s";$totalNewLawTrafficSigns")
                }
                else
                  baseRow
                }

          line + rows.mkString("\r\n")
        }.toList

    output.mkString("\r\n")
  }


  def decodeAssetsToProcess(assetTypesList: List[Int]): List[Int] = {

    val ( pointAssets, linearAssets ) = ( assetTypesList.contains(PointAssets.id), assetTypesList.contains(LinearAssets.id) )

    if ( assetTypesList.contains(AllAssets.id) ) {
      AssetTypeInfo.values.map(_.typeId).toList
    }
    else if ( pointAssets || linearAssets ) {

      val (linearAssetType, pointAssetType) = AssetTypeInfo.values.toList.partition( _.geometryType == "linear" )

      val allPoints = pointAssetType.map(_.typeId)
      val allLinears = linearAssetType.map(_.typeId)

      (pointAssets, linearAssets) match {
        case (true, false) =>
          (allPoints ++ assetTypesList).filterNot(_ == PointAssets.id)

        case (false, true) =>
          (allLinears ++ assetTypesList).filterNot(_ == LinearAssets.id)

        case _ =>
          allPoints ++ allLinears
      }

    }
    else
      assetTypesList
  }


  def decodeMunicipalitiesToProcess(municipalities: List[Int]): List[Int] = {
    if ( municipalities.contains(1000)) { /* 1000 = All municipalities */
      withDynSession {
        municipalityDao.getMunicipalitiesInfo.map(_._1).toList
      }
    }
    else
      municipalities
  }


  def exportAssetsByMunicipalityCSVGenerator(assetTypesList: List[Int], municipalitiesList: List[Int], logId: Long, withTransaction: Boolean = true): Long = {

    try {

      val finalAssetList = decodeAssetsToProcess(assetTypesList)
      val (linearAssets, pointAssets) = finalAssetList.partition( AssetTypeInfo(_).geometryType == "linear")

      val linearContent = if (linearAssets.nonEmpty) exportAssetsByMunicipality(assetTypesList, municipalitiesList, withTransaction = withTransaction)
                        else List()

      val pointContent = if (pointAssets.nonEmpty) exportAssetsByMunicipality(assetTypesList, municipalitiesList, extractPointsAsset = true, withTransaction)
                        else List()

      val allContent = (linearContent ++ pointContent).sortBy(_.assetNameFI)
      val csvData = extractRecords( allContent, withTransaction )

      val fileContent = mainHeaders ++ csvData
      update(logId, Status.OK, Some(fileContent), withTransaction )

    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Tapahtui odottamaton virhe: " + e.toString), withTransaction)
    }
  }


}
