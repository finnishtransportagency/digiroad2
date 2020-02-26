package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.dao.Queries.bytesToPoint
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.{GetResult, PositionedResult}
import slick.jdbc.StaticQuery.interpolation
case class MunicipalityInfo(id: Int, ely: Int, name: String)

case class MapViewZoom(geometry : Point, zoom: Int)

class MunicipalityDao {


  implicit val getMapViewZoom = new GetResult[MapViewZoom] {
    def apply(r: PositionedResult) = {

      val geometry = r.nextBytesOption().map(bytesToPoint).get
      val zoom = r.nextInt()

      MapViewZoom(geometry, zoom)
    }
  }


  def getMunicipalities: Seq[Int] = {
    sql"""
      select id from municipality
    """.as[Int].list
  }

  def getMunicipalitiesInfo: Seq[(Int, String)] = {
    sql"""
      select id, name_fi from municipality
    """.as[(Int, String)].list
  }

  def getMunicipalityNameByCode(id: Int): String = {
    sql"""
      select name_fi from municipality where id = $id""".as[String].first
  }

  def getMunicipalityIdByName(municipalityName: String): List[MunicipalityInfo] = {
    sql"""
      select id, ely_nro, name_fi
      from municipality
      where LOWER(name_fi) = LOWER($municipalityName)"""
      .as[(Int, Int, String)].list
      .map{ case(id, ely, name) =>
        MunicipalityInfo(id, ely, name)}
  }

  def getMunicipalityById(id: Int): Seq[Int] = {
    sql"""select id from municipality where id = $id """.as[Int].list
  }

  def getMunicipalityByCoordinates(coordinates: Point): Seq[MunicipalityInfo] = {
    sql"""
          select m.id, m.ely_nro,
                 case when m.name_fi is not null then m.name_fi else m.name_sv end as name
          from municipality m,
               table(sdo_util.getvertices(m.geometry)) t
            where trunc( t.x ) = ${coordinates.x} and trunc( t.y ) = ${coordinates.y}
      """.as[(Int, Int, String)].list
      .map { case (id, ely, name) =>
        MunicipalityInfo(id, ely, name)
      }
  }

  def getMunicipalitiesNameAndIdByCode(codes: Set[Int]): List[MunicipalityInfo] = {
    val filter = if (codes.nonEmpty) {"where id in (" + codes.mkString(",") + ")" } else ""

    sql"""
      select id, ely_nro, name_fi from municipality
      #$filter
    """.as[(Int, Int, String)].list
      .map{ case(id, ely, name) =>
        MunicipalityInfo(id, ely, name)}
  }

  def getMunicipalitiesNameAndIdByEly(ely: Set[Int]): List[MunicipalityInfo] = {
    val filter = if (ely.nonEmpty) {"where ely_nro in (" + ely.mkString(",") + ")" } else ""

    sql"""
      select id, ely_nro, name_fi from municipality
      #$filter
    """.as[(Int, Int, String)].list
      .map{ case(id, ely, name) =>
        MunicipalityInfo(id, ely, name)}
  }

  def getCenterViewMunicipality(municipalityId: Int): Option[MapViewZoom] =  {
    OracleDatabase.withDynSession {
      sql"""select geometry, zoom from municipality where id = $municipalityId""".as[MapViewZoom].firstOption
    }
  }


  def getCenterViewArea(area: Int): Option[MapViewZoom] =  {
    OracleDatabase.withDynSession {
      sql"""select geometry, zoom from service_area where id = $area""".as[MapViewZoom].firstOption
    }
  }


  def getCenterViewEly(ely: Int): Option[MapViewZoom] =  {
    OracleDatabase.withDynSession {
      sql"""select geometry, zoom from ely where id = $ely""".as[MapViewZoom].firstOption
    }
  }

  def getElysByMunicipalities(municipalities: Set[Int]): Seq[Int] =  {
    OracleDatabase.withDynSession {
      sql"""select ELY_NRO from municipality  where id in (#${municipalities.mkString(",")} ) group by ELY_NRO""".as[Int].list
    }
  }

  def getElysIdAndNamesByCode(elys: Set[Int]): Seq[(Int, String)] ={
    OracleDatabase.withDynSession {
      sql"""select id, name_fi from ely where id in (#${elys.mkString(",")} )""".as[(Int, String)].list
    }
  }

  def getElysIdAndNamesByCoordinates(lon: Int, lat: Int): Seq[(Int, String)] = {
    sql"""
         select e.id,
         	case
         		when e.name_fi is not null then e.name_fi
         		else e.name_sv
         	end as name
         from ely e,
              table(sdo_util.getvertices(e.geometry)) t
         	where trunc( t.x ) = $lon and trunc( t.y ) = $lat
       """.as[(Int, String)].list
  }
}