(function(root) {
  root.PointAssetStyle = function() {
    var featureTypeRules = [
      new OpenLayersRule().where('floating').is(false).use({ externalGraphic: 'images/point-assets/point_blue.svg' }),
      new OpenLayersRule().where('floating').is(true).use({ externalGraphic: 'images/point-assets/point_red.svg' })
    ];

    var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      graphicWidth: 14,
      graphicHeight: 14,
      graphicXOffset: -7,
      graphicYOffset: -7
    }));
    browseStyle.addRules(featureTypeRules);
    var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });

    return {
      browsing: browseStyleMap
    };
  };
})(this);

