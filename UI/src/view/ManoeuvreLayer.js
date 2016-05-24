(function(root){
  root.ManoeuvreLayer = function(application, map, roadLayer, selectedManoeuvreSource, manoeuvresCollection, roadCollection) {
    var layerName = 'manoeuvre';
    Layer.call(this, layerName, roadLayer);
    var me = this;
    this.minZoomForContent = zoomlevels.minZoomForAssets;
    var indicatorLayer = new OpenLayers.Layer.Boxes('adjacentLinkIndicators');
    roadLayer.setLayerSpecificMinContentZoomLevel(layerName, me.minZoomForContent);

    var manoeuvreStyle = ManoeuvreStyle(roadLayer);
    roadLayer.setLayerSpecificStyleMap(layerName, manoeuvreStyle.defaultStyleMap);

    /*
     * ------------------------------------------
     *  Public methods
     * ------------------------------------------
     */

    var show = function(map) {
      map.addLayer(indicatorLayer);
      me.show(map);
    };

    var hideLayer = function() {
      unselectManoeuvre();
      me.stop();
      me.hide();
      map.removeLayer(indicatorLayer);
    };

    /**
     * Sets up indicator layer for adjacent markers. Attaches event handlers to events listened by the eventListener.
     * Overrides the Layer.js layerStarted method
     */
    this.layerStarted = function(eventListener) {
      indicatorLayer.setZIndex(1000);
      var manoeuvreChangeHandler = _.partial(handleManoeuvreChanged, eventListener);
      var manoeuvreEditConclusion = _.partial(concludeManoeuvreEdit, eventListener);
      var manoeuvreSaveHandler = _.partial(handleManoeuvreSaved, eventListener);
      eventListener.listenTo(eventbus, 'manoeuvre:changed', manoeuvreChangeHandler);
      eventListener.listenTo(eventbus, 'manoeuvres:cancelled', manoeuvreEditConclusion);
      eventListener.listenTo(eventbus, 'manoeuvres:saved', manoeuvreSaveHandler);
      eventListener.listenTo(eventbus, 'manoeuvres:selected', handleManoeuvreSourceLinkSelected);
      eventListener.listenTo(eventbus, 'application:readOnly', reselectManoeuvre);
    };

    /**
     * Fetches the road links and manoeuvres again on the layer.
     * Overrides the Layer.js refreshView method
     */
    this.refreshView = function() {
      manoeuvresCollection.fetch(map.getExtent(), map.getZoom(), draw);
    };

    /**
     * Overrides the Layer.js removeLayerFeatures method
     */
    this.removeLayerFeatures = function() {
      indicatorLayer.clearMarkers();
    };

    /*
     * ------------------------------------------
     *  Utility functions
     * ------------------------------------------
     */

    /**
     * Selects a manoeuvre on the map. First checks that road link is a car traffic road.
     * Sets up the selection style and redraws the road layer. Sets up the selectedManoeuvreSource.
     * This variable is set as onSelect property in selectControl.
     * @param feature Selected OpenLayers feature (road link)
       */
    var selectManoeuvre = function(feature) {
      if (roadCollection.get([feature.attributes.linkId])[0].isCarTrafficRoad()) {
        roadLayer.setLayerSpecificStyleMap(layerName, manoeuvreStyle.selectionStyleMap);
        roadLayer.redraw();
        selectedManoeuvreSource.open(feature.attributes.linkId);
      } else {
        unselectManoeuvre();
      }
    };

    /**
     * Closes the current selectedManoeuvreSource. Sets up the default style and redraws the road layer.
     * Empties all the highlight functions. Cleans up the adjacent link markers on indicator layer.
     * This variable is set as onUnselect property in selectControl.
     */
    var unselectManoeuvre = function() {
      selectedManoeuvreSource.close();
      roadLayer.setLayerSpecificStyleMap(layerName, manoeuvreStyle.defaultStyleMap);
      roadLayer.redraw();
      highlightFeatures(null);
      highlightOneWaySigns([]);
      highlightOverlayFeatures([]);
      indicatorLayer.clearMarkers();
    };

    /**
     * Creates an OpenLayers selectControl and defines onSelect and onUnselect properties for it.
     * The selectControl is then added as Layer.js this.selectControl value and as a control to the OpenLayers map.
     */
    var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
      onSelect: selectManoeuvre,
      onUnselect: unselectManoeuvre
    });
    this.selectControl = selectControl;
    map.addControl(selectControl);

    var highlightFeatures = function(linkId) {
      _.each(roadLayer.layer.features, function(x) {
        if (x.attributes.type === 'normal') {
          if (linkId && (x.attributes.linkId === linkId)) {
            selectControl.highlight(x);
          } else {
            selectControl.unhighlight(x);
          }
        }
      });
    };

    var highlightOneWaySigns = function(linkIds) {
      var isOneWaySign = function(feature) { return !_.isUndefined(feature.attributes.rotation); };

      _.each(roadLayer.layer.features, function(x) {
        if (isOneWaySign(x)) {
          if (_.contains(linkIds, x.attributes.linkId)) {
            selectControl.highlight(x);
          } else {
            selectControl.unhighlight(x);
          }
        }
      });
    };

    var highlightOverlayFeatures = function(linkIds) {
      _.each(roadLayer.layer.features, function(x) {
        if (x.attributes.type === 'overlay') {
          if (_.contains(linkIds, x.attributes.linkId)) {
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

    var createIntermediateFeatures = function(roadLinks) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var attributes = _.merge({}, roadLink, {
          type: 'intermediate'
        });
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
      }));
    };

    var createMultipleFeatures = function(roadLinks) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var attributes = _.merge({}, roadLink, {
          type: 'multiple'
        });
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
      }));
    };

    var createSourceDestinationFeatures = function(roadLinks) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var attributes = _.merge({}, roadLink, {
          type: 'sourceDestination'
        });
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
      }));
    };

    var destroySourceDestinationFeatures = function(roadLinks) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points));
      }));
    };

    var drawDashedLineFeatures = function(roadLinks) {
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return !_.isEmpty(roadLink.destinationOfManoeuvres);
      });
      roadLayer.layer.addFeatures(createDashedLineFeatures(dashedRoadLinks));
    };

    var drawIntermediateFeatures = function(roadLinks) {
      var intermediateRoadLinks = _.filter(roadLinks, function(roadLink) {
        return !_.isEmpty(roadLink.intermediateManoeuvres);
      });
      roadLayer.layer.addFeatures(createIntermediateFeatures(intermediateRoadLinks));
    };

    var drawMultipleSourceFeatures = function(roadLinks) {
      var multipleSourceRoadLinks = _.filter(roadLinks, function(roadLink) {
        return !_.isEmpty(roadLink.multipleSourceManoeuvres);
      });
      roadLayer.layer.addFeatures(createMultipleFeatures(multipleSourceRoadLinks));
      manoeuvresCollection.cleanHMapSourceManoeuvres();
    };

    var drawMultipleIntermediateFeatures = function(roadLinks) {
      var multipleIntermediateRoadLinks = _.filter(roadLinks, function(roadLink) {
        return !_.isEmpty(roadLink.multipleIntermediateManoeuvres);
      });
      roadLayer.layer.addFeatures(createMultipleFeatures(multipleIntermediateRoadLinks));
      manoeuvresCollection.cleanHMapIntermidiateManoeuvres();
    };

    var drawMultipleDestinationFeatures = function(roadLinks) {
      var multipleDestinationRoadLinks = _.filter(roadLinks, function(roadLink) {
        return !_.isEmpty(roadLink.multipleDestinationManoeuvres);
      });
      roadLayer.layer.addFeatures(createMultipleFeatures(multipleDestinationRoadLinks));
      manoeuvresCollection.cleanHMapDestinationManoeuvres();
    };

    var drawSourceDestinationFeatures = function(roadLinks) {
      var sourceDestinationRoadLinks = _.filter(roadLinks, function(roadLink) {
        return !_.isEmpty(roadLink.sourceDestinationManoeuvres);
      });
      //roadLinks.layer.removeFeatures(destroySourceDestinationFeatures(sourceDestinationRoadLinks));
      roadLayer.layer.addFeatures(createSourceDestinationFeatures(sourceDestinationRoadLinks));
      manoeuvresCollection.cleanHMapSourceDestinationManoeuvres();
    };

    var reselectManoeuvre = function() {
      selectControl.activate();
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      if (selectedManoeuvreSource.exists()) {
        var firstTargetLinkIds = manoeuvresCollection.getFirstTargetRoadLinksBySourceLinkId(selectedManoeuvreSource.getLinkId());
        markAdjacentFeatures(application.isReadOnly() ? firstTargetLinkIds : _.pluck(adjacentLinks(selectedManoeuvreSource.get()), 'linkId'));
        redrawRoadLayer();
        var feature = _.find(roadLayer.layer.features, function(feature) {
          return feature.attributes.linkId === selectedManoeuvreSource.getLinkId();
        });
        if (feature) {
          selectControl.select(feature);
        }
        highlightOneWaySigns([selectedManoeuvreSource.getLinkId()]);
        highlightOverlayFeatures(firstTargetLinkIds);
        indicatorLayer.clearMarkers();
        updateAdjacentLinkIndicators();
      }
      selectControl.onSelect = originalOnSelectHandler;
    };

    var draw = function() {
      selectControl.deactivate();
      var linksWithManoeuvres = manoeuvresCollection.getAll();
      roadLayer.drawRoadLinks(linksWithManoeuvres, map.getZoom());
      drawDashedLineFeatures(linksWithManoeuvres);
      drawIntermediateFeatures(linksWithManoeuvres);
      drawMultipleSourceFeatures(linksWithManoeuvres);
      drawMultipleIntermediateFeatures(linksWithManoeuvres);
      drawMultipleDestinationFeatures(linksWithManoeuvres);
      drawSourceDestinationFeatures(linksWithManoeuvres);
      me.drawOneWaySigns(roadLayer.layer, linksWithManoeuvres);
      reselectManoeuvre();
      if (selectedManoeuvreSource.isDirty()) {
        selectControl.deactivate();
      }
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
      var indicators = me.mapOverLinkMiddlePoints(links, function(link, middlePoint) {
        var bounds = OpenLayers.Bounds.fromArray([middlePoint.x, middlePoint.y, middlePoint.x, middlePoint.y]);
        var box = new OpenLayers.Marker.Box(bounds, "00000000");
        $(box.div).html(markerTemplate(link));
        $(box.div).css('overflow', 'visible');
        return box;
      });

      _.forEach(indicators, function(indicator) {
        indicatorLayer.addMarker(indicator);
      });
    };

    var adjacentLinks = function(roadLink) {
      return _.chain(roadLink.adjacent)
        .map(function(adjacent) {
          return _.merge({}, adjacent, _.find(roadCollection.getAll(), function(link) {
            return link.linkId === adjacent.linkId;
          }));
        })
        .reject(function(adjacentLink) { return _.isUndefined(adjacentLink.points); })
        .value();
    };

    var markAdjacentFeatures = function(adjacentLinkIds) {
      _.forEach(roadLayer.layer.features, function(feature) {
        feature.attributes.adjacent = feature.attributes.type === 'normal' && _.contains(adjacentLinkIds, feature.attributes.linkId);
      });
    };

    var redrawRoadLayer = function() {
      roadLayer.redraw();
      indicatorLayer.setZIndex(1000);
    };

    /**
     * Sets up manoeuvre visualization when manoeuvre source road link is selected.
     * Fetches adjacent links. Visualizes source link and its one way sign. Fetches first target links of manoeuvres starting from source link.
     * Sets the shown branching link set as first target links in view mode and adjacent links in edit mode.
     * Redraws the layer. Shows adjacent link markers in edit mode.
     * @param roadLink
     */
    var handleManoeuvreSourceLinkSelected = function(roadLink) {
      var aLinks = adjacentLinks(roadLink);
      var adjacentLinkIds = _.pluck(aLinks, 'linkId');
      highlightFeatures(roadLink.linkId);
      var firstTargetLinkIds = manoeuvresCollection.getFirstTargetRoadLinksBySourceLinkId(roadLink.linkId);
      highlightOneWaySigns([roadLink.linkId]);
      highlightOverlayFeatures(firstTargetLinkIds);
      markAdjacentFeatures(application.isReadOnly() ? firstTargetLinkIds : adjacentLinkIds);
      redrawRoadLayer();
      if (!application.isReadOnly()) {
        drawIndicators(aLinks);
      }
    };

    var updateAdjacentLinkIndicators = function() {
      if (!application.isReadOnly()) {
        if(selectedManoeuvreSource.exists()) {
          drawIndicators(adjacentLinks(selectedManoeuvreSource.get()));
        }
      } else {
        indicatorLayer.clearMarkers();
      }
    };

    return {
      show: show,
      hide: hideLayer,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);
