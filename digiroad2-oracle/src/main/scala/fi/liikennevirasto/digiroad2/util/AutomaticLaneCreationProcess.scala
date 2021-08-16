package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.viite.{ChangeInformation, IntegrationViiteClient}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

object AutomaticLaneCreationProcess {
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  lazy val integrationViiteClient: IntegrationViiteClient = {
    new IntegrationViiteClient(Digiroad2Properties.viiteRestApiEndPoint, HttpClientBuilder.create().build())
  }

  // change type 2
  private def newLane(changes:ChangeInformation):Long = {
    throw new NotImplementedError()
  }
  // change type 3
  private def transferLane(changes:ChangeInformation):Long = {
    throw new NotImplementedError()
  }
  // change type 4
  private def renumbering(changes:ChangeInformation):Long = {
    throw new NotImplementedError()
  }
  // change type 5
  private def expiringLane(changes:ChangeInformation):Long= {
    throw new NotImplementedError()
  }

  def process() = {
    val changeInformations = integrationViiteClient.fetchRoadwayChangesChanges(new DateTime().minusDays(1))
    
    if(changeInformations.isDefined){
     changeInformations.foreach(change=>{
       // add operation
     })
    }
  }

  withDynTransaction {
    process()
  }

}
