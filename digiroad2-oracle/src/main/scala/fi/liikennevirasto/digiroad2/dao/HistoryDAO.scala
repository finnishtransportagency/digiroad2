package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

object HistoryDAO {
  //  val tableHistoryRelations = Map(
  //    "ASSET" -> "ASSET_HISTORY",
  //    "DATE_PERIOD_VALUE" -> "DATE_PERIOD_VALUE_HISTORY",
  //    "ADDITIONAL_PANEL" -> "ADDITIONAL_PANEL_HISTORY",
  //    "VALIDITY_PERIOD_PROPERTY_VALUE" -> "VAL_PERIOD_PROPERTY_VALUE_HIST",
  //    "DATE_PROPERTY_VALUE" -> "DATE_PROPERTY_VALUE_HISTORY",
  //    "SERVICE_POINT_VALUE" -> "SERVICE_POINT_VALUE_HISTORY",
  //    "NUMBER_PROPERTY_VALUE" -> "NUMBER_PROPERTY_VALUE_HISTORY",
  //    "MULTIPLE_CHOICE_VALUE" -> "MULTIPLE_CHOICE_VALUE_HISTORY",
  //    "SINGLE_CHOICE_VALUE" -> "SINGLE_CHOICE_VALUE_HISTORY",
  //    "TEXT_PROPERTY_VALUE" -> "TEXT_PROPERTY_VALUE_HISTORY",
  //    "PROHIBITION_VALUE" -> "PROHIBITION_VALUE_HISTORY",
  //    "PROHIBITION_EXCEPTION" -> "PROHIBITION_EXCEPTION_HISTORY",
  //    "PROHIBITION_VALIDITY_PERIOD" -> "PROH_VAL_PERIOD_HISTORY",
  //    "LRM_POSITION" -> "LRM_POSITION_HISTORY",
  //    "ASSET_LINK" -> "ASSET_LINK_HISTORY")

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
          SELECT $historyLrmPositionId, lp.LANE_CODE, lp.SIDE_CODE, lp.START_MEASURE, lp.END_MEASURE, lp.MML_ID,
          lp.LINK_ID, lp.ADJUSTED_TIMESTAMP, lp.MODIFIED_DATE, lp.LINK_SOURCE
          FROM LRM_POSITION lp WHERE lp.ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO ASSET_LINK_HISTORY(ASSET_ID, POSITION_ID) VALUES (historyId, historyLrmPositionId)
    """.execute



    //Copy standard values
    // TODO: check if possible to remove some of them since they are practically the same(check val tableHistoryRelations)
    sqlu"""
        INSERT INTO DATE_PERIOD_VALUE_HISTORY(ID, ASSET_ID, PROPERTY_ID, START_DATE, END_DATE)
          SELECT primary_key_seq.nextval, $historyId, dpv.PROPERTY_ID, dpv.START_DATE, dpv.END_DATE
          FROM DATE_PERIOD_VALUE dpv WHERE dpv.ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO ADDITIONAL_PANEL_HISTORY(ID, ASSET_ID, PROPERTY_ID, ADDITIONAL_SIGN_TYPE, ADDITIONAL_SIGN_VALUE,
        ADDITIONAL_SIGN_INFO, FORM_POSITION, ADDITIONAL_SIGN_TEXT, ADDITIONAL_SIGN_SIZE, ADDITIONAL_SIGN_COATING_TYPE,
        ADDITIONAL_SIGN_PANEL_COLOR)
          SELECT primary_key_seq.nextval, $historyId, ap.PROPERTY_ID, ap.ADDITIONAL_SIGN_TYPE, ap.ADDITIONAL_SIGN_VALUE,
          ap.ADDITIONAL_SIGN_INFO, ap.FORM_POSITION, ap.ADDITIONAL_SIGN_TEXT, ap.ADDITIONAL_SIGN_SIZE,
          ap.ADDITIONAL_SIGN_COATING_TYPE, ap.ADDITIONAL_SIGN_PANEL_COLOR
          FROM ADDITIONAL_PANEL ap WHERE ap.ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO VAL_PERIOD_PROPERTY_VALUE_HIST(ID, ASSET_ID, PROPERTY_ID, TYPE, PERIOD_WEEK_DAY, START_HOUR,
        END_HOUR, START_MINUTE, END_MINUTE)
          SELECT primary_key_seq.nextval, $historyId, vppv.PROPERTY_ID, vppv.TYPE, vppv.PERIOD_WEEK_DAY,
          vppv.START_HOUR, vppv.END_HOUR, vppv.START_MINUTE, vppv.END_MINUTE
          FROM VALIDITY_PERIOD_PROPERTY_VALUE vppv WHERE vppv.ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO DATE_PROPERTY_VALUE_HISTORY(ID, ASSET_ID, PROPERTY_ID, DATE_TIME)
          SELECT primary_key_seq.nextval, $historyId, vppv.PROPERTY_ID, vppv.DATE_TIME
          FROM DATE_PROPERTY_VALUE dpv WHERE dpv.ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO SERVICE_POINT_VALUE_HISTORY(ID, ASSET_ID, TYPE, ADDITIONAL_INFO, PARKING_PLACE_COUNT, NAME,
        TYPE_EXTENSION, IS_AUTHORITY_DATA,WEIGHT_LIMIT)
          SELECT primary_key_seq.nextval, $historyId, spv.TYPE, spv.ADDITIONAL_INFO, spv.PARKING_PLACE_COUNT,
          spv.NAME, spv.TYPE_EXTENSION, spv.IS_AUTHORITY_DATA, spv.WEIGHT_LIMIT
          FROM SERVICE_POINT_VALUE spv WHERE spv.ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO NUMBER_PROPERTY_VALUE_HISTORY(ID, ASSET_ID, PROPERTY_ID, VALUE, GROUPED_ID)
          SELECT primary_key_seq.nextval, $historyId, npv.PROPERTY_ID, npv.VALUE, npv.GROUPED_ID
          FROM NUMBER_PROPERTY_VALUE npv WHERE npv.ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO MULTIPLE_CHOICE_VALUE_HISTORY(ID, ASSET_ID, PROPERTY_ID, ENUMERATED_VALUE_ID, MODIFIED_DATE,
        MODIFIED_BY, GROUPED_ID)
          SELECT primary_key_seq.nextval, $historyId, mcv.PROPERTY_ID, mcv.ENUMERATED_VALUE_ID, mcv.MODIFIED_DATE,
          mcv.MODIFIED_BY, mcv.GROUPED_ID
          FROM MULTIPLE_CHOICE_VALUE mcv WHERE mcv.ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO SINGLE_CHOICE_VALUE_HISTORY(ASSET_ID, PROPERTY_ID, ENUMERATED_VALUE_ID, MODIFIED_DATE, MODIFIED_BY,
        GROUPED_ID)
          SELECT $historyId, scv.PROPERTY_ID, scv.ENUMERATED_VALUE_ID, scv.MODIFIED_DATE, scv.MODIFIED_BY, scv.GROUPED_ID
          FROM SINGLE_CHOICE_VALUE scv WHERE scv.ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO TEXT_PROPERTY_VALUE_HISTORY(ID, ASSET_ID, PROPERTY_ID, VALUE_FI, VALUE_SV, CREATED_DATE, CREATED_BY,
        MODIFIED_DATE, MODIFIED_BY, GROUPED_ID)
          SELECT primary_key_seq.nextval, $historyId, tpv.PROPERTY_ID, tpv.VALUE_FI, tpv.VALUE_SV, tpv.CREATED_DATE,
          tpv.CREATED_BY, tpv.MODIFIED_DATE, tpv.MODIFIED_BY, tpv.GROUPED_ID
          FROM TEXT_PROPERTY_VALUE tpv WHERE tpv.ASSET_ID = $assetId
    """.execute



    //Copy prohibition value
    val historyProhibitionValueId = Sequences.nextPrimaryKeySeqValue

    sqlu"""
        INSERT INTO PROHIBITION_VALUE_HISTORY(ID, ASSET_ID, TYPE, ADDITIONAL_INFO)
          SELECT $historyProhibitionValueId, $historyId, pv.TYPE, pv.ADDITIONAL_INFO
          FROM PROHIBITION_VALUE pv WHERE pv.ASSET_ID = $assetId
    """.execute

    sqlu"""
        INSERT INTO PROHIBITION_EXCEPTION_HISTORY(ID, PROHIBITION_VALUE_ID, TYPE)
          SELECT primary_key_seq.nextval, $historyProhibitionValueId, pe.TYPE
          FROM PROHIBITION_EXCEPTION pe
          WHERE pe.PROHIBITION_VALUE_ID = (SELECT pv.ID FROM PROHIBITION_VALUE pv WHERE pv.ASSET_ID = $assetId)
    """.execute

    sqlu"""
        INSERT INTO PROH_VAL_PERIOD_HISTORY(ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR, START_MINUTE,
        END_MINUTE)
          SELECT primary_key_seq.nextval, $historyProhibitionValueId, pvp.TYPE, pvp.START_HOUR, pvp.END_HOUR,
          pvp.START_MINUTE, pvp.END_MINUTE
          FROM PROHIBITION_VALIDITY_PERIOD pvp
          WHERE pvp.PROHIBITION_VALUE_ID = (SELECT pv.ID FROM PROHIBITION_VALUE pv WHERE pv.ASSET_ID = $assetId)
    """.execute

    historyId
  }

  def deleteAsset(assetId: Long) = {
    //Delete standard values
    standardTableValues.foreach { tableValue =>
      sqlu"""DELETE FROM $tableValue WHERE ASSET_ID = $assetId""".execute
    }



    //Delete prohibition value
    sqlu"""DELETE FROM PROHIBITION_EXCEPTION
          WHERE PROHIBITION_VALUE_ID = (SELECT pv.ID FROM PROHIBITION_VALUE pv WHERE pv.ASSET_ID = $assetId)""".execute

    sqlu"""DELETE FROM PROHIBITION_VALIDITY_PERIOD
          WHERE PROHIBITION_VALUE_ID = (SELECT pv.ID FROM PROHIBITION_VALUE pv WHERE pv.ASSET_ID = $assetId)""".execute

    //TODO check if this one can be on standardTableValues
    sqlu"""DELETE FROM PROHIBITION_VALUE WHERE ASSET_ID = $assetId""".execute



    //Delete asset/lrm position values and relation
    val positionId = sql"""SELECT POSITION_ID FROM ASSET_LINK WHERE ASSET_ID = $assetId""".as[Long].first

    //TODO check if this one can be on standardTableValues
    sqlu"""DELETE FROM ASSET_LINK WHERE ASSET_ID = $assetId""".execute

    sqlu"""DELETE FROM LRM_POSITION WHERE ID = $positionId""".execute
    sqlu"""DELETE FROM ASSET WHERE ID = $assetId""".execute
  }
}