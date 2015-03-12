(function(root){
  root.ManoeuvreLayer = function(map, roadLayer, geometryUtils, selectedManoeuvreSource, manoeuvresCollection, roadCollection) {
    var layerName = 'manoeuvre';
    Layer.call(this, layerName, roadLayer);
    var me = this;
    this.minZoomForContent = zoomlevels.minZoomForAssets;
    var indicatorLayer = new OpenLayers.Layer.Boxes('adjacentLinkIndicators');
    roadLayer.setLayerSpecificMinContentZoomLevel(layerName, me.minZoomForContent);
    var manoeuvreSourceLookup = {
      0: { strokeColor: '#a4a4a2', externalGraphic: 'images/link-properties/arrow-grey.svg' },
      1: { strokeColor: '#0000ff', externalGraphic: 'images/link-properties/arrow-blue.svg' }
    };
    var featureTypeLookup = {
      normal: { strokeWidth: 8},
      overlay: { strokeColor: '#be0000', strokeLinecap: 'square', strokeWidth: 6, strokeDashstyle: '1 10'  }
    };
    var oneWaySignSizeLookup = {
      9: { pointRadius: 0 },
      10: { pointRadius: 12 },
      11: { pointRadius: 14 },
      12: { pointRadius: 16 },
      13: { pointRadius: 20 },
      14: { pointRadius: 24 },
      15: { pointRadius: 24 }
    };
    var defaultStyleMap = new OpenLayers.StyleMap({
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({ strokeOpacity: 0.65, pointRadius: 12, rotation: '${rotation}' }))
    });
    defaultStyleMap.addUniqueValueRules('default', 'manoeuvreSource', manoeuvreSourceLookup);
    defaultStyleMap.addUniqueValueRules('default', 'type', featureTypeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(defaultStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    roadLayer.setLayerSpecificStyleMap(layerName, defaultStyleMap);

    var selectionStyleMap = new OpenLayers.StyleMap({
      'select':  new OpenLayers.Style(OpenLayers.Util.applyDefaults({ strokeOpacity: 0.9, pointRadius: 12, rotation: '${rotation}' })),
      'default': new OpenLayers.Style(OpenLayers.Util.applyDefaults({ strokeOpacity: 0.3, pointRadius: 12, rotation: '${rotation}' }))
    });
    selectionStyleMap.addUniqueValueRules('default', 'manoeuvreSource', manoeuvreSourceLookup);
    selectionStyleMap.addUniqueValueRules('select', 'manoeuvreSource', manoeuvreSourceLookup);
    selectionStyleMap.addUniqueValueRules('default', 'type', featureTypeLookup);
    selectionStyleMap.addUniqueValueRules('select', 'type', featureTypeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(selectionStyleMap, 'default', 'zoomLevel', oneWaySignSizeLookup);
    roadLayer.addUIStateDependentLookupToStyleMap(selectionStyleMap, 'select', 'zoomLevel', oneWaySignSizeLookup);

    var unselectManoeuvre = function() {
      selectedManoeuvreSource.close();
      roadLayer.setLayerSpecificStyleMap(layerName, defaultStyleMap);
      roadLayer.redraw();
      highlightFeatures(null);
      highlightOverlayFeatures([]);
      indicatorLayer.clearMarkers();
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
      me.drawOneWaySigns(roadLayer.layer, linksWithManoeuvres, geometryUtils);
      reselectManoeuvre();
      if (selectedManoeuvreSource.isDirty()) {
        selectControl.deactivate();
      }
    };

    this.refreshView = function() {
      manoeuvresCollection.fetch(map.getExtent(), map.getZoom(), draw);
    };

    var show = function(map) {
      map.addLayer(indicatorLayer);
      if (zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
        me.start();
      }
    };

    var hideLayer = function() {
      unselectManoeuvre();
      me.stop();
      me.hide();
      map.removeLayer(indicatorLayer);
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

    var drawIndicators = function(links) {
      var markerTemplate = _.template('<span class="marker"><%= marker %></span>');
      var indicators = _.map(links, function(link) {
        var points = _.map(link.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var lineString = new OpenLayers.Geometry.LineString(points);
        var indicatorPosition = geometryUtils.calculateMidpointOfLineString(lineString);
        var bounds = OpenLayers.Bounds.fromArray([indicatorPosition.x, indicatorPosition.y, indicatorPosition.x, indicatorPosition.y]);
        var box = new OpenLayers.Marker.Box(bounds, "00000000");
        $(box.div).html(markerTemplate(link));
        $(box.div).css('overflow', 'visible');
        return box;
      });

      _.forEach(indicators, function(indicator) {
        indicatorLayer.addMarker(indicator);
      });
    };

    var handleManoeuvreSelected = function(roadLink) {
      var adjacentLinks = _.map(roadLink.adjacent, function(adjacent) {
        return _.merge({}, adjacent, _.find(roadCollection.getAll(), function(link) {
          return link.roadLinkId === adjacent.id;
        }));
      });
      drawIndicators(adjacentLinks);
    };

    this.removeLayerFeatures = function() {
      indicatorLayer.clearMarkers();
    };

    this.bindEventHandlers = function(eventListener) {
      var manoeuvreChangeHandler = _.partial(handleManoeuvreChanged, eventListener);
      var manoeuvreEditConclusion = _.partial(concludeManoeuvreEdit, eventListener);
      var manoeuvreSaveHandler = _.partial(handleManoeuvreSaved, eventListener);
      eventListener.listenTo(eventbus, 'manoeuvre:changed', manoeuvreChangeHandler);
      eventListener.listenTo(eventbus, 'manoeuvres:cancelled', manoeuvreEditConclusion);
      eventListener.listenTo(eventbus, 'manoeuvres:saved', manoeuvreSaveHandler);
      eventListener.listenTo(eventbus, 'manoeuvres:selected', handleManoeuvreSelected);
    };

    return {
      show: show,
      hide: hideLayer,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);