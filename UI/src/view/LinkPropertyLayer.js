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
    eventbus.on('roadLinks:drawn', reSelectRoadLink);

    var start = function() {
      selectControl.activate();
    };

    var stop = function() {
      selectControl.deactivate();
    };

    var show = function(map) {
      if (zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
        start();
      }
    };

    var hide = function() {
      stop();
    };

    return {
      show: show,
      hide: hide
    };
  };
})(this);
