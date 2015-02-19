(function(root){
  root.ManoeuvreLayer = function(map, roadLayer, selectedManoeuvre, manoeuvresCollection) {
    var layerName = 'manoeuvre';
    Layer.call(this, layerName);
    var me = this;
    var manoeuvreSourceLookup = {
      0: { strokeColor: '#a4a4a2' },
      1: { strokeColor: '#0000ff' }
    };
    var featureTypeLookup = {
      normal: { strokeWidth: 8},
      overlay: { strokeColor: '#be0000', strokeLinecap: 'square', strokeWidth: 6, strokeDashstyle: '1 10'  }
    };
    var defaultStyleMap = new OpenLayers.StyleMap({
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({  strokeOpacity: 0.65  }))
    });
    defaultStyleMap.addUniqueValueRules('default', 'manoeuvreSource', manoeuvreSourceLookup);
    defaultStyleMap.addUniqueValueRules('default', 'type', featureTypeLookup);
    roadLayer.setLayerSpecificStyleMap(layerName, defaultStyleMap);

    var selectionStyleMap = new OpenLayers.StyleMap({
      'select':  new OpenLayers.Style(OpenLayers.Util.applyDefaults({ strokeOpacity: 0.9 })),
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({ strokeOpacity: 0.3 }))
    });
    selectionStyleMap.addUniqueValueRules('default', 'manoeuvreSource', manoeuvreSourceLookup);
    selectionStyleMap.addUniqueValueRules('select', 'manoeuvreSource', manoeuvreSourceLookup);
    selectionStyleMap.addUniqueValueRules('default', 'type', featureTypeLookup);
    selectionStyleMap.addUniqueValueRules('select', 'type', featureTypeLookup);

    var eventListener = _.extend({running: false}, eventbus);
    this.eventListener = eventListener;
    var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
      onSelect: function(feature) {
        selectedManoeuvre.open(feature.attributes.roadLinkId);
        roadLayer.setLayerSpecificStyleMap(layerName, selectionStyleMap);
        roadLayer.redraw();
      },
      onUnselect: function() {
        selectedManoeuvre.close();
        roadLayer.setLayerSpecificStyleMap(layerName, defaultStyleMap);
        roadLayer.redraw();
      }
    });
    this.selectControl = selectControl;
    map.addControl(selectControl);

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

    var reselectManoeuvre = function() {
      selectControl.activate();
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      var feature = _.find(roadLayer.layer.features, function(feature) { return feature.attributes.roadLinkId === selectedManoeuvre.getRoadLinkId(); });
      if (feature) { selectControl.select(feature); }
      selectControl.onSelect = originalOnSelectHandler;
    };

    var draw = function() {
      selectControl.deactivate();
      roadLayer.drawRoadLinks(manoeuvresCollection.getAll(), map.getZoom());
      drawDashedLineFeatures(manoeuvresCollection.getAll());
      reselectManoeuvre();
    };

    this.refreshView = function() {
      manoeuvresCollection.fetch(map.getExtent(), map.getZoom(), draw);
    };

    var show = function(map) {
      if (zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
        me.start();
      }
    };

    var hide = function() {
      me.stop();
    };

    return {
      show: show,
      hide: hide,
      minZoomForContent: zoomlevels.minZoomForRoadLinks
    };
  };
})(this);