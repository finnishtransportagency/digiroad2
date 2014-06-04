var MassTransitStop = function(data) {
    var unknownAssetType = '99';
    var cachedMarker = null;
    var cachedDirectionArrow = null;
    
    var createIcon = function() {
        var createIconImages = function(imageIds) {
            var callout = document.createElement("div");
            callout.className = "callout";
            var arrowContainer = document.createElement("div");
            arrowContainer.className = "arrow-container";
            var arrow = document.createElement("div");
            arrow.className = "arrow";
            _.each(imageIds, function (imageId) {
                var img = document.createElement("img");
                img.setAttribute("src", "api/images/" + imageId + ".png");
                callout.appendChild(img);
            });
            arrowContainer.appendChild(arrow);
            callout.appendChild(arrowContainer);
            var dropHandle = document.createElement("div");
            dropHandle.className="dropHandle";
            callout.appendChild(dropHandle);
            return callout;
        };

        var size;
        var imageIds = data.imageIds.length > 0 ? data.imageIds : [unknownAssetType + '_'];
        if (imageIds.length > 1) {
            size = new OpenLayers.Size(28, ((15 * imageIds.length) + (imageIds.length - 1)));
        } else {
            size = new OpenLayers.Size(28, 16);
        }
        var offset = new OpenLayers.Pixel(0, -size.h-9);
        var icon = new OpenLayers.Icon("", size, offset);
        icon.imageDiv.className = "callout-wrapper";
        icon.imageDiv.removeChild(icon.imageDiv.getElementsByTagName("img")[0]);
        icon.imageDiv.setAttribute("style", "");
        icon.imageDiv.appendChild(createIconImages(imageIds));
        return icon;
    };

    var createMarker = function() {
        return new OpenLayers.Marker(new OpenLayers.LonLat(data.lon, data.lat), createIcon());
    };

    var createDirectionArrow = function() {
        var getAngleFromBearing = function(bearing, validityDirection) {
            if (bearing === null || bearing === undefined) {
              console.log('Bearing was null, find out why');
              return 90;
            }
            return bearing + (90 * validityDirection);
        };
        var validityDirection = (data.validityDirection === 3) ? 1 : -1;
        var angle = getAngleFromBearing(data.bearing, validityDirection);
        return new OpenLayers.Feature.Vector(
            new OpenLayers.Geometry.Point(data.lon, data.lat),
            null,
            {
                externalGraphic: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow.svg',
                graphicHeight: 16, graphicWidth: 30, graphicXOffset:-15, graphicYOffset:-8, rotation: angle
            }
        );
    };
    
    var getMarker = function(shouldCreate) {
        if (shouldCreate || !cachedMarker) {
            cachedMarker = createMarker();
        }
        return cachedMarker;
    }
    
    var getDirectionArrow = function(shouldCreate) {
        if (shouldCreate || !cachedDirectionArrow) {
          cachedDirectionArrow = createDirectionArrow();
        }
        return cachedDirectionArrow;
    }

    return {
        getMarker: getMarker,
        getDirectionArrow: getDirectionArrow
    };
};

