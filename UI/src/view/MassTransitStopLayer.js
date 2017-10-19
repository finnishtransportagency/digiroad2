window.MassTransitStopLayer = function(map, roadCollection, mapOverlay, assetGrouping, roadLayer, roadAddressInfoPopup) {
  var layerName = 'massTransitStop';
  Layer.call(this, layerName, roadLayer);
  var me = this;
  me.minZoomForContent = zoomlevels.minZoomForAssets;
  var eventListener = _.extend({running: false}, eventbus);
  var selectedAsset;
  var movementPermissionConfirmed = false;
  var requestingMovePermission  = false;
  var massTransitStopLayerStyles = MassTransitStopLayerStyles(roadLayer);
  var visibleAssets;
  var overrideMessageAllow = true;
  var publicIds = {
    roadNameFi: 'osoite_suomeksi',
    roadNameSe: 'osoite_ruotsiksi'
  };

  var selectedControl = 'Select';

  var assetSource = new ol.source.Vector();
  var assetLayer = new ol.layer.Vector({
    source : assetSource,
    style : function(feature) {
      var properties = feature.getProperties();
      if(properties.massTransitStop)
        return properties.massTransitStop.getMarkerDefaultStyles();
      return [];
    },
    //Increase the buffer around the viewport extend because of group bus stops
    renderBuffer: 300
  });
  var terminalSource = new ol.source.Vector();
  var terminalLayer = new ol.layer.Vector({
      source : terminalSource,
      style : function(feature) {
          var properties = feature.getProperties();
          if(properties.massTransitStop)
              return properties.massTransitStop.getMarkerDefaultStyles();
          return [];
      }
  });

  assetLayer.set('name', layerName);
  assetLayer.setOpacity(1);
  assetLayer.setVisible(true);
  map.addLayer(assetLayer);

  terminalLayer.set('name', 'terminalLayer');
  terminalLayer.setOpacity(1);
  terminalLayer.setVisible(true);
  map.addLayer(terminalLayer);


  function onSelectMassTransitStop(event) {
    if(event.selected.length > 0){
      _.each(event.selected, function(feature){

        if(!feature.getProperties().data || !feature.getProperties().data.nationalId)
          return;

        feature.getProperties().massTransitStop.getMarkerSelectionStyles();
        selectedMassTransitStopModel.change(feature.getProperties().data);
        movementPermissionConfirmed = false;
        overrideMessageAllow = true;
      });
      toggleMode();
    }
    else {
      if(event.deselected.length > 0) {
        selectedMassTransitStopModel.close();
      }
    }
  }

  var selectControl = new SelectToolControl(applicationModel, assetLayer, map, {
    style : function (feature) {
      var properties = feature.getProperties();
      if(properties.massTransitStop)
        return properties.massTransitStop.getMarkerSelectionStyles();
      return [];
    },
    onSelect: onSelectMassTransitStop,
    draggable : false,
    filterGeometry : function(feature){
      return feature.getGeometry() instanceof ol.geom.Point && !_.isUndefined(feature.getProperties().data);
    }
  });

  this.selectControl = selectControl;

  var dragControl = defineOpenLayersDragControl();

  function defineOpenLayersDragControl() {

    var dragControl = new ol.interaction.Translate({
      features : selectControl.getSelectInteraction().getFeatures()
    });

    dragControl.set('name', 'translate_massTransitStop');

    var translateSelectedAsset = function(event) {
      var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(),event.coordinate[0], event.coordinate[1]);
      var angle = geometrycalculator.getLineDirectionDegAngle(nearestLine);
      var position = geometrycalculator.nearestPointOnLine(nearestLine, { x: event.coordinate[0], y: event.coordinate[1]});

      doMovement(event, angle, nearestLine, {lon: position.x, lat: position.y}, false);

    };

    var translateEndedAsset = function(event){
      var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(),event.coordinate[0], event.coordinate[1]);
      var angle = geometrycalculator.getLineDirectionDegAngle(nearestLine);
      var position = geometrycalculator.nearestPointOnLine(nearestLine, { x: event.coordinate[0], y: event.coordinate[1]});

      restrictMovement(event, {lon: selectedAsset.data.originalLon, lat: selectedAsset.data.originalLat}, angle, nearestLine, {lon: position.x, lat: position.y});
    };

    dragControl.on('translating', translateSelectedAsset);
    dragControl.on('translateend', translateEndedAsset);

    var activate = function () {
      map.addInteraction(dragControl);
    };

    var deactivate = function () {
      map.removeInteraction(dragControl);
    };

    return {
      activate : activate,
      deactivate : deactivate
    };
  }

  roadLayer.setLayerSpecificStyleProvider('massTransitStop', function() {
    return massTransitStopLayerStyles;
  });

  var createAsset = function(assetData) {
    var massTransitStop = new MassTransitStop(assetData, massTransitStopsCollection, map);
    var marker = massTransitStop.getMarker();
    var asset = {};
    asset.data = assetData;
    asset.massTransitStop = massTransitStop;
    marker.feature.setProperties(asset);

    if(massTransitStopsCollection.selectedValidityPeriodsContain(asset.data.validityPeriod))
      assetSource.addFeature(marker.feature);

    return asset;
  };

  var removeAssetFromMap = function(asset) {
    var feature = asset.massTransitStop.getMarkerFeature();
    massTransitStopsCollection.destroyAsset(asset.data.id);
    if(_.some(assetSource.getFeatures(), function(f){ return f == feature; }))
      assetSource.removeFeature(feature);
  };

  this.removeLayerFeatures = function () {
    roadLayer.clearSelection();
    _.each(visibleAssets, function (asset) { destroyAsset(asset); });
  };

  var isSelected = function(asset) {
    return selectedAsset && selectedAsset.data.id === asset.id;
  };

  var convertBackendAssetToUIAsset = function(backendAsset, centroidLonLat, assetGroup) {
    var uiAsset = backendAsset;
    var lon = centroidLonLat.lon;
    var lat = centroidLonLat.lat;
    if (isSelected(uiAsset)) {
      lon = selectedAsset.data.lon;
      lat = selectedAsset.data.lat;
      uiAsset.lon = lon;
      uiAsset.lat = lat;
    }
    uiAsset.group = {
      lon: lon,
      lat: lat,
      assetGroup: assetGroup
    };
    return uiAsset;
  };

  var renderAssets = function(assetDatas) {
    assetLayer.setVisible(true);
    _.each(massTransitStopsCollection.getComplementaryAssets(), removeAssetFromMap);
    _.each(assetDatas, function(assetGroup) {
      assetGroup = _.sortBy(assetGroup, 'id');
      var centroidLonLat = geometrycalculator.getCentroid(assetGroup);
      _.each(assetGroup, function(asset) {
        var uiAsset = convertBackendAssetToUIAsset(asset, centroidLonLat, assetGroup);
        if (!massTransitStopsCollection.getAsset(uiAsset.id)) {
            var assetInModel = createAsset(uiAsset);
            massTransitStopsCollection.insertAsset(assetInModel, uiAsset.id);
        }
      });

    });
  };

  var cancelCreate = function() {
    roadLayer.clearSelection();
    removeOverlay();
    selectControl.clear();
    deselectAsset(selectedAsset);
    removeAssetFromMap(selectedAsset);
  };

  var cancelUpdate = function(asset) {
    roadLayer.clearSelection();
    selectControl.clear();
    deselectAsset(selectedAsset);
    destroyAsset(asset);
    selectedAsset = addNewAsset(asset);
    selectedAsset = regroupAssetIfNearOtherAssets(selectedAsset.data);
  };

  var updateAsset = function(asset) {
    removeAssetFromMap(selectedAsset);
    selectedAsset = addNewAsset(asset);
  };

  var handleValidityPeriodChanged = function() {
    assetSource.clear();
    _.each(massTransitStopsCollection.getAssets(), function(asset) {
      var marker = asset.massTransitStop.getMarker();
      if (massTransitStopsCollection.selectedValidityPeriodsContain(asset.data.validityPeriod) && zoomlevels.isInAssetZoomLevel(map.getView().getZoom())) {
        assetSource.addFeature(marker.feature);
      }
    });
    if (selectedAsset && selectedAsset.data.validityPeriod === undefined) {
      return;
    }

    if (selectedAsset && !massTransitStopsCollection.selectedValidityPeriodsContain(selectedAsset.data.validityPeriod)) {
      closeAsset();
    }
  };

  var addAssetToGroup = function(asset, group) {
    var assetGroup = _.sortBy(group.assetGroup.concat([asset.data]), 'id');
    _.each(assetGroup, function(asset) {
      asset.group.assetGroup = assetGroup;
      asset.group.lon = group.lon;
      asset.group.lat = group.lat;
    });
  };

  function createAndGroupUIAsset(backendAsset) {
    var uiAsset;
    var assetToGroupWith = assetGrouping.findNearestAssetWithinGroupingDistance(_.values(massTransitStopsCollection.getAssets()), backendAsset);
    if (assetToGroupWith) {
      uiAsset = createAsset(convertBackendAssetToUIAsset(backendAsset, assetToGroupWith.data.group, assetToGroupWith.data.group.assetGroup));
      massTransitStopsCollection.insertAsset(uiAsset, uiAsset.data.id);
      addAssetToGroup(uiAsset, assetToGroupWith.data.group);
    } else {
      var group = createDummyGroup(backendAsset.lon, backendAsset.lat, backendAsset);
      uiAsset = createAsset(convertBackendAssetToUIAsset(backendAsset, group, group.assetGroup));
      massTransitStopsCollection.insertAsset(uiAsset, uiAsset.data.id);
    }
    return uiAsset;
  }

  var handleAssetCreated = function(asset) {
    removeAssetFromMap(selectedAsset);
    if (asset)
      movementPermissionConfirmed = false;
    selectedAsset = createAndGroupUIAsset(asset);
    removeOverlay();
  };

  var handleAssetSaved = function(asset, positionUpdated) {
    _.merge(massTransitStopsCollection.getAsset(asset.id).data, asset);

    var features = selectControl.getSelectInteraction().getFeatures();
    _.each(features.getArray(), function(feature) {
      var properties = feature.getProperties();
      feature.setStyle(properties.massTransitStop.getMarkerSelectionStyles());
    });

    if (positionUpdated) {
      destroyAsset(asset);
      deselectAsset(selectedAsset);
      selectedAsset = createAndGroupUIAsset(asset);
    }
  };

  var parseAssetDataFromAssetsWithMetadata = function(assets) {
    return _.chain(assets)
      .values()
      .pluck('data')
      .map(function(x) { return _.omit(x, 'group'); })
      .value();
  };

  var regroupAssetIfNearOtherAssets = function(asset) {
    var regroupedAssets = assetGrouping.groupByDistance(parseAssetDataFromAssetsWithMetadata(massTransitStopsCollection.getAssets()), map.getView().getZoom());
    var groupContainingSavedAsset = _.find(regroupedAssets, function(assetGroup) {
      var assetGroupIds = _.pluck(assetGroup, 'id');
      return _.contains(assetGroupIds, asset.id);
    });
    var assetIds = _.map(groupContainingSavedAsset, function(asset) { return asset.id.toString(); });

    if (groupContainingSavedAsset && groupContainingSavedAsset.length > 1) {
      massTransitStopsCollection.destroyGroup(assetIds);
    }

    return massTransitStopsCollection.getAsset(asset.id);
  };

  var reRenderGroup = function(destroyedAssets) {
    _.each(destroyedAssets, removeAssetFromMap);
    renderAssets([parseAssetDataFromAssetsWithMetadata(destroyedAssets)]);
  };

  var extractStopTypes = function(properties) {
    return _.chain(properties)
        .where({ publicId: 'pysakin_tyyppi' })
        .pluck('values')
        .flatten()
        .pluck('propertyValue')
        .value();
  };

  var handleAssetPropertyValueChanged = function(propertyData) {
    var features = selectControl.getSelectInteraction().getFeatures();
    if (propertyData.propertyData.publicId === 'vaikutussuunta') {
      _.each(features.getArray(), function(feature){
        var properties = feature.getProperties();
        var validityDirection = propertyData.propertyData.values[0].propertyValue;
        properties.data.validityDirection = validityDirection;
        feature.setProperties(_.omit(properties, 'geometry'));
        feature.setStyle(properties.massTransitStop.getMarkerSelectionStyles());
      });
    }

    if (_.contains(['pysakin_tyyppi', 'nimi_suomeksi'], propertyData.propertyData.publicId)) {
      var assetProperties = selectedMassTransitStopModel.getProperties();
      _.each(features.getArray(), function(feature){
        var properties = feature.getProperties();
        var stopTypes = extractStopTypes(assetProperties);
        properties.data.stopTypes = stopTypes;
        feature.setProperties(_.omit(properties, 'geometry'));
        feature.setStyle(properties.massTransitStop.getMarkerSelectionStyles());
      });
    }

    if(_.contains(['liitetyt_pysakit'], propertyData.propertyData.publicId)){
      var asset = selectedMassTransitStopModel.getCurrentAsset();
      _.each(terminalSource.getFeatures(), function(feature){
          var busStop = feature.getProperties();
          if(asset.id == busStop.data.id || _.some(propertyData.propertyData.values, function(value){ return busStop.data.id == parseInt(value.propertyValue);  } )){
            feature.setStyle(busStop.massTransitStop.getMarkerSelectionStyles());
          }
          else{
            feature.setStyle(busStop.massTransitStop.getMarkerDefaultStyles());
          }
      });
    }
  };

  var createNewAsset = function(coordinate, placement, stopTypes) {
    var default_asset_direction = {BothDirections: 2, TowardsDigitizing: 2, AgainstDigitizing: 3};
    var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), coordinate.x, coordinate.y);
    var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, coordinate);
    var bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
    var data = {
      bearing: bearing,
      validityDirection: default_asset_direction[nearestLine.trafficDirection],
      lon: projectionOnNearestLine.x,
      lat: projectionOnNearestLine.y,
      roadLinkId: nearestLine.roadLinkId,
      linkId: nearestLine.linkId,
      stopTypes: stopTypes
    };
    data.group = createDummyGroup(projectionOnNearestLine.x, projectionOnNearestLine.y, data);
    var massTransitStop = new MassTransitStop(data, massTransitStopsCollection);
    var currentAsset = selectedMassTransitStopModel.getCurrentAsset();
    deselectAsset();
    if(placement){
      selectedMassTransitStopModel.place(data, currentAsset);
    }else {
      selectedMassTransitStopModel.place(data);
    }
    eventbus.trigger('terminalBusStop:selected', stopTypes[0]);
    selectedAsset = createAsset(data);
    var feature = selectedAsset.massTransitStop.getMarkerFeature();
    selectControl.addSelectionFeatures([feature], false, false);
    applyBlockingOverlays();
  };

  var applyBlockingOverlays = function() {
    mapOverlay.show();
  };

  var removeOverlay = function() {
    mapOverlay.hide();
  };

  var addNewAsset = function(asset) {
    asset.group = createDummyGroup(asset.lon, asset.lat, asset);
    var uiAsset = createAsset(asset);
    massTransitStopsCollection.insertAsset(uiAsset, asset.id);
    return uiAsset;
  };

  var createDummyGroup = function(lon, lat, asset) {
    return {lon: lon, lat: lat, assetGroup: [asset]};
  };

  var closeAsset = function() {
    deselectAsset(selectedAsset);
    eventbus.trigger('application:controledTR',false);
  };

  var destroyAsset = function(backendAsset) {
    var uiAsset = massTransitStopsCollection.getAsset(backendAsset.id);
    if(uiAsset) {
      removeAssetFromMap(uiAsset);
      massTransitStopsCollection.destroyAsset(backendAsset.id);
    }
  };

  var deselectAsset = function(asset) {
    _.each(terminalSource.getFeatures(), function(feature){
      feature.setStyle(feature.getProperties().massTransitStop.getMarkerDefaultStyles());
    });
    if(selectedAsset)
      selectedAsset.massTransitStop.getMarkerFeature().setStyle(selectedAsset.massTransitStop.getMarkerDefaultStyles());
    terminalSource.clear();
    if (asset)
      movementPermissionConfirmed = false;
      overrideMessageAllow = true;
    };

  var handleAssetFetched = function(backendAsset) {
    deselectAsset(selectedAsset);
    selectedAsset = massTransitStopsCollection.getAsset(backendAsset.id);

    var nearestStops = massTransitStopsCollection.getAllTerminalNearestStops(backendAsset.propertyData);
    var features = _.without(_.map(nearestStops, function(nearestStop){
      var childAsset = massTransitStopsCollection.getAsset(nearestStop.id);
      if(childAsset){
        if(!nearestStop.isChild){
            if(applicationModel.isReadOnly())
              return null;
            childAsset.massTransitStop.getMarkerFeature().setStyle(childAsset.massTransitStop.getMarkerDefaultStyles());
        }
        else
        {
            childAsset.massTransitStop.getMarkerFeature().setStyle(childAsset.massTransitStop.getMarkerSelectionStyles());
        }
        return childAsset.massTransitStop.getMarkerFeature();
      }
      return null;
    }), null);

    selectedAsset.massTransitStop.getMarkerFeature().setStyle(selectedAsset.massTransitStop.getMarkerSelectionStyles());
    terminalSource.clear();
    terminalSource.addFeatures(features);
    selectControl.addSelectionFeatures([selectedAsset.massTransitStop.getMarker().feature], false, false);
  };

  var ownedByELY = function () {
    if(applicationModel.isReadOnly()){
      return true;
    }

    return selectedMassTransitStopModel.isAdministratorELY();
  };

  var ownedByHSL = function(){
    var properties = selectedMassTransitStopModel.getProperties();
    return selectedMassTransitStopModel.isAdministratorHSL(properties) && selectedMassTransitStopModel.isAdminClassState(properties);
  };

  var autoUpdateAddressNames = function (originalLinkId, newLinkId) {
    if(isTerminalBusStop())
      return;

    var popupMessageToShow = 'Säilyykö pysäkin osoite (katunimi) samana? Jos ei, tarkista uusi osoite tallennuksen jälkeen.';
    var roadLinkData = roadCollection.getRoadLinkByLinkId(newLinkId).getData();

    if (overrideMessageAllow) {
      if (selectedMassTransitStopModel.isRoadNameDif(roadLinkData.roadNameFi, publicIds.roadNameFi) ||
          selectedMassTransitStopModel.isRoadNameDif(roadLinkData.roadNameSe, publicIds.roadNameSe)) {
        new GenericConfirmPopup(popupMessageToShow, {
          successCallback: function () {
          },
          closeCallback: function () {
            overrideMessageAllow = false;
            selectedMassTransitStopModel.setProperty(publicIds.roadNameFi, [{propertyValue: ''}]);
            selectedMassTransitStopModel.setProperty(publicIds.roadNameSe, [{propertyValue: ''}]);

            selectedMassTransitStopModel.setRoadNameFields(roadLinkData, publicIds);
          }
        });
      }
    } else {
      selectedMassTransitStopModel.setRoadNameFields(roadLinkData, publicIds);
    }
  };

  var isTerminalChild = function () {
    var properties = selectedMassTransitStopModel.getProperties();
    return selectedMassTransitStopModel.isTerminalChild(properties);
  };

  var isTerminalBusStop = function() {
      return _.some(selectedMassTransitStopModel.getProperties(), function(property) {
          return property.publicId == 'pysakin_tyyppi' && _.some(property.values, function(value){
                  return value.propertyValue == "6";
              });
      });
  };

  var restrictMovement = function (event, originalCoordinates, angle, nearestLine, coordinates) {
    var movementLimit = 50; //50 meters
    var popupMessageToShow;
    //The method geometrycalculator.getSquaredDistanceBetweenPoints() will return the distance in Meters so we multiply the result for this
    var distance = Math.sqrt(geometrycalculator.getSquaredDistanceBetweenPoints(coordinates, originalCoordinates));

    if (distance > movementLimit && !movementPermissionConfirmed)
    {
      requestingMovePermission = true;
      if (ownedByELY() || ownedByHSL()){
        popupMessageToShow = 'Pysäkkiä siirretty yli 50 metriä. Siirron yhteydessä vanha pysäkki lakkautetaan ja luodaan uusi pysäkki.';
      } else {
        popupMessageToShow = 'Pysäkkiä siirretty yli 50 metriä. Haluatko siirtää pysäkin uuteen sijaintiin?';
      }

      if(isTerminalChild())
          popupMessageToShow += ' <br><br> *Pysäkin viittaus terminaaliin häviää siirron yhteydessä. Luo yhteys uudelleen tarvittaessa. ' ;

      new GenericConfirmPopup(popupMessageToShow,{
        successCallback: function(){
          doMovement(event, angle, nearestLine, coordinates);
          roadLayer.clearSelection();
          movementPermissionConfirmed = true;
          requestingMovePermission = false;
          autoUpdateAddressNames(selectedAsset.data.linkId, nearestLine.linkId);
        },
        closeCallback: function(){
          //Moves the stop to the original position
          var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), originalCoordinates.lon, originalCoordinates.lat);
          var angle = geometrycalculator.getLineDirectionDegAngle(nearestLine);
          doMovement(event, angle, nearestLine, originalCoordinates);
          roadLayer.clearSelection();
          movementPermissionConfirmed = false;
          requestingMovePermission = false;
        }
      });
    }
    else
    {
      doMovement(event, angle, nearestLine, coordinates);
      autoUpdateAddressNames(selectedAsset.data.linkId, nearestLine.linkId);
      roadLayer.clearSelection();
    }
  };

  var doMovement= function(event, angle, nearestLine, coordinates) {
    var feature = event.features.getArray()[0];
    var properties = feature.getProperties();

    properties.data.bearing = angle;
    properties.data.roadDirection = angle;

    properties.data.lon = coordinates.lon;
    properties.data.lat = coordinates.lat;

    roadLayer.selectRoadLink(nearestLine);
    feature.getGeometry().setCoordinates([coordinates.lon, coordinates.lat]);
    selectedAsset.massTransitStop.getMarkerFeature().setStyle(selectedAsset.massTransitStop.getMarkerSelectionStyles());

    selectedMassTransitStopModel.move({
      lon: coordinates.lon,
      lat: coordinates.lat,
      bearing: angle,
      roadLinkId: nearestLine.roadLinkId,
      linkId: nearestLine.linkId,
      validityDirection: selectedAsset.data.validityDirection
    });
  };

  var toolSelectionChange = function(action) {
    selectedControl = action;
  };

  var createNewUIAssets = function(backendAssetGroups) {
    return _.map(backendAssetGroups, function(group) {
      var centroidLonLat = geometrycalculator.getCentroid(group);
      return _.map(group, function(backendAsset) {
        return createAsset(convertBackendAssetToUIAsset(backendAsset, centroidLonLat, group));
      });
    });
  };

  var addNewGroupsToModel = function(uiAssetGroups) {
    _.each(uiAssetGroups, massTransitStopsCollection.insertAssetsFromGroup);
  };

  var handleNewAssetsFetched = function(newBackendAssets) {
    var backendAssetGroups = assetGrouping.groupByDistance(newBackendAssets, map.getView().getZoom());
    var uiAssetGroups = createNewUIAssets(backendAssetGroups);
    addNewGroupsToModel(uiAssetGroups);
  };

  var backendAssetsWithSelectedAsset = function(assets) {
    var transformSelectedAsset = function(asset) {
      if (asset) {
        var transformedAsset = asset;
        transformedAsset.lon = selectedAsset.data.lon;
        transformedAsset.lat = selectedAsset.data.lat;
        transformedAsset.bearing = selectedAsset.data.bearing;
        transformedAsset.validityDirection = selectedAsset.data.validityDirection;
        return [transformedAsset];
      }
      return [];
    };
    var transformedSelectedAsset = transformSelectedAsset(_.find(assets, isSelected));
    return _.reject(assets, isSelected).concat(transformedSelectedAsset);
  };

  var updateAllAssets = function(assets) {
    var assetsWithSelectedAsset = backendAssetsWithSelectedAsset(assets);
    var groupedAssets = assetGrouping.groupByDistance(assetsWithSelectedAsset, map.getView().getZoom());
    renderAssets(groupedAssets);
  };

  function handleAllAssetsUpdated(assets) {
    visibleAssets = assets;
    if (zoomlevels.isInAssetZoomLevel(map.getView().getZoom())) {
      updateAllAssets(assets);
    }
  }

  var handleMapClick = function(coordinates) {
    if ((selectedControl === 'Add' || selectedControl === 'AddTerminal') && zoomlevels.isInRoadLinkZoomLevel(map.getView().getZoom())) {
      selectControl.deactivate();
      createNewAsset(coordinates, false, selectedControl === 'AddTerminal' ? ['6'] : []);
    } else {
      if (selectedMassTransitStopModel.isDirty()) {
        selectControl.deactivate();
        if(!requestingMovePermission)
          new Confirm();
      } else {
        selectControl.activate();
        selectedMassTransitStopModel.close();
      }
    }
  };

  this.refreshView = function() {
    var extent = map.getView().calculateExtent(map.getSize());

    eventbus.once('roadLinks:fetched', function () {
      var roadLinks = roadCollection.getAll();
      roadLayer.drawRoadLinks(roadLinks, map.getView().getZoom());
      me.drawOneWaySigns(roadLayer.layer, roadLinks);
    });

    massTransitStopsCollection.refreshAssets({ bbox: extent, hasZoomLevelChanged: true });

    if (massTransitStopsCollection.isComplementaryActive()) {
      roadCollection.fetchWithComplementary(extent);
    } else {
      roadCollection.fetch(extent);
    }
  };

  function toggleMode() {
    if(applicationModel.isReadOnly()){
      dragControl.deactivate();
    } else {
      dragControl.activate();
    }
  }

  var handleMapMoved = function(mapMoveEvent) {
    if (zoomlevels.isInAssetZoomLevel(mapMoveEvent.zoom)) {
      me.handleMapMoved(mapMoveEvent);
    } else {
      if (applicationModel.getSelectedLayer() === 'massTransitStop') {
          assetSource.clear();
      }
    }
  };

  var bindEvents = function() {
    eventListener.listenTo(eventbus, 'validityPeriod:changed', handleValidityPeriodChanged);
    eventListener.listenTo(eventbus, 'tool:changed', toolSelectionChange);
    eventListener.listenTo(eventbus, 'assetPropertyValue:saved', updateAsset);
    eventListener.listenTo(eventbus, 'assetPropertyValue:changed', handleAssetPropertyValueChanged);
    eventListener.listenTo(eventbus, 'asset:saved', handleAssetSaved);
    eventListener.listenTo(eventbus, 'asset:created', handleAssetCreated);
    eventListener.listenTo(eventbus, 'asset:fetched', handleAssetFetched);
    eventListener.listenTo(eventbus, 'asset:created', removeOverlay);
    eventListener.listenTo(eventbus, 'asset:creationCancelled asset:creationFailed asset:creationTierekisteriFailed asset:creationNotFoundRoadAddressVKM', cancelCreate);
    eventListener.listenTo(eventbus, 'asset:updateCancelled asset:updateFailed asset:updateTierekisteriFailed asset:updateNotFoundRoadAddressVKM', cancelUpdate);
    eventListener.listenTo(eventbus, 'asset:closed', closeAsset);
    eventListener.listenTo(eventbus, 'asset:modified', function(){
      terminalSource.clear();
      var asset = selectedMassTransitStopModel.getCurrentAsset();
      if(asset.propertyMetadata){
        var property = _.find(asset.propertyMetadata, function(prop){
          return prop.publicId == 'liitetyt_pysakit';
        });
        if(property)
          _.each(property.values, function(value){
              var busStop = massTransitStopsCollection.getAsset(parseInt(value.propertyValue));
              if(busStop)
                  terminalSource.addFeature(busStop.massTransitStop.getMarkerFeature());
          });
      }
    });
    eventListener.listenTo(eventbus, 'assets:fetched', function(assets) {
      if (zoomlevels.isInAssetZoomLevel(map.getView().getZoom())) {
        var groupedAssets = assetGrouping.groupByDistance(assets, map.getView().getZoom());
        renderAssets(groupedAssets);
      }
    });
    eventListener.listenTo(eventbus, 'assets:all-updated', handleAllAssetsUpdated);
    eventListener.listenTo(eventbus, 'assets:new-fetched', handleNewAssetsFetched);
    eventListener.listenTo(eventbus, 'assetGroup:destroyed', reRenderGroup);
    eventListener.listenTo(eventbus, 'map:moved', handleMapMoved);
    eventListener.listenTo(eventbus, 'map:clicked', handleMapClick);
    eventListener.listenTo(eventbus, 'layer:selected', closeAsset);
    eventListener.listenTo(eventbus, 'massTransitStopDeleted', function(asset){
      closeAsset();
      destroyAsset(asset);
      eventbus.trigger("asset:closed");
    });
    eventListener.listenTo(eventbus, 'massTransitStop:expired', function(asset){
      destroyAsset(asset);
    });
    eventListener.listenTo(eventbus, 'massTransitStop:movementPermission', function(movementPermission){
      movementPermissionConfirmed = movementPermission;
    });
    eventListener.listenTo(eventbus, 'roadLinkComplementaryBS:show', showWithComplementary);
    eventListener.listenTo(eventbus, 'roadLinkComplementaryBS:hide', hideComplementary);
    eventListener.listenTo(eventbus, 'road-type:selected', roadLayer.toggleRoadTypeWithSpecifiedStyle);

    eventListener.listenTo(eventbus, 'application:readOnly', toggleMode);
    eventListener.listenTo(eventbus, 'toggleWithRoadAddress', refreshSelectedView);
  };

  var startListening = function() {
    if (!eventListener.running) {
      eventListener.running = true;
      bindEvents();
    }
  };

  var stopListening = function() {
    eventListener.stopListening(eventbus);
    eventListener.running = false;
  };

  var registerRoadLinkFetched = function(){
    if (zoomlevels.isInAssetZoomLevel(map.getView().getZoom())) {
      eventbus.once('roadLinks:fetched', function() {
          var roadLinks = roadCollection.getAll();
          roadLayer.drawRoadLinks(roadLinks, map.getView().getZoom());
          massTransitStopsCollection.fetchAssets( map.getView().calculateExtent(map.getSize()));
          me.drawOneWaySigns(roadLayer.layer, roadLinks);
      });
      if(massTransitStopsCollection.isComplementaryActive())
        roadCollection.fetchWithComplementary(map.getView().calculateExtent(map.getSize()));
      else
        roadCollection.fetch( map.getView().calculateExtent(map.getSize()));
    }
  };

  var show = function(map) {
    roadLayer.deactivateSelection();
    selectedControl = 'Select';
    startListening();
    assetLayer.setVisible(true);
    registerRoadLinkFetched();
    roadAddressInfoPopup.start();
    me.show(map);
  };

  var showWithComplementary = function() {
    massTransitStopsCollection.activeComplementary(true);
    registerRoadLinkFetched();
  };

  var hideComplementary = function (){
    massTransitStopsCollection.activeComplementary(false);
    registerRoadLinkFetched();
    selectedMassTransitStopModel.close();
    selectControl.clear();
  };

  var hideLayer = function() {
    roadLayer.activateSelection();
    selectedMassTransitStopModel.close();
    selectControl.clear();
    assetLayer.setVisible(false);
    stopListening();
    roadAddressInfoPopup.stop();
    me.stop();
    me.hide();
  };

  var refreshSelectedView = function(){
    if(applicationModel.getSelectedLayer() == layerName)
      me.refreshView();
  };

  return {
    show: show,
    hide: hideLayer
  };
};
