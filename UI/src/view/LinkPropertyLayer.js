(function(root) {
  root.LinkPropertyLayer = function(map, roadLayer) {
    var selectedRoadLinkId = null;

    var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
      onSelect:  function(feature) {
        selectedRoadLinkId = feature.attributes.roadLinkId;
        eventbus.trigger('linkProperties:selected', feature.attributes);
      },
      onUnselect: function() {
        selectedRoadLinkId = null;
        eventbus.trigger('linkProperties:unselected');
      }
    });
    map.addControl(selectControl);

    var eventListener = _.extend({running: false}, eventbus);

    var linkPropertyFeatureSizeLookup = {
      9: { strokeWidth: 3 },
      10: { strokeWidth: 5 },
      11: { strokeWidth: 9 },
      12: { strokeWidth: 16 },
      13: { strokeWidth: 16 },
      14: { strokeWidth: 16 },
      15: { strokeWidth: 16 }
    };

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
    roadLayer.addUIStateDependentLookupToStyleMap(roadLayerStyleMap, 'default', 'zoomLevel', linkPropertyFeatureSizeLookup);
    roadLayer.setLayerSpecificStyleMap('linkProperties', roadLayerStyleMap);

    var handleMapMoved = function(state) {
      if (zoomlevels.isInRoadLinkZoomLevel(state.zoom) && state.selectedLayer === 'linkProperties') {
        start();
      } else {
        stop();
      }
    };

    var reSelectRoadLink = function() {
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      var feature = _.find(roadLayer.layer.features, function(feature) { return feature.attributes.roadLinkId === selectedRoadLinkId; });
      if (feature) {
        selectControl.select(feature);
      }
      selectControl.onSelect = originalOnSelectHandler;
    };

    eventbus.on('map:moved', handleMapMoved);

    var start = function() {
      if (!eventListener.running) {
        eventListener.running = true;
        eventListener.listenTo(eventbus, 'roadLinks:drawn', reSelectRoadLink);
        selectControl.activate();
      }
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
      selectedRoadLinkId = null;
    };

    return {
      show: show,
      hide: hide
    };
  };
})(this);
