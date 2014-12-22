(function(root) {
  root.LinkPropertyLayer = function(map, roadLayer) {
    var selectedRoadLinkId = null;

    var roadLinkTypeStyleLookup = {
      PrivateRoad: { strokeColor: '#0011bb' },
      Street: { strokeColor: '#11bb00' },
      Road: { strokeColor: '#ff0000' }
    };

    var defaultStyleMap = new OpenLayers.StyleMap({
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeOpacity: 0.7
      }))
    });
    roadLayer.addUIStateDependentLookupToStyleMap(defaultStyleMap, 'default', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    defaultStyleMap.addUniqueValueRules('default', 'type', roadLinkTypeStyleLookup);
    roadLayer.setLayerSpecificStyleMap('linkProperties', defaultStyleMap);

    var selectionStyleMap = new OpenLayers.StyleMap({
      'select': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeOpacity: 0.7
      })),
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeOpacity: 0.3
      }))
    });
    roadLayer.addUIStateDependentLookupToStyleMap(selectionStyleMap, 'default', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(selectionStyleMap, 'select', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup);
    selectionStyleMap.addUniqueValueRules('default', 'type', roadLinkTypeStyleLookup);
    selectionStyleMap.addUniqueValueRules('select', 'type', roadLinkTypeStyleLookup);

    var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
      onSelect:  function(feature) {
        selectedRoadLinkId = feature.attributes.roadLinkId;
        eventbus.trigger('linkProperties:selected', feature.attributes);
        roadLayer.setLayerSpecificStyleMap('linkProperties', selectionStyleMap);
        roadLayer.layer.redraw();
      },
      onUnselect: function() {
        deselectRoadLink();
        eventbus.trigger('linkProperties:unselected');
        roadLayer.layer.redraw();
      }
    });
    map.addControl(selectControl);

    var eventListener = _.extend({running: false}, eventbus);

    var handleMapMoved = function(state) {
      if (zoomlevels.isInRoadLinkZoomLevel(state.zoom) && state.selectedLayer === 'linkProperties') {
        start();
      } else {
        stop();
      }
    };

    var reselectRoadLink = function() {
      selectControl.activate();
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      var feature = _.find(roadLayer.layer.features, function(feature) { return feature.attributes.roadLinkId === selectedRoadLinkId; });
      if (feature) {
        selectControl.select(feature);
      }
      selectControl.onSelect = originalOnSelectHandler;
    };

    var deselectRoadLink = function() {
      selectedRoadLinkId = null;
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
        eventListener.listenTo(eventbus, 'roadLinks:drawn', reselectRoadLink);
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
      deselectRoadLink();
    };

    return {
      show: show,
      hide: hide
    };
  };
})(this);
