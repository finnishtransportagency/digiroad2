package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.viite.IntegrationViiteClient
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

object AutomaticLaneCreationProcess {
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  lazy val integrationViiteClient: IntegrationViiteClient = {
    new IntegrationViiteClient(Digiroad2Properties.viiteRestApiEndPoint, HttpClientBuilder.create().build())
  }

  // change type 2
  private def newLane()= {
    throw new NotImplementedError()
  }
  // change type 3
  private def transferLane() = {
    throw new NotImplementedError()
  }
  // change type 4
  private def renumbering() = {
    throw new NotImplementedError()
  }
  // change type 5
  private def expiringLane() = {
    throw new NotImplementedError()
  }

  def process() = {
    val changeInformation = integrationViiteClient.fetchRoadwayChangesChanges(new DateTime().minusDays(1))
    

  }

  withDynTransaction {
    process()
  }

}
