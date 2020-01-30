package fi.liikennevirasto.digiroad2.util

import java.io.{BufferedOutputStream, OutputStreamWriter}

import fi.liikennevirasto.digiroad2.asset.DateParser
import org.joda.time.DateTime

case class LorryParkingInDATEX2(servicePointId: Option[Long] = None,
                                serviceId: Option[Long] = None,
                                parkingType: Int,
                                parkingTypeMeaning: Int,
                                name: Option[String] = None,
                                additionalInfo: Option[String] = None,
                                lon: Double,
                                lat: Double,
                                modifiedDate: Option[String] = None,
                                municipalityCode: Int)

class Datex2Generator() {
  val countryInicial = "fi"
  val nationalIdentifier = "Finnish parking sites for lorries"
  val timeOfCreation = DateParser.dateToString(DateTime.now(), DateParser.DateTimePropertyFormatMsTimeZoneWithT)
  val genericPublicationName = "ParkingTablePublication"
  val version = "1"
  val parkingTableId = "FIN_FTA_TruckParkingTable_1"

  val headerXML =
    s"""<d2LogicalModel xmlns="http://datex2.eu/schema/2/2_0"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema"
       modelBaseVersion="2"><exchange><supplierIdentification><country>${countryInicial}</country>
       <nationalIdentifier>${nationalIdentifier}</nationalIdentifier></supplierIdentification></exchange>
       <payloadPublication xsi:type="GenericPublication" lang="fi"><publicationTime>${timeOfCreation}</publicationTime>
       <publicationCreator><country>${countryInicial}</country><nationalIdentifier>${nationalIdentifier}
       </nationalIdentifier></publicationCreator><genericPublicationName>${genericPublicationName}</genericPublicationName>
       <genericPublicationExtension><parkingTablePublication><parkingTable version="${version}" id="${parkingTableId}">
       <parkingTableName><values><value lang="en">${nationalIdentifier}</value></values></parkingTableName>
       <parkingTableVersionTime>${timeOfCreation}</parkingTableVersionTime>"""


  val bottomXML =
    s"""</parkingTable></parkingTablePublication></genericPublicationExtension></payloadPublication></d2LogicalModel>"""

  var serviceType = Map(
    6 -> "Rest area or gas station",
    12 -> "Parking lot, min. 40-50 places",
    13 -> "Car shipping terminal",
    14 -> "Coach and lorry parking",
    15 -> "Parking house"
  )

  var restAreaType = Map(
    1 -> "rest area, comprehensive facilities",
    2 -> "rest area, basic facilities"
  )

  def generateDatex2Body(lorryParkings: Set[LorryParkingInDATEX2]): String = {
    lorryParkings.map { lp =>
      val servicePointId = lp.servicePointId.getOrElse("")
      val serviceId = lp.serviceId.getOrElse("")
      val parkingType = lp.parkingType
      val parkingTypeMeaning = lp.parkingTypeMeaning
      val name = lp.name.getOrElse("")
      val additionalInfo = lp.additionalInfo.getOrElse("")
      val modifiedDate = lp.modifiedDate.getOrElse("")
      val municipalityCode = lp.municipalityCode
      val lon = lp.lon
      val lat = lp.lon

      //parkingNumberOfSpaces needs to have integer
      //equipmentType needs to have a value
      //publishingAgreement needs to be boolean
      //parkingTypeOfGroup needs to have a value [adjacentSpaces, nonAdjacentSpaces, completeFloor, mixedUsage, statisticsOnly, singleParameters, other]
      //interUrbanParkingSiteLocation needs to have a value [motorway, nearbyMotorway, layBy, onStreet, other]
      //accessCategory needs to have a value [vehicleEntranceAndExit, vehicleEntrance, vehicleExit, pedestrianEntranceAndExit, pedestrianEntrance, pedestrianExit, emergencyExit, unspecified, unknown, other]
      //all latitude and longitude needs to have floats

      s"""<parkingRecord xsi:type="InterUrbanParkingSite" id="FI-${servicePointId}" version="${version}">
          <parkingName>
          <values>
          <value lang="${countryInicial}">${name}</value>
          </values>
          </parkingName>
          <parkingRecordVersionTime>${timeOfCreation}</parkingRecordVersionTime>
          <operator xsi:type="ContactDetails" id="FI-${servicePointId}" version="${version}">
          <contactOrganisationName>
          <values><value lang="${countryInicial}"></value></values>
          </contactOrganisationName>
          <contactDetailsTelephoneNumber></contactDetailsTelephoneNumber>
          <contactDetailsEMail></contactDetailsEMail>
          <urlLinkAddress></urlLinkAddress>
          <publishingAgreement> </publishingAgreement>
          </operator>
          <parkingLocation xsi:type="Point">
          <pointByCoordinates>
          <pointCoordinates>
          <latitude>${lat}</latitude><longitude>${lon}</longitude>
          </pointCoordinates>
          </pointByCoordinates>
          </parkingLocation>
          <parkingEquipmentOrServiceFacility equipmentOrServiceFacilityIndex="1">
          <parkingEquipmentOrServiceFacility xsi:type="Equipment"><equipmentType></equipmentType></parkingEquipmentOrServiceFacility>
          </parkingEquipmentOrServiceFacility>
          <groupOfParkingSpaces groupIndex="1">
          <parkingSpaceBasics xsi:type="GroupOfParkingSpaces">
          <onlyAssignedParking>
          <vehicleCharacteristics><vehicleType>lorry</vehicleType></vehicleCharacteristics>
          </onlyAssignedParking>
          <parkingNumberOfSpaces></parkingNumberOfSpaces>
          <parkingTypeOfGroup></parkingTypeOfGroup>
          </parkingSpaceBasics>
          </groupOfParkingSpaces>
          <parkingSiteAddress><contactUnknown>true</contactUnknown></parkingSiteAddress>
          <parkingAccess id="FI-${servicePointId}-Access-1">
          <accessCategory></accessCategory>
          <primaryRoad>
          <roadIdentifier>
          <values><value lang="${countryInicial}"></value></values>
          </roadIdentifier>
          <roadDestination>
          <values><value lang="${countryInicial}"></value></values>
          </roadDestination>
          </primaryRoad>
          <location xsi:type="Point">
          <locationForDisplay>
          <latitude></latitude><longitude></longitude>
          </locationForDisplay>
          <pointByCoordinates>
          <pointCoordinates>
          <latitude></latitude><longitude></longitude>
          </pointCoordinates>
          </pointByCoordinates>
          </location>
          </parkingAccess>
          <parkingStandardsAndSecurity>
          <parkingSecurity>unknown</parkingSecurity>
          </parkingStandardsAndSecurity>
          <interUrbanParkingSiteLocation></interUrbanParkingSiteLocation>
          </parkingRecord>"""

    }.mkString(" ")


  }

  def convertToDatex2(lorryParkingInfo: Set[LorryParkingInDATEX2]): Unit = {
    val writer = new OutputStreamWriter(new BufferedOutputStream(System.out), "UTF-8")
    val bodyXML = generateDatex2Body(lorryParkingInfo)
    writer.write(headerXML + bodyXML + bottomXML)
    writer.flush()
  }
}
