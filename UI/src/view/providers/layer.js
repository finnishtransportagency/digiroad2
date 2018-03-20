(function(root) {
  root.Layer = function(layerName, roadLayer) {
    var me = this;

    var mapOverLinkMiddlePoints = function(links, transformation) {
      return _.map(links, function(link) {
        var points = _.map(link.points, function(point) {
          return [point.x, point.y];
        });
        var lineString = new ol.geom.LineString(points);
        var middlePoint = GeometryUtils.calculateMidpointOfLineString(lineString);
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
    this.start = function(event) {
      if (!me.isStarted()) {
        me.activateSelection();
        me.eventListener.running = true;
        me.layerStarted(me.eventListener);
        me.refreshView(event);
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
          me.start('moved');
        }
        else {
          me.refreshView('moved');
        }
      } else {
        me.stop();
      }
    };
    this.drawOneWaySigns = function(layer, roadLinks) {
      var filteredLinks = _.filter(roadLinks, function(link) {
        return link.trafficDirection === 'AgainstDigitizing' || link.trafficDirection === 'TowardsDigitizing';
      });
      var oneWaySigns = mapOverLinkMiddlePoints(filteredLinks, function(link, middlePoint) {
        var rotation = link.trafficDirection === 'AgainstDigitizing' ? middlePoint.angleFromNorth + Math.PI : middlePoint.angleFromNorth;
        var attributes = _.merge({}, link, { rotation: rotation  });
        return new ol.Feature(_.merge(attributes,{ geometry: new ol.geom.Point([middlePoint.x, middlePoint.y])}));
      });

      layer.getSource().addFeatures(oneWaySigns);
    };
    this.mapOverLinkMiddlePoints = mapOverLinkMiddlePoints;
    this.show = function(map) {
      eventbus.on('map:moved', me.handleMapMoved);
      if (map.getView().getZoom() >= me.minZoomForContent) {
        me.start('shown');
      }
    };
    this.hide = function() {
      roadLayer.clear();
      eventbus.off('map:moved', me.handleMapMoved);
    };
  };
})(this);
