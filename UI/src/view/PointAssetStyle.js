(function(root) {
  root.PointAssetStyle = function() {
    var defaultStyleParameters = {
      graphicWidth: 14,
      graphicHeight: 14,
      graphicXOffset: -7,
      graphicYOffset: -7
    };

    var featureTypeRules = [
      new OpenLayersRule().where('floating').is(false).use({ externalGraphic: 'images/point-assets/point_blue.svg' }),
      new OpenLayersRule().where('floating').is(true).use({ externalGraphic: 'images/point-assets/point_red.svg' })
    ];

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

