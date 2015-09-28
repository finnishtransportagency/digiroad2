(function(root) {
  root.Layer = function(layerName, roadLayer) {
    var me = this;

    var mapOverLinkMiddlePoints = function(links, geometryUtils, transformation) {
      return _.map(links, function(link) {
        var points = _.map(link.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var lineString = new OpenLayers.Geometry.LineString(points);
        var middlePoint = geometryUtils.calculateMidpointOfLineString(lineString);
        return transformation(link, middlePoint);
      });
    };

    this.eventListener = _.extend({running: false}, eventbus);
    this.refreshView = function() {};
    this.isDirty = function() { return false; };
    this.layerStarted = function() {};
    this.removeLayerFeatures = function() {};
    this.isStarted = function() {
      return me.eventListener.running;
    };
    this.activateSelection = function() {
      me.selectControl.activate();
    };
    this.deactivateSelection = function() {
      me.selectControl.deactivate();
    };
    this.start = function() {
      if (!me.isStarted()) {
        me.activateSelection();
        me.eventListener.running = true;
        me.layerStarted(me.eventListener);
        me.refreshView();
      }
    };
    this.stop = function() {
      if (me.isStarted()) {
        me.removeLayerFeatures();
        me.deactivateSelection();
        me.eventListener.stopListening(eventbus);
        me.eventListener.running = false;
      }
    };
    this.displayConfirmMessage = function() { new Confirm(); };
    this.handleMapMoved = function(state) {
      if (state.selectedLayer === layerName && state.zoom >= me.minZoomForContent) {
        if (!me.isStarted()) {
          me.start();
        }
        else {
          me.refreshView();
        }
      } else {
        me.stop();
      }
    };
    this.drawOneWaySigns = function(layer, roadLinks, geometryUtils) {
      var filteredLinks = _.filter(roadLinks, function(link) {
        return link.trafficDirection === 'AgainstDigitizing' || link.trafficDirection === 'TowardsDigitizing';
      });
      var oneWaySigns = mapOverLinkMiddlePoints(filteredLinks, geometryUtils, function(link, middlePoint) {
        var rotation = link.trafficDirection === 'AgainstDigitizing' ? middlePoint.angleFromNorth + 180.0 : middlePoint.angleFromNorth;
        var attributes = _.merge({}, link, { rotation: rotation });
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(middlePoint.x, middlePoint.y), attributes);
      });

      layer.addFeatures(oneWaySigns);
    };
    this.mapOverLinkMiddlePoints = mapOverLinkMiddlePoints;
    this.show = function() {
      eventbus.on('map:moved', me.handleMapMoved);
    };
    this.hide = function() {
      roadLayer.clear();
      eventbus.off('map:moved', me.handleMapMoved);
    };

  };
})(this);
