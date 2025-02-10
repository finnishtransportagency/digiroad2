(function(root){
  root.ManoeuvreLayer = function(application, map, roadLayer, selectedManoeuvreSource, manoeuvresCollection, roadCollection, trafficSignReadOnlyLayer, suggestionLabel) {
    var me = this;
    var layerName = 'manoeuvre';
    
    var mode = "view";
    var authorizationPolicy = new ManoeuvreAuthorizationPolicy();
    var isActiveTrafficSigns = false;
    BaseManoeuvreLayer.call(this, map, roadLayer, manoeuvresCollection, suggestionLabel);

    /*
     * ------------------------------------------
     *  Public methods
     * ------------------------------------------
     */

    var show = function(map) {
      me.indicatorLayer.setVisible(true);
      me.show(map);
    };

    var hideLayer = function() {
      hideReadOnlyLayer();
      unselectManoeuvre();
      me.hideLayer();

    };

    /**
     * Sets up indicator layer for adjacent markers. Attaches event handlers to events listened by the eventListener.
     * Overrides the layer.js layerStarted method
     */
    this.layerStarted = function(eventListener) {
      me.indicatorLayer.setZIndex(1000);
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
      eventListener.listenTo(eventbus, 'manoeuvre:suggestion', handleSuggestion);
      eventListener.listenTo(eventbus, 'manoeuvre:linkAdded', manoeuvreChangeHandler);
      eventListener.listenTo(eventbus, 'manoeuvre:linkDropped', manoeuvreChangeHandler);
      eventListener.listenTo(eventbus, 'adjacents:updated', drawExtension);
      eventListener.listenTo(eventbus, 'manoeuvre:removeMarkers', manoeuvreRemoveMarkers);
      eventListener.listenTo(eventbus, layerName + '-readOnlyTrafficSigns:hide', hideReadOnlyTrafficSigns);
      eventListener.listenTo(eventbus, layerName + '-readOnlyTrafficSigns:show', showReadOnlyTrafficSigns);
    };

    /**
     * Fetches the road links and manoeuvres again on the layer.
     * Overrides the layer.js refreshView method
     */
    this.refreshView = function() {
      manoeuvresCollection.fetch(map.getView().calculateExtent(map.getSize()), zoomlevels.getViewZoom(map), draw, map.getView().getCenter());
      if(isActiveTrafficSigns)
        trafficSignReadOnlyLayer.refreshView();
    };

    /**
     * Overrides the layer.js removeLayerFeatures method
     */
    this.removeLayerFeatures = function() {
      me.indicatorLayer.getSource().clear();
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
      unSelectManoeuvreFeatures(event.deselected);
      unselectManoeuvre();

      if (event.selected.length > 0 && enableSelect(event)) {
        selectManoeuvreFeatures(event.selected[0]);
        selectedManoeuvreSource.open(event.selected[0].getProperties().linkId);
        unHighLightReadOnlyLayer();
       }
    };

  /**
   * Selects all features with the same id, and linkId. In order to for example
   * not allow the selection of only the directional sign.
   * @param features
   */
    var selectManoeuvreFeatures = function (features) {

    if (!application.isReadOnly() && authorizationPolicy.editModeAccessByFeatures(features)){
      var style = me.manoeuvreStyle.getSelectedStyle().getStyle(features, {zoomLevel: zoomlevels.getViewZoom(map)});
      features.setStyle(style);
    }

    selectControl.addSelectionFeatures([features]);
    };

    var unSelectManoeuvreFeatures = function (features) {
      _.each(features, function(feature){
        feature.setStyle(me.manoeuvreStyle.getDefaultStyle().getStyle(feature, {zoomLevel: zoomlevels.getViewZoom(map)}));
      });
    };

    /**
     * Closes the current selectedManoeuvreSource. Sets up the default style and redraws the road layer.
     * Empties all the highlight functions. Cleans up the adjacent link markers on indicator layer.
     * This variable is set as onUnselect property in selectControl.
     */
    var unselectManoeuvre = function() {
      selectControl.clear();
      selectedManoeuvreSource.close();
      me.indicatorLayer.getSource().clear();
      selectedManoeuvreSource.setTargetRoadLink(null);
      highLightReadOnlyLayer();
    };

    var selectControl = new SelectToolControl(application, roadLayer.layer, map, false, {
        style : function(feature){
            return me.manoeuvreStyle.getDefaultStyle().getStyle(feature, {zoomLevel: zoomlevels.getViewZoom(map)});
        },
        onSelect: selectManoeuvre,
        draggable : false,
        enableSelect : enableSelect,
        layerName : layerName
    });

    this.selectControl = selectControl;


    var reselectManoeuvre = function() {
      if (!selectedManoeuvreSource.isDirty()) {
        selectControl.activate();
      }

      if(!application.isReadOnly()){
        _.each(selectControl.getSelectInteraction().getFeatures().getArray(), function(feature){
          if(authorizationPolicy.editModeAccessByFeatures(feature))
            feature.setStyle(me.manoeuvreStyle.getSelectedStyle().getStyle(feature, {zoomLevel: zoomlevels.getViewZoom(map)}));
        });

      } else {
        unSelectManoeuvreFeatures(selectControl.getSelectInteraction().getFeatures().getArray());
      }

      if (selectedManoeuvreSource.exists()  && authorizationPolicy.formEditModeAccess(selectedManoeuvreSource)) {
        var manoeuvreSource = selectedManoeuvreSource.get();

        me.indicatorLayer.getSource().clear();
        var addedManoeuvre = manoeuvresCollection.getAddedManoeuvre();

        if(!application.isReadOnly()){

          if(!_.isEmpty(addedManoeuvre)) {
            if(!("adjacentLinks" in addedManoeuvre))
              addedManoeuvre.adjacentLinks = selectedManoeuvreSource.getAdjacents(addedManoeuvre.destLinkId);
          }

          var manoeuvreAdjacentLinks = _.isEmpty(addedManoeuvre) ?  adjacentLinks(manoeuvreSource) : addedManoeuvre.adjacentLinks;

          markAdjacentFeatures(_.map(manoeuvreAdjacentLinks,'linkId'));
          drawIndicators(manoeuvreAdjacentLinks);
        }
        redrawRoadLayer();
      }
    };

    var draw = function() {
      selectControl.deactivate();
      me.draw();
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
      unSelectManoeuvreFeatures(selectControl.getSelectInteraction().getFeatures().getArray());
      unselectManoeuvre();
    };

    var concludeManoeuvreSaved = function(eventListener) {
      mode="consult";
      selectControl.activate();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      selectedManoeuvreSource.setTargetRoadLink(null);
      draw();
    };

    var handleManoeuvreSaved = function(eventListener) {
      manoeuvresCollection.fetch(map.getView().calculateExtent(map.getSize()), zoomlevels.getViewZoom(map), function() {
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
      me.indicatorLayer.getSource().addFeatures(features);
    };

    var adjacentLinks = function(roadLink) {
      return _.chain(roadLink.adjacent)
        .map(function(adjacent) {
          return _.merge({}, adjacent, _.find(roadCollection.getAll(), function(link) {
            return link.linkId === adjacent.linkId;
          }));
        })
        .reject(function(adjacentLink) { return _.isUndefined(adjacentLink.points) || !authorizationPolicy.editModeAccessByLink(adjacentLink);})
        .value();
    };

    var nonAdjacentTargetLinks = function(roadLink) {
      return _.chain(roadLink.nonAdjacentTargets)
        .map(function(adjacent) {
          return _.merge({}, adjacent, _.find(roadCollection.getAll(), function(link) {
            return link.linkId === adjacent.linkId;
          }));
        })
        .reject(function(adjacentLink) { return _.isUndefined(adjacentLink.points) || !authorizationPolicy.editModeAccessByLink(adjacentLink);})
        .value();
    };

    var markAdjacentFeatures = function(adjacentLinkIds) {
      _.forEach(roadLayer.layer.getSource().getFeatures(), function(feature) {
        feature.getProperties().adjacent = feature.getProperties().type === 'normal' && _.includes(adjacentLinkIds, feature.getProperties().linkId);
      });
    };

    var redrawRoadLayer = function() {
      me.indicatorLayer.setZIndex(1000);
    };

    function applySelection(roadLink) {
      if (selectedManoeuvreSource.exists()) {
        var feature = _.filter(roadLayer.layer.getSource().getFeatures(), function(feature) { return roadLink.linkId === feature.getProperties().linkId; });
        if (feature) {
          selectControl.addSelectionFeatures(feature);
        }
      }
    }

    /**
     * Sets up manoeuvre visualization when manoeuvre source road link is selected.
     * Fetches adjacent links. Visualizes source link and its one way sign. Fetches first target links of manoeuvres starting from source link.
     * Sets the shown branching link set as first target links in view mode and adjacent links in edit mode.
     * Redraws the layer. Shows adjacent link markers in edit mode.
     * @param roadLink
     */
    var handleManoeuvreSourceLinkSelected = function(roadLink) {
      applySelection(roadLink);
      me.indicatorLayer.getSource().clear();
      var aLinks = adjacentLinks(roadLink);
      var tLinks = nonAdjacentTargetLinks(roadLink);
      var adjacentLinkIds = _.map(aLinks, 'linkId');
      var targetLinkIds = _.map(tLinks, 'linkId');

      if(application.isReadOnly()){

      manoeuvresCollection.getNextTargetRoadLinksBySourceLinkId(roadLink.linkId);
      }

      markAdjacentFeatures(application.isReadOnly() ? targetLinkIds : adjacentLinkIds);

      manoeuvresCollection.getDestinationRoadLinksBySource(selectedManoeuvreSource.get());
      manoeuvresCollection.getIntermediateRoadLinksBySource(selectedManoeuvreSource.get());
      if (!application.isReadOnly() && authorizationPolicy.editModeAccessByLink(roadLink)) {
        drawIndicators(tLinks);
        drawIndicators(aLinks);
      }
      redrawRoadLayer();
    };

    var handleManoeuvreExtensionBuilding = function(manoeuvre) {
      mode="create";
      if (!application.isReadOnly()) {
        if(manoeuvre) {

          me.indicatorLayer.getSource().clear();

          drawIndicators(_.filter(manoeuvre.adjacentLinks, function(link){return authorizationPolicy.editModeAccessByLink(link);}));
          selectControl.deactivate();

          var targetMarkers = _.chain(manoeuvre.adjacentLinks)
              .filter(function (adjacentLink) {
                return adjacentLink.linkId;
              })
              .map('linkId')
              .value();

          markAdjacentFeatures(targetMarkers);

          var linkIdLists = _.without(manoeuvre.linkIds, manoeuvre.sourceLinkId);
          var destLinkId = _.last(linkIdLists);

          selectControl.addNewFeature(me.createIntermediateFeatures(_.map(_.without(linkIdLists, destLinkId), function(linkId){
              return roadCollection.getRoadLinkByLinkId(linkId).getData();
          })), false);

          selectControl.addNewFeature(me.createDashedLineFeatures([roadCollection.getRoadLinkByLinkId(destLinkId).getData()]), false);

          selectedManoeuvreSource.setTargetRoadLink(manoeuvre.destLinkId);

          redrawRoadLayer();
          if (selectedManoeuvreSource.isDirty()) {
            selectControl.deactivate();
          }
        }
      } else {
        me.indicatorLayer.getSource().clear();
      }

    };

    var handleSuggestion = function() {
        draw();
    };

    var manoeuvreRemoveMarkers = function(data){
      mode="edit";
      if (!application.isReadOnly()) {
        me.indicatorLayer.getSource().clear();
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
        me.indicatorLayer.getSource().clear();
      }
    };

    var drawExtension = function(manoeuvre) {
      console.log("Draw Extension");
      console.log(manoeuvre);
      handleManoeuvreExtensionBuilding(manoeuvre);
    };

    var unHighLightReadOnlyLayer = function(){
      trafficSignReadOnlyLayer.unHighLightLayer();
    };

    var highLightReadOnlyLayer = function(){
      trafficSignReadOnlyLayer.highLightLayer();
    };

    var hideReadOnlyLayer = function(){
      trafficSignReadOnlyLayer.hide();
      trafficSignReadOnlyLayer.removeLayerFeatures();
    };

    var showReadOnlyTrafficSigns = function() {
      isActiveTrafficSigns = true;
    };

    var hideReadOnlyTrafficSigns = function() {
      isActiveTrafficSigns = false;
    };

    return {
      show: show,
      hide: hideLayer,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);
