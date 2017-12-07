package fi.liikennevirasto.digiroad2.dao

object Sequences {
  def nextPrimaryKeySeqValue = {
    nextPrimaryKeyId.as[Long].first
  }

  def nextLrmPositionPrimaryKeySeqValue = {
    nextLrmPositionPrimaryKeyId.as[Long].first
  }

  def nextViitePrimaryKeySeqValue = {
    nextViitePrimaryKeyId.as[Long].first
  }

  def fetchViitePrimaryKeySeqValues(len: Int) = {
    fetchViitePrimaryKeyId(len)
  }

}
