package fi.liikennevirasto.digiroad2.user.oracle

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database
import slick.jdbc.{StaticQuery => Q, PositionedResult, GetResult}
import Database.dynamicSession
import Q.interpolation
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.user.User
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}
import fi.liikennevirasto.digiroad2.user.Configuration
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.{Point}

class OracleUserProvider extends UserProvider {
  implicit val formats = Serialization.formats(NoTypeHints)
  implicit val getUser = new GetResult[User] {
    def apply(r: PositionedResult) = {
     User(r.nextLong(), r.nextString(), read[Configuration](r.nextString()))
    }
  }
  implicit val getUserArea = new GetResult[Point] {
    def apply(r: PositionedResult) = {
      Point(r.nextDouble(), r.nextDouble(), r.nextDouble())
    }
  }

  def createUser(username: String, config: Configuration) = {
    OracleDatabase.withDynSession {
      sqlu"""
        insert into service_user (id, username, configuration)
        values (primary_key_seq.nextval, ${username.toLowerCase}, ${write(config)})
      """.execute
    }
  }

  def getUser(username: String): Option[User] = {
    if (username == null) return None
    OracleDatabase.withDynSession {
      sql"""select id, username, configuration from service_user where lower(username) = ${username.toLowerCase}""".as[User].firstOption
    }
  }

  def saveUser(user: User): User = {
    OracleDatabase.withDynSession {
      sqlu"""update service_user set configuration = ${write(user.configuration)} where lower(username) = ${user.username.toLowerCase}""".execute
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
