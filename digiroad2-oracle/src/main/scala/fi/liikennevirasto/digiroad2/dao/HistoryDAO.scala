package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

object HistoryDAO {
  val standardTableValues = Seq("DATE_PERIOD_VALUE","ADDITIONAL_PANEL", "VALIDITY_PERIOD_PROPERTY_VALUE",
    "DATE_PROPERTY_VALUE", "SERVICE_POINT_VALUE", "NUMBER_PROPERTY_VALUE", "MULTIPLE_CHOICE_VALUE",
    "SINGLE_CHOICE_VALUE", "TEXT_PROPERTY_VALUE")

  def getExpiredAssetsIdsByAssetTypeAndYearGap(assetTypeId: Int, yearGap: Int) = {
    sql"""
      SELECT ID FROM ASSET WHERE ASSET_TYPE_ID = $assetTypeId AND EXTRACT(YEAR FROM VALID_TO) < EXTRACT(YEAR FROM SYSDATE) - $yearGap
    """.as[Long].list
  }

  def transferExpiredAssetToHistoryById(assetId: Long) = {
    val historyId = copyAssetToHistory(assetId)
    deleteAsset(assetId)

    historyId
  }

  def copyAssetToHistory(assetId: Long) = {
    val historyId = Sequences.nextPrimaryKeySeqValue
    val historyLrmPositionId = Sequences.nextPrimaryKeySeqValue

    //Copy asset/lrm position values and relation
    sqlu"""
        INSERT INTO ASSET_HISTORY
          SELECT $historyId, a.* FROM ASSET a WHERE a.ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO LRM_POSITION_HISTORY(ID, LANE_CODE, SIDE_CODE, START_MEASURE, END_MEASURE, MML_ID, LINK_ID,
        ADJUSTED_TIMESTAMP, MODIFIED_DATE, LINK_SOURCE)
          SELECT $historyLrmPositionId, LANE_CODE, SIDE_CODE, START_MEASURE, END_MEASURE, MML_ID, LINK_ID,
          ADJUSTED_TIMESTAMP, MODIFIED_DATE, LINK_SOURCE
          FROM LRM_POSITION WHERE ID = (SELECT al.POSITION_ID FROM ASSET_LINK al WHERE al.ASSET_ID = $assetId)
    """.execute

    sqlu"""
        INSERT INTO ASSET_LINK_HISTORY(ASSET_ID, POSITION_ID) VALUES ($historyId, $historyLrmPositionId)
    """.execute



    //Copy standard values
    // TODO: check if possible to remove some of them since they are practically the same
    sqlu"""
        INSERT INTO DATE_PERIOD_VALUE_HISTORY(ID, ASSET_ID, PROPERTY_ID, START_DATE, END_DATE)
          SELECT primary_key_seq.nextval, $historyId, PROPERTY_ID, START_DATE, END_DATE
          FROM DATE_PERIOD_VALUE WHERE ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO ADDITIONAL_PANEL_HISTORY(ID, ASSET_ID, PROPERTY_ID, ADDITIONAL_SIGN_TYPE, ADDITIONAL_SIGN_VALUE,
        ADDITIONAL_SIGN_INFO, FORM_POSITION, ADDITIONAL_SIGN_TEXT, ADDITIONAL_SIGN_SIZE, ADDITIONAL_SIGN_COATING_TYPE,
        ADDITIONAL_SIGN_PANEL_COLOR)
          SELECT primary_key_seq.nextval, $historyId, PROPERTY_ID, ADDITIONAL_SIGN_TYPE, ADDITIONAL_SIGN_VALUE,
          ADDITIONAL_SIGN_INFO, FORM_POSITION, ADDITIONAL_SIGN_TEXT, ADDITIONAL_SIGN_SIZE, ADDITIONAL_SIGN_COATING_TYPE,
          ADDITIONAL_SIGN_PANEL_COLOR
          FROM ADDITIONAL_PANEL WHERE ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO VAL_PERIOD_PROPERTY_VALUE_HIST(ID, ASSET_ID, PROPERTY_ID, TYPE, PERIOD_WEEK_DAY, START_HOUR,
        END_HOUR, START_MINUTE, END_MINUTE)
          SELECT primary_key_seq.nextval, $historyId, PROPERTY_ID, TYPE, PERIOD_WEEK_DAY, START_HOUR, END_HOUR,
          START_MINUTE, END_MINUTE
          FROM VALIDITY_PERIOD_PROPERTY_VALUE WHERE ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO DATE_PROPERTY_VALUE_HISTORY(ID, ASSET_ID, PROPERTY_ID, DATE_TIME)
          SELECT primary_key_seq.nextval, $historyId, PROPERTY_ID, DATE_TIME
          FROM DATE_PROPERTY_VALUE WHERE ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO SERVICE_POINT_VALUE_HISTORY(ID, ASSET_ID, TYPE, ADDITIONAL_INFO, PARKING_PLACE_COUNT, NAME,
        TYPE_EXTENSION, IS_AUTHORITY_DATA,WEIGHT_LIMIT)
          SELECT primary_key_seq.nextval, $historyId, TYPE, ADDITIONAL_INFO, PARKING_PLACE_COUNT, NAME, TYPE_EXTENSION,
          IS_AUTHORITY_DATA, WEIGHT_LIMIT
          FROM SERVICE_POINT_VALUE WHERE ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO NUMBER_PROPERTY_VALUE_HISTORY(ID, ASSET_ID, PROPERTY_ID, VALUE, GROUPED_ID)
          SELECT primary_key_seq.nextval, $historyId, PROPERTY_ID, VALUE, GROUPED_ID
          FROM NUMBER_PROPERTY_VALUE WHERE ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO MULTIPLE_CHOICE_VALUE_HISTORY(ID, ASSET_ID, PROPERTY_ID, ENUMERATED_VALUE_ID, MODIFIED_DATE,
        MODIFIED_BY, GROUPED_ID)
          SELECT primary_key_seq.nextval, $historyId, PROPERTY_ID, ENUMERATED_VALUE_ID, MODIFIED_DATE, MODIFIED_BY,
          GROUPED_ID
          FROM MULTIPLE_CHOICE_VALUE WHERE ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO SINGLE_CHOICE_VALUE_HISTORY(ASSET_ID, PROPERTY_ID, ENUMERATED_VALUE_ID, MODIFIED_DATE, MODIFIED_BY,
        GROUPED_ID)
          SELECT $historyId, PROPERTY_ID, ENUMERATED_VALUE_ID, MODIFIED_DATE, MODIFIED_BY, GROUPED_ID
          FROM SINGLE_CHOICE_VALUE WHERE ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO TEXT_PROPERTY_VALUE_HISTORY(ID, ASSET_ID, PROPERTY_ID, VALUE_FI, VALUE_SV, CREATED_DATE, CREATED_BY,
        MODIFIED_DATE, MODIFIED_BY, GROUPED_ID)
          SELECT primary_key_seq.nextval, $historyId, PROPERTY_ID, VALUE_FI, VALUE_SV, CREATED_DATE, CREATED_BY,
          MODIFIED_DATE, MODIFIED_BY, GROUPED_ID
          FROM TEXT_PROPERTY_VALUE WHERE ASSET_ID = $assetId
    """.execute



    //Copy prohibition value
    val historyProhibitionValueId = Sequences.nextPrimaryKeySeqValue

    sqlu"""
        INSERT INTO PROHIBITION_VALUE_HISTORY(ID, ASSET_ID, TYPE, ADDITIONAL_INFO)
          SELECT $historyProhibitionValueId, $historyId, TYPE, ADDITIONAL_INFO
          FROM PROHIBITION_VALUE WHERE ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO PROHIBITION_EXCEPTION_HISTORY(ID, PROHIBITION_VALUE_ID, TYPE)
          SELECT primary_key_seq.nextval, $historyProhibitionValueId, TYPE
          FROM PROHIBITION_EXCEPTION
          WHERE PROHIBITION_VALUE_ID = (SELECT pv.ID FROM PROHIBITION_VALUE pv WHERE pv.ASSET_ID = $assetId)
    """.execute

    sqlu"""
        INSERT INTO PROH_VAL_PERIOD_HISTORY(ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR, START_MINUTE,
        END_MINUTE)
          SELECT primary_key_seq.nextval, $historyProhibitionValueId, TYPE, START_HOUR, END_HOUR, START_MINUTE,
          END_MINUTE
          FROM PROHIBITION_VALIDITY_PERIOD
          WHERE PROHIBITION_VALUE_ID = (SELECT pv.ID FROM PROHIBITION_VALUE pv WHERE pv.ASSET_ID = $assetId)
    """.execute

    historyId
  }

