package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.service.AssetOnExpiredLink
import slick.jdbc.StaticQuery.interpolation

class AssetsOnExpiredLinksDAO {

  def insertToWorkList(asset: AssetOnExpiredLink): Unit = {
    sqlu"""INSERT INTO assets_on_expired_road_links (asset_id, asset_type_id, link_id, side_code, start_measure, end_measure, road_link_expired_date, asset_geometry)
          VALUES (${asset.id},
                  ${asset.assetTypeId},
                  ${asset.linkId},
                  ${asset.sideCode},
                  ${asset.startMeasure},
                  ${asset.endMeasure},
                  ${asset.roadLinkExpiredDate},
                  ${asset.geometry})
        """
  }
}