window.AssetLayer = function(map, roadLayer) {
    var selectedValidityPeriods = ['current'];

    var selectedAsset;
    var backend = Backend;
    var readOnly = true;
    var assetDirectionLayer = new OpenLayers.Layer.Vector('assetDirection');
    var assetLayer = new OpenLayers.Layer.Markers('asset');

    map.addLayer(assetDirectionLayer);
    map.addLayer(assetLayer);

    var assets = {};
    var overlay;
    var selectedControl = 'Select';
    var assetMoveWaitTime = 300;

    var clickTimestamp;
    var clickCoords;
    var assetIsMoving = false;

    var hideAsset = function(asset) {
        assetDirectionLayer.destroyFeatures(asset.directionArrow);
        asset.marker.display(false);
    };

    var showAsset = function(asset) {
        asset.marker.display(true);
        assetDirectionLayer.addFeatures(asset.directionArrow);
    };

    var mouseUpFunction;

    var mouseUpHandler = function(asset, x, y) {
        clickTimestamp = null;
        // Opacity back
        asset.marker.setOpacity(1);
        asset.marker.actionMouseDown = false;
        // Not need listeners anymore
        map.events.unregister("mouseup", map, mouseUpFunction);
        // Moved update
        if (!readOnly && assetIsMoving && (asset.marker.actionDownX != x ||  asset.marker.actionDownY != y)) {
            eventbus.trigger('asset:moved', {
                lon: asset.marker.lonlat.lon,
                lat: asset.marker.lonlat.lat,
                bearing: asset.data.bearing,
                roadLinkId: asset.roadLinkId
            });
        }
        assetIsMoving = false;
    };

    var mouseUp = function(asset) {
        return function(evt) {
            OpenLayers.Event.stop(evt);
            mouseUpHandler(asset, evt.clientX, evt.clientY);
        };
    };

    var mouseDown = function(asset, mouseUpFn, mouseClickFn) {
        return function(evt) {
            if (selectedControl === 'Select') {
                var anotherAssetHasBeenModified = function() {
                    return (selectedAsset && selectedAsset.data.id !== asset.data.id && selectedAssetController.isDirty());
                };

                if (anotherAssetHasBeenModified()) {
                    new Confirm();
                } else {
                    clickTimestamp = new Date().getTime();
                    clickCoords = [evt.clientX, evt.clientY];
                    OpenLayers.Event.stop(evt);
                    if (selectedAsset && selectedAsset.data.id !== asset.data.id) {
                        eventbus.trigger('asset:unselected', selectedAsset.data.id);
                    }
                    selectedAsset = asset;
                    // push marker up
                    assetLayer.removeMarker(asset.marker);
                    assetLayer.addMarker(asset.marker);
                    // Opacity because we want know what is moving
                    asset.marker.setOpacity(0.6);
                    // Mouse need to be down until can be moved
                    asset.marker.actionMouseDown = true;
                    //Save original position
                    asset.marker.actionDownX = evt.clientX;
                    asset.marker.actionDownY = evt.clientY;
                    //register up
                    map.events.register("mouseup", map, mouseUpFn, true);
                    mouseUpFunction = mouseUpFn;
                    mouseClickFn(asset);
                }
            }
        };
    };

    var mouseClick = function(asset) {
        return function (evt) {
            OpenLayers.Event.stop(evt);
            window.location.hash = '#/asset/' + asset.data.externalId + '?keepPosition=true';
        };
    };

    var insertAsset = function(assetData) {
        var massTransitStop = new MassTransitStop(assetData);
        var directionArrow = massTransitStop.getDirectionArrow(true);
        assetDirectionLayer.addFeatures(directionArrow);
        // new bus stop marker
        var marker = massTransitStop.getMarker(true);
        var asset = {};
        asset.marker = marker;
        asset.data = assetData;
        asset.directionArrow = directionArrow;
        asset.massTransitStop = massTransitStop;
        var mouseClickFn = mouseClick(asset);
        var mouseUpFn = mouseUp(asset);
        var mouseDownFn = mouseDown(asset, mouseUpFn, mouseClickFn);
        marker.events.register("mousedown", assetLayer, mouseDownFn);
        if (!_.contains(selectedValidityPeriods, assetData.validityPeriod)) {
            hideAsset(asset);
        }
        assetLayer.addMarker(marker);
        return asset;
    };

    var removeAssetFromMap = function(asset) {
        assetDirectionLayer.removeFeatures(asset.directionArrow);
        assetLayer.removeMarker(asset.marker);
    };

    var renderAssets = function(assetDatas) {
        assetLayer.setVisibility(true);
        _.each(assetDatas, function(asset) {
            var isAssetSelectedAndDirty = function(asset) {
              return (selectedAsset && selectedAsset.data.id === asset.id) && selectedAssetController.isDirty();
            };
            if (isAssetSelectedAndDirty(asset)) {
              return;
            }
            assets = assets || {};
            if (assets[asset.id]) {
              removeAssetFromMap(assets[asset.id]);
            }
            assets[asset.id] = insertAsset(asset);
            if (selectedAsset && selectedAsset.data.id == asset.id) {
              selectedAsset = assets[asset.id];
              highlightAsset(selectedAsset);
            }
        });

    };

    var cancelCreate = function() {
        removeOverlay();
        removeAssetFromMap(selectedAsset);
    };

    var updateAsset = function(asset) {
        removeAssetFromMap(selectedAsset);
        addNewAsset(asset);
    };

    var handleValidityPeriodChanged = function(selection) {
        selectedValidityPeriods = selection;
        _.each(assets, function(asset) {
            if (_.contains(selection, asset.data.validityPeriod)) {
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
    };

    var handleAssetSaved = function(asset) {
        selectedAsset.data = asset;
        assets[asset.id] = selectedAsset;
    };

    var handleAssetFetched = function(assetData, keepPosition) {
        if (assets[assetData.id]) {
            removeAssetFromMap(assets[assetData.id]);
            assets[assetData.id].data = assetData;
            selectedAsset = assets[assetData.id];
            highlightAsset(selectedAsset);
        }
        addNewAsset(assetData);
        if (!keepPosition) {
            eventbus.trigger('coordinates:selected', { lat: selectedAsset.data.lat, lon: selectedAsset.data.lon });
        }
    };

    var handleAssetPropertyValueChanged = function(propertyData) {
        var turnArrow = function(asset, direction) {
            assetDirectionLayer.destroyFeatures(asset.directionArrow);
            asset.directionArrow.style.rotation = direction;
            assetDirectionLayer.addFeatures(asset.directionArrow);
        };

        if (propertyData.propertyData.publicId === 'vaikutussuunta') {
            var value = propertyData.propertyData.values[0].propertyValue;
            selectedAsset.data.validityDirection = value;
            var validityDirection = (value == 3) ? 1 : -1;
            turnArrow(selectedAsset, selectedAsset.data.bearing + (90 * validityDirection));
        } else if (propertyData.propertyData.publicId === 'pysakin_tyyppi'  ) {

            var values = _.pluck(propertyData.propertyData.values, 'propertyValue');
            selectedAsset.data.imageIds = _.map(values, function(v) {
                return v + '_';
            });
            assetLayer.removeMarker(selectedAsset.marker);
            selectedAsset.marker = selectedAsset.massTransitStop.getMarker(true);
            assetLayer.addMarker(selectedAsset.marker);
            var mouseClickFn = mouseClick(selectedAsset);
            var mouseUpFn = mouseUp(selectedAsset);
            var mouseDownFn = mouseDown(selectedAsset, mouseUpFn, mouseClickFn);
            selectedAsset.marker.events.register('mousedown', assetLayer, mouseDownFn);
            assetLayer.redraw();
        }
    };

    var createNewAsset = function(lonlat) {
        if (selectedAsset) {
            eventbus.trigger('asset:unselected', selectedAsset.data.id);
        }
        var selectedLon = lonlat.lon;
        var selectedLat = lonlat.lat;
        var features = roadLayer.features;
        var nearestLine = geometrycalculator.findNearestLine(features, selectedLon, selectedLat);
        var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, { x: selectedLon, y: selectedLat });
        var projectionLonLat = {
            lon: projectionOnNearestLine.x,
            lat: projectionOnNearestLine.y
        };
        var bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
        var data = {bearing: bearing,
            position: assetPosition,
            validityDirection: 2,
            lon: projectionOnNearestLine.x,
            lat: projectionOnNearestLine.y,
            roadLinkId: nearestLine.roadLinkId};
        var massTransitStop = new MassTransitStop(data);
        selectedAsset = {directionArrow: massTransitStop.getDirectionArrow(true),
            data: data,
            massTransitStop: massTransitStop};
        assetDirectionLayer.addFeatures(selectedAsset.directionArrow);
        var assetPosition = { lonLat: projectionLonLat, bearing: bearing, validityDirection: 2 };
        highlightAsset(selectedAsset);
        selectedAsset.data.imageIds = [];
        var marker = selectedAsset.massTransitStop.getMarker(true);
        assetLayer.addMarker(marker);
        selectedAsset.marker = marker;
        eventbus.trigger('asset:placed', selectedAsset.data);

        var applyBlockingOverlays = function() {
            var overlay = Oskari.clazz.create('Oskari.userinterface.component.Overlay');
            overlay.overlay('#contentMap,#maptools');
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
        var lonLat = { lon : asset.lon, lat : asset.lat};
        selectedAsset = insertAsset(asset);
        assets[asset.id] = selectedAsset;
        asset.position = {
            lonLat: lonLat,
            heading: asset.bearing + 90
        };
        highlightAsset(selectedAsset);
    };

    var closeAsset = function() {
        if (selectedAsset) {
            unhighlightAsset(selectedAsset);
        }
        selectedAsset = null;
    };

    var unhighlightAsset = function(asset) {
        var arrow = asset.directionArrow;
        arrow.style.backgroundGraphic = null;
        arrow.style.backgroundHeight = null;
        arrow.style.backgroundWidth = null;
        assetDirectionLayer.redraw();
    };

    var removeAssetsFromLayer = function() {
        if (selectedAsset) {
            eventbus.trigger('asset:unselected');
        }
        assetDirectionLayer.removeAllFeatures();
        assetLayer.clearMarkers();
    };

    var highlightAsset = function(asset) {
        var arrow = asset.directionArrow;
        arrow.style.backgroundGraphic = 'src/resources/digiroad2/bundle/assetlayer/images/hover.png';
        arrow.style.backgroundHeight = 68;
        arrow.style.backgroundWidth = 68;
        assetDirectionLayer.redraw();
    };

    var moveSelectedAsset = function(pxPosition) {
        if (selectedAsset.marker && selectedAsset.marker.actionMouseDown) {
            //var pxPosition = this._map.getPixelFromLonLat(new OpenLayers.LonLat(lon, lat));
            var busStopCenter = new OpenLayers.Pixel(pxPosition.x,pxPosition.y);
            var lonlat = map.getLonLatFromPixel(busStopCenter);
            var nearestLine = geometrycalculator.findNearestLine(roadLayer.features, lonlat.lon, lonlat.lat);
            eventbus.trigger('asset:moving', nearestLine);
            var angle = geometrycalculator.getLineDirectionDegAngle(nearestLine);
            selectedAsset.data.bearing = angle;
            selectedAsset.data.roadDirection = angle;
            selectedAsset.directionArrow.style.rotation = angle + (90 * (selectedAsset.data.validityDirection == 3 ? 1 : -1 ));
            var position = geometrycalculator.nearestPointOnLine(
                nearestLine,
                { x: lonlat.lon, y: lonlat.lat});
            lonlat.lon = position.x;
            lonlat.lat = position.y;
            selectedAsset.roadLinkId = nearestLine.roadLinkId;
            selectedAsset.marker.lonlat = lonlat;
            selectedAsset.directionArrow.move(lonlat);
            assetLayer.redraw();
        }
    };

    var toolSelectionChange = function(action) {
        selectedControl = action;
        if (selectedAsset) {
            eventbus.trigger('asset:unselected');
        }
    };

    eventbus.on('validityPeriod:changed', handleValidityPeriodChanged, this);
    eventbus.on('asset:selected', function(data) {
        backend.getAsset(data.id);
    }, this);
    eventbus.on('asset:selected', function(data, keepPosition) {
        selectedAsset = assets[data.id];
        highlightAsset(selectedAsset);
        if (!keepPosition) {
            eventbus.trigger('coordinates:selected', { lat: selectedAsset.data.lat, lon: selectedAsset.data.lon });
        }
    }, this);
    eventbus.on('asset:unselected', closeAsset, this);
    eventbus.on('tool:changed', toolSelectionChange, this);
    eventbus.on('assetPropertyValue:saved', updateAsset, this);
    eventbus.on('assetPropertyValue:changed', handleAssetPropertyValueChanged, this);
    eventbus.on('asset:saved', handleAssetSaved, this);
    eventbus.on('asset:created', handleAssetCreated, this);
    eventbus.on('asset:fetched', handleAssetFetched, this);
    eventbus.on('asset:created', removeOverlay, this);
    eventbus.on('asset:cancelled', cancelCreate, this);
    eventbus.on('application:readOnly', function(value) {
        readOnly = value;
    }, this);
    eventbus.on('assets:fetched', function(assets) {
        if (zoomlevels.isInAssetZoomLevel(map.getZoom())) {
            renderAssets(assets);
        }
    }, this);
    eventbus.on('map:moved', function(state) {
      if (!zoomlevels.isInAssetZoomLevel(map.getZoom()) && selectedAssetController.isDirty()) {
        new Confirm();
      } else if (8 < state.zoom && assetLayer.map && assetDirectionLayer.map) {
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
    events.register('mousemove', map, function(e) {
        if (readOnly || !selectedAsset || !zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
            return;
        }
        if (clickTimestamp && (new Date().getTime() - clickTimestamp) > assetMoveWaitTime &&
                (clickCoords && approximately(clickCoords[0], e.clientX) && approximately(clickCoords[1], e.clientY)) || assetIsMoving) {
            assetIsMoving = true;
            var pixel = new OpenLayers.Pixel(e.xy.x, e.xy.y);
            moveSelectedAsset(pixel);
        }
    }, true);

    events.register('click', map, function(e) {
        if (selectedControl === 'Add' && zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
            var pixel = new OpenLayers.Pixel(e.xy.x, e.xy.y);
            createNewAsset(map.getLonLatFromPixel(pixel));
        }
    });

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

    $('#mapdiv').on('mouseleave', function(e) {
        if (assetIsMoving === true) {
            mouseUpHandler(selectedAsset, e.clientX, e.clientY);
        }
    });
};
