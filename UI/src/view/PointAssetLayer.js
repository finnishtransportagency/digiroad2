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
      roadAddressInfoPopup = params.roadAddressInfoPopup;

    Layer.call(this, layerName, roadLayer);
    var me = this;
    me.minZoomForContent = zoomlevels.minZoomForAssets;
    var extraEventListener = _.extend({running: false}, eventbus);
    var vectorSource = new ol.source.Vector();
    var vectorLayer = new ol.layer.Vector({
       source : vectorSource,
       style : function(feature){
           return style.browsingStyleProvider.getStyle(feature);
       }
    });
    vectorLayer.set('name', layerName);
    vectorLayer.setOpacity(1);
    vectorLayer.setVisible(true);
    map.addLayer(vectorLayer);

    var selectControl = new SelectToolControl(application, vectorLayer, map, {
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
        selectedAsset.open(feature.selected[0].getProperties());
        toggleMode(application.isReadOnly());
      }
      else {
        if(feature.deselected.length > 0 && !selectedAsset.isDirty()) {
          selectedAsset.close();
        }else{
          applySelection();
        }
      }
    }

    this.selectControl = selectControl;

    var dragControl = defineOpenLayersDragControl();
    function defineOpenLayersDragControl() {
        var dragHandler = layerName === 'servicePoints' ? dragFreely : dragAlongNearestLink;
        var dragControl = new ol.interaction.Translate({
           features : selectControl.getSelectInteraction().getFeatures()
        });

        dragControl.on('translating', dragHandler);

        function dragFreely(feature) {
          if (selectedAsset.isSelected(feature.attributes)) {
            selectedAsset.set({lon: feature.coordinate[0], lat: feature.coordinate[1]});
          }
        }

        function dragAlongNearestLink(feature) {
          if (selectedAsset.isSelected(feature.features.getArray()[0].getProperties())) {
            var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), feature.coordinate[0], feature.coordinate[1]);
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
      var feature =  new ol.Feature({geometry : new ol.geom.Point([asset.lon, asset.lat])});
      var obj = _.merge({}, asset, {rotation: rotation, bearing: bearing}, feature.getProperties());
      feature.setProperties(obj);
      return feature;
    }

    function determineRotation(asset) {
      var rotation = 0;
      if (!asset.floating && asset.geometry && asset.geometry.length > 0){
        var bearing = determineBearing(asset);
        rotation = validitydirections.calculateRotation(bearing, asset.validityDirection);
      } else if (layerName == 'directionalTrafficSigns'){
        rotation = validitydirections.calculateRotation(asset.bearing, asset.validityDirection);
      }
      return rotation;
    }

    function determineBearing(asset) {
      var bearing = 90;
      if (!asset.floating && asset.geometry && asset.geometry.length > 0){
        var nearestLine = geometrycalculator.findNearestLine([{ points: asset.geometry }], asset.lon, asset.lat);
        bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
      } else if (layerName == 'directionalTrafficSigns'){
        bearing = asset.bearing;
      }
      return bearing;
    }

    this.refreshView = function() {
      eventbus.once('roadLinks:fetched', function () {
        roadLayer.drawRoadLinks(roadCollection.getAll(), map.getView().getZoom());
         selectControl.activate();
      });
      if(collection.complementaryIsActive())
        roadCollection.fetchWithComplementary(map.getView().calculateExtent(map.getSize()));
      else
      roadCollection.fetch(map.getView().calculateExtent(map.getSize()));
      collection.fetch(map.getView().calculateExtent(map.getSize())).then(function(assets) {
        if (selectedAsset.exists()) {
          var assetsWithoutSelectedAsset = _.reject(assets, {id: selectedAsset.getId()});
          assets = assetsWithoutSelectedAsset.concat([selectedAsset.get()]);
        }

        if (me.isStarted()) {
          withDeactivatedSelectControl(function() {
            me.removeLayerFeatures();
          });
          var features = _.map(assets, createFeature);
          selectControl.clear();
          vectorLayer.getSource().addFeatures(features);
          applySelection();
        }
      });
    };

    this.removeLayerFeatures = function() {
      vectorLayer.getSource().clear();
    };

    function applySelection() {
      if (selectedAsset.exists()) {
        var feature = _.find(vectorLayer.getSource().getFeatures(), function(feature) { return selectedAsset.isSelected(feature.getProperties());});
        if (feature) {
          selectControl.addSelectionFeatures([feature]);
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
      bindEvents(eventListener);
    };

    function toggleMode(readOnly) {
      if(readOnly){
        dragControl.deactivate();
      } else {
        dragControl.activate();
      }
    }

    function bindEvents(eventListener) {
      eventListener.listenTo(eventbus, 'map:clicked', handleMapClick);
      eventListener.listenTo(eventbus, layerName + ':saved ' + layerName + ':cancelled', handleSavedOrCancelled);
      eventListener.listenTo(eventbus, layerName + ':creationCancelled', handleCreationCancelled);
      eventListener.listenTo(eventbus, layerName + ':selected', handleSelected);
      eventListener.listenTo(eventbus, layerName + ':unselected', handleUnSelected);
      eventListener.listenTo(eventbus, layerName + ':changed', handleChanged);
      eventListener.listenTo(eventbus, 'application:readOnly', toggleMode);
    }

    var startListeningExtraEvents = function(){
      extraEventListener.listenTo(eventbus, 'withComplementary:show', showWithComplementary);
      extraEventListener.listenTo(eventbus, 'withComplementary:hide', hideComplementary);
    };

    var stopListeningExtraEvents = function(){
      extraEventListener.stopListening(eventbus);
    };

    function handleCreationCancelled() {
      mapOverlay.hide();
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
      var newAsset = _.merge({}, asset, {rotation: determineRotation(asset), bearing: determineBearing(asset)});
      _.find(vectorLayer.getSource().getFeatures(), {values_: {id: newAsset.id}}).values_= newAsset;
      var featureRedraw = _.find(vectorLayer.getSource().getFeatures(), function(feature) {
          return feature.getProperties().id === newAsset.id;
      });
      featureRedraw.setProperties({'geometry': new ol.geom.Point([newAsset.lon, newAsset.lat])});
      selectControl.addSelectionFeatures([featureRedraw]);

    }

    function handleMapClick(coordinates) {
      if (application.getSelectedTool() === 'Add' && zoomlevels.isInAssetZoomLevel(map.getView().getZoom())) {
        createNewAsset(coordinates);
      } else if (selectedAsset.isDirty()) {
        me.displayConfirmMessage();
      }
    }

    function createNewAsset(coordinates) {
      var selectedLon = coordinates.x;
      var selectedLat = coordinates.y;
      var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), selectedLon, selectedLat);
      var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, { x: selectedLon, y: selectedLat });
      var bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);

      var asset = createAssetWithPosition(selectedLat, selectedLon, nearestLine, projectionOnNearestLine, bearing);

      vectorLayer.getSource().addFeature(createFeature(asset));
      selectedAsset.place(asset);
      mapOverlay.show();
    }

    function createAssetWithPosition(selectedLat, selectedLon, nearestLine, projectionOnNearestLine, bearing) {
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
        bearing: bearing
      });
    }

    function showWithComplementary() {
      collection.activeComplementary(true);
      me.refreshView();
    }

    function show(map) {
      startListeningExtraEvents();
      vectorLayer.setVisible(true);
      roadAddressInfoPopup.start();
      me.refreshView();
      me.show(map);
    }

    function hideComplementary() {
      collection.activeComplementary(false);
      selectedAsset.close();
      me.refreshView();
    }

    function hide() {
      selectedAsset.close();
      vectorLayer.setVisible(false);
      roadAddressInfoPopup.stop();
      stopListeningExtraEvents();
      me.stop();
      me.hide();
    }

    return {
      show: show,
      hide: hide,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);
