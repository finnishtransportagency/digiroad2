package fi.liikennevirasto.digiroad2.csvDataExporter

import fi.liikennevirasto.digiroad2.asset.{DateParser, TrafficSigns}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, OracleUserProvider}
import fi.liikennevirasto.digiroad2.dao.csvexporter.{AssetReport, AssetReporterDAO}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.{CsvDataExporter, DigiroadEventBus, Status}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat


case class ExportAssetReport(municipalityName: String, assetTypeId: Int, assetNameFI: String, assetGeomType: String,
                             operatorUser: String, operatorModifiedDate: Option[DateTime],
                             municipalityUser: String, municipalityModifiedDate: Option[DateTime] )


class AssetReportCsvExporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataExporter(eventBusImpl: DigiroadEventBus){
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  val assetReporterDAO = new AssetReporterDAO()
  val municipalityDao = new MunicipalityDao()
  val userProvider: UserProvider = new OracleUserProvider

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

    mainHeaders + allRecords.mkString("\r\n")
  }


  def exportAssetsByMunicipalityCSVGenerator(assetTypesList: List[Int], municipalitiesList: List[Int], extractPointsAsset: Boolean = false) = {

    val user = userProvider.getCurrentUser()
    val filename = "export_".concat(DateParser.dateToString(DateTime.now, DateTimeFormat.forPattern("ddMMyyyy_HHmmss")) )
                            .concat(".csv")

    val logId = insertData( user.username, filename, assetTypesList.mkString(","), "" )

    try {
      val fileContent = generateCSVContent(assetTypesList, municipalitiesList, extractPointsAsset)
      update(logId, Status.OK, Some(fileContent) )

    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Tapahtui odottamaton virhe: " + e.toString))
    }
  }


}
