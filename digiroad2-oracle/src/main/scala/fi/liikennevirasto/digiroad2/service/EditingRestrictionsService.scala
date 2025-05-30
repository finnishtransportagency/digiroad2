package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, Municipality, State}
import fi.liikennevirasto.digiroad2.dao.{EditingRestrictions, EditingRestrictionsDAO}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase

class EditingRestrictionsService {

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  protected def dao : EditingRestrictionsDAO = new EditingRestrictionsDAO

  def fetchRestrictions(): Seq[EditingRestrictions] = {
    withDynTransaction(dao.fetchAllRestrictions())
  }

  def isEditingRestricted(assetTypeId: Int, municipality: Int, adminClass: AdministrativeClass, newTransaction: Boolean = false): Boolean = {
    val restrictions = if (newTransaction) withDynTransaction(dao.fetchRestrictionsByMunicipality(municipality)) else dao.fetchRestrictionsByMunicipality(municipality)
    restrictions match {
      case Some(restriction) =>
        if (adminClass == Municipality && restriction.municipalityRoadRestrictedAssetTypes.contains(assetTypeId)) {
          return true
        }
        if (adminClass == State && restriction.stateRoadRestrictedAssetTypes.contains(assetTypeId)) {
          return true
        }
        return false
      case _ => return false
    }
    false
  }

}
