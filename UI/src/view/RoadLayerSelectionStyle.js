(function(root) {
  var linkSizeLookup = {
    9: { strokeWidth: 3 },
    10: { strokeWidth: 5 },
    11: { strokeWidth: 9 },
    12: { strokeWidth: 16 },
    13: { strokeWidth: 16 },
    14: { strokeWidth: 16 },
    15: { strokeWidth: 16 }
  };

  root.RoadLayerSelectionStyle = {
    linkSizeLookup: linkSizeLookup,
    add: function(roadLayer, layerName) {
      var roadLayerStyleMap = new OpenLayers.StyleMap({
        "select": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
          strokeOpacity: 0.85,
          strokeColor: "#7f7f7c"
        })),
        "default": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
          strokeColor: "#a4a4a2",
          strokeOpacity: 0.3
        }))
      });
      roadLayer.addUIStateDependentLookupToStyleMap(roadLayerStyleMap, 'default', 'zoomLevel', linkSizeLookup);
      roadLayer.setLayerSpecificStyleMap(layerName, roadLayerStyleMap);
    }
  };
})(this);
