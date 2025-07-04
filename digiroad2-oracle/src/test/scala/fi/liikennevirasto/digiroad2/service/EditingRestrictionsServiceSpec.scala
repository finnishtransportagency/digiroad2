import fi.liikennevirasto.digiroad2.asset.{Municipality, State}
import fi.liikennevirasto.digiroad2.dao.{EditingRestrictions, EditingRestrictionsDAO}
import fi.liikennevirasto.digiroad2.service.EditingRestrictionsService
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class EditingRestrictionsServiceTest extends FunSuite with Matchers {

  val mockDAO: EditingRestrictionsDAO = MockitoSugar.mock[EditingRestrictionsDAO]
  val service = new EditingRestrictionsService {
    override protected def dao: EditingRestrictionsDAO = mockDAO
  }

  test("isEditingRestricted returns true when asset type id is found in municipality restrictions") {

    val assetTypeId = 60
    val municipality = 1
    val adminClasses = Municipality
    val mockRestrictions = Some(EditingRestrictions(0, 1, Seq(), Seq(assetTypeId), None, None))

    when(mockDAO.fetchRestrictionsByMunicipality(municipality)).thenReturn(mockRestrictions)

    val result = service.isEditingRestricted(assetTypeId, municipality, adminClasses)

    result shouldBe true
  }

  test("isEditingRestricted returns true when asset type ID is found in state restrictions") {

    val assetTypeId = 70
    val municipality = 2
    val adminClasses = State
    val mockRestrictions = Some(EditingRestrictions(0, 3, Seq(assetTypeId), Seq(), None, None))

    when(mockDAO.fetchRestrictionsByMunicipality(municipality)).thenReturn(mockRestrictions)

    val result = service.isEditingRestricted(assetTypeId, municipality, adminClasses)

    result shouldBe true
  }

  test("isEditingRestricted returns false when asset type ID is not found") {

    val assetTypeId = 80
    val municipality = 3
    val adminClasses = Municipality
    val mockRestrictions = Some(EditingRestrictions(0, 4, Seq(60), Seq(70), None, None))

    when(mockDAO.fetchRestrictionsByMunicipality(municipality)).thenReturn(mockRestrictions)

    val result = service.isEditingRestricted(assetTypeId, municipality, adminClasses)

    result shouldBe false
  }
}

