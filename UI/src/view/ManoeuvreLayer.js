(function(root){
  root.ManoeuvreLayer = function(map, roadLayer, geometryUtils, selectedManoeuvreSource, manoeuvresCollection) {
    var layerName = 'manoeuvre';
    Layer.call(this, layerName, geometryUtils);
    var me = this;
    this.minZoomForContent = zoomlevels.minZoomForAssets;
    roadLayer.setLayerSpecificMinContentZoomLevel(layerName, me.minZoomForContent);
    var manoeuvreSourceLookup = {
      0: { strokeColor: '#a4a4a2', externalGraphic: 'images/link-properties/arrow-grey.svg' },
      1: { strokeColor: '#0000ff', externalGraphic: 'images/link-properties/arrow-blue.svg' }
    };
    var featureTypeLookup = {
      normal: { strokeWidth: 8},
      overlay: { strokeColor: '#be0000', strokeLinecap: 'square', strokeWidth: 6, strokeDashstyle: '1 10'  }
    };
    var defaultStyleMap = new OpenLayers.StyleMap({
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({ strokeOpacity: 0.65, pointRadius: 12 }))
    });
    defaultStyleMap.addUniqueValueRules('default', 'manoeuvreSource', manoeuvreSourceLookup);
    defaultStyleMap.addUniqueValueRules('default', 'type', featureTypeLookup);
    roadLayer.setLayerSpecificStyleMap(layerName, defaultStyleMap);

    var selectionStyleMap = new OpenLayers.StyleMap({
      'select':  new OpenLayers.Style(OpenLayers.Util.applyDefaults({ strokeOpacity: 0.9, pointRadius: 12 })),
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({ strokeOpacity: 0.3, pointRadius: 12 }))
    });
    selectionStyleMap.addUniqueValueRules('default', 'manoeuvreSource', manoeuvreSourceLookup);
    selectionStyleMap.addUniqueValueRules('select', 'manoeuvreSource', manoeuvreSourceLookup);
    selectionStyleMap.addUniqueValueRules('default', 'type', featureTypeLookup);
    selectionStyleMap.addUniqueValueRules('select', 'type', featureTypeLookup);

    var unselectManoeuvre = function() {
      selectedManoeuvreSource.close();
      roadLayer.setLayerSpecificStyleMap(layerName, defaultStyleMap);
      roadLayer.redraw();
      highlightFeatures(null);
      highlightOverlayFeatures([]);
    };

    var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
      onSelect: function(feature) {
        selectedManoeuvreSource.open(feature.attributes.roadLinkId);
        roadLayer.setLayerSpecificStyleMap(layerName, selectionStyleMap);
        roadLayer.redraw();
        highlightFeatures(feature.attributes.roadLinkId);
        highlightOverlayFeatures(manoeuvresCollection.getDestinationRoadLinksBySourceRoadLink(feature.attributes.roadLinkId));
      },
      onUnselect: function() {
        unselectManoeuvre();
      }
    });
    this.selectControl = selectControl;
    map.addControl(selectControl);

    var highlightFeatures = function(roadLinkId) {
      _.each(roadLayer.layer.features, function(x) {
        if (x.attributes.type === 'normal') {
          if (roadLinkId && (x.attributes.roadLinkId === roadLinkId)) {
            selectControl.highlight(x);
          } else {
            selectControl.unhighlight(x);
          }
        }
      });
    };

    var highlightOverlayFeatures = function(roadLinkIds) {
      _.each(roadLayer.layer.features, function(x) {
        if (x.attributes.type === 'overlay') {
          if (_.contains(roadLinkIds, x.attributes.roadLinkId)) {
            selectControl.highlight(x);
          } else {
            selectControl.unhighlight(x);
          }
        }
      });
    };

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
        return !_.isEmpty(roadLink.destinationOfManoeuvres);
      });
      roadLayer.layer.addFeatures(createDashedLineFeatures(dashedRoadLinks));
    };

    var reselectManoeuvre = function() {
      selectControl.activate();
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      if (selectedManoeuvreSource.exists()) {
        var feature = _.find(roadLayer.layer.features, function(feature) {
          return feature.attributes.roadLinkId === selectedManoeuvreSource.getRoadLinkId();
        });
        if (feature) {
          selectControl.select(feature);
        }
        highlightOverlayFeatures(manoeuvresCollection.getDestinationRoadLinksBySourceRoadLink(selectedManoeuvreSource.getRoadLinkId()));
      }
      selectControl.onSelect = originalOnSelectHandler;
    };

    var draw = function() {
      selectControl.deactivate();
      var linksWithManoeuvres = manoeuvresCollection.getAll();
      roadLayer.drawRoadLinks(linksWithManoeuvres, map.getZoom());
      drawDashedLineFeatures(linksWithManoeuvres);
      me.drawOneWaySigns(roadLayer.layer, linksWithManoeuvres);
      reselectManoeuvre();
      if (selectedManoeuvreSource.isDirty()) {
        selectControl.deactivate();
      }
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
      unselectManoeuvre();
      me.stop();
    };

    var handleManoeuvreChanged = function(eventListener) {
      draw();
      selectControl.deactivate();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
    };

    var concludeManoeuvreEdit = function(eventListener) {
      selectControl.activate();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      draw();
    };

    var handleManoeuvreSaved = function(eventListener) {
      manoeuvresCollection.fetch(map.getExtent(), map.getZoom(), function() {
        concludeManoeuvreEdit(eventListener);
        selectedManoeuvreSource.refresh();
      });
    };

    this.bindEventHandlers = function(eventListener) {
      var manoeuvreChangeHandler = _.partial(handleManoeuvreChanged, eventListener);
      var manoeuvreEditConclusion = _.partial(concludeManoeuvreEdit, eventListener);
      var manoeuvreSaveHandler = _.partial(handleManoeuvreSaved, eventListener);
      eventListener.listenTo(eventbus, 'manoeuvre:changed', manoeuvreChangeHandler);
      eventListener.listenTo(eventbus, 'manoeuvres:cancelled', manoeuvreEditConclusion);
      eventListener.listenTo(eventbus, 'manoeuvres:saved', manoeuvreSaveHandler);
    };

    return {
      show: show,
      hide: hide,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);