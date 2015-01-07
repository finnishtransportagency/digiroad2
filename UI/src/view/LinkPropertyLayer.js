(function(root) {
  root.LinkPropertyLayer = function(map, roadLayer, geometryUtils, selectedLinkProperty) {
    var roadLinkTypeStyleLookup = {
      PrivateRoad: { strokeColor: '#0011bb', externalGraphic: 'images/link-properties/privateroad.svg' },
      Street: { strokeColor: '#11bb00', externalGraphic: 'images/link-properties/street.svg' },
      Road: { strokeColor: '#ff0000', externalGraphic: 'images/link-properties/road.svg' }
    };

    var oneWaySignSizeLookup = {
      9: { pointRadius: 0 },
      10: { pointRadius: 13 },
      11: { pointRadius: 16 },
      12: { pointRadius: 20 },
      13: { pointRadius: 25 },
      14: { pointRadius: 30 },
      15: { pointRadius: 35 }
    };

    var defaultStyleMap = new OpenLayers.StyleMap({
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeOpacity: 0.7,
        rotation: '${rotation}'
      }))
    });
    roadLayer.addUIStateDependentLookupToStyleMap(defaultStyleMap, 'default', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(defaultStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    defaultStyleMap.addUniqueValueRules('default', 'type', roadLinkTypeStyleLookup);
    roadLayer.setLayerSpecificStyleMap('linkProperties', defaultStyleMap);

    var selectionStyleMap = new OpenLayers.StyleMap({
      'select': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeOpacity: 0.7,
        graphicOpacity: 1.0,
        rotation: '${rotation}'
      })),
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeOpacity: 0.3,
        graphicOpacity: 0.3,
        rotation: '${rotation}'
      }))
    });
    roadLayer.addUIStateDependentLookupToStyleMap(selectionStyleMap, 'default', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(selectionStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(selectionStyleMap, 'select', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(selectionStyleMap, 'select', 'zoomLevel', oneWaySignSizeLookup);
    selectionStyleMap.addUniqueValueRules('default', 'type', roadLinkTypeStyleLookup);
    selectionStyleMap.addUniqueValueRules('select', 'type', roadLinkTypeStyleLookup);

    var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
      onSelect:  function(feature) {
        selectedLinkProperty.open(feature.attributes.roadLinkId);
        roadLayer.setLayerSpecificStyleMap('linkProperties', selectionStyleMap);
        roadLayer.layer.redraw();
        highlightFeatures(feature);
      },
      onUnselect: function() {
        deselectRoadLink();
        selectedLinkProperty.close();
        roadLayer.layer.redraw();
        highlightFeatures(null);
      }
    });
    map.addControl(selectControl);

    var eventListener = _.extend({running: false}, eventbus);

    var highlightFeatures = function(feature) {
      _.each(roadLayer.layer.features, function(x) {
        if (feature && (x.attributes.roadLinkId === feature.attributes.roadLinkId)) {
          selectControl.highlight(x);
        } else {
          selectControl.unhighlight(x);
        }
      });
    };

    var handleMapMoved = function(state) {
      if (zoomlevels.isInRoadLinkZoomLevel(state.zoom) && state.selectedLayer === 'linkProperties') {
        start();
      } else {
        stop();
      }
    };

    var drawOneWaySigns = function(roadLinks) {
      var oneWaySigns = _.chain(roadLinks)
        .filter(function(link) {
          return link.trafficDirection === 'AgainstDigitizing' || link.trafficDirection === 'TowardsDigitizing';
        })
        .map(function(link) {
          var points = _.map(link.points, function(point) {
            return new OpenLayers.Geometry.Point(point.x, point.y);
          });
          var lineString = new OpenLayers.Geometry.LineString(points);
          var signPosition = geometryUtils.calculateMidpointOfLineString(lineString);
          var rotation = link.trafficDirection === 'AgainstDigitizing' ? signPosition.angleFromNorth + 180.0 : signPosition.angleFromNorth;
          var attributes = _.merge({}, link, { rotation: rotation });
          return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(signPosition.x, signPosition.y), attributes);
        })
        .value();

      roadLayer.layer.addFeatures(oneWaySigns);
    };

    var reselectRoadLink = function() {
      selectControl.activate();
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      var feature = _.find(roadLayer.layer.features, function(feature) { return feature.attributes.roadLinkId === selectedLinkProperty.getId(); });
      if (feature) {
        selectControl.select(feature);
        highlightFeatures(feature);
      }
      selectControl.onSelect = originalOnSelectHandler;
    };

    var deselectRoadLink = function() {
      roadLayer.setLayerSpecificStyleMap('linkProperties', defaultStyleMap);
    };

    var prepareRoadLinkDraw = function() {
      selectControl.deactivate();
    };

    eventbus.on('map:moved', handleMapMoved);

    var start = function() {
      if (!eventListener.running) {
        eventListener.running = true;
        eventListener.listenTo(eventbus, 'roadLinks:beforeDraw', prepareRoadLinkDraw);
        eventListener.listenTo(eventbus, 'roadLinks:afterDraw', function(roadLinks) {
          drawOneWaySigns(roadLinks);
          reselectRoadLink();
        });
        eventListener.listenTo(eventbus, 'linkProperties:changed', handleLinkPropertyChanged);
        eventListener.listenTo(eventbus, 'linkProperties:cancelled', handleLinkPropertyCancelled);
        selectControl.activate();
      }
    };


    var displayConfirmMessage = function() { new Confirm(); };

    var handleLinkPropertyChanged = function() {
      selectControl.deactivate();
      eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
      eventListener.listenTo(eventbus, 'map:clicked', displayConfirmMessage);
      var selectedFeatures = _.filter(roadLayer.layer.features, function(feature) { return feature.attributes.roadLinkId === selectedLinkProperty.getId(); });
      roadLayer.layer.removeFeatures(selectedFeatures);
      var data = selectedLinkProperty.get().getData();
      roadLayer.drawRoadLink(data);
      drawOneWaySigns([data]);
      reselectRoadLink();
    };

    var handleLinkPropertyCancelled = function() {
      selectControl.activate();
      eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    };

    var stop = function() {
      selectControl.deactivate();
      eventListener.stopListening(eventbus);
      eventListener.running = false;
    };

    var show = function(map) {
      if (zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
        start();
      }
    };

    var hide = function() {
      stop();
      deselectRoadLink();
    };

    return {
      show: show,
      hide: hide
    };
  };
})(this);
