window.AssetLayer = function(map, roadLayer) {
  var selectedValidityPeriods = ['current'];

  var selectedAsset;
  var backend = Backend;
  var readOnly = true;
  var assetDirectionLayer = new OpenLayers.Layer.Vector('assetDirection');
  var assetLayer = new OpenLayers.Layer.Boxes('asset');

  map.addLayer(assetDirectionLayer);
  map.addLayer(assetLayer);

  var assets = {};
  var overlay;
  var selectedControl = 'Select';
  var assetMoveWaitTime = 10;

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

  var mouseUpFunction;

  var mouseUpHandler = function() {
    clickTimestamp = null;
    map.events.unregister("mouseup", map, mouseUpFunction);
    assetIsMoving = false;
  };

  var mouseUp = function(asset) {
    return function(evt) {
      OpenLayers.Event.stop(evt);
      mouseUpHandler(asset);
    };
  };

  var mouseDown = function(asset, mouseUpFn, mouseClickFn) {
    return function(evt) {
      if (selectedControl === 'Select') {
        var changeSuccess = true;
        if (selectedAssetModel.getId() !== asset.data.id) {
          changeSuccess = selectedAssetModel.change(asset.data);
        }
        if (!changeSuccess) {
          new Confirm();
        } else {
          clickTimestamp = new Date().getTime();
          clickCoords = [evt.clientX, evt.clientY];
          OpenLayers.Event.stop(evt);
          selectedAsset = asset;
          // Mouse need to be down until can be moved
          selectedAsset.massTransitStop.getMarker().actionMouseDown = true;
          //Save original position
          selectedAsset.massTransitStop.getMarker().actionDownX = evt.clientX;
          selectedAsset.massTransitStop.getMarker().actionDownY = evt.clientY;
          //register up
          map.events.register("mouseup", map, mouseUpFn, true);
          mouseUpFunction = mouseUpFn;
          mouseClickFn(asset);
        }
      }
    };
  };

  var mouseClick = function(asset) {
    return function(evt) {
      OpenLayers.Event.stop(evt);
      window.location.hash = '#/asset/' + asset.data.externalId + '?keepPosition=true';
    };
  };

  var insertAsset = function(assetData) {
    var massTransitStop = new MassTransitStop(assetData, map);
    assetDirectionLayer.addFeatures(massTransitStop.getDirectionArrow(true));
    // new bus stop marker
    var marker = massTransitStop.getMarker(true);
    var asset = {};
    asset.data = assetData;
    asset.massTransitStop = massTransitStop;
    var mouseClickFn = mouseClick(asset);
    var mouseUpFn = mouseUp(asset);
    var mouseDownFn = mouseDown(asset, mouseUpFn, mouseClickFn);
    marker.events.register("mousedown", assetLayer, mouseDownFn);
    marker.events.register("click", assetLayer, OpenLayers.Event.stop);
    return asset;
  };

  var addAssetToLayers = function(asset) {
    assetLayer.addMarker(asset.massTransitStop.getMarker());
    assetDirectionLayer.addFeatures(asset.massTransitStop.getDirectionArrow());
    if (!_.contains(selectedValidityPeriods, asset.data.validityPeriod)) {
      hideAsset(asset);
    }
    else {
      showAsset(asset);
    }
  };

  var removeAssetFromMap = function(asset) {
    assetDirectionLayer.removeFeatures(asset.massTransitStop.getDirectionArrow());
    var marker = asset.massTransitStop.getMarker();
    assetLayer.removeMarker(marker);
  };
  var groupId = 0;
  var renderAssets = function(assetDatas) {
    assetLayer.setVisibility(true);
    _.each(assetDatas, function(assetGroup) {
      groupId++;
      var i = 0;
      if (!_.isArray(assetGroup)) {
        assetGroup = [assetGroup];
      }
      var centroidLonLat = geometrycalculator.getCentroid(assetGroup);
      _.each(assetGroup, function(asset) {
        asset.group = {
          id: groupId,
          lon: centroidLonLat.lon,
          lat: centroidLonLat.lat,
          groupIndex: i++,
          size: assetGroup.length,
          assetGroup: assetGroup
        };

        var isAssetSelectedAndDirty = function(asset) {
          return (selectedAsset && selectedAsset.data.id === asset.id) && selectedAssetModel.isDirty();
        };
        if (isAssetSelectedAndDirty(asset)) {
          return;
        }
        assets = assets || {};
        if (!assets[asset.id]) {
          assets[asset.id] = insertAsset(asset);
        }
        addAssetToLayers(assets[asset.id]);
        if (selectedAsset && selectedAsset.data.id == asset.id) {
          selectedAsset = assets[asset.id];
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
    addNewAsset(asset);
  };

  var handleValidityPeriodChanged = function(selection) {
    selectedValidityPeriods = selection;
    _.each(assets, function(asset) {
      if (_.contains(selection, asset.data.validityPeriod) && zoomlevels.isInAssetZoomLevel(map.getZoom())) {
        showAsset(asset);
      } else {
        hideAsset(asset);
      }
    });
    if (selectedAsset && selectedAsset.data.validityPeriod === undefined) {
      return;
    }

    if (selectedAsset && !_.contains(selectedValidityPeriods, selectedAsset.data.validityPeriod)) {
      closeAsset();
    }
  };

  var handleAssetCreated = function(asset) {
    removeAssetFromMap(selectedAsset);
    addNewAsset(asset);
    eventbus.trigger('asset:selected', asset);
  };

  var handleAssetSaved = function(asset) {
    selectedAsset.data = asset;
    assets[asset.id] = selectedAsset;
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
    data.group = createDummyGroup(projectionOnNearestLine.x, projectionOnNearestLine.y);
    var massTransitStop = new MassTransitStop(data);
    selectedAsset = {directionArrow: massTransitStop.getDirectionArrow(true),
      data: data,
      massTransitStop: massTransitStop};
    assetDirectionLayer.addFeatures(selectedAsset.massTransitStop.getDirectionArrow());
    selectedAsset.data.imageIds = [];
    assetLayer.addMarker(selectedAsset.massTransitStop.createNewMarker());
    eventbus.trigger('asset:placed', selectedAsset.data);

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
    asset.group = createDummyGroup(asset.lon, asset.lat);
    selectedAsset = insertAsset(asset);
    assets[asset.id] = selectedAsset;
    addAssetToLayers(assets[asset.id]);
  };

  var createDummyGroup = function(lon, lat) {
    return {id: groupId++, lon: lon, lat: lat, groupIndex: 0, size: 1};
  };

  var closeAsset = function() {
    selectedAsset = null;
  };

  var removeAssetsFromLayer = function() {
    assetDirectionLayer.removeAllFeatures();
    assetLayer.setVisibility(false);
  };

  var handleAssetFetched = function(assetData, keepPosition) {
    if (assets[assetData.id]) {
      assets[assetData.id].data = assetData;
      selectedAsset = assets[assetData.id];
      selectedAsset.massTransitStop.getDirectionArrow().style.rotation = assetData.bearing + (90 * (selectedAsset.data.validityDirection == 3 ? 1 : -1 ));
    } else {
      addNewAsset(assetData);
      eventbus.trigger('asset:selected', assetData);
    }
    if (!keepPosition) {
      eventbus.trigger('coordinates:selected', { lat: selectedAsset.data.lat, lon: selectedAsset.data.lon });
    }
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
      var groupedAssets = window.groupBusStops ? assetGrouping.groupByDistance(assets) : assets;
      renderAssets(groupedAssets);
    }
  }, this);
  eventbus.on('map:moved', function(state) {
    if (!zoomlevels.isInAssetZoomLevel(state.zoom) && selectedAssetModel.isDirty()) {
      new Confirm();
    } else if (zoomlevels.isInAssetZoomLevel(state.zoom) && assetLayer.map && assetDirectionLayer.map) {
      backend.getAssets(10, state.bbox);
    } else {
      removeAssetsFromLayer();
    }
  }, this);

  var approximately = function(n, m) {
    var threshold = 10;
    return threshold >= Math.abs(n - m);
  };

  var events = map.events;
  var initialClickOffsetFromBubbleBottomLeft = { x: 0, y: 0 };
  events.register('mousemove', map, function(e) {
    if (readOnly || !selectedAsset || !zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
      return;
    }
    if (clickTimestamp && (new Date().getTime() - clickTimestamp) > assetMoveWaitTime &&
      (clickCoords && approximately(clickCoords[0], e.clientX) && approximately(clickCoords[1], e.clientY)) || assetIsMoving) {
      assetIsMoving = true;
      var xAdjustedForClickOffset = e.xy.x - initialClickOffsetFromBubbleBottomLeft.x;
      var yAdjustedForClickOffset = e.xy.y - initialClickOffsetFromBubbleBottomLeft.y;
      var pixel = new OpenLayers.Pixel(xAdjustedForClickOffset, yAdjustedForClickOffset);
      moveSelectedAsset(pixel);
    } else {
      var bubbleDiv = $(selectedAsset.massTransitStop.getMarker().div).children();
      var bubblePosition = bubbleDiv.offset();
      initialClickOffsetFromBubbleBottomLeft.x = e.clientX - bubblePosition.left;
      initialClickOffsetFromBubbleBottomLeft.y = e.clientY - (bubblePosition.top + bubbleDiv.height());
    }
  }, true);


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

    onClick: function(e) {
      if (selectedControl === 'Add' && zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
        var pixel = new OpenLayers.Pixel(e.xy.x, e.xy.y);
        createNewAsset(map.getLonLatFromPixel(pixel));
      } else {
        if (selectedAssetModel.isDirty()) {
          new Confirm();
        } else {
          selectedAssetModel.close();
          window.location.hash = '';
        }
      }
    }
  });
  var click = new Click();
  map.addControl(click);
  click.activate();

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
  eventbus.on('asset:new-state-rendered', function(lonlat) {
    assetLayer.redraw();
    selectedAsset.massTransitStop.getDirectionArrow().move(lonlat);
  });
};
