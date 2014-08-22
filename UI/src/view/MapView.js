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
  root.MapView = function(map, roadCollection) {
    var roadLayer;
    var selectControl;
    var roadTypeSelected;
    var isInitialized = false;
    var centerMarkerLayer;
    var layers;

    var showAssetZoomDialog = function() {
      var dialog = Oskari.clazz.create('Oskari.userinterface.component.Popup');
      dialog.show('Zoomaa l&auml;hemm&auml;ksi, jos haluat n&auml;hd&auml; kohteita');
      dialog.fadeout(2000);
    };

    var enableColorsOnRoadLayer = function() {
      var roadLinkTypeStyleLookup = {
        PrivateRoad: { strokeColor: "#0011bb" },
        Street: { strokeColor: "#11bb00" },
        Road: { strokeColor: "#ff0000" }
      };
      roadLayer.styleMap.addUniqueValueRules("default", "type", roadLinkTypeStyleLookup);
    };

    var disableColorsOnRoadLayer = function() {
      roadLayer.styleMap.styles.default.rules = [];
    };

    var changeRoadsWidthByZoomLevel = function() {
      var widthBase = 2 + (map.getZoom() - zoomlevels.minZoomForRoadLinks);
      var roadWidth = widthBase * widthBase;
      if (roadTypeSelected) {
        roadLayer.styleMap.styles.default.defaultStyle.strokeWidth = roadWidth;
        roadLayer.styleMap.styles.select.defaultStyle.strokeWidth = roadWidth;
      } else {
        roadLayer.styleMap.styles.default.defaultStyle.strokeWidth = 5;
        roadLayer.styleMap.styles.select.defaultStyle.strokeWidth = 7;
      }
    };

    var toggleRoadType = function(toggle) {
      if (toggle) {
        enableColorsOnRoadLayer();
      } else {
        disableColorsOnRoadLayer();
      }
      roadTypeSelected = toggle;
      changeRoadsWidthByZoomLevel();
      roadLayer.redraw();
    };

    var handleRoadsVisibility = function() {
      if (_.isObject(roadLayer)) {
        roadLayer.setVisibility(zoomlevels.isInRoadLinkZoomLevel(map.getZoom()));
      }
    };

    var mapMovedHandler = function(mapState) {
      if (zoomlevels.isInRoadLinkZoomLevel(mapState.zoom)) {
        changeRoadsWidthByZoomLevel();
        roadCollection.fetch(mapState.bbox);
      } else {
        roadLayer.removeAllFeatures();
      }

      handleRoadsVisibility();
      if (!zoomlevels.isInAssetZoomLevel(mapState.zoom)) {
        if (isInitialized && mapState.hasZoomLevelChanged) {
          showAssetZoomDialog();
        }
      }
    };

    var drawCenterMarker = function(position) {
      var size = new OpenLayers.Size(16, 16);
      var offset = new OpenLayers.Pixel(-(size.w / 2), -size.h / 2);
      var icon = new OpenLayers.Icon('./images/center-marker.svg', size, offset);

      centerMarkerLayer.clearMarkers();
      var marker = new OpenLayers.Marker(new OpenLayers.LonLat(position.lon, position.lat), icon);
      centerMarkerLayer.addMarker(marker);
    };

    var drawRoadLinks = function(roadLinks) {
      roadLayer.removeAllFeatures();
      var features = _.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), roadLink);
      });
      roadLayer.addFeatures(features);
    };

    var addLayersToMap = function(templates) {
      roadLayer = new OpenLayers.Layer.Vector("road", {
        styleMap: templates.roadStyles
      });
      roadLayer.setVisibility(false);
      selectControl = new OpenLayers.Control.SelectFeature(roadLayer);

      centerMarkerLayer = new OpenLayers.Layer.Markers('centerMarker');
      map.addLayer(roadLayer);

      var layers = {
        asset: new AssetLayer(map, roadCollection),
        speedLimit: new SpeedLimitLayer(map, backend)
      };
      map.addLayer(centerMarkerLayer);

      return layers;
    };

    eventbus.on('application:initialized', function() {
      var zoom = map.getZoom();
      applicationModel.setZoomLevel(zoom);
      if (!zoomlevels.isInAssetZoomLevel(zoom)) {
        showAssetZoomDialog();
      }
      new CoordinateSelector($('.mapplugin.coordinates'), map.getMaxExtent());
      isInitialized = true;
      eventbus.trigger('map:initialized', map);
    }, this);

    eventbus.on('asset:moving', function(nearestLine) {
      var nearestFeature = _.find(roadLayer.features, function(feature) {
        return feature.attributes.roadLinkId == nearestLine.roadLinkId;
      });
      selectControl.unselectAll();
      selectControl.select(nearestFeature);
    }, this);

    eventbus.on('asset:saved asset:updateCancelled asset:updateFailed', function() {
      selectControl.unselectAll();
    }, this);

    eventbus.on('road-type:selected', toggleRoadType, this);

    eventbus.on('tool:changed', function(action) {
      var cursor = {'Select': 'default', 'Add': 'crosshair', 'Remove': 'no-drop'};
      $('.olMap').css('cursor', cursor[action]);
    });

    eventbus.on('coordinates:selected coordinates:marked', function(position) {
      map.setCenter(new OpenLayers.LonLat(position.lon, position.lat), zoomlevels.getAssetZoomLevelIfNotCloser(map.getZoom()));
    }, this);

    eventbus.on('map:moved', mapMovedHandler, this);

    eventbus.on('coordinates:marked', function(position) {
      drawCenterMarker(position);
    }, this);

    eventbus.on('roadLinks:fetched', function(roadLinks) {
      drawRoadLinks(roadLinks);
    }, this);

    eventbus.on('layer:selected', function(layer) {
      var assetLayer = layers.asset;
      if (layer === 'speedLimit') {
        showSpeedLimitLayer();
        assetLayer.hide();
        disableColorsOnRoadLayer();
        roadLayer.redraw();
      } else {
        assetLayer.show();
        hideSpeedLimitLayer();
        toggleRoadType(roadTypeSelected);
      }
    }, this);

    var showSpeedLimitLayer = function() {
      var speedLimitLayer = layers.speedLimit;
      map.addLayer(speedLimitLayer.vectorLayer);
      speedLimitLayer.vectorLayer.setVisibility(true);
      speedLimitLayer.update(map.getZoom(), map.getExtent());
    };

    var hideSpeedLimitLayer = function() {
      layers.speedLimit.reset();
      map.removeLayer(layers.speedLimit.vectorLayer);
    };

    map.events.register('moveend', this, function() {
      applicationModel.moveMap(map.getZoom(), map.getExtent());
    });

    layers = addLayersToMap(new RoadStyles());
  };
})(this);
