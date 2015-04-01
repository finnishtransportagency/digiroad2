(function(root) {
  var linkSizeLookup = {
    9: { strokeWidth: 3 },
    10: { strokeWidth: 5 },
    11: { strokeWidth: 8 },
    12: { strokeWidth: 10 },
    13: { strokeWidth: 10 },
    14: { strokeWidth: 14 },
    15: { strokeWidth: 14 }
  };

  root.RoadLayerSelectionStyle = {
    linkSizeLookup: linkSizeLookup,
    create: function(roadLayer, defaultOpacity) {
      var roadLayerStyleMap = new OpenLayers.StyleMap({
        "select": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
          strokeOpacity: 0.85,
          strokeColor: "#7f7f7c"
        })),
        "default": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
          strokeColor: "#a4a4a2",
          strokeOpacity: defaultOpacity
        }))
      });
      roadLayer.addUIStateDependentLookupToStyleMap(roadLayerStyleMap, 'default', 'zoomLevel', linkSizeLookup);
      return roadLayerStyleMap;
    }
  };
})(this);
