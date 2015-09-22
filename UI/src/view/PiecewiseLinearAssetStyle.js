(function(root) {
  root.PiecewiseLinearAssetStyle = function(applicationModel) {
    var expirationRules = [
      new OpenLayersRule().where('expired').is(true).use({strokeColor: '#7f7f7c'}),
      new OpenLayersRule().where('expired').is(false).use({strokeColor: '#ff0000'})
    ];

    var zoomLevelRules = [
      new OpenLayersRule().where('level', applicationModel.zoom).is(9).use(RoadLayerSelectionStyle.linkSizeLookup[9]),
      new OpenLayersRule().where('level', applicationModel.zoom).is(10).use(RoadLayerSelectionStyle.linkSizeLookup[10]),
      new OpenLayersRule().where('level', applicationModel.zoom).is(11).use(RoadLayerSelectionStyle.linkSizeLookup[11]),
      new OpenLayersRule().where('level', applicationModel.zoom).is(12).use(RoadLayerSelectionStyle.linkSizeLookup[12]),
      new OpenLayersRule().where('level', applicationModel.zoom).is(13).use(RoadLayerSelectionStyle.linkSizeLookup[13]),
      new OpenLayersRule().where('level', applicationModel.zoom).is(14).use(RoadLayerSelectionStyle.linkSizeLookup[14]),
      new OpenLayersRule().where('level', applicationModel.zoom).is(15).use(RoadLayerSelectionStyle.linkSizeLookup[15])
    ];

    var featureTypeRules = [
      new OpenLayersRule().where('type').is('line').use({ strokeOpacity: 0.7 }),
      new OpenLayersRule().where('type').is('cutter').use({ externalGraphic: 'images/cursor-crosshair.svg', pointRadius: 11.5 })
    ];

    var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults());
    browseStyle.addRules(expirationRules);
    browseStyle.addRules(zoomLevelRules);
    browseStyle.addRules(featureTypeRules);
    var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });

    var selectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.15
    }));
    var selectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7
    }));
    selectionDefaultStyle.addRules(expirationRules);
    selectionDefaultStyle.addRules(zoomLevelRules);
    selectionSelectStyle.addRules(featureTypeRules);
    var selectionStyle = new OpenLayers.StyleMap({
      default: selectionDefaultStyle,
      select: selectionSelectStyle
    });

    var lineFeatures = function(linearAssets) {
      return _.flatten(_.map(linearAssets, function(linearAsset) {
        var points = _.map(linearAsset.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), linearAsset);
      }));
    };

    var renderFeatures = function(linearAssets) {
      var linearAssetsWithType = _.map(linearAssets, function(limit) {
        var expired = _.isUndefined(limit.id) || limit.expired;
        return _.merge({}, limit, { type: 'line', expired: expired });
      });
      var offsetBySideCode = function(linearAsset) {
        return LinearAsset().offsetBySideCode(applicationModel.zoom.level, linearAsset);
      };
      var linearAssetsWithAdjustments = _.map(linearAssetsWithType, offsetBySideCode);
      return lineFeatures(linearAssetsWithAdjustments);
    };

    return {
      browsing: browseStyleMap,
      selection: selectionStyle,
      renderFeatures: renderFeatures
    };
  };
})(this);

