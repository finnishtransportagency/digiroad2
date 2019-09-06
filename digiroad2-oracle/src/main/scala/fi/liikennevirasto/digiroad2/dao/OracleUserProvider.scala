package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.dao.Queries.bytesToPoint
import fi.liikennevirasto.digiroad2.user.{Configuration, User, UserProvider}
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

  def createUser(username: String, config: Configuration, name: Option[String] = None, newTransaction: Boolean = true) = {
    if (newTransaction) {
      OracleDatabase.withDynSession {
        sqlu"""
        insert into service_user (id, username, configuration, name, created_at)
        values (primary_key_seq.nextval, ${username.toLowerCase}, ${write(config)}, $name, sysdate)
      """.execute
      }
    }else {
        sqlu"""
        insert into service_user (id, username, configuration, name, created_at)
        values (primary_key_seq.nextval, ${username.toLowerCase}, ${write(config)}, $name, sysdate)
      """.execute
      }
  }

  def getUser(username: String, newTransaction: Boolean = true): Option[User] = {
    if (username == null) return None

    if (newTransaction) {
      OracleDatabase.withDynSession {
        sql"""select id, username, configuration, name from service_user where lower(username) = ${username.toLowerCase}""".as[User].firstOption
      }
    } else {
      sql"""select id, username, configuration, name from service_user where lower(username) = ${username.toLowerCase}""".as[User].firstOption
    }
  }

  def updateUserConfiguration(user: User, newTransaction: Boolean = true): User = {
    if (newTransaction) {
      OracleDatabase.withDynSession {
        sqlu"""update service_user set configuration = ${write(user.configuration)}, name = ${user.name} where lower(username) = ${user.username.toLowerCase}""".execute
        user
      }
    } else{
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


}
