(function(root) {
  function getFeatureTypeRules(layerName) {
    var featureTypeRules;
    if (layerName === 'obstacles') {
      featureTypeRules = [
        new OpenLayersRule().where('obstacleType').is(1).use({externalGraphic: 'images/point-assets/point_blue.svg'}),
        new OpenLayersRule().where('obstacleType').is(2).use({externalGraphic: 'images/point-assets/point_green.svg'}),
        new OpenLayersRule().where('floating').is(true).use({externalGraphic: 'images/point-assets/point_red.svg'})
      ];
    }
    else if (layerName === 'directionalTrafficSigns') {
      featureTypeRules = [
        new OpenLayersRule().where('floating').is(false).use({
          externalGraphic: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-directional-traffic-sign.svg',
          rotation: '${rotation}',
          graphicWidth: 30,
          graphicHeight: 16,
          graphicXOffset: -15,
          graphicYOffset: -8 }),
        new OpenLayersRule().where('floating').is(true).use({
          externalGraphic: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-warning-directional-traffic-sign.svg',
          rotation: '${rotation}',
          graphicWidth: 30,
          graphicHeight: 16,
          graphicXOffset: -15,
          graphicYOffset: -8 })
      ];
    }
    else {
      featureTypeRules = [
        new OpenLayersRule().where('floating').is(false).use({externalGraphic: 'images/point-assets/point_blue.svg'}),
        new OpenLayersRule().where('floating').is(true).use({externalGraphic: 'images/point-assets/point_red.svg'})
      ];
    }
    return featureTypeRules;
  }

  root.PointAssetStyle = function(layerName) {
    var defaultStyleParameters = {
      graphicWidth: 14,
      graphicHeight: 14,
      graphicXOffset: -7,
      graphicYOffset: -7
    };

    var featureTypeRules = getFeatureTypeRules(layerName);

    var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults(defaultStyleParameters));
    browseStyle.addRules(featureTypeRules);
    var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });

    var selectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults(
      _.merge({}, defaultStyleParameters, { graphicOpacity: 0.3 })
    ));
    var selectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({ graphicOpacity: 0.9 }));
    selectionDefaultStyle.addRules(featureTypeRules);
    var selectionStyle = new OpenLayers.StyleMap({
      default: selectionDefaultStyle,
      select: selectionSelectStyle
    });

    return {
      browsing: browseStyleMap,
      selection: selectionStyle
    };
  };
})(this);

