(function(root){
  root.ManoeuvreLayer = function(map, roadLayer, roadCollection, backend) {

    var layerName = 'manoeuvre';
    var manoeuvreColorLookup = {
      0: { strokeColor: '#a4a4a2' },
      1: { strokeColor: '#0000ff' }
    };
    var defaultStyleMap = new OpenLayers.StyleMap({
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeWidth: 5,
        strokeOpacity: 0.7
      }))
    });
    defaultStyleMap.addUniqueValueRules('default', 'startsManoeuvre', manoeuvreColorLookup);

    var eventListener = _.extend({running: false}, eventbus);

    roadLayer.setLayerSpecificStyleMap(layerName, defaultStyleMap);

    var draw = function() {
      backend.getManoeuvres(map.getExtent(), function(manoeuvres) {
        var roadLinks = _.map(roadCollection.getAll(), function(roadLink) {
          var manoeuvre = _.find(manoeuvres, function(manoeuvre) {
            return manoeuvre.sourceRoadLinkId === roadLink.roadLinkId;
          });
          return _.merge({}, roadLink, { startsManoeuvre: manoeuvre ? 1 : 0 });
        });
        roadLayer.drawRoadLinks(roadLinks, map.getZoom());
      });
    };

    var handleMapMoved = function(state) {
      if (zoomlevels.isInRoadLinkZoomLevel(state.zoom) && state.selectedLayer === layerName) {
        if (!isStarted()) {
          start();
        }
        else {
          eventbus.once('roadLinks:fetched', function() {
            draw();
          });
          roadCollection.fetch(map.getExtent(), map.getZoom());
        }
      } else {
        stop();
      }
    };

    eventbus.on('map:moved', handleMapMoved);

    var isStarted = function() {
      return eventListener.running;
    };

    var start = function() {
      if (!isStarted()) {
        eventListener.running = true;
        eventbus.once('roadLinks:fetched', function() {
          draw();
        });
        roadCollection.fetch(map.getExtent(), map.getZoom());
      }
    };

    var stop = function() {
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
    };

    return {
      show: show,
      hide: hide,
      minZoomForContent: zoomlevels.minZoomForRoadLinks
    };
  };
})(this);