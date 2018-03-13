(function(root){
  root.ManoeuvreLayer = function(application, map, roadLayer, selectedManoeuvreSource, manoeuvresCollection, roadCollection) {
    var me = this;
    var layerName = 'manoeuvre';
    var indicatorVector = new ol.source.Vector({});
    var indicatorLayer = new ol.layer.Vector({
      source : indicatorVector
    });
    map.addLayer(indicatorLayer);
    indicatorLayer.setVisible(false);

    var manoeuvreStyle = ManoeuvreStyle(roadLayer);
    var mode = "view";

    this.minZoomForContent = zoomlevels.minZoomForAssets;
    Layer.call(this, layerName, roadLayer);
    roadLayer.setLayerSpecificMinContentZoomLevel(layerName, me.minZoomForContent);
    roadLayer.setLayerSpecificStyleProvider(layerName, manoeuvreStyle.getDefaultStyle);

    /*
     * ------------------------------------------
     *  Public methods
     * ------------------------------------------
     */

    var show = function(map) {
      indicatorLayer.setVisible(true);
      me.show(map);
    };

    var hideLayer = function() {
      unselectManoeuvre();
      me.stop();
      me.hide();
      indicatorLayer.setVisible(false);

    };

    /**
     * Sets up indicator layer for adjacent markers. Attaches event handlers to events listened by the eventListener.
     * Overrides the layer.js layerStarted method
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
      eventListener.listenTo(eventbus, 'manoeuvre:showExtension', handleManoeuvreExtensionBuilding);
      eventListener.listenTo(eventbus, 'manoeuvre:extend', extendManoeuvre);
      eventListener.listenTo(eventbus, 'manoeuvre:linkAdded', manoeuvreChangeHandler);
      eventListener.listenTo(eventbus, 'manoeuvre:linkDropped', manoeuvreChangeHandler);
      eventListener.listenTo(eventbus, 'adjacents:updated', drawExtension);
      eventListener.listenTo(eventbus, 'manoeuvre:removeMarkers', manoeuvreRemoveMarkers);
    };

    /**
     * Fetches the road links and manoeuvres again on the layer.
     * Overrides the layer.js refreshView method
     */
    this.refreshView = function() {
      manoeuvresCollection.fetch(map.getView().calculateExtent(map.getSize()),map.getView().getZoom(), draw);
    };

    /**
     * Overrides the layer.js removeLayerFeatures method
     */
    this.removeLayerFeatures = function() {
      indicatorLayer.getSource().clear();
    };

    /*
     * ------------------------------------------
     *  Utility functions
     * ------------------------------------------
     */

    var enableSelect = function (event) {
        return event.selected.length > 0 && roadCollection.get([event.selected[0].getProperties().linkId])[0].isCarTrafficRoad();
    };


    /**
     * Selects a manoeuvre on the map. First checks that road link is a car traffic road.
     * Sets up the selection style and redraws the road layer. Sets up the selectedManoeuvreSource.
     * This variable is set as onSelect property in selectControl.
     * @param feature Selected OpenLayers feature (road link)
       */
    var selectManoeuvre = function(event) {
      if (event.selected.length > 0 && enableSelect(event)) {
        selectManoeuvreFeatures(event.selected[0]);
        selectedManoeuvreSource.open(event.selected[0].getProperties().linkId);
      } else {
        _.each(event.deselected, function(feature){
            feature.setStyle(manoeuvreStyle.getDefaultStyle().getStyle(feature, {zoomLevel: map.getView().getZoom()}));
        });
        unselectManoeuvre();
      }
    };

  /**
   * Selects all features with the same id, and linkId. In order to for example
   * not allow the selection of only the directional sign.
   * @param features
   */
    var selectManoeuvreFeatures = function (features) {
      var selectedFeatures = _.filter(roadLayer.layer.getSource().getFeatures(), function(feature) {
          return feature.getProperties().linkId === features.getProperties().linkId;
      });

      if(selectedFeatures){
          _.each(selectedFeatures, function(feature){
              var style = manoeuvreStyle.getSelectedStyle().getStyle(feature, {zoomLevel: map.getView().getZoom()});
              style.setStroke(_.merge(style.getStroke(), new ol.style.Stroke({color: '#00f'})));
              feature.setStyle(style);
          });
      }
      selectControl.addSelectionFeatures(selectedFeatures);
    };

    /**
     * Closes the current selectedManoeuvreSource. Sets up the default style and redraws the road layer.
     * Empties all the highlight functions. Cleans up the adjacent link markers on indicator layer.
     * This variable is set as onUnselect property in selectControl.
     */
    var unselectManoeuvre = function() {
      selectControl.clear();
      selectedManoeuvreSource.close();
      indicatorLayer.getSource().clear();
      selectedManoeuvreSource.setTargetRoadLink(null);
    };

    var selectControl = new SelectToolControl(application, roadLayer.layer, map, {
        style : function(feature){
            return manoeuvreStyle.getDefaultStyle().getStyle(feature, {zoomLevel: map.getView().getZoom()});
        },
        onSelect: selectManoeuvre,
        draggable : false,
        enableSelect : enableSelect
    });

    this.selectControl = selectControl;

    var createDashedLineFeatures = function(roadLinks) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return [point.x, point.y];
        });
        var attributes = _.merge({}, roadLink, {
          type: 'overlay'
        });
        var feature = new ol.Feature(new ol.geom.LineString(points));
        feature.setProperties(attributes);
        return feature;
      }));
    };

    var createIntermediateFeatures = function(roadLinks) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return [point.x, point.y];
        });
        var attributes = _.merge({}, roadLink, {
          type: 'intermediate'
        });
        var feature = new ol.Feature(new ol.geom.LineString(points));
          feature.setProperties(attributes);
          return feature;
      }));
    };

    var createMultipleFeatures = function(roadLinks) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return [point.x, point.y];
        });
        var attributes = _.merge({}, roadLink, {
          type: 'multiple'
        });
        var feature = new ol.Feature(new ol.geom.LineString(points));
        feature.setProperties(attributes);
        return feature;
      }));
    };

    var createSourceDestinationFeatures = function(roadLinks) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return [point.x, point.y];
        });
        var attributes = _.merge({}, roadLink, {
          type: 'sourceDestination'
        });
        var feature = new ol.Feature(new ol.geom.LineString(points));
        feature.setProperties(attributes);
        return feature;
      }));
    };

    var destroySourceDestinationFeatures = function(roadLinks) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return [point.x, point.y];
        });
        return new ol.Feature(new ol.geom.LineString(points));
      }));
    };

    var drawDashedLineFeatures = function(roadLinks) {
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return !_.isEmpty(roadLink.destinationOfManoeuvres);
      });
      roadLayer.layer.getSource().addFeatures(createDashedLineFeatures(dashedRoadLinks));
    };

    var drawIntermediateFeatures = function(roadLinks) {
      var intermediateRoadLinks = _.filter(roadLinks, function(roadLink) {
        return !_.isEmpty(roadLink.intermediateManoeuvres) && _.isEmpty(roadLink.destinationOfManoeuvres) &&
          _.isEmpty(roadLink.manoeuvreSource);
      });
      roadLayer.layer.getSource().addFeatures(createIntermediateFeatures(intermediateRoadLinks));
    };

    var drawMultipleSourceFeatures = function(roadLinks) {
      var multipleSourceRoadLinks = _.filter(roadLinks, function(roadLink) {
        return !_.isEmpty(roadLink.multipleSourceManoeuvres) && _.isEmpty(roadLink.sourceDestinationManoeuvres);
      });
      roadLayer.layer.getSource().addFeatures(createMultipleFeatures(multipleSourceRoadLinks));
    };

    var drawMultipleIntermediateFeatures = function(roadLinks) {
      var multipleIntermediateRoadLinks = _.filter(roadLinks, function(roadLink) {
        return !_.isEmpty(roadLink.multipleIntermediateManoeuvres) && _.isEmpty(roadLink.destinationOfManoeuvres) &&
          _.isEmpty(roadLink.manoeuvreSource);
      });
      roadLayer.layer.getSource().addFeatures(createMultipleFeatures(multipleIntermediateRoadLinks));
    };

    var drawMultipleDestinationFeatures = function(roadLinks) {
      var multipleDestinationRoadLinks = _.filter(roadLinks, function(roadLink) {
        return !_.isEmpty(roadLink.multipleDestinationManoeuvres) &&
          _.isEmpty(roadLink.manoeuvreSource);
      });
      roadLayer.layer.getSource().addFeatures(createMultipleFeatures(multipleDestinationRoadLinks));
    };

    var drawSourceDestinationFeatures = function(roadLinks) {
      var sourceDestinationRoadLinks = _.filter(roadLinks, function(roadLink) {
        return !_.isEmpty(roadLink.sourceDestinationManoeuvres);
      });
      roadLayer.layer.getSource().addFeatures(createSourceDestinationFeatures(sourceDestinationRoadLinks));
    };

    var reselectManoeuvre = function() {
      if (!selectedManoeuvreSource.isDirty()) {
        selectControl.activate();
      }
      if (selectedManoeuvreSource.exists()) {
        var manoeuvreSource = selectedManoeuvreSource.get();

        indicatorLayer.getSource().clear();
        var addedManoeuvre = manoeuvresCollection.getAddedManoeuvre();

        if(!application.isReadOnly()){

          if(!_.isEmpty(addedManoeuvre)) {
            if(!("adjacentLinks" in addedManoeuvre))
              addedManoeuvre.adjacentLinks = selectedManoeuvreSource.getAdjacents(addedManoeuvre.destLinkId);
          }

          var manoeuvreAdjacentLinks = _.isEmpty(addedManoeuvre) ?  adjacentLinks(manoeuvreSource) : addedManoeuvre.adjacentLinks;

          markAdjacentFeatures(_.pluck(manoeuvreAdjacentLinks,'linkId'));
          drawIndicators(manoeuvreAdjacentLinks);
        }
        redrawRoadLayer();
      }
    };

    var draw = function() {
      selectControl.deactivate();
      var linksWithManoeuvres = manoeuvresCollection.getAll();
      roadLayer.drawRoadLinks(linksWithManoeuvres, map.getView().getZoom());
      drawDashedLineFeatures(linksWithManoeuvres);
      drawIntermediateFeatures(linksWithManoeuvres);
      drawMultipleSourceFeatures(linksWithManoeuvres);
      drawMultipleIntermediateFeatures(linksWithManoeuvres);
      drawMultipleDestinationFeatures(linksWithManoeuvres);
      drawSourceDestinationFeatures(linksWithManoeuvres);
      me.drawOneWaySigns(roadLayer.layer, linksWithManoeuvres);
      reselectManoeuvre();
    };

    var handleManoeuvreChanged = function(eventListener) {
      selectControl.deactivate();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
    };

    var concludeManoeuvreEdit = function(eventListener) {
      mode="consult";
      selectControl.activate();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      selectedManoeuvreSource.setTargetRoadLink(null);
      selectControl.removeFeatures(function(features){
          return features && features.getProperties().linkId !== selectedManoeuvreSource.get().linkId;
      });
      draw();
    };

    var concludeManoeuvreSaved = function(eventListener) {
      mode="consult";
      selectControl.activate();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      selectedManoeuvreSource.setTargetRoadLink(null);
      draw();
    };

    var handleManoeuvreSaved = function(eventListener) {
      manoeuvresCollection.fetch(map.getView().calculateExtent(map.getSize()), map.getView().getZoom(), function() {
        concludeManoeuvreSaved(eventListener);
        unselectManoeuvre();
      });
    };

    var drawIndicators = function(links) {
      var features = [];

      var markerContainer = function(link, position) {
        var style = new ol.style.Style({
            image : new ol.style.Icon({
                src: 'images/center-marker2.svg'
            }),
            text : new ol.style.Text({
                text : link.marker,
                fill: new ol.style.Fill({
                    color: "#ffffff"
                })
            })
        });
        var marker = new ol.Feature({
            geometry : new ol.geom.Point([position.x, position.y])
        });
        marker.setStyle(style);
        features.push(marker);
      };

      var indicators = function() {
        return me.mapOverLinkMiddlePoints(links, function(link, middlePoint) {
          markerContainer(link, middlePoint);
        });
      };
      indicators();
      indicatorLayer.getSource().addFeatures(features);
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

    var targetLinksAdjacents = function(manoeuvre) {
      var ret = _.find(roadCollection.getAll(), function(link) {
        return link.linkId === manoeuvre.destLinkId;
      });
      return ret.adjacentLinks;
    };

    var nonAdjacentTargetLinks = function(roadLink) {
      return _.chain(roadLink.nonAdjacentTargets)
        .map(function(adjacent) {
          return _.merge({}, adjacent, _.find(roadCollection.getAll(), function(link) {
            return link.linkId === adjacent.linkId;
          }));
        })
        .reject(function(adjacentLink) { return _.isUndefined(adjacentLink.points); })
        .value();
    };

    var markAdjacentFeatures = function(adjacentLinkIds) {
      _.forEach(roadLayer.layer.getSource().getFeatures(), function(feature) {
        feature.getProperties().adjacent = feature.getProperties().type === 'normal' && _.contains(adjacentLinkIds, feature.getProperties().linkId);
      });
    };

    var redrawRoadLayer = function() {
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
      indicatorLayer.getSource().clear();
      var aLinks = adjacentLinks(roadLink);
      var tLinks = nonAdjacentTargetLinks(roadLink);
      var adjacentLinkIds = _.pluck(aLinks, 'linkId');
      var targetLinkIds = _.pluck(tLinks, 'linkId');

      if(application.isReadOnly()){
        var allTargetLinkIds = manoeuvresCollection.getNextTargetRoadLinksBySourceLinkId(roadLink.linkId);
      //  highlightManoeuvreFeatures(allTargetLinkIds);
      }

      markAdjacentFeatures(application.isReadOnly() ? targetLinkIds : adjacentLinkIds);

      var destinationRoadLinkList = manoeuvresCollection.getDestinationRoadLinksBySource(selectedManoeuvreSource.get());
      manoeuvresCollection.getIntermediateRoadLinksBySource(selectedManoeuvreSource.get());
     // highlightOverlayFeatures(destinationRoadLinkList);
      if (!application.isReadOnly()) {
        drawIndicators(tLinks);
        drawIndicators(aLinks);
      }
      redrawRoadLayer();
      //roadLayer.setLayerSpecificStyleProvider(layerName, manoeuvreStyle.getSelectedStyle);
      //roadLayer.redraw();
    };

    var updateAdjacentLinkIndicators = function() {
        if (!application.isReadOnly() && !_.isEqual(mode,"edit")) {
        if(selectedManoeuvreSource.exists()) {
          drawIndicators(adjacentLinks(selectedManoeuvreSource.get()));
          if(!_.isEqual(mode,"consult"))
          drawIndicators(nonAdjacentTargetLinks(selectedManoeuvreSource.get()));
        }
      } else {
         indicatorLayer.getSource().clear();
      }
    };

    var handleManoeuvreExtensionBuilding = function(manoeuvre) {
      mode="create";
      if (!application.isReadOnly()) {
        if(manoeuvre) {

          indicatorLayer.getSource().clear();
          drawIndicators(manoeuvre.adjacentLinks);
          selectControl.deactivate();

          var targetMarkers = _.chain(manoeuvre.adjacentLinks)
              .filter(function (adjacentLink) {
                return adjacentLink.linkId;
              })
              .pluck('linkId')
              .value();

          markAdjacentFeatures(targetMarkers);

          var linkIdLists = _.without(manoeuvre.linkIds, manoeuvre.sourceLinkId);
          var destLinkId = _.last(linkIdLists);

          selectControl.addNewFeature(createIntermediateFeatures(_.map(_.without(linkIdLists, destLinkId), function(linkId){
              return roadCollection.getRoadLinkByLinkId(linkId).getData();
          })), false);

          selectControl.addNewFeature(createDashedLineFeatures([roadCollection.getRoadLinkByLinkId(destLinkId).getData()]), false);

          selectedManoeuvreSource.setTargetRoadLink(manoeuvre.destLinkId);

          redrawRoadLayer();
          if (selectedManoeuvreSource.isDirty()) {
            selectControl.deactivate();
          }
        }
      } else {
        indicatorLayer.getSource().clear();
      }

    };

    var manoeuvreRemoveMarkers = function(data){
      mode="edit";
      if (!application.isReadOnly()) {
        indicatorLayer.getSource().clear();
      }
    };

    var extendManoeuvre = function(data) {
      var manoeuvreToRewrite = data.manoeuvre;
      var newDestLinkId = data.newTargetId;
      var oldDestLinkId = data.target;

      console.log("Storaged");
      console.log(selectedManoeuvreSource.get());

      var persisted = _.merge({}, manoeuvreToRewrite, selectedManoeuvreSource.get().manoeuvres.find(function (m) {
        return m.destLinkId === oldDestLinkId && (!manoeuvreToRewrite.manoeuvreId ||
          m.manoeuvreId === manoeuvreToRewrite.manoeuvreId); }));

      console.log("manoeuvre extension");
      console.log(persisted);
      console.log("" + oldDestLinkId + " > " + newDestLinkId);

      selectedManoeuvreSource.addLink(persisted, newDestLinkId);

      if (application.isReadOnly()) {
        indicatorLayer.getSource().clear();
      }
    };

    var drawExtension = function(manoeuvre) {
      console.log("Draw Extension");
      console.log(manoeuvre);
      handleManoeuvreExtensionBuilding(manoeuvre);
    };

    return {
      show: show,
      hide: hideLayer,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);
