window.AssetLayer = function(map, roadLayer) {
  var selectedAsset;
  var backend = Backend;
  var readOnly = true;
  var assetDirectionLayer = new OpenLayers.Layer.Vector('assetDirection');
  var assetLayer = new OpenLayers.Layer.Boxes('asset');

  map.addLayer(assetDirectionLayer);
  map.addLayer(assetLayer);

  var overlay;
  var selectedControl = 'Select';

  var clickTimestamp;
  var clickCoords;
  var assetIsMoving = false;

  var groupId = 0;
  var generateNewGroupId = function() { return groupId++; };

  var hideAsset = function(asset) {
    assetDirectionLayer.destroyFeatures(asset.massTransitStop.getDirectionArrow());
    asset.massTransitStop.getMarker().display(false);
  };

  var showAsset = function(asset) {
    asset.massTransitStop.getMarker().display(true);
    assetDirectionLayer.addFeatures(asset.massTransitStop.getDirectionArrow());
  };

  var mouseUpFunction;

  var mouseUpHandler = function(asset) {
    clickTimestamp = null;
    map.events.unregister("mouseup", map, mouseUpFunction);
    asset.mouseUpHandler = null;
    assetIsMoving = false;
  };

  var mouseUp = function(asset) {
    return function(evt) {
      OpenLayers.Event.stop(evt);
      mouseUpHandler(asset);
    };
  };

  var mouseDown = function(asset, mouseUpFn) {
    return function(evt) {
      var commenceAssetDragging = function() {
        clickTimestamp = new Date().getTime();
        clickCoords = [evt.clientX, evt.clientY];
        OpenLayers.Event.stop(evt);
        selectedAsset = asset;
        selectedAsset.massTransitStop.getMarker().actionMouseDown = true;
        selectedAsset.massTransitStop.getMarker().actionDownX = evt.clientX;
        selectedAsset.massTransitStop.getMarker().actionDownY = evt.clientY;
        map.events.register("mouseup", map, mouseUpFn, true);
        mouseUpFunction = mouseUpFn;
        asset.mouseUpHandler = mouseUpFn;
        setInitialClickOffsetFromMarkerBottomLeft(evt.clientX, evt.clientY);
      };

      var selectAsset = function() {
        OpenLayers.Event.stop(evt);
        selectedAsset = asset;
        window.location.hash = '#/asset/' + asset.data.externalId + '?keepPosition=true';
      };

      if (selectedControl === 'Select') {
        if (selectedAssetModel.getId() === asset.data.id) {
          commenceAssetDragging();
        } else {
          if (selectedAssetModel.isDirty()) {
            new Confirm();
          } else {
            selectedAssetModel.change(asset.data);
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

  var createAsset = function(assetData) {
    var massTransitStop = new MassTransitStop(assetData, map);
    assetDirectionLayer.addFeatures(massTransitStop.getDirectionArrow(true));
    // new bus stop marker
    var marker = massTransitStop.getMarker(true);
    var asset = {};
    asset.data = assetData;
    asset.massTransitStop = massTransitStop;
    var mouseUpFn = mouseUp(asset);
    var mouseDownFn = mouseDown(asset, mouseUpFn);
    asset.mouseDownHandler = mouseDownFn;
    marker.events.register("mousedown", assetLayer, mouseDownFn);
    marker.events.register("click", assetLayer, OpenLayers.Event.stop);
    return asset;
  };

  var addAssetToLayers = function(asset) {
    assetLayer.addMarker(asset.massTransitStop.getMarker());
    assetDirectionLayer.addFeatures(asset.massTransitStop.getDirectionArrow());
    if (AssetsModel.selectedValidityPeriodsContain(asset.data.validityPeriod)) {
      showAsset(asset);
    } else {
      hideAsset(asset);
    }
  };

  var removeAssetFromMap = function(asset) {
    assetDirectionLayer.removeFeatures(asset.massTransitStop.getDirectionArrow());
    var marker = asset.massTransitStop.getMarker();
    assetLayer.removeMarker(marker);
  };

  var isSelected = function(asset) {
    return selectedAsset && selectedAsset.data.id === asset.id;
  };

  var convertBackendAssetToUIAsset = function(backendAsset, centroidLonLat, assetGroup, groupId) {
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
      id: groupId,
      lon: lon,
      lat: lat,
      assetGroup: assetGroup
    };
    return uiAsset;
  };

  var renderAssets = function(assetDatas) {
    assetLayer.setVisibility(true);
    _.each(assetDatas, function(assetGroup) {
      var groupId = generateNewGroupId();
      assetGroup = _.sortBy(assetGroup, 'id');
      var centroidLonLat = geometrycalculator.getCentroid(assetGroup);
      _.each(assetGroup, function(asset) {
        var uiAsset = convertBackendAssetToUIAsset(asset, centroidLonLat, assetGroup, groupId);
        if (!AssetsModel.getAsset(uiAsset.id)) {
          AssetsModel.insertAsset(createAsset(uiAsset), uiAsset.id);
        }
        addAssetToLayers(AssetsModel.getAsset(uiAsset.id));
        if (isSelected(uiAsset)) {
          selectedAsset = AssetsModel.getAsset(uiAsset.id);
          eventbus.trigger('asset:selected', uiAsset);
        }
      });
    });
  };

  var cancelCreate = function() {
    if (selectedControl == 'Add' && selectedAsset.data.id === undefined) {
      removeOverlay();
      removeAssetFromMap(selectedAsset);
    }
  };

  var updateAsset = function(asset) {
    removeAssetFromMap(selectedAsset);
    selectedAsset = addNewAsset(asset);
  };

  var handleValidityPeriodChanged = function() {
    _.each(AssetsModel.getAssets(), function(asset) {
      if (AssetsModel.selectedValidityPeriodsContain(asset.data.validityPeriod) && zoomlevels.isInAssetZoomLevel(map.getZoom())) {
        showAsset(asset);
      } else {
        hideAsset(asset);
      }
    });
    if (selectedAsset && selectedAsset.data.validityPeriod === undefined) {
      return;
    }

    if (selectedAsset && !AssetsModel.selectedValidityPeriodsContain(selectedAsset.data.validityPeriod)) {
      closeAsset();
    }
  };

  var handleAssetCreated = function(asset) {
    removeAssetFromMap(selectedAsset);
    selectedAsset = addNewAsset(asset);
    regroupAssetIfNearOtherAssets(asset);
    eventbus.trigger('asset:selected', asset);
  };

  var handleAssetSaved = function(asset) {
    selectedAsset.data = asset;
    AssetsModel.insertAsset(selectedAsset, asset.id);
    regroupAssetIfNearOtherAssets(asset);
  };

  var parseAssetDataFromAssetsWithMetadata = function(assets) {
    return _.chain(assets)
      .values()
      .pluck('data')
      .map(function(x) { return _.omit(x, 'group'); })
      .value();
  };

  var regroupAssetIfNearOtherAssets = function(asset) {
    var regroupedAssets = assetGrouping.groupByDistance(parseAssetDataFromAssetsWithMetadata(AssetsModel.getAssets()), map.getZoom());
    var groupContainingSavedAsset = _.find(regroupedAssets, function(assetGroup) {
      var assetGroupIds = _.pluck(assetGroup, 'id');
      return _.contains(assetGroupIds, asset.id);
    });
    var assetIds = _.map(groupContainingSavedAsset, function(asset) { return asset.id.toString(); });

    if (groupContainingSavedAsset.length > 1) {
      AssetsModel.destroyGroup(assetIds);
    }
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
      var value = propertyData.propertyData.values[0].propertyValue;
      selectedAsset.data.validityDirection = value;
      var validityDirection = (value == 3) ? 1 : -1;
      turnArrow(selectedAsset, selectedAsset.data.bearing + (90 * validityDirection));
    }
  };

  var createNewAsset = function(lonlat) {
    var selectedLon = lonlat.lon;
    var selectedLat = lonlat.lat;
    var features = roadLayer.features;
    var nearestLine = geometrycalculator.findNearestLine(features, selectedLon, selectedLat);
    var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, { x: selectedLon, y: selectedLat });
    var bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
    var data = {
      bearing: bearing,
      validityDirection: 2,
      lon: projectionOnNearestLine.x,
      lat: projectionOnNearestLine.y,
      roadLinkId: nearestLine.roadLinkId
    };
    data.group = createDummyGroup(projectionOnNearestLine.x, projectionOnNearestLine.y, data);
    var massTransitStop = new MassTransitStop(data);
    selectedAsset = {directionArrow: massTransitStop.getDirectionArrow(true),
      data: data,
      massTransitStop: massTransitStop};
    selectedAsset.data.imageIds = [];
    eventbus.trigger('asset:placed', selectedAsset.data);
    assetDirectionLayer.addFeatures(selectedAsset.massTransitStop.getDirectionArrow());
    assetLayer.addMarker(selectedAsset.massTransitStop.createNewMarker());

    var applyBlockingOverlays = function() {
      var overlay = Oskari.clazz.create('Oskari.userinterface.component.Overlay');
      overlay.overlay('#contentMap,#map-tools');
      overlay.followResizing(true);
      return overlay;
    };
    overlay = applyBlockingOverlays();
  };

  var removeOverlay = function() {
    if (overlay) {
      overlay.close();
    }
  };

  var addNewAsset = function(asset) {
    asset.group = createDummyGroup(asset.lon, asset.lat, asset);
    var uiAsset = createAsset(asset);
    AssetsModel.insertAsset(uiAsset, asset.id);
    addAssetToLayers(AssetsModel.getAsset(asset.id));
    return uiAsset;
  };

  var createDummyGroup = function(lon, lat, asset) {
    return {id: generateNewGroupId(), lon: lon, lat: lat, assetGroup: [asset]};
  };

  var closeAsset = function() {
    selectedAsset = null;
  };

  var hideAssets = function() {
    assetDirectionLayer.removeAllFeatures();
    assetLayer.setVisibility(false);
  };

  var destroyAsset = function(backendAsset) {
    var uiAsset = AssetsModel.getAsset(backendAsset.id);
    if(uiAsset) {
      removeAssetFromMap(uiAsset);
      AssetsModel.destroyAsset(backendAsset.id);
    }
  };

  var deselectAsset = function(asset) {
    if (asset) {
      asset.massTransitStop.deselect();
      selectedAsset = null;
    }
  };

  var handleAssetFetched = function(backendAsset) {
    deselectAsset(selectedAsset);
    destroyAsset(backendAsset);
    var uiAsset = addNewAsset(backendAsset);
    regroupAssetIfNearOtherAssets(backendAsset);
    selectedAsset = uiAsset;
    eventbus.trigger('asset:selected', backendAsset);
  };

  var moveSelectedAsset = function(pxPosition) {
    if (selectedAsset.massTransitStop.getMarker()) {
      var busStopCenter = new OpenLayers.Pixel(pxPosition.x, pxPosition.y);
      var lonlat = map.getLonLatFromPixel(busStopCenter);
      var nearestLine = geometrycalculator.findNearestLine(roadLayer.features, lonlat.lon, lonlat.lat);
      eventbus.trigger('asset:moving', nearestLine);
      var angle = geometrycalculator.getLineDirectionDegAngle(nearestLine);
      selectedAsset.data.bearing = angle;
      selectedAsset.data.roadDirection = angle;
      selectedAsset.massTransitStop.getDirectionArrow().style.rotation = angle + (90 * (selectedAsset.data.validityDirection == 3 ? 1 : -1 ));
      var position = geometrycalculator.nearestPointOnLine(
        nearestLine,
        { x: lonlat.lon, y: lonlat.lat});
      lonlat.lon = position.x;
      lonlat.lat = position.y;
      selectedAsset.roadLinkId = nearestLine.roadLinkId;
      selectedAsset.data.lon = lonlat.lon;
      selectedAsset.data.lat = lonlat.lat;
      moveMarker(lonlat);

      eventbus.trigger('asset:moved', {
        lon: lonlat.lon,
        lat: lonlat.lat,
        bearing: angle,
        roadLinkId: nearestLine.roadLinkId
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
        return createAsset(convertBackendAssetToUIAsset(backendAsset, centroidLonLat, group, generateNewGroupId()));
      });
    });
  };

  var addNewGroupsToModel = function(uiAssetGroups) {
    _.each(uiAssetGroups, AssetsModel.insertAssetsFromGroup);
  };

  var renderNewGroups = function(uiAssetGroups) {
    _.each(uiAssetGroups, function(uiAssetGroup) {
      _.each(uiAssetGroup, addAssetToLayers);
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
    _.each(AssetsModel.getAssets(), removeAssetFromMap);
    AssetsModel.destroyAssets();
    renderAssets(groupedAssets);
  };

  function handleAllAssetsUpdated(assets) {
    if (zoomlevels.isInAssetZoomLevel(map.getZoom())) {
      updateAllAssets(assets);
    }
  }

  var handleMouseMoved = function(event) {
    if (readOnly || !selectedAsset || !zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
      return;
    }
    if (clickTimestamp && (new Date().getTime() - clickTimestamp) > ApplicationModel.assetDragDelay &&
      (clickCoords && approximately(clickCoords[0], event.clientX) && approximately(clickCoords[1], event.clientY)) || assetIsMoving) {
      assetIsMoving = true;
      var xAdjustedForClickOffset = event.xy.x - initialClickOffsetFromMarkerBottomleft.x;
      var yAdjustedForClickOffset = event.xy.y - initialClickOffsetFromMarkerBottomleft.y;
      var pixel = new OpenLayers.Pixel(xAdjustedForClickOffset, yAdjustedForClickOffset);
      moveSelectedAsset(pixel);
    }
  };

  eventbus.on('validityPeriod:changed', handleValidityPeriodChanged, this);
  eventbus.on('tool:changed', toolSelectionChange, this);
  eventbus.on('assetPropertyValue:saved', updateAsset, this);
  eventbus.on('assetPropertyValue:changed', handleAssetPropertyValueChanged, this);
  eventbus.on('asset:saved', handleAssetSaved, this);
  eventbus.on('asset:created', handleAssetCreated, this);
  eventbus.on('asset:fetched', handleAssetFetched, this);
  eventbus.on('asset:created', removeOverlay, this);
  eventbus.on('asset:cancelled', cancelCreate, this);
  eventbus.on('asset:closed', closeAsset, this);
  eventbus.on('application:readOnly', function(value) {
    readOnly = value;
  }, this);
  eventbus.on('assets:fetched', function(assets) {
    if (zoomlevels.isInAssetZoomLevel(map.getZoom())) {
      var groupedAssets = assetGrouping.groupByDistance(assets, map.getZoom());
      renderAssets(groupedAssets);
    }
  }, this);

  eventbus.on('assets:all-updated', handleAllAssetsUpdated, this);
  eventbus.on('assets:new-fetched', handleNewAssetsFetched, this);
  eventbus.on('assetModifications:confirm', function() {
    new Confirm();
  }, this);
  eventbus.on('assets:outOfZoom', hideAssets, this);
  eventbus.on('assetGroup:destroyed', reRenderGroup, this);
  eventbus.on('map:mouseMoved', handleMouseMoved, this);

  var approximately = function(n, m) {
    var threshold = 10;
    return threshold >= Math.abs(n - m);
  };

  var events = map.events;
  var initialClickOffsetFromMarkerBottomleft = { x: 0, y: 0 };
  events.register('mousemove', map, function(event) { eventbus.trigger('map:mouseMoved', event); }, true);

  var Click = OpenLayers.Class(OpenLayers.Control, {
    defaultHandlerOptions: {
      'single': true,
      'double': false,
      'pixelTolerance': 0,
      'stopSingle': false,
      'stopDouble': false
    },

    initialize: function(options) {
      this.handlerOptions = OpenLayers.Util.extend(
        {}, this.defaultHandlerOptions
      );
      OpenLayers.Control.prototype.initialize.apply(
        this, arguments
      );
      this.handler = new OpenLayers.Handler.Click(
        this, {
          'click': this.onClick
        }, this.handlerOptions
      );
    },

    onClick: function(event) {
      eventbus.trigger('map:clicked', {x: event.xy.x, y: event.xy.y});
    }
  });
  var click = new Click();
  map.addControl(click);
  click.activate();

  var handleMapClick = function(coordinates) {
    if (selectedControl === 'Add' && zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
      var pixel = new OpenLayers.Pixel(coordinates.x, coordinates.y);
      createNewAsset(map.getLonLatFromPixel(pixel));
    } else {
      if (selectedAssetModel.isDirty()) {
        new Confirm();
      } else {
        selectedAssetModel.close();
        window.location.hash = '';
      }
    }
  };

  eventbus.on('map:clicked', handleMapClick, this);
  eventbus.on('layer:selected', function(layer) {
    if (layer !== 'asset') {
      if (assetLayer.map && assetDirectionLayer.map) {
        map.removeLayer(assetLayer);
        map.removeLayer(assetDirectionLayer);
      }
    } else {
      map.addLayer(assetDirectionLayer);
      map.addLayer(assetLayer);
      if (zoomlevels.isInAssetZoomLevel(map.getZoom())) {
        backend.getAssets(10, map.getExtent());
      }
    }
  }, this);

  eventbus.on('layer:selected', closeAsset, this);
  $('#mapdiv').on('mouseleave', function() {
    if (assetIsMoving === true) {
      mouseUpHandler(selectedAsset);
    }
  });
};
