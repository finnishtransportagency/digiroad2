window.MassTransitStopLayer = function(map, roadCollection, mapOverlay, assetGrouping, roadLayer) {
  Layer.call(this, 'massTransitStop', roadLayer);
  var me = this;
  me.minZoomForContent = zoomlevels.minZoomForAssets;
  var eventListener = _.extend({running: false}, eventbus);
  var selectedAsset;
  //var assetDirectionLayer = new OpenLayers.Layer.Vector('assetDirection');
  //var assetLayer = new OpenLayers.Layer.Boxes('massTransitStop');
  var movementPermissionConfirmed = false;
  var requestingMovePermission  = false;
  var isComplementaryActiveBS = false;
  var massTransitStopLayerStyles = MassTransitStopLayerStyles(roadLayer);

  var selectedControl = 'Select';

  var assetSource = new ol.source.Vector();
  var assetLayer = new ol.layer.Vector({
    source : assetSource,
    style : function(feature) {
      var properties = feature.getProperties();
      if(properties.massTransitStop)
        return properties.massTransitStop.getMarkerDefaultStyles();
      return [];
    }
  });

  assetLayer.setOpacity(1);
  assetLayer.setVisible(true);
  map.addLayer(assetLayer);

  function onSelectMassTransitStop(event) {
    if(event.selected.length > 0){
      _.each(event.selected, function(feature){
        //TODO maybe it should be the open
        if(!feature.getProperties().data || !feature.getProperties().data.nationalId)
          return;

        //if(applicationModel.isReadOnly()){
          selectedMassTransitStopModel.change(feature.getProperties().data);
        //}else{
        //  selectedMassTransitStopModel.place(feature.getProperties().asset, selectedMassTransitStopModel.getCurrentAsset());
        //}

        movementPermissionConfirmed = false;
      });
      toggleMode();
    }
    else {
      if(event.deselected.length > 0) {
        selectedMassTransitStopModel.close();
      }
    }
  }

  var selectControl = new SelectAndDragToolControl(applicationModel, assetLayer, map, {
    style : function (feature) {
      var properties = feature.getProperties();
      if(properties.massTransitStop)
        return properties.massTransitStop.getMarkerSelectionStyles();
      return [];
    },
    onSelect: onSelectMassTransitStop,
    draggable : false
  });

  this.selectControl = selectControl;

  var dragControl = defineOpenLayersDragControl();
  //TODO since this exact code is beeing used on point and masstransit stop we can create a reusable class
  function defineOpenLayersDragControl() {

    var dragControl = new ol.interaction.Translate({
      features : selectControl.getSelectInteraction().getFeatures()
    });

    var translateSelectedAsset = function(event) {
      //if (feature.massTransitStop.getMarker()) {
      //var busStopCenter = new OpenLayers.Pixel(pxPosition.x, pxPosition.y);
      //var lonlat = map.getLonLatFromPixel(busStopCenter);
      //var busStopPoint = new OpenLayers.LonLat(selectedAsset.data.originalLon, selectedAsset.data.originalLat);
      var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(),event.coordinate[0], event.coordinate[1]);
      var angle = geometrycalculator.getLineDirectionDegAngle(nearestLine);
      var position = geometrycalculator.nearestPointOnLine(
          nearestLine,
          { x: event.coordinate[0], y: event.coordinate[1]});

      //TODO restrictMovement

      restrictMovement(event, {lon: selectedAsset.data.originalLon, lat: selectedAsset.data.originalLat}, angle, nearestLine, {lon: position.x, lat: position.y});
      //doMovement(event, angle, nearestLine, {lon: position.x, lat: position.y}, false);
      //}
    };

    dragControl.on('translating', translateSelectedAsset);
    dragControl.on('translateend', function(){ roadLayer.clearSelection(); });

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

  //selectControl.activate();

  //TODO belive this can be removed
  var clickTimestamp;
  var clickCoords;
  var assetIsMoving = false;

  roadLayer.setLayerSpecificStyleProvider('massTransitStop', function() {
    return massTransitStopLayerStyles;
  });

  //var hideAsset = function(asset) {
  //  //assetDirectionLayer.destroyFeatures(asset.massTransitStop.getDirectionArrow());
  //  //asset.massTransitStop.getMarker().display(false);
  //};
  //
  //var showAsset = function(asset) {
  //  //asset.massTransitStop.getMarker().display(true);
  //  //assetDirectionLayer.addFeatures(asset.massTransitStop.getDirectionArrow());
  //};

  //var mouseDown = function(asset) {
  //  return function(evt) {
  //    var commenceAssetDragging = function() {
  //      clickTimestamp = new Date().getTime();
  //      clickCoords = [evt.clientX, evt.clientY];
  //      //OpenLayers.Event.stop(evt);
  //      selectedAsset = asset;
  //      registerMouseUpHandler(asset);
  //      setInitialClickOffsetFromMarkerBottomLeft(evt.clientX, evt.clientY);
  //    };
  //
  //    if (selectedControl === 'Select') {
  //      if (selectedMassTransitStopModel.getId() === asset.data.id) {
  //        commenceAssetDragging();
  //      }
  //    }
  //  };
  //};

  //var createMouseClickHandler = function(asset) {
  //  return function() {
  //    var selectAsset = function() {
  //      selectedMassTransitStopModel.change(asset.data);
  //      movementPermissionConfirmed = false;
  //    };
  //
  //    if (selectedControl === 'Select') {
  //      if (selectedMassTransitStopModel.getId() !== asset.data.id) {
  //        if (selectedMassTransitStopModel.isDirty()) {
  //          new Confirm();
  //        } else {
  //          selectAsset();
  //        }
  //      }
  //    }
  //  };
  //};

  //var setInitialClickOffsetFromMarkerBottomLeft = function(mouseX, mouseY) {
  //  var markerPosition = $(selectedAsset.massTransitStop.getMarker().div).offset();
  //  initialClickOffsetFromMarkerBottomleft.x = mouseX - markerPosition.left;
  //  initialClickOffsetFromMarkerBottomleft.y = mouseY - markerPosition.top;
  //};

  var registerMouseUpHandler = function(asset) {
    var mouseUpFn = mouseUp(asset);
    //asset.mouseUpHandler = mouseUpFn;
    //map.events.register('mouseup', map, mouseUpFn, true);
  };

  var unregisterMouseUpHandler = function(asset) {
    //map.events.unregister('mouseup', map, asset.mouseUpHandler);
    //asset.mouseUpHandler = null;
  };

  //var registerMouseDownHandler = function(asset) {
  //  var mouseDownFn = mouseDown(asset);
  //  asset.mouseDownHandler = mouseDownFn;
  //  asset.massTransitStop.getMarker().events.register('mousedown', assetLayer, mouseDownFn);
  //};
  //
  //var unregisterMouseDownHandler = function(asset) {
  //  asset.massTransitStop.getMarker().events.unregister('mousedown', assetLayer, asset.mouseDownHandler);
  //  asset.mouseDownHandler = null;
  //};

  var createAsset = function(assetData) {
    var massTransitStop = new MassTransitStop(assetData, map);
    var marker = massTransitStop.getMarker();
    //assetDirectionLayer.addFeatures(massTransitStop.getDirectionArrow(true));
    //var marker = massTransitStop.getMarker(true);
    var asset = {};
    asset.data = assetData;
    asset.massTransitStop = massTransitStop;
    //var mouseClickHandler = createMouseClickHandler(asset);
    //asset.mouseClickHandler = mouseClickHandler;
    //marker.events.register('click', assetLayer, mouseClickHandler);
    //{ massTransitStopStyles: marker.styles, asset: assetData }
    marker.feature.setProperties(asset);
    assetSource.addFeature(marker.feature);
    return asset;
  };

  //var setAssetVisibilityByValidityPeriod = function(asset) {
  //  if (massTransitStopsCollection.selectedValidityPeriodsContain(asset.data.validityPeriod)) {
  //    showAsset(asset);
  //  } else {
  //    hideAsset(asset);
  //  }
  //};

  //var addAssetToLayers = function(asset) {
  //  //assetLayer.addMarker(asset.massTransitStop.getMarker());
  //  //assetDirectionLayer.addFeatures(asset.massTransitStop.getDirectionArrow());
  //};

  //var addAssetToLayersAndSetVisibility = function(asset) {
  //  addAssetToLayers(asset);
  //  //setAssetVisibilityByValidityPeriod(asset);
  //};

  var removeAssetFromMap = function(asset) {
    //assetDirectionLayer.removeFeatures(asset.massTransitStop.getDirectionArrow());
    var feature = asset.massTransitStop.getMarkerFeature();
    assetSource.removeFeature(feature);
    //assetLayer.removeMarker(marker);
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
    _.each(assetDatas, function(assetGroup) {

      assetGroup = _.sortBy(assetGroup, 'id');
      var centroidLonLat = geometrycalculator.getCentroid(assetGroup);
      _.each(assetGroup, function(asset) {
        var uiAsset = convertBackendAssetToUIAsset(asset, centroidLonLat, assetGroup);
        if (!massTransitStopsCollection.getAsset(uiAsset.id)) {
          var assetInModel = createAsset(uiAsset);
          massTransitStopsCollection.insertAsset(assetInModel, uiAsset.id);
          //addAssetToLayers(assetInModel);
        }
        //setAssetVisibilityByValidityPeriod(massTransitStopsCollection.getAsset(uiAsset.id));
        //if (isSelected(uiAsset)) {
        //  selectedAsset = massTransitStopsCollection.getAsset(uiAsset.id);
        //  selectedAsset.massTransitStop.select();
        //  registerMouseDownHandler(selectedAsset);
        //}
      });

    });
  };

  var cancelCreate = function() {
    removeOverlay();
    selectControl.clear();
    removeAssetFromMap(selectedAsset);
  };

  var cancelUpdate = function(asset) {
    selectControl.clear();
    deselectAsset(selectedAsset);
    destroyAsset(asset);
    addNewAsset(asset);
    selectedAsset = regroupAssetIfNearOtherAssets(asset);
    roadLayer.clearSelection();
    //registerMouseDownHandler(selectedAsset);
    //selectedAsset.massTransitStop.select();
  };

  var updateAsset = function(asset) {
    removeAssetFromMap(selectedAsset);
    selectedAsset = addNewAsset(asset);
  };

  var handleValidityPeriodChanged = function() {
    //_.each(massTransitStopsCollection.getAssets(), function(asset) {
    //  if (massTransitStopsCollection.selectedValidityPeriodsContain(asset.data.validityPeriod) && zoomlevels.isInAssetZoomLevel(map.getView().getZoom())) {
    //    showAsset(asset);
    //  } else {
    //    hideAsset(asset);
    //  }
    //});
    if (selectedAsset && selectedAsset.data.validityPeriod === undefined) {
      return;
    }

    if (selectedAsset && !massTransitStopsCollection.selectedValidityPeriodsContain(selectedAsset.data.validityPeriod)) {
      closeAsset();
    }
  };

  function redrawGroup(group) {
    var groupAssets = group.assetGroup;
    _.each(groupAssets, function(asset) {
      var uiAsset = massTransitStopsCollection.getAsset(asset.id);
      //uiAsset.massTransitStop.rePlaceInGroup();
    });
  }
//TODO don't understand when this is needed
  var addAssetToGroup = function(asset, group) {
    var assetGroup = _.sortBy(group.assetGroup.concat([asset.data]), 'id');
    _.each(assetGroup, function(asset) {
      asset.group.assetGroup = assetGroup;
    });
  };

  function createAndGroupUIAsset(backendAsset) {
    var uiAsset;
    var assetToGroupWith = assetGrouping.findNearestAssetWithinGroupingDistance(_.values(massTransitStopsCollection.getAssets()), backendAsset);
    if (assetToGroupWith) {
      uiAsset = createAsset(convertBackendAssetToUIAsset(backendAsset, assetToGroupWith.data.group, assetToGroupWith.data.group.assetGroup));
      massTransitStopsCollection.insertAsset(uiAsset, uiAsset.data.id);
      addAssetToGroup(uiAsset, assetToGroupWith.data.group);
      redrawGroup(assetToGroupWith.data.group);
    } else {
      var group = createDummyGroup(backendAsset.lon, backendAsset.lat, backendAsset);
      uiAsset = createAsset(convertBackendAssetToUIAsset(backendAsset, group, group.assetGroup));
      massTransitStopsCollection.insertAsset(uiAsset, uiAsset.data.id);
    }
    return uiAsset;
  }

  var handleAssetCreated = function(asset) {
    removeAssetFromMap(selectedAsset);
    deselectAsset(selectedAsset);

    var uiAsset = createAndGroupUIAsset(asset);
    //addAssetToLayersAndSetVisibility(uiAsset);

    selectedAsset = uiAsset;
    //selectedAsset.massTransitStop.select();
    //registerMouseDownHandler(selectedAsset);

    removeOverlay();
  };

  var handleAssetSaved = function(asset, positionUpdated) {
    _.merge(massTransitStopsCollection.getAsset(asset.id).data, asset);
    if (positionUpdated) {
      redrawGroup(selectedAsset.data.group);
      destroyAsset(asset);
      deselectAsset(selectedAsset);

      var uiAsset = createAndGroupUIAsset(asset);
      //addAssetToLayersAndSetVisibility(uiAsset);

      selectedAsset = uiAsset;
      //selectedAsset.massTransitStop.select();
      //registerMouseDownHandler(selectedAsset);
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

    if (groupContainingSavedAsset.length > 1) {
      massTransitStopsCollection.destroyGroup(assetIds);
    }

    return massTransitStopsCollection.getAsset(asset.id);
  };

  var reRenderGroup = function(destroyedAssets) {
    _.each(destroyedAssets, removeAssetFromMap);
    renderAssets([parseAssetDataFromAssetsWithMetadata(destroyedAssets)]);
  };

  var handleAssetPropertyValueChanged = function(propertyData) {
    var turnArrow = function(asset, direction) {
      //assetDirectionLayer.destroyFeatures(asset.massTransitStop.getDirectionArrow());
      asset.massTransitStop.getDirectionArrow().style.rotation = direction;
      //assetDirectionLayer.addFeatures(asset.massTransitStop.getDirectionArrow());
    };

    if (propertyData.propertyData.publicId === 'vaikutussuunta') {
      var validityDirection = propertyData.propertyData.values[0].propertyValue;
      selectedAsset.data.validityDirection = validityDirection;
      turnArrow(selectedAsset, validitydirections.calculateRotation(selectedAsset.data.bearing, validityDirection));
    }
  };

  var createNewAsset = function(coordinate, placement) {
    var default_asset_direction = {BothDirections: 2, TowardsDigitizing: 2, AgainstDigitizing: 3};
    //var selectedLon = coordinate.x;
    //var selectedLat = coordinate.y;
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
      stopTypes: []
    };
    data.group = createDummyGroup(projectionOnNearestLine.x, projectionOnNearestLine.y, data);
    var massTransitStop = new MassTransitStop(data);
    var currentAsset = selectedMassTransitStopModel.getCurrentAsset();
    deselectAsset();
    //selectedAsset = {directionArrow: massTransitStop.getDirectionArrow(true),
    //  data: data,
    //  massTransitStop: massTransitStop};
    //selectedAsset.data.stopTypes = [];
    if(placement){
      selectedMassTransitStopModel.place(data, currentAsset);
    }else {
      selectedMassTransitStopModel.place(data);
    }

    selectedAsset = createAsset(data);
    //onCreationFeature = marker.massTransitStop.getMarkerFeature();
    //selectControl.addSelectionFeatures([feature], false, false);
    //assetDirectionLayer.addFeatures(selectedAsset.massTransitStop.getDirectionArrow());
    //assetLayer.addMarker(selectedAsset.massTransitStop.createNewMarker());

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
    //addAssetToLayersAndSetVisibility(massTransitStopsCollection.getAsset(asset.id));
    return uiAsset;
  };

  var createDummyGroup = function(lon, lat, asset) {
    return {lon: lon, lat: lat, assetGroup: [asset]};
  };

  var closeAsset = function() {
    deselectAsset(selectedAsset);
    eventbus.trigger('application:controledTR',false);
  };

  var hideAssets = function() {
    //assetDirectionLayer.removeAllFeatures();
    //assetLayer.setVisibility(false);
  };

  var destroyAsset = function(backendAsset) {
    var uiAsset = massTransitStopsCollection.getAsset(backendAsset.id);
    if(uiAsset) {
      removeAssetFromMap(uiAsset);
      massTransitStopsCollection.destroyAsset(backendAsset.id);
    }
  };

  var deselectAsset = function(asset) {
    if (asset) {
      movementPermissionConfirmed = false;
      //unregisterMouseDownHandler(asset);
      //asset.massTransitStop.deselect();
      //selectedAsset = null;
    }
  };

  var handleAssetFetched = function(backendAsset) {
    deselectAsset(selectedAsset);
    selectedAsset = massTransitStopsCollection.getAsset(backendAsset.id);
    //registerMouseDownHandler(selectedAsset);
    //selectedAsset.massTransitStop.select();
  };

  //var moveSelectedAsset = function(feature) {
  //  //if (feature.massTransitStop.getMarker()) {
  //    //var busStopCenter = new OpenLayers.Pixel(pxPosition.x, pxPosition.y);
  //    //var lonlat = map.getLonLatFromPixel(busStopCenter);
  //    var busStopPoint = new OpenLayers.LonLat(selectedAsset.data.originalLon, selectedAsset.data.originalLat);
  //    var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(),feature.coordinate[0], feature.coordinate[1]);
  //    var angle = geometrycalculator.getLineDirectionDegAngle(nearestLine);
  //    var position = geometrycalculator.nearestPointOnLine(
  //      nearestLine,
  //      { x: feature.coordinate[0], y: feature.coordinate[1]});
  //
  //
  //    doMovement(angle, nearestLine, {lon: position.x, lat: position.y}, false);
  // //}
  //};

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

  var mouseUpHandler = function(asset) {
    clickTimestamp = null;
    unregisterMouseUpHandler(asset);
    if (assetIsMoving) {

      var busStopPoint = new OpenLayers.LonLat(selectedAsset.data.originalLon, selectedAsset.data.originalLat);
      var lonlat = {
        lon: selectedMassTransitStopModel.get('lon'),
        lat: selectedMassTransitStopModel.get('lat')
      };
      var angle = selectedMassTransitStopModel.get('bearing');
      var nearestLine = {
        roadLinkId: selectedMassTransitStopModel.get('roadLinkId'),
        linkId: selectedMassTransitStopModel.get('linkId')
      };

      restrictMovement(busStopPoint, lonlat, angle, nearestLine, lonlat);

      assetIsMoving = false;
    }
  };

  var mouseUp = function(asset) {
    return function(evt) {
      //OpenLayers.Event.stop(evt);
      mouseUpHandler(asset);
    };
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

      new GenericConfirmPopup(popupMessageToShow,{
        successCallback: function(){
          doMovement(event, angle, nearestLine, coordinates, true);
          movementPermissionConfirmed = true;
          requestingMovePermission = false;
        },
        closeCallback: function(){
          //Moves the stop to the original position
          var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), originalCoordinates.lon, originalCoordinates.lat);
          var angle = geometrycalculator.getLineDirectionDegAngle(nearestLine);
          doMovement(event, angle, nearestLine, originalCoordinates, false);
          roadLayer.clearSelection();
          movementPermissionConfirmed = false;
          requestingMovePermission = false;
        }
      });
    }
    else
    {
      doMovement(event, angle, nearestLine, coordinates, true);
    }
  };

  var doMovement= function(event, angle, nearestLine, coordinates, disableMarkerMovement) {
    var feature = event.features.getArray()[0];
    var properties = feature.getProperties();

    properties.data.bearing = angle;
    properties.data.roadDirection = angle;
    //TODO do this is very importante otherwise the arrow will not change
    //selectedAsset.massTransitStop.getDirectionArrow().style.rotation = validitydirections.calculateRotation(angle, selectedAsset.data.validityDirection);
    //TODO change the roadlink
    //selectedAsset.roadLinkId = nearestLine.roadLinkId;
    properties.data.lon = coordinates.lon;
    properties.data.lat = coordinates.lat;

    //if (!disableMarkerMovement){
      roadLayer.selectRoadLink(nearestLine);
      feature.getGeometry().setCoordinates([coordinates.lon, coordinates.lat]);
    //}

    selectedMassTransitStopModel.move({
      lon: coordinates.lon,
      lat: coordinates.lat,
      bearing: angle,
      roadLinkId: nearestLine.roadLinkId,
      linkId: nearestLine.linkId
    });

  };

  //var moveMarker = function(lonlat) {
  //
  //  selectedAsset.massTransitStop.moveTo(lonlat);
  //  assetLayer.redraw();
  //};

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

  //var renderNewGroups = function(uiAssetGroups) {
  //  _.each(uiAssetGroups, function(uiAssetGroup) {
  //    _.each(uiAssetGroup, addAssetToLayersAndSetVisibility);
  //  });
  //};

  var handleNewAssetsFetched = function(newBackendAssets) {
    var backendAssetGroups = assetGrouping.groupByDistance(newBackendAssets, map.getView().getZoom());
    var uiAssetGroups = createNewUIAssets(backendAssetGroups);
    addNewGroupsToModel(uiAssetGroups);
    renderNewGroups(uiAssetGroups);
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
    if (zoomlevels.isInAssetZoomLevel(map.getView().getZoom())) {
      updateAllAssets(assets);
    }
  }
  //
  //var handleMouseMoved = function(event) {
  //  if (applicationModel.isReadOnly() || !selectedAsset || !zoomlevels.isInRoadLinkZoomLevel(map.getView().getZoom())) {
  //    return;
  //  }
  //  if (clickTimestamp && (new Date().getTime() - clickTimestamp) >= applicationModel.assetDragDelay &&
  //    (clickCoords && approximately(clickCoords[0], event.clientX) && approximately(clickCoords[1], event.clientY)) || assetIsMoving) {
  //    assetIsMoving = true;
  //    var xAdjustedForClickOffset = event.xy.x - initialClickOffsetFromMarkerBottomleft.x;
  //    var yAdjustedForClickOffset = event.xy.y - initialClickOffsetFromMarkerBottomleft.y;
  //    var pixel = new OpenLayers.Pixel(xAdjustedForClickOffset, yAdjustedForClickOffset);
  //    moveSelectedAsset(pixel);
  //  }
  //};

  var approximately = function(n, m) {
    var threshold = 10;
    return threshold >= Math.abs(n - m);
  };

  var initialClickOffsetFromMarkerBottomleft = { x: 0, y: 0 };
  var handleMapClick = function(coordinates) {
    if (selectedControl === 'Add' && zoomlevels.isInRoadLinkZoomLevel(map.getView().getZoom())) {
      //var pixel = new OpenLayers.Pixel(coordinates.x, coordinates.y);
      selectControl.deactivate();
      createNewAsset(coordinates, false);
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

  $('#mapdiv').on('mouseleave', function() {
    if (assetIsMoving === true) {
      mouseUpHandler(selectedAsset);
    }
  });

  this.refreshView = function() {
    var extent = map.getView().calculateExtent(map.getSize());

    eventbus.once('roadLinks:fetched', function () {
      roadLayer.drawRoadLinks(roadCollection.getAll(), map.getView().getZoom());
    });

    //TODO check better the hasZoomLevelChanged so it can be optimized
    massTransitStopsCollection.refreshAssets({ bbox: extent, hasZoomLevelChanged: true });

    if (isComplementaryActiveBS) {
      roadCollection.fetchWithComplementary(extent);
    } else {
      roadCollection.fetch(extent);
    }
  };

  //var handleMapMoved = function(mapMoveEvent) {
  //  if (zoomlevels.isInAssetZoomLevel(mapMoveEvent.zoom)) {
  //    if (mapMoveEvent.selectedLayer === 'massTransitStop') {
  //      eventbus.once('roadLinks:fetched', function () {
  //        roadLayer.drawRoadLinks(roadCollection.getAll(), map.getView().getZoom());
  //        massTransitStopsCollection.refreshAssets(mapMoveEvent);
  //      });
  //      if (isComplementaryActiveBS) {
  //        roadCollection.fetchWithComplementary( map.getView().calculateExtent(map.getSize()));
  //      } else {
  //        roadCollection.fetch( map.getView().calculateExtent(map.getSize()));
  //      }
  //    }
  //  } else {
  //    if (applicationModel.getSelectedLayer() === 'massTransitStop') {
  //      hideAssets();
  //    }
  //  }
  //};

  function toggleMode() {
    if(applicationModel.isReadOnly()){
      dragControl.deactivate();
    } else {
      dragControl.activate();
    }
  }

  var bindEvents = function() {
    eventListener.listenTo(eventbus, 'validityPeriod:changed', handleValidityPeriodChanged);
    eventListener.listenTo(eventbus, 'tool:changed', toolSelectionChange);
    eventListener.listenTo(eventbus, 'assetPropertyValue:saved', updateAsset);
    eventListener.listenTo(eventbus, 'assetPropertyValue:changed', handleAssetPropertyValueChanged);
    eventListener.listenTo(eventbus, 'asset:saved', handleAssetSaved);
    eventListener.listenTo(eventbus, 'asset:created', handleAssetCreated);
    eventListener.listenTo(eventbus, 'asset:fetched', handleAssetFetched);
    //eventListener.listenTo(eventbus, 'asset:created', removeOverlay);
    eventListener.listenTo(eventbus, 'asset:creationCancelled asset:creationFailed asset:creationTierekisteriFailed asset:creationNotFoundRoadAddressVKM', cancelCreate);
    eventListener.listenTo(eventbus, 'asset:updateCancelled asset:updateFailed asset:updateTierekisteriFailed asset:updateNotFoundRoadAddressVKM', cancelUpdate);
    eventListener.listenTo(eventbus, 'asset:closed', closeAsset);
    eventListener.listenTo(eventbus, 'assets:fetched', function(assets) {
      if (zoomlevels.isInAssetZoomLevel(map.getView().getZoom())) {
        var groupedAssets = assetGrouping.groupByDistance(assets, map.getView().getZoom());
        renderAssets(groupedAssets);
      }
    });

    eventListener.listenTo(eventbus, 'assets:all-updated', handleAllAssetsUpdated);
    eventListener.listenTo(eventbus, 'assets:new-fetched', handleNewAssetsFetched);
    eventListener.listenTo(eventbus, 'assetGroup:destroyed', reRenderGroup);
    //eventListener.listenTo(eventbus, 'map:mouseMoved', handleMouseMoved);
    eventListener.listenTo(eventbus, 'map:moved', me.handleMapMoved);
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
    eventListener.listenTo(eventbus, 'roadLinkComplementaryBS:hide', show);
    //eventListener.listenTo(eventbus, 'road-type:selected', roadLayer.toggleRoadTypeWithSpecifiedStyle);

    eventListener.listenTo(eventbus, 'application:readOnly', toggleMode);
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
        roadLayer.drawRoadLinks(roadCollection.getAll(), map.getView().getZoom());
        massTransitStopsCollection.fetchAssets( map.getView().calculateExtent(map.getSize()));
      });
      roadCollection.fetchWithComplementary( map.getView().calculateExtent(map.getSize()));
    }
  };

  var show = function() {
    roadLayer.deactivateSelection();
    selectedControl = 'Select';
    startListening();
    //map.addLayer(assetDirectionLayer);
    assetLayer.setVisible(true);
    registerRoadLinkFetched();
    isComplementaryActiveBS = false;
  };

  var showWithComplementary = function() {
    isComplementaryActiveBS = true;
    registerRoadLinkFetched();
  };

  var hideLayer = function() {
    roadLayer.activateSelection();
    selectedMassTransitStopModel.close();
    assetLayer.setVisible(false);
    /*
    if (assetLayer.map && assetDirectionLayer.map) {
      map.removeLayer(assetLayer);
      map.removeLayer(assetDirectionLayer);
    }
    */
    stopListening();
    me.hide();
  };

  return {
    show: show,
    hide: hideLayer
  };
};