  def deleteAsset(assetId: Long) = {
    //Delete standard values
    standardTableValues.foreach { tableValue =>
      sqlu"""DELETE FROM #$tableValue WHERE ASSET_ID = $assetId""".execute
    }



    //Delete prohibition value
    sqlu"""DELETE FROM PROHIBITION_EXCEPTION
          WHERE PROHIBITION_VALUE_ID = (SELECT pv.ID FROM PROHIBITION_VALUE pv WHERE pv.ASSET_ID = $assetId)""".execute
    sqlu"""DELETE FROM PROHIBITION_VALIDITY_PERIOD
          WHERE PROHIBITION_VALUE_ID = (SELECT pv.ID FROM PROHIBITION_VALUE pv WHERE pv.ASSET_ID = $assetId)""".execute
    sqlu"""DELETE FROM PROHIBITION_VALUE WHERE ASSET_ID = $assetId""".execute



    //Delete asset/lrm position values and relation
    val positionId = sql"""SELECT POSITION_ID FROM ASSET_LINK WHERE ASSET_ID = $assetId""".as[Long].first
    sqlu"""DELETE FROM ASSET_LINK WHERE ASSET_ID = $assetId""".execute
    sqlu"""DELETE FROM LRM_POSITION WHERE ID = $positionId""".execute
    sqlu"""DELETE FROM ASSET WHERE ID = $assetId""".execute
  }
}