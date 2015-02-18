(function(root){
  root.ManoeuvreLayer = function(map, roadLayer, roadCollection, backend) {

    var layerName = 'manoeuvre';
    var manoeuvreSourceLookup = {
      0: { strokeColor: '#a4a4a2' },
      1: { strokeColor: '#0000ff' }
    };
    var featureTypeLookup = {
      normal: { strokeWidth: 8, strokeOpacity: 0.5 },
      overlay: { strokeOpacity: 0.7, strokeColor: '#be0000', strokeLinecap: 'square', strokeWidth: 6, strokeDashstyle: '1 10'  }
    };
    var defaultStyleMap = new OpenLayers.StyleMap({
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({}))
    });
    defaultStyleMap.addUniqueValueRules('default', 'manoeuvreSource', manoeuvreSourceLookup);
    defaultStyleMap.addUniqueValueRules('default', 'type', featureTypeLookup);

    var eventListener = _.extend({running: false}, eventbus);

    roadLayer.setLayerSpecificStyleMap(layerName, defaultStyleMap);

    var createDashedLineFeatures = function(roadLinks) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var attributes = _.merge({}, roadLink, {
          type: 'overlay'
        });
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
      }));
    };

    var drawDashedLineFeatures = function(roadLinks) {
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return roadLink.manoeuvreDestination === 1;
      });
      roadLayer.layer.addFeatures(createDashedLineFeatures(dashedRoadLinks));
    };

    var draw = function() {
      backend.getManoeuvres(map.getExtent(), function(manoeuvres) {
        var roadLinks = _.map(roadCollection.getAll(), function(roadLink) {
          var manoeuvreSourceLink = _.find(manoeuvres, function(manoeuvre) {
            return manoeuvre.sourceRoadLinkId === roadLink.roadLinkId;
          });
          var manoeuvreDestinationLink = _.find(manoeuvres, function(manoeuvre) {
            return manoeuvre.destRoadLinkId === roadLink.roadLinkId;
          });
          return _.merge({}, roadLink, {
            manoeuvreSource: manoeuvreSourceLink ? 1 : 0,
            manoeuvreDestination: manoeuvreDestinationLink ? 1 : 0,
            type: 'normal'
          });
        });
        roadLayer.drawRoadLinks(roadLinks, map.getZoom());
        drawDashedLineFeatures(roadLinks);
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