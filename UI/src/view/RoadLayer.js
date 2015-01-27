var RoadStyles = function() {
  var styleMap = new OpenLayers.StyleMap({
    "select": new OpenLayers.Style({
      strokeWidth: 6,
      strokeOpacity: 1,
      strokeColor: "#5eaedf"
    }),
    "default": new OpenLayers.Style({
      strokeWidth: 5,
      strokeColor: "#a4a4a2",
      strokeOpacity: 0.7
    })
  });

  this.roadStyles = styleMap;
  styleMap.styles.default.rules.push(new OpenLayers.Rule({
    elseFilter: true,
    symbolizer: styleMap.styles.default.defaultStyle
  }));
};

(function(root) {
  root.RoadLayer = function(map, roadCollection) {
    var vectorLayer;
    var selectControl;
    var layerStyleMaps = {};
    var uiState = { zoomLevel: 9 };

    var enableColorsOnRoadLayer = function() {
      if (_.isUndefined(layerStyleMaps[applicationModel.getSelectedLayer()])) {
        var administrativeClassStyleLookup = {
          Private: { strokeColor: "#0011bb" },
          Municipality: { strokeColor: "#11bb00" },
          State: { strokeColor: "#ff0000" }
        };
        vectorLayer.styleMap.addUniqueValueRules("default", "administrativeClass", administrativeClassStyleLookup);
      }
    };

    var disableColorsOnRoadLayer = function() {
      if (_.isUndefined(layerStyleMaps[applicationModel.getSelectedLayer()])) {
        vectorLayer.styleMap.styles.default.rules = [];
      }
    };

    var changeRoadsWidthByZoomLevel = function() {
      if (_.isUndefined(layerStyleMaps[applicationModel.getSelectedLayer()])) {
        var widthBase = 2 + (map.getZoom() - zoomlevels.minZoomForRoadLinks);
        var roadWidth = widthBase * widthBase;
        if (applicationModel.isRoadTypeShown()) {
          vectorLayer.styleMap.styles.default.defaultStyle.strokeWidth = roadWidth;
          vectorLayer.styleMap.styles.select.defaultStyle.strokeWidth = roadWidth;
        } else {
          vectorLayer.styleMap.styles.default.defaultStyle.strokeWidth = 5;
          vectorLayer.styleMap.styles.select.defaultStyle.strokeWidth = 7;
        }
      }
    };

    var toggleRoadType = function() {
      if (applicationModel.isRoadTypeShown()) {
        enableColorsOnRoadLayer();
      } else {
        disableColorsOnRoadLayer();
      }
      changeRoadsWidthByZoomLevel();
      vectorLayer.redraw();
    };

    var handleRoadsVisibility = function() {
      if (_.isObject(vectorLayer)) {
        vectorLayer.setVisibility(zoomlevels.isInRoadLinkZoomLevel(map.getZoom()));
      }
    };

    var fetchRoads = function(bbox, zoom) {
      roadCollection.fetch(bbox, zoom);
    };

    var mapMovedHandler = function(mapState) {
      if (zoomlevels.isInRoadLinkZoomLevel(mapState.zoom)) {
        fetchRoads(mapState.bbox, mapState.zoom);
        changeRoadsWidthByZoomLevel();
      } else {
        vectorLayer.removeAllFeatures();
        roadCollection.reset();
      }
      handleRoadsVisibility();
    };

    var drawRoadLinks = function(roadLinks, zoom) {
      uiState.zoomLevel = zoom;
      eventbus.trigger('roadLinks:beforeDraw');
      vectorLayer.removeAllFeatures();
      var features = _.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), roadLink);
      });
      vectorLayer.addFeatures(features);
      eventbus.trigger('roadLinks:afterDraw', roadLinks);
    };

    var drawRoadLink = function(roadLink) {
      var points = _.map(roadLink.points, function(point) {
        return new OpenLayers.Geometry.Point(point.x, point.y);
      });
      var feature = new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), roadLink);
      vectorLayer.addFeatures([feature]);
    };

    var setLayerSpecificStyleMap = function(layer, styleMap) {
      layerStyleMaps[layer] = styleMap;
      if (applicationModel.getSelectedLayer() === layer) {
        activateLayerStyleMap(layer);
      }
    };

    var addUIStateDependentLookupToStyleMap = function(styleMap, renderingIntent, uiAttribute, lookup) {
      styleMap.addUniqueValueRules(renderingIntent, uiAttribute, lookup, uiState);
    };

    var createZoomLevelFilter = function(zoomLevel) {
      return new OpenLayers.Filter.Function({ evaluate: function() { return uiState.zoomLevel === zoomLevel; } });
    };

    var activateLayerStyleMap = function(layer) {
      vectorLayer.styleMap = layerStyleMaps[layer] || new RoadStyles().roadStyles;
    };

    eventbus.on('road:active', function(roadLinkId) {
      var nearestFeature = _.find(vectorLayer.features, function(feature) {
        return feature.attributes.roadLinkId == roadLinkId;
      });
      selectControl.unselectAll();
      selectControl.select(nearestFeature);
    }, this);

    eventbus.on('asset:saved asset:updateCancelled asset:updateFailed', function() {
      selectControl.unselectAll();
    }, this);

    eventbus.on('road-type:selected', toggleRoadType, this);

    eventbus.on('map:moved', mapMovedHandler, this);

    eventbus.on('roadLinks:fetched', function(roadLinks, zoom) {
      drawRoadLinks(roadLinks, zoom);
    }, this);

    eventbus.on('layer:selected', function(layer) {
      activateLayerStyleMap(layer);
      if (zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
        fetchRoads(map.getExtent(), map.getZoom());
      }
      toggleRoadType();
    }, this);

    vectorLayer = new OpenLayers.Layer.Vector("road", {
      styleMap: new RoadStyles().roadStyles
    });
    vectorLayer.setVisibility(false);
    selectControl = new OpenLayers.Control.SelectFeature(vectorLayer);
    map.addLayer(vectorLayer);
    toggleRoadType();

    return {
      layer: vectorLayer,
      setLayerSpecificStyleMap: setLayerSpecificStyleMap,
      addUIStateDependentLookupToStyleMap: addUIStateDependentLookupToStyleMap,
      drawRoadLink: drawRoadLink,
      createZoomLevelFilter: createZoomLevelFilter
    };
  };
})(this);
