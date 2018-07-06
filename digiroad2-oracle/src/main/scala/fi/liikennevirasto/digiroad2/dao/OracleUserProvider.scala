package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.dao.Queries.bytesToPoint
import fi.liikennevirasto.digiroad2.user.{Configuration, MapViewZoom, User, UserProvider}
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}

case class StartUpParameters(userId: Long, username: String, configuration: Configuration,  municipalityId: Long, municipalityGeometry: Point, municipalityZoom: Int,
                             elyId: Long, elyGeometry: Point, elyZoom: Int, serviceAreaId: Long, serviceAreaGeometry: Point, serviceAreaZoom: Int)

class OracleUserProvider extends UserProvider {
  implicit val formats = Serialization.formats(NoTypeHints)
  implicit val getUser = new GetResult[User] {
    def apply(r: PositionedResult) = {
     User(r.nextLong(), r.nextString(), read[Configuration](r.nextString()), r.nextStringOption())
    }
  }
  implicit val getUserArea = new GetResult[Point] {
    def apply(r: PositionedResult) = {
      Point(r.nextDouble(), r.nextDouble(), r.nextDouble())
    }
  }

  implicit val getMapViewZoom = new GetResult[MapViewZoom] {
    def apply(r: PositionedResult) = {

      val geometry = r.nextBytesOption().map(bytesToPoint).get
      val zoom = r.nextInt()

      MapViewZoom(geometry, zoom)
    }
  }

  def createUser(username: String, config: Configuration, name: Option[String] = None) = {
    OracleDatabase.withDynSession {
      sqlu"""
        insert into service_user (id, username, configuration, name, created_at)
        values (primary_key_seq.nextval, ${username.toLowerCase}, ${write(config)}, $name, sysdate)
      """.execute
    }
  }

  def getUser(username: String): Option[User] = {
    if (username == null) return None
    OracleDatabase.withDynSession {
      sql"""select id, username, configuration, name from service_user where lower(username) = ${username.toLowerCase}""".as[User].firstOption
    }
  }

  def updateUserConfiguration(user: User): User = {
    OracleDatabase.withDynSession {
      sqlu"""update service_user set configuration = ${write(user.configuration)}, name = ${user.name} where lower(username) = ${user.username.toLowerCase}""".execute
      user
    }
  }

  def saveUser(user: User): User = {
    OracleDatabase.withDynSession {
      sqlu"""update service_user set configuration = ${write(user.configuration)}, name = ${user.name}, modified_at = sysdate where lower(username) = ${user.username.toLowerCase}""".execute
      user
    }
  }

  def deleteUser(username: String) = {
    OracleDatabase.withDynSession {
      sqlu"""delete from service_user where lower(username) = ${username.toLowerCase}""".execute
    }
  }

  def getUserArea(userAreaId: Int): Seq[Point] = {
    OracleDatabase.withDynSession {
      sql"""select x, y, z from table(sdo_util.getvertices((select geometry from authorized_area where kpalue = $userAreaId))) order by id""".as[Point].list
    }
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

}
