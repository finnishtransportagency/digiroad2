package fi.liikennevirasto

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
                                   */

  val MinAllowedRoadAddressLength = 0.1
  /* No road address can be generated on a segment smaller than this. */

  val NewRoadAddress: Long = -1000L
}
