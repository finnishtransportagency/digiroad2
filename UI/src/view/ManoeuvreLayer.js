(function(root){
  root.ManoeuvreLayer = function(application, map, roadLayer, selectedManoeuvreSource, manoeuvresCollection, roadCollection) {
    var layerName = 'manoeuvre';
    Layer.call(this, layerName, roadLayer);
    var me = this;
    this.minZoomForContent = zoomlevels.minZoomForAssets;
    var indicatorLayer = new OpenLayers.Layer.Boxes('adjacentLinkIndicators');
    roadLayer.setLayerSpecificMinContentZoomLevel(layerName, me.minZoomForContent);
    var featureTypeRules = [
      new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeWidth: 2 }),
      new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeWidth: 5 }),
      new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeWidth: 7 }),
      new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeWidth: 10 }),
      new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeWidth: 10 }),
      new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeWidth: 14 }),
      new OpenLayersRule().where('type').is('normal').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeWidth: 14 }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeColor: '#ff0000', strokeWidth: 1 }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#ff0000', strokeWidth: 3 }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#ff0000', strokeWidth: 5 }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeColor: '#ff0000', strokeWidth: 8 }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeColor: '#ff0000', strokeWidth: 8 }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeColor: '#ff0000', strokeWidth: 12 }),
      new OpenLayersRule().where('type').is('overlay').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeColor: '#ff0000', strokeWidth: 12 }),
      new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeColor: '#11bb00', strokeWidth: 1 }),
      new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#11bb00', strokeWidth: 3 }),
      new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#11bb00', strokeWidth: 5 }),
      new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeColor: '#11bb00', strokeWidth: 8 }),
      new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeColor: '#11bb00', strokeWidth: 8 }),
      new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeColor: '#11bb00', strokeWidth: 12 }),
      new OpenLayersRule().where('type').is('intermediate').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeColor: '#11bb00', strokeWidth: 12 }),
      new OpenLayersRule().where('type').is('multipleSource').and('zoomLevel', roadLayer.uiState).is(9).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 6' }),
      new OpenLayersRule().where('type').is('multipleSource').and('zoomLevel', roadLayer.uiState).is(10).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 3, strokeDashstyle: '1 10' }),
      new OpenLayersRule().where('type').is('multipleSource').and('zoomLevel', roadLayer.uiState).is(11).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 5, strokeDashstyle: '1 15' }),
      new OpenLayersRule().where('type').is('multipleSource').and('zoomLevel', roadLayer.uiState).is(12).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
      new OpenLayersRule().where('type').is('multipleSource').and('zoomLevel', roadLayer.uiState).is(13).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
      new OpenLayersRule().where('type').is('multipleSource').and('zoomLevel', roadLayer.uiState).is(14).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' }),
      new OpenLayersRule().where('type').is('multipleSource').and('zoomLevel', roadLayer.uiState).is(15).use({ strokeColor: '#FFFFFF', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' }),
      new OpenLayersRule().where('linkType').is(8).use({ strokeWidth: 5 }),
      new OpenLayersRule().where('linkType').is(9).use({ strokeWidth: 5 }),
      new OpenLayersRule().where('linkType').is(21).use({ strokeWidth: 5 })
    ];
    var signSizeRules = [
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(9).use({ pointRadius: 0 }),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(10).use({ pointRadius: 10 }),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(11).use({ pointRadius: 12 }),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(12).use({ pointRadius: 13 }),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(13).use({ pointRadius: 14 }),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(14).use({ pointRadius: 16 }),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(15).use({ pointRadius: 16 })
    ];
    var defaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.65,
      pointRadius: 12,
      rotation: '${rotation}',
      graphicOpacity: 1.0
    }));
    defaultStyle.addRules([
      new OpenLayersRule().where('manoeuvreSource').is(1).use({ strokeColor: '#00f', externalGraphic: 'images/link-properties/arrow-drop-blue.svg' }),
      new OpenLayersRule().where('manoeuvreSource').is(0).use({ strokeColor: '#888', externalGraphic: 'images/link-properties/arrow-drop-grey.svg' })
    ]);
    defaultStyle.addRules(featureTypeRules);
    defaultStyle.addRules([
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(9).use({ pointRadius: 0 }),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(10).use({ pointRadius: 10 }),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(11).use({ pointRadius: 12 }),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(12).use({ pointRadius: 13 }),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(13).use({ pointRadius: 14 }),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(14).use({ pointRadius: 16 }),
      new OpenLayersRule().where('zoomLevel', roadLayer.uiState).is(15).use({ pointRadius: 16 })
    ]);
    var defaultStyleMap = new OpenLayers.StyleMap({ default: defaultStyle });
    roadLayer.setLayerSpecificStyleMap(layerName, defaultStyleMap);

    var selectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.9,
      pointRadius: 12,
      rotation: '${rotation}',
      graphicOpacity: 1.0,
      strokeColor: '#00f',
      externalGraphic: 'images/link-properties/arrow-drop-blue.svg'
    }));
    var selectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.15,
      pointRadius: 12,
      rotation: '${rotation}',
      graphicOpacity: 0.15,
      strokeColor: '#888',
      externalGraphic: 'images/link-properties/arrow-drop-grey.svg'
    }));
    selectionDefaultStyle.addRules(featureTypeRules);
    selectionSelectStyle.addRules(featureTypeRules);
    selectionDefaultStyle.addRules(signSizeRules);
    selectionSelectStyle.addRules(signSizeRules);
    selectionDefaultStyle.addRules([
      new OpenLayersRule().where('adjacent').is(0).use({ strokeOpacity: 0.15, graphicOpacity: 0.15 }),
      new OpenLayersRule().where('adjacent').is(1).use({ strokeOpacity: 0.9, graphicOpacity: 1.0 })
    ]);
    var selectionStyleMap = new OpenLayers.StyleMap({
      select:  selectionSelectStyle,
      default: selectionDefaultStyle
    });

    //----------------------------------
    // Public methods
    //----------------------------------

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

    // Override Layer.js method
    this.layerStarted = function(eventListener) {
      indicatorLayer.setZIndex(1000);
      var manoeuvreChangeHandler = _.partial(handleManoeuvreChanged, eventListener);
      var manoeuvreEditConclusion = _.partial(concludeManoeuvreEdit, eventListener);
      var manoeuvreSaveHandler = _.partial(handleManoeuvreSaved, eventListener);
      eventListener.listenTo(eventbus, 'manoeuvre:changed', manoeuvreChangeHandler);
      eventListener.listenTo(eventbus, 'manoeuvres:cancelled', manoeuvreEditConclusion);
      eventListener.listenTo(eventbus, 'manoeuvres:saved', manoeuvreSaveHandler);
      eventListener.listenTo(eventbus, 'manoeuvres:selected', handleManoeuvreSelected);
      eventListener.listenTo(eventbus, 'application:readOnly', reselectManoeuvre);
    };

    // Override Layer.js method
    this.refreshView = function() {
      manoeuvresCollection.fetch(map.getExtent(), map.getZoom(), draw);
    };

    // Override Layer.js method
    this.removeLayerFeatures = function() {
      indicatorLayer.clearMarkers();
    };

    //---------------------------------------
    // Utility functions
    //---------------------------------------

    var unselectManoeuvre = function() {
      selectedManoeuvreSource.close();
      roadLayer.setLayerSpecificStyleMap(layerName, defaultStyleMap);
      roadLayer.redraw();
      highlightFeatures(null);
      highlightOneWaySigns([]);
      highlightOverlayFeatures([]);
      indicatorLayer.clearMarkers();
    };

    var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
      onSelect: function(feature) {
        if (roadCollection.get([feature.attributes.linkId])[0].isCarTrafficRoad()) {
          roadLayer.setLayerSpecificStyleMap(layerName, selectionStyleMap);
          roadLayer.redraw();
          selectedManoeuvreSource.open(feature.attributes.linkId);
        } else {
          unselectManoeuvre();
        }
      },
      onUnselect: function() {
        unselectManoeuvre();
      }
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

    var createMultipleSourceFeatures = function(roadLinks) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var attributes = _.merge({}, roadLink, {
          type: 'multipleSource'
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
      roadLayer.layer.addFeatures(createMultipleSourceFeatures(multipleSourceRoadLinks));
    };

    var reselectManoeuvre = function() {
      selectControl.activate();
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      if (selectedManoeuvreSource.exists()) {
        var destinationLinkIds = manoeuvresCollection.getDestinationRoadLinksBySourceLinkId(selectedManoeuvreSource.getLinkId());
        markAdjacentFeatures(application.isReadOnly() ? destinationLinkIds : _.pluck(adjacentLinks(selectedManoeuvreSource.get()), 'linkId'));
        redrawRoadLayer();
        var feature = _.find(roadLayer.layer.features, function(feature) {
          return feature.attributes.linkId === selectedManoeuvreSource.getLinkId();
        });
        if (feature) {
          selectControl.select(feature);
        }
        highlightOneWaySigns([selectedManoeuvreSource.getLinkId()]);
        highlightOverlayFeatures(destinationLinkIds);
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

    var handleManoeuvreSelected = function(roadLink) {
      var aLinks = adjacentLinks(roadLink);
      var adjacentLinkIds = _.pluck(aLinks, 'linkId');
      highlightFeatures(roadLink.linkId);
      var destinationLinkIds = manoeuvresCollection.getDestinationRoadLinksBySourceLinkId(roadLink.linkId);
      highlightOneWaySigns([roadLink.linkId]);
      highlightOverlayFeatures(destinationLinkIds);
      markAdjacentFeatures(application.isReadOnly() ? destinationLinkIds : adjacentLinkIds);
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
