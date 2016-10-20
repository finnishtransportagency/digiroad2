window.MassTransitStopLayer = function(map, roadCollection, mapOverlay, assetGrouping, roadLayer) {
  Layer.call(this, 'massTransitStop', roadLayer);
  var me = this;
  var eventListener = _.extend({running: false}, eventbus);
  var selectedAsset;
  var assetDirectionLayer = new OpenLayers.Layer.Vector('assetDirection');
  var assetLayer = new OpenLayers.Layer.Boxes('massTransitStop');
  var movementPermissionConfirmed = false;

  var selectedControl = 'Select';

  var clickTimestamp;
  var clickCoords;
  var assetIsMoving = false;

  var hideAsset = function(asset) {
    assetDirectionLayer.destroyFeatures(asset.massTransitStop.getDirectionArrow());
    asset.massTransitStop.getMarker().display(false);
  };

  var showAsset = function(asset) {
    asset.massTransitStop.getMarker().display(true);
    assetDirectionLayer.addFeatures(asset.massTransitStop.getDirectionArrow());
  };

  var mouseUpHandler = function(asset) {
    clickTimestamp = null;
    unregisterMouseUpHandler(asset);
    if (assetIsMoving) {
      selectedMassTransitStopModel.move({
        lon: selectedMassTransitStopModel.get('lon'),
        lat: selectedMassTransitStopModel.get('lat'),
        bearing: selectedMassTransitStopModel.get('bearing'),
        roadLinkId: selectedMassTransitStopModel.get('roadLinkId'),
        linkId: selectedMassTransitStopModel.get('linkId')
      });
      eventbus.trigger('asset:moveCompleted');
      assetIsMoving = false;
    }
  };

  var mouseUp = function(asset) {
    return function(evt) {
      OpenLayers.Event.stop(evt);
      mouseUpHandler(asset);
    };
  };

  var mouseDown = function(asset) {
    return function(evt) {
      var commenceAssetDragging = function() {
        clickTimestamp = new Date().getTime();
        clickCoords = [evt.clientX, evt.clientY];
        OpenLayers.Event.stop(evt);
        selectedAsset = asset;
        registerMouseUpHandler(asset);
        setInitialClickOffsetFromMarkerBottomLeft(evt.clientX, evt.clientY);
      };

      if (selectedControl === 'Select') {
        if (selectedMassTransitStopModel.getId() === asset.data.id) {
          commenceAssetDragging();
        }
      }
    };
  };

  var createMouseClickHandler = function(asset) {
    return function() {
      var selectAsset = function() {
        selectedMassTransitStopModel.change(asset.data);
        movementPermissionConfirmed = false;
      };

      if (selectedControl === 'Select') {
        if (selectedMassTransitStopModel.getId() !== asset.data.id) {
          if (selectedMassTransitStopModel.isDirty()) {
            new Confirm();
          } else {
            selectAsset();
          }
        }
      }
    };
  };

  var setInitialClickOffsetFromMarkerBottomLeft = function(mouseX, mouseY) {
    var markerPosition = $(selectedAsset.massTransitStop.getMarker().div).offset();
    initialClickOffsetFromMarkerBottomleft.x = mouseX - markerPosition.left;
    initialClickOffsetFromMarkerBottomleft.y = mouseY - markerPosition.top;
  };

  var registerMouseUpHandler = function(asset) {
    var mouseUpFn = mouseUp(asset);
    asset.mouseUpHandler = mouseUpFn;
    map.events.register('mouseup', map, mouseUpFn, true);
  };

  var unregisterMouseUpHandler = function(asset) {
    map.events.unregister('mouseup', map, asset.mouseUpHandler);
    asset.mouseUpHandler = null;
  };

  var registerMouseDownHandler = function(asset) {
    var mouseDownFn = mouseDown(asset);
    asset.mouseDownHandler = mouseDownFn;
    asset.massTransitStop.getMarker().events.register('mousedown', assetLayer, mouseDownFn);
  };

  var unregisterMouseDownHandler = function(asset) {
    asset.massTransitStop.getMarker().events.unregister('mousedown', assetLayer, asset.mouseDownHandler);
    asset.mouseDownHandler = null;
  };

  var createAsset = function(assetData) {
    var massTransitStop = new MassTransitStop(assetData, map);
    assetDirectionLayer.addFeatures(massTransitStop.getDirectionArrow(true));
    var marker = massTransitStop.getMarker(true);
    var asset = {};
    asset.data = assetData;
    asset.massTransitStop = massTransitStop;
    var mouseClickHandler = createMouseClickHandler(asset);
    asset.mouseClickHandler = mouseClickHandler;
    marker.events.register('click', assetLayer, mouseClickHandler);
    return asset;
  };

  var setAssetVisibilityByValidityPeriod = function(asset) {
    if (massTransitStopsCollection.selectedValidityPeriodsContain(asset.data.validityPeriod)) {
      showAsset(asset);
    } else {
      hideAsset(asset);
    }
  };

  var addAssetToLayers = function(asset) {
    assetLayer.addMarker(asset.massTransitStop.getMarker());
    assetDirectionLayer.addFeatures(asset.massTransitStop.getDirectionArrow());
  };

  var addAssetToLayersAndSetVisibility = function(asset) {
    addAssetToLayers(asset);
    setAssetVisibilityByValidityPeriod(asset);
  };

  var removeAssetFromMap = function(asset) {
    assetDirectionLayer.removeFeatures(asset.massTransitStop.getDirectionArrow());
    var marker = asset.massTransitStop.getMarker();
    assetLayer.removeMarker(marker);
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
    assetLayer.setVisibility(true);
    _.each(assetDatas, function(assetGroup) {
      assetGroup = _.sortBy(assetGroup, 'id');
      var centroidLonLat = geometrycalculator.getCentroid(assetGroup);
      _.each(assetGroup, function(asset) {
        var uiAsset = convertBackendAssetToUIAsset(asset, centroidLonLat, assetGroup);
        if (!massTransitStopsCollection.getAsset(uiAsset.id)) {
          var assetInModel = createAsset(uiAsset);
          massTransitStopsCollection.insertAsset(assetInModel, uiAsset.id);
          addAssetToLayers(assetInModel);
        }
        setAssetVisibilityByValidityPeriod(massTransitStopsCollection.getAsset(uiAsset.id));
        if (isSelected(uiAsset)) {
          selectedAsset = massTransitStopsCollection.getAsset(uiAsset.id);
          selectedAsset.massTransitStop.select();
          registerMouseDownHandler(selectedAsset);
        }
      });
    });
  };

  var cancelCreate = function() {
    removeOverlay();
    removeAssetFromMap(selectedAsset);
  };

  var cancelUpdate = function(asset) {
    deselectAsset(selectedAsset);
    destroyAsset(asset);
    addNewAsset(asset);
    selectedAsset = regroupAssetIfNearOtherAssets(asset);
    registerMouseDownHandler(selectedAsset);
    selectedAsset.massTransitStop.select();
  };

  var updateAsset = function(asset) {
    removeAssetFromMap(selectedAsset);
    selectedAsset = addNewAsset(asset);
  };

  var handleValidityPeriodChanged = function() {
    _.each(massTransitStopsCollection.getAssets(), function(asset) {
      if (massTransitStopsCollection.selectedValidityPeriodsContain(asset.data.validityPeriod) && zoomlevels.isInAssetZoomLevel(map.getZoom())) {
        showAsset(asset);
      } else {
        hideAsset(asset);
      }
    });
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
      uiAsset.massTransitStop.rePlaceInGroup();
    });
  }

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
    addAssetToLayersAndSetVisibility(uiAsset);

    selectedAsset = uiAsset;
    selectedAsset.massTransitStop.select();
    registerMouseDownHandler(selectedAsset);
  };

  var handleAssetSaved = function(asset, positionUpdated) {
    _.merge(massTransitStopsCollection.getAsset(asset.id).data, asset);
    if (positionUpdated) {
      redrawGroup(selectedAsset.data.group);
      destroyAsset(asset);
      deselectAsset(selectedAsset);

      var uiAsset = createAndGroupUIAsset(asset);
      addAssetToLayersAndSetVisibility(uiAsset);

      selectedAsset = uiAsset;
      selectedAsset.massTransitStop.select();
      registerMouseDownHandler(selectedAsset);
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
    var regroupedAssets = assetGrouping.groupByDistance(parseAssetDataFromAssetsWithMetadata(massTransitStopsCollection.getAssets()), map.getZoom());
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
      assetDirectionLayer.destroyFeatures(asset.massTransitStop.getDirectionArrow());
      asset.massTransitStop.getDirectionArrow().style.rotation = direction;
      assetDirectionLayer.addFeatures(asset.massTransitStop.getDirectionArrow());
    };

    if (propertyData.propertyData.publicId === 'vaikutussuunta') {
      var validityDirection = propertyData.propertyData.values[0].propertyValue;
      selectedAsset.data.validityDirection = validityDirection;
      turnArrow(selectedAsset, validitydirections.calculateRotation(selectedAsset.data.bearing, validityDirection));
    }
  };

  var createNewAsset = function(lonlat, placement) {
    var selectedLon = lonlat.lon;
    var selectedLat = lonlat.lat;
    var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), selectedLon, selectedLat);
    var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, { x: selectedLon, y: selectedLat });
    var bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
    var data = {
      bearing: bearing,
      validityDirection: validitydirections.sameDirection,
      lon: projectionOnNearestLine.x,
      lat: projectionOnNearestLine.y,
      roadLinkId: nearestLine.roadLinkId,
      linkId: nearestLine.linkId
    };
    data.group = createDummyGroup(projectionOnNearestLine.x, projectionOnNearestLine.y, data);
    var massTransitStop = new MassTransitStop(data);
    var currentAsset = selectedMassTransitStopModel.getCurrentAsset();
    deselectAsset(selectedAsset);
    selectedAsset = {directionArrow: massTransitStop.getDirectionArrow(true),
      data: data,
      massTransitStop: massTransitStop};
    selectedAsset.data.stopTypes = [];
    if(placement){
      selectedMassTransitStopModel.place(selectedAsset.data, currentAsset);
    }else {
      selectedMassTransitStopModel.place(selectedAsset.data);
    }
    assetDirectionLayer.addFeatures(selectedAsset.massTransitStop.getDirectionArrow());
    assetLayer.addMarker(selectedAsset.massTransitStop.createNewMarker());

    var applyBlockingOverlays = function() {
      mapOverlay.show();
    };
    applyBlockingOverlays();
  };

  var removeOverlay = function() {
    mapOverlay.hide();
  };

  var addNewAsset = function(asset) {
    asset.group = createDummyGroup(asset.lon, asset.lat, asset);
    var uiAsset = createAsset(asset);
    massTransitStopsCollection.insertAsset(uiAsset, asset.id);
    addAssetToLayersAndSetVisibility(massTransitStopsCollection.getAsset(asset.id));
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
    assetDirectionLayer.removeAllFeatures();
    assetLayer.setVisibility(false);
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
      unregisterMouseDownHandler(asset);
      asset.massTransitStop.deselect();
      selectedAsset = null;
    }
  };

  var handleAssetFetched = function(backendAsset) {
    deselectAsset(selectedAsset);
    selectedAsset = massTransitStopsCollection.getAsset(backendAsset.id);
    registerMouseDownHandler(selectedAsset);
    selectedAsset.massTransitStop.select();
  };

  var moveSelectedAsset = function(pxPosition) {
    if (selectedAsset.massTransitStop.getMarker()) {
      var busStopCenter = new OpenLayers.Pixel(pxPosition.x, pxPosition.y);
      var lonlat = map.getLonLatFromPixel(busStopCenter);
      var busStopPoint = new OpenLayers.LonLat(selectedAsset.data.originalLon, selectedAsset.data.originalLat);
      var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), lonlat.lon, lonlat.lat);
      var angle = geometrycalculator.getLineDirectionDegAngle(nearestLine);
      var position = geometrycalculator.nearestPointOnLine(
        nearestLine,
        { x: lonlat.lon, y: lonlat.lat});
      lonlat.lon = position.x;
      lonlat.lat = position.y;
      restrictMovement(busStopPoint, lonlat, angle, nearestLine,lonlat);

   }
  };

  var restrictMovement = function (busStop, currentPoint, angle, nearestLine, coordinates) {
    var movementLimit = 50; //50 meters
    var popupMessageToShow;
    //The method geometrycalculator.getSquaredDistanceBetweenPoints() will return the distance in Meters so we multiply the result for this
    var distance = Math.sqrt(geometrycalculator.getSquaredDistanceBetweenPoints(busStop, currentPoint));

    if (distance > movementLimit && !movementPermissionConfirmed)
    {
      if (controlledByTR()){
        popupMessageToShow = 'Pysäkkiä siirretty yli 50 metriä. Siirron yhteydessä vanha pysäkki lakkautetaan ja luodaan uusi pysäkki.';
      } else {
        popupMessageToShow = 'Pysäkkiä siirretty yli 50 metriä. Haluatko siirtää pysäkin uuteen sijaintiin?';
      }

      new GenericConfirmPopup(popupMessageToShow,{
        successCallback: function(){
          doMovement(angle, nearestLine, coordinates, true);
          movementPermissionConfirmed = true;
        },
        closeCallback: function(){
          //Moves the stop to the original position
          doMovement(angle, nearestLine, busStop, false);
          movementPermissionConfirmed = false;
        }
      });
    }
    else
    {
      doMovement(angle, nearestLine, coordinates, false);
    }
  };

  var doMovement= function(angle, nearestLine, coordinates, disableMovement) {
    roadLayer.selectRoadLink(nearestLine);

    if (!disableMovement){
      selectedAsset.data.bearing = angle;
      selectedAsset.data.roadDirection = angle;
      selectedAsset.massTransitStop.getDirectionArrow().style.rotation = validitydirections.calculateRotation(angle, selectedAsset.data.validityDirection);
      selectedAsset.roadLinkId = nearestLine.roadLinkId;
      selectedAsset.data.lon = coordinates.lon;
      selectedAsset.data.lat = coordinates.lat;
      moveMarker(coordinates);
      selectedMassTransitStopModel.move({
        lon: coordinates.lon,
        lat: coordinates.lat,
        bearing: angle,
        roadLinkId: nearestLine.roadLinkId,
        linkId: nearestLine.linkId
      });
    }
  };

  var moveMarker = function(lonlat) {
    selectedAsset.massTransitStop.moveTo(lonlat);
    assetLayer.redraw();
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

  var renderNewGroups = function(uiAssetGroups) {
    _.each(uiAssetGroups, function(uiAssetGroup) {
      _.each(uiAssetGroup, addAssetToLayersAndSetVisibility);
    });
  };

  var handleNewAssetsFetched = function(newBackendAssets) {
    var backendAssetGroups = assetGrouping.groupByDistance(newBackendAssets, map.getZoom());
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
    var groupedAssets = assetGrouping.groupByDistance(assetsWithSelectedAsset, map.getZoom());
    renderAssets(groupedAssets);
  };

  function handleAllAssetsUpdated(assets) {
    if (zoomlevels.isInAssetZoomLevel(map.getZoom())) {
      updateAllAssets(assets);
    }
  }

  var handleMouseMoved = function(event) {
    if (applicationModel.isReadOnly() || !selectedAsset || !zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
      return;
    }
    if (clickTimestamp && (new Date().getTime() - clickTimestamp) >= applicationModel.assetDragDelay &&
      (clickCoords && approximately(clickCoords[0], event.clientX) && approximately(clickCoords[1], event.clientY)) || assetIsMoving) {
      assetIsMoving = true;
      var xAdjustedForClickOffset = event.xy.x - initialClickOffsetFromMarkerBottomleft.x;
      var yAdjustedForClickOffset = event.xy.y - initialClickOffsetFromMarkerBottomleft.y;
      var pixel = new OpenLayers.Pixel(xAdjustedForClickOffset, yAdjustedForClickOffset);
      moveSelectedAsset(pixel);
    }
  };

  var approximately = function(n, m) {
    var threshold = 10;
    return threshold >= Math.abs(n - m);
  };

  var initialClickOffsetFromMarkerBottomleft = { x: 0, y: 0 };
  var handleMapClick = function(coordinates) {
    if (selectedControl === 'Add' && zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
      var pixel = new OpenLayers.Pixel(coordinates.x, coordinates.y);
      createNewAsset(map.getLonLatFromPixel(pixel), false);
    } else {
      if (selectedMassTransitStopModel.isDirty()) {
        new Confirm();
      } else {
        selectedMassTransitStopModel.close();
      }
    }
  };

  $('#mapdiv').on('mouseleave', function() {
    if (assetIsMoving === true) {
      mouseUpHandler(selectedAsset);
    }
  });

  var handleMapMoved = function(mapMoveEvent) {
    if (zoomlevels.isInAssetZoomLevel(mapMoveEvent.zoom)) {
      if (mapMoveEvent.selectedLayer === 'massTransitStop') {
        eventbus.once('roadLinks:fetched', function() {
          roadLayer.drawRoadLinks(roadCollection.getAll(), map.getZoom());
          massTransitStopsCollection.refreshAssets(mapMoveEvent);
        });
        roadCollection.fetch(map.getExtent());
      }
    } else {
      if (applicationModel.getSelectedLayer() === 'massTransitStop') {
        hideAssets();
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
    eventListener.listenTo(eventbus, 'asset:creationCancelled asset:creationFailed asset:creationTierekisteriFailed', cancelCreate);
    eventListener.listenTo(eventbus, 'asset:updateCancelled asset:updateFailed asset:updateTierekisteriFailed', cancelUpdate);
    eventListener.listenTo(eventbus, 'asset:closed', closeAsset);
    eventListener.listenTo(eventbus, 'assets:fetched', function(assets) {
      if (zoomlevels.isInAssetZoomLevel(map.getZoom())) {
        var groupedAssets = assetGrouping.groupByDistance(assets, map.getZoom());
        renderAssets(groupedAssets);
      }
    });

    eventListener.listenTo(eventbus, 'assets:all-updated', handleAllAssetsUpdated);
    eventListener.listenTo(eventbus, 'assets:new-fetched', handleNewAssetsFetched);
    eventListener.listenTo(eventbus, 'assetGroup:destroyed', reRenderGroup);
    eventListener.listenTo(eventbus, 'map:mouseMoved', handleMouseMoved);
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

  var show = function() {
    selectedControl = 'Select';
    startListening();
    map.addLayer(assetDirectionLayer);
    map.addLayer(assetLayer);
    if (zoomlevels.isInAssetZoomLevel(map.getZoom())) {
      eventbus.once('roadLinks:fetched', function() {
        roadLayer.drawRoadLinks(roadCollection.getAll(), map.getZoom());
        massTransitStopsCollection.fetchAssets(map.getExtent());
      });
      roadCollection.fetch(map.getExtent());
    }
  };

  var hideLayer = function() {
    selectedMassTransitStopModel.close();
    if (assetLayer.map && assetDirectionLayer.map) {
      map.removeLayer(assetLayer);
      map.removeLayer(assetDirectionLayer);
    }
    stopListening();
    me.hide();
  };

  return {
    show: show,
    hide: hideLayer
  };
};
