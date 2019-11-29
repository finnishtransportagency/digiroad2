(function(root) {
  root.PointAssetLayer = function(params) {
    var roadLayer = params.roadLayer,
      application= applicationModel,
      collection = params.collection,
      map = params.map,
      roadCollection = params.roadCollection,
      style = params.style,
      selectedAsset = params.selectedAsset,
      mapOverlay = params.mapOverlay,
      layerName = params.layerName,
      newAsset = params.newAsset,
      roadAddressInfoPopup = params.roadAddressInfoPopup,
      assetLabel = params.assetLabel,
      allowGrouping = params.allowGrouping,
      assetGrouping = params.assetGrouping,
      authorizationPolicy = params.authorizationPolicy,
      trafficSignReadOnlyLayer = params.readOnlyLayer;
    var pointAssetLayerStyles = PointAssetLayerStyles(params.roadLayer);

    Layer.call(this, layerName, roadLayer);
    var me = this;
    me.minZoomForContent = zoomlevels.minZoomForAssets;
    var extraEventListener = _.extend({running: false}, eventbus);
    var vectorSource = new ol.source.Vector();
    var vectorLayer = new ol.layer.Vector({
       source : vectorSource,
       style : function(feature){
           return style.browsingStyleProvider.getStyle(feature);
       },
      renderBuffer: 300
    });
    vectorLayer.set('name', layerName);
    vectorLayer.setOpacity(1);
    vectorLayer.setVisible(true);
    map.addLayer(vectorLayer);

    var selectControl = new SelectToolControl(application, vectorLayer, map, false,{
        style : function (feature) {
            return style.browsingStyleProvider.getStyle(feature);
        },
        onSelect : pointAssetOnSelect,
        draggable : false,
        filterGeometry : function(feature){
           return feature.getGeometry() instanceof ol.geom.Point;
        }
    });

    function pointAssetOnSelect(feature) {
      if(feature.selected.length > 0 && feature.deselected.length === 0){
          var properties = feature.selected[0].getProperties();
          var administrativeClass = obtainAdministrativeClass(properties);
          var asset = _.merge({}, properties, {administrativeClass: administrativeClass});
          selectedAsset.open(asset);
          if(authorizationPolicy.formEditModeAccess(selectedAsset, roadCollection) && !applicationModel.isReadOnly())
            dragControl.activate();
      }
      else {
        if(feature.deselected.length > 0 && !selectedAsset.isDirty()) {
          selectedAsset.close();
          if(trafficSignReadOnlyLayer){
            trafficSignReadOnlyLayer.highLightLayer();
          }
        }else{
          applySelection();
        }
        dragControl.deactivate();
      }
    }

    this.selectControl = selectControl;

    function isAllowedToDrag(features) {
      if (selectedAsset.exists() && (layerName == 'trafficSigns' && authorizationPolicy.formEditModeAccess(selectedAsset)))
        return [];
      return features;
    }

    var dragControl = defineOpenLayersDragControl();
    function defineOpenLayersDragControl() {
        var dragHandler = layerName === 'servicePoints' ? dragFreely : dragAlongNearestLink;
        var dragControl = new ol.interaction.Translate({
           features : new FilterCollection(selectControl.getSelectInteraction().getFeatures(), isAllowedToDrag)
        });

        dragControl.on('translating', dragHandler);

        function dragFreely(feature) {
          if (selectedAsset.isSelected(feature.features.getArray()[0].getProperties())) {
            selectedAsset.set({lon: feature.coordinate[0], lat: feature.coordinate[1]});
          }
        }

        function dragAlongNearestLink(feature) {
          if (selectedAsset.isSelected(feature.features.getArray()[0].getProperties())) {
            var nearestLine = geometrycalculator.findNearestLine(excludeRoadByAdminClass(roadCollection.getRoadsForPointAssets()), feature.coordinate[0], feature.coordinate[1]);
            if (nearestLine) {
              var newPosition = geometrycalculator.nearestPointOnLine(nearestLine, { x: feature.coordinate[0], y: feature.coordinate[1]});
              roadLayer.selectRoadLink(roadCollection.getRoadLinkByLinkId(nearestLine.linkId).getData());
              feature.features.getArray()[0].getGeometry().setCoordinates([newPosition.x, newPosition.y]);
              var newBearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
              selectedAsset.set({lon: newPosition.x, lat: newPosition.y, linkId: nearestLine.linkId, geometry: feature.features.getArray()[0].getGeometry(), floating: false, bearing: newBearing});
            }
          }
        }

        var activate = function () {
            map.addInteraction(dragControl);
        };

        var deactivate = function () {
          var forRemove = _.filter(map.getInteractions().getArray(), function(interaction) {
              return interaction instanceof ol.interaction.Translate;
          });
          _.each(forRemove, function (interaction) {
             map.removeInteraction(interaction);
          });
        };

        return {
            activate : activate,
            deactivate : deactivate
        };
    }

    function createFeature(asset) {
      var rotation = determineRotation(asset);
      var bearing = determineBearing(asset);
      var administrativeClass = obtainAdministrativeClass(asset);
      var feature =  new ol.Feature({geometry : new ol.geom.Point([asset.lon, asset.lat])});
      var obj = _.merge({}, asset, {rotation: rotation, bearing: bearing, administrativeClass: administrativeClass}, feature.getProperties());
      feature.setProperties(obj);
      return feature;
    }

    function determineRotation(asset) {
      var rotation = 0;
      if (!asset.floating && asset.geometry && asset.geometry.length > 0){
        var bearing = determineBearing(asset);
        rotation = validitydirections.calculateRotation(bearing, asset.validityDirection);
      } else if (layerName == 'directionalTrafficSigns' || !_.isUndefined(asset.bearing) && layerName == 'trafficSigns'){
        rotation = validitydirections.calculateRotation(asset.bearing, asset.validityDirection);
      }
      return rotation;
    }

    function determineBearing(asset) {
      var bearing = 90;
      if (!asset.floating && asset.geometry && asset.geometry.length > 0){
        var nearestLine = geometrycalculator.findNearestLine([{ points: asset.geometry }], asset.lon, asset.lat);
        bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
      } else if (layerName == 'directionalTrafficSigns' || layerName == 'trafficSigns'){
        bearing = asset.bearing;
      }
      return bearing;
    }

    this.refreshView = function() {
      eventbus.once('roadLinks:fetched', function () {
        var roadLinks = roadCollection.getAll();
        roadLayer.drawRoadLinks(roadLinks, zoomlevels.getViewZoom(map));
        me.drawOneWaySigns(roadLayer.layer, roadLinks);
        selectControl.activate();
      });
      if(collection.complementaryIsActive()) {
        roadCollection.fetchWithComplementary(map.getView().calculateExtent(map.getSize()));
        if (trafficSignReadOnlyLayer)
          trafficSignReadOnlyLayer.refreshView();
      }
      else
      roadCollection.fetch(map.getView().calculateExtent(map.getSize()));
      collection.fetch(map.getView().calculateExtent(map.getSize()), map.getView().getCenter()).then(function(assets) {
        if (selectedAsset.exists()) {
          var assetsWithoutSelectedAsset = _.reject(assets, {id: selectedAsset.getId()});
          assets = assetsWithoutSelectedAsset.concat([selectedAsset.get()]);
        }

        if (me.isStarted()) {
          withDeactivatedSelectControl(function() {
            me.removeLayerFeatures();
          });
          var features = (!allowGrouping) ? _.map(assets, createFeature) : getGroupedFeatures(assets);
          selectControl.clear();
          vectorLayer.getSource().addFeatures(features);
          if(assetLabel)
            vectorLayer.getSource().addFeatures(assetLabel.renderFeaturesByPointAssets(assets, zoomlevels.getViewZoom(map)));
          applySelection();
        }

        if (trafficSignReadOnlyLayer)
          trafficSignReadOnlyLayer.refreshView();
      });
    };

    this.stop = function() {
      if (me.isStarted()) {
        me.removeLayerFeatures();
        me.deactivateSelection();
        me.eventListener.stopListening(eventbus);
        me.eventListener.running = false;
        handleUnSelected();
      }
    };

    var getGroupedFeatures = function (assets) {
      var assetGroups = assetGrouping.groupByDistance(assets, zoomlevels.getViewZoom(map));
      var modifiedAssets = _.forEach(assetGroups, function (assetGroup) {
        _.map(assetGroup, function (asset) {
          asset.lon = _.head(assetGroup).lon;
          asset.lat = _.head(assetGroup).lat;
        });
      });
      return _.map(_.flatten(modifiedAssets), createFeature);
    };

    function obtainAdministrativeClass(asset){
      return selectedAsset.getAdministrativeClass(asset.linkId);
    }

    this.removeLayerFeatures = function() {
      vectorLayer.getSource().clear();
    };

    function applySelection() {
      if (selectedAsset.exists()) {
        var feature = _.filter(vectorLayer.getSource().getFeatures(), function(feature) { return selectedAsset.isSelected(feature.getProperties());});
        if (feature) {
          selectControl.addSelectionFeatures(feature);
        }
      }
    }

    function withDeactivatedSelectControl(f) {
      var isActive = me.selectControl.active;
      if (isActive) {
          selectControl.deactivate();
        f();
          selectControl.activate();
      } else {
        f();
      }
    }

    this.layerStarted = function(eventListener) {
      this.bindEvents(eventListener);
      showRoadLinkInformation();
    };

    function toggleMode(readOnly) {
      if(readOnly){
        dragControl.deactivate();
      } else if(selectedAsset.exists() && authorizationPolicy.formEditModeAccess(selectedAsset, roadCollection)) {
        dragControl.activate();
      }
    }

    this.bindEvents = function(eventListener) {
      eventListener.listenTo(eventbus, 'map:clicked', this.handleMapClick);
      eventListener.listenTo(eventbus, layerName + ':saved ' + layerName + ':cancelled', handleSavedOrCancelled);
      eventListener.listenTo(eventbus, layerName + ':creationCancelled', handleCreationCancelled);
      eventListener.listenTo(eventbus, layerName + ':selected', handleSelected);
      eventListener.listenTo(eventbus, layerName + ':unselected', handleUnSelected);
      eventListener.listenTo(eventbus, layerName + ':changed', handleChanged);
      eventListener.listenTo(eventbus, 'application:readOnly', toggleMode);
      eventListener.listenTo(eventbus, 'toggleWithRoadAddress', refreshSelectedView);
    };
    eventbus.on( layerName + ':changeSigns', function(trafficSignData){
      setTrafficSigns(trafficSignData[0], trafficSignData[1]);
    });

    eventbus.on( layerName + ':signsChanged', function(trafficSignsShowing) {
      selectedAsset.checkSelectedSign(trafficSignsShowing);
    });

    var setTrafficSigns = function(trafficSign, isShowing) {
      collection.setTrafficSigns(trafficSign, isShowing);
      me.refreshView();
    };

    var startListeningExtraEvents = function(){
      extraEventListener.listenTo(eventbus, layerName+'-complementaryLinks:show', showWithComplementary);
      extraEventListener.listenTo(eventbus, layerName+'-complementaryLinks:hide', hideComplementary);
    };

    var stopListeningExtraEvents = function(){
      extraEventListener.stopListening(eventbus);
    };

    function handleCreationCancelled() {
      mapOverlay.hide();
      unHighLightReadOnlyLayer();
      roadLayer.clearSelection();
      me.refreshView();
    }

    function handleSelected() {
      applySelection();
    }

    function handleUnSelected() {
       selectControl.clear();
    }

    function handleSavedOrCancelled() {
      mapOverlay.hide();
      me.activateSelection();
      roadLayer.clearSelection();
      me.refreshView();
    }

    function handleChanged() {
      var asset = selectedAsset.get();
      var newAsset = _.merge({}, asset, {rotation: determineRotation(asset), bearing: determineBearing(asset), administrativeClass: obtainAdministrativeClass(asset)});
      _.find(vectorLayer.getSource().getFeatures(), {values_: {id: newAsset.id}}).values_= newAsset;
      var featureRedraw = _.find(vectorLayer.getSource().getFeatures(), function(feature) {
          return feature.getProperties().id === newAsset.id;
      });
      featureRedraw.setProperties({'geometry': new ol.geom.Point([newAsset.lon, newAsset.lat])});
      selectControl.addSelectionFeatures([featureRedraw]);

    }

    this.createNewAsset =  function(coordinates) {
      var selectedLon = coordinates.x;
      var selectedLat = coordinates.y;
      var nearestLine = geometrycalculator.findNearestLine(excludeRoadByAdminClass(roadCollection.getRoadsForPointAssets()), selectedLon, selectedLat);
      if(nearestLine.end && nearestLine.start){
        var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, { x: selectedLon, y: selectedLat });
        var bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
        var administrativeClass = obtainAdministrativeClass(nearestLine);

        var asset = createAssetWithPosition(selectedLat, selectedLon, nearestLine, projectionOnNearestLine, bearing, administrativeClass);

        vectorLayer.getSource().addFeature(createFeature(asset));
        selectedAsset.place(asset);
        mapOverlay.show();
      }
    };

    this.handleMapClick = function(coordinates) {
      if (application.getSelectedTool() === 'Add' && zoomlevels.isInAssetZoomLevel(zoomlevels.getViewZoom(map))) {
        me.createNewAsset(coordinates);
      } else if (selectedAsset.isDirty()) {
        me.displayConfirmMessage();
      }
    };

    function createAssetWithPosition(selectedLat, selectedLon, nearestLine, projectionOnNearestLine, bearing, administrativeClass) {
      var isServicePoint = newAsset.services;

      return _.merge({}, newAsset, isServicePoint ? {
        lon: selectedLon,
        lat: selectedLat,
        id: 0
      } : {
        lon: projectionOnNearestLine.x,
        lat: projectionOnNearestLine.y,
        floating: false,
        linkId: nearestLine.linkId,
        id: 0,
        geometry: [nearestLine.start, nearestLine.end],
        bearing: bearing,
        administrativeClass: administrativeClass
      });
    }

    function showWithComplementary() {
      if(trafficSignReadOnlyLayer)
        trafficSignReadOnlyLayer.showTrafficSignsComplementary();
      collection.activeComplementary(true);
      me.refreshView();
    }

    function show(map) {
      startListeningExtraEvents();
      vectorLayer.setVisible(true);
      roadAddressInfoPopup.start();
      me.show(map);
    }

    function hideComplementary() {
      if(trafficSignReadOnlyLayer)
        trafficSignReadOnlyLayer.hideTrafficSignsComplementary();
      collection.activeComplementary(false);
      selectedAsset.close();
      me.refreshView();
    }

    function hide() {
      selectedAsset.close();
      vectorLayer.setVisible(false);
      hideReadOnlyLayer();
      roadAddressInfoPopup.stop();
      stopListeningExtraEvents();
      me.stop();
      me.hide();
    }

    var hideReadOnlyLayer = function(){
      if(trafficSignReadOnlyLayer){
        trafficSignReadOnlyLayer.hide();
        trafficSignReadOnlyLayer.removeLayerFeatures();
      }
    };

    var unHighLightReadOnlyLayer = function(){
      if(trafficSignReadOnlyLayer){
        trafficSignReadOnlyLayer.unHighLightLayer();
      }
    };

    function excludeRoadByAdminClass(roadCollection) {
      return _.filter(roadCollection, function (roads) {
        return authorizationPolicy.filterRoadLinks(roads);
      });
    }

    var refreshSelectedView = function(){
      if(applicationModel.getSelectedLayer() == layerName)
        me.refreshView();
    };

    function showRoadLinkInformation() {
      if(params.showRoadLinkInfo) {
        roadLayer.setLayerSpecificStyleProvider(params.layerName, function() {
          return pointAssetLayerStyles;
        });
      }
    }

    return {
      show: show,
      hide: hide,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);
