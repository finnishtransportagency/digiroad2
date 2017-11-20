package fi.liikennevirasto

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.BaseRoadAddress
import fi.liikennevirasto.viite.model.RoadAddressLinkLike

package object viite {
  /* Tolerance in which we can allow MValues to be equal */
  val MaxAllowedMValueError = 0.001
  /* Smallest mvalue difference we can tolerate values to be "equal to zero". One micrometer.
     See https://en.wikipedia.org/wiki/Floating_point#Accuracy_problems
  */
  val Epsilon = 1E-6

  val MaxDistanceDiffAllowed = 1.0 /* Temporary restriction from PO: Filler limit on modifications
                                      (LRM adjustments) is limited to 1 meter. If there is a need to fill /
                                      cut more than that then nothing is done to the road address LRM data.

                                      Used also for checking the integrity of the targets of floating road links: no
                                      three roads may have ending points closer to this in the target geometry
                                   */

  val MinAllowedRoadAddressLength = 0.1
  /* No road address can be generated on a segment smaller than this. */

  val MaxMoveDistanceBeforeFloating = 1.0
  /* Maximum amount a road start / end may move until it is turned into a floating road address */

  val NewRoadAddress: Long = -1000L

  val MaxDistanceForConnectedLinks = 0.1

  /* Used for small jumps on discontinuity or self-crossing tracks */
  val MaxJumpForSection = 50.0

  /* Maximum distance to consider the tracks to go side by side */
  val MaxDistanceBetweenTracks = 50.0

  val newCalibrationPointId: Long = -1000L

  /* Maximum distance of regular road link geometry to suravage geometry difference where splitting is allowed */
  val MaxSuravageToleranceToGeometry = 0.5

  val ErrorNoMatchingProjectLinkForSplit = "Suravage-linkkiä vastaavaa käsittelemätöntä tieosoitelinkkiä ei löytynyt projektista"
  val ErrorFollowingRoadPartsNotFoundInDB = "Seuraavia tieosia ei löytynyt tietokannasta:"
  val ErrorFollowingPartsHaveDifferingEly = "Seuraavat tieosat ovat eri ELY-numerolla kuin projektin muut osat:"
  val ErrorRoadPartsHaveDifferingEly = "Tieosat ovat eri ELYistä"
  val ErrorSuravageLinkNotFound = "Suravage-linkkiä ei löytynyt"
  val RampsMinBound = 20001
  val RampsMaxBound = 39999

  val MaxLengthChange = 1.0

  val DefaultScreenWidth = 1920
  val DefaultScreenHeight = 1080
  val Resolutions = Array(2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25, 0.125, 0.0625)


  def switchSideCode(sideCode: SideCode): SideCode = {
    // Switch between against and towards 2 -> 3, 3 -> 2
    SideCode.apply(5-sideCode.value)
  }

  private def isRamp(roadNumber: Long, trackCode: Long): Boolean = {
    roadNumber >= RampsMinBound && roadNumber <= RampsMaxBound && trackCode == 0
  }

  def isRamp(r: RoadAddressLinkLike): Boolean = {
    isRamp(r.roadNumber, r.trackCode)
  }

  def isRamp(r: BaseRoadAddress): Boolean = {
    isRamp(r.roadNumber, r.track.value)
  }

  def toString(geometry: Seq[Point]) = {
    def toBD(d: Double): String = {
      BigDecimal(d).setScale(3, BigDecimal.RoundingMode.HALF_UP).toString
    }
    geometry.map(p => Seq(p.x, p.y, p.z).map(toBD).mkString(",", "[","]")).mkString(",")
  }

  def toGeometry(geometryString: String) = {

  }
}
