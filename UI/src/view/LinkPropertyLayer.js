(function(root) {
  root.LinkPropertyLayer = function(map, roadLayer) {
    var selectedRoadLinkId = null;

    var styleMap = RoadLayerSelectionStyle.create(roadLayer, 0.7);
    var roadLinkTypeStyleLookup = {
      PrivateRoad: { strokeColor: '#0011bb' },
      Street: { strokeColor: '#11bb00' },
      Road: { strokeColor: '#ff0000' }
    };
    styleMap.addUniqueValueRules('default', 'type', roadLinkTypeStyleLookup);
    roadLayer.setLayerSpecificStyleMap('linkProperties', styleMap);

    var selectionStyleMap = RoadLayerSelectionStyle.create(roadLayer, 0.3);
    selectionStyleMap.addUniqueValueRules('default', 'type', roadLinkTypeStyleLookup);

    var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
      onSelect:  function(feature) {
        selectedRoadLinkId = feature.attributes.roadLinkId;
        eventbus.trigger('linkProperties:selected', feature.attributes);
        roadLayer.setLayerSpecificStyleMap('linkProperties', selectionStyleMap);
        roadLayer.layer.redraw();
      },
      onUnselect: function() {
        selectedRoadLinkId = null;
        eventbus.trigger('linkProperties:unselected');
        roadLayer.setLayerSpecificStyleMap('linkProperties', styleMap);
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
      selectedRoadLinkId = null;
    };

    return {
      show: show,
      hide: hide
    };
  };
})(this);
