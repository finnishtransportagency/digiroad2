package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.{Point, VVHClient}
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import org.scalatest.{FunSuite, Matchers}
import java.util.Properties

class VVHClientSpec extends FunSuite with Matchers{
  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }
  /**
    * Checks that history URL is correct (might have to change it if new info is fetched
    */
  test("HistorylinksBoundingboxsearch forms correct url") {
    val vvhapiEP=properties.getProperty("digiroad2.VVHRestApiEndPoint")
    val vvhClient= new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val result =  vvhClient.historyData.uRLgenerator(BoundingRectangle(Point(564000, 6930000),Point(566000, 6931000)), Set(420))
    result should equal( vvhapiEP +"Roadlink_data_history/FeatureServer/query?layerDefs=%5B%7B%22layerId%22%3A0%2C%22where%22%3A%22MUNICIPALITYCODE+IN+%28420%29%22%2C%22outFields%22%3A%22MTKID%2CLINKID%2CMTKHEREFLIP%2CMUNICIPALITYCODE%2CVERTICALLEVEL%2CHORIZONTALACCURACY%2CVERTICALACCURACY%2CMTKCLASS%2CADMINCLASS%2CDIRECTIONTYPE%2CCONSTRUCTIONTYPE%2CROADNAME_FI%2CROADNAME_SM%2CROADNAME_SE%2CFROM_LEFT%2CTO_LEFT%2CFROM_RIGHT%2CTO_RIGHT%2CLAST_EDITED_DATE%2CROADNUMBER%2CROADPARTNUMBER%2CVALIDFROM%2CGEOMETRY_EDITED_DATE%2CSURFACETYPE%2CEND_DATE%2CLINKID_NEW%2COBJECTID%22%7D%5D&geometry=564000.0,6930000.0,566000.0,6931000.0&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&returnGeometry=true&returnZ=true&returnM=true&geometryPrecision=3&f=pjson") ;
  }

  /**
    * Checks that VVH history boundingbox search works
    */
  test("Tries to connect VVH history API and retrive result") {
    val vvhapiEP=properties.getProperty("digiroad2.VVHRestApiEndPoint")
    val vvhClient= new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val result= vvhClient.historyData.fetchVVHRoadlinkHistoryByBoundsAndMunicipalities(BoundingRectangle(Point(564000, 6930000),Point(566000, 6931000)), Set(420))
     result.size should be >1
    }
}
