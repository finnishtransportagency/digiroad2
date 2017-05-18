package fi.liikennevirasto.viite

import java.util.Properties

import org.apache.http.client.methods.HttpPost
import org.scalatest.{FunSuite, Matchers}

import scala.reflect.io.File

/**
  * Created by alapeijario on 17.5.2017.
  */
class ViiteTierekisteriClientSpec extends FunSuite with Matchers{
  {

    val properties: Properties = {
      val props = new Properties()
      props.load(getClass.getResourceAsStream("/digiroad2.properties"))
      props

    }

    def getRestEndPoint: String = {
      val loadedKeyString = properties.getProperty("digiroad2.tierekisteriViiteRestApiEndPoint")
      println("viite-endpoint = "+loadedKeyString)
      if (loadedKeyString == null)
        throw new IllegalArgumentException("Missing TierekisteriViiteRestApiEndPoint")
      loadedKeyString
    }


    test("TR-connection Create test") {
      val request = new HttpPost(getRestEndPoint + "addresschange/")

      val message= ViiteTierekisteriClient.sendJsonMessage(changepPoject(0, "Testproject", "TestUser", 3, "2017-06-01", Seq {
        changeInfoitem(2, 1, 1, changeInfoRoadParts(None, None, None, None, None, None), changeInfoRoadParts(Option(403), Option(0), Option(8), Option(0), Option(8), Option(1001))) // projectid 0 wont be added to TR
      }))
      message.projectId should be (0)
      message.reason should startWith ("Created")
    }


    test("TR-project-status") {
      val response = ViiteTierekisteriClient.getProjectStatus("8900")
    }


  }
}
