package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

import scala.io.Source

class MunicipalityCodeImporter {

  def importMunicipalityCodes() = {
    PostGISDatabase.withDynTransaction {
      try {
        val src = Source.fromInputStream(getClass.getResourceAsStream("/kunnat_ja_elyt_2014.csv"))
        src.getLines().toList.drop(1).map(row => {
          val elems = row.replace("\"", "").split(";")
          val roadMaintainerID = elems(3) match {
            case "1" => 14
            case "2" => 12
            case "3" => 10
            case "4" => 9
            case "5" => 8
            case "6" => 4
            case "7" => 2
            case "8" => 3
            case "9" => 1
            case "0" => 0
          }
          sqlu"""
          insert into municipality(id, name_fi, name_sv, ely_nro, road_maintainer_id) values( ${elems(0).toInt}, ${elems(1)}, ${elems(2)}, ${elems(3).toInt} ,$roadMaintainerID)
        """.execute
        })
      } catch {
        case  npe: NullPointerException => {
          println("//kunnat_ja_elyt_2014.csv was not found, skipping.")
        }
      }
    }
  }

  def updateMunicipalityCodes() = {
    PostGISDatabase.withDynTransaction {
      try {
        val src = Source.fromInputStream(getClass.getResourceAsStream("/kunnat_ja_elyt_2018.csv"))
        src.getLines().toList.drop(1).map(row => {
          val elems = row.replace("\"", "").split(";")
          val point= s"POINT(${elems(5).toDouble.toString} ${elems(6).toDouble.toString})"
          sqlu"""
          UPDATE MUNICIPALITY
          SET GEOMETRY = ST_GEOMFROMTEXT($point,3067),
          ZOOM = ${elems(7).toInt}
          WHERE ID = ${elems(0).toInt}
          """.execute
        })
      } catch {
        case  npe: NullPointerException => {
          println("//kunnat_ja_elyt_2018.csv was not found, skipping.")
        }
      }
    }
  }
}

object MunicipalityCodeImporter extends App {
  new MunicipalityCodeImporter().importMunicipalityCodes()
}
