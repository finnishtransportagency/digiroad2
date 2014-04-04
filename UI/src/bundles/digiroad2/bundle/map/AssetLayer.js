window.AssetLayer = function(map, roadLayer) {
    var unknownAssetType = '99';

    var selectedValidityPeriods = ['current'];

    var selectedAsset;
    var backend = Backend;
    var readOnly = true;
    var assetDirectionLayer = new OpenLayers.Layer.Vector('assetDirection');
    var assetLayer = new OpenLayers.Layer.Markers('asset');

    map.addLayer(assetDirectionLayer);
    map.addLayer(assetLayer);

    var assets = null;
    var overlay;
    var selectedControl;

    var getDirectionArrow = function(bearing, validityDirection, lon, lat) {
        var getAngleFromBearing = function(bearing, validityDirection) {
            return (bearing) ? bearing + (90 * validityDirection): 90;
        };
        var angle = getAngleFromBearing(bearing, validityDirection);
        return new OpenLayers.Feature.Vector(
            new OpenLayers.Geometry.Point(lon, lat),
            null,
            {externalGraphic: 'src/resources/digiroad2/bundle/assetlayer/images/suuntain.png',
                graphicHeight: 16, graphicWidth: 23, graphicXOffset:-8, graphicYOffset:-8, rotation: angle}
        );
    };

    var getIcon = function(imageIds) {
        var getIconImages = function(imageIds) {
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

        var size = new OpenLayers.Size(28, 16 * imageIds.length);
        var offset = new OpenLayers.Pixel(-(size.w/2+1), -size.h-5);
        var icon = new OpenLayers.Icon("", size, offset);
        icon.imageDiv.className = "callout-wrapper";
        icon.imageDiv.removeChild(icon.imageDiv.getElementsByTagName("img")[0]);
        icon.imageDiv.setAttribute("style", "");
        icon.imageDiv.appendChild(getIconImages(imageIds));
        return icon;
    };

    var hideAsset = function(asset) {
        assetDirectionLayer.destroyFeatures(asset.directionArrow);
        asset.marker.display(false);
    };

    var showAsset = function(asset) {
        asset.marker.display(true);
        assetDirectionLayer.addFeatures(asset.directionArrow);
    };

    var mouseUpFunction;

    var mouseUp = function(asset, mouseClickFn) {
        return function(evt) {
            OpenLayers.Event.stop(evt);
            var bearing = "0";
            if (selectedAsset) {
                bearing = selectedAsset.data.roadDirection;
            }
            // Opacity back
            asset.marker.setOpacity(1);
            asset.marker.actionMouseDown = false;
            // Not need listeners anymore
            map.events.unregister("mouseup", map, mouseUpFunction);
            // Moved update
            if (!readOnly && (asset.marker.actionDownX != evt.clientX ||  asset.marker.actionDownY != evt.clientY)) {
                var data = {
                    assetTypeId: asset.data.assetTypeId,
                    lon: asset.marker.lonlat.lon,
                    lat: asset.marker.lonlat.lat,
                    roadLinkId: asset.roadLinkId,
                    bearing: bearing
                };
                backend.updateAsset(asset.data.id, data);
            }
            var streetViewCoordinates = { lonLat: asset.marker.lonlat };
            mouseClickFn(evt, streetViewCoordinates);
        };
    };

    var mouseDown = function(asset, mouseUpFn) {
        return function (evt) {
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
        };
    };

    var mouseClick = function(asset) {
        return function (evt) {
            OpenLayers.Event.stop(evt);
            window.location.hash = '#/asset/' + asset.data.externalId + '?keepPosition=true';
        };
    };

    var insertAsset = function(assetData) {
        var validityDirection = (assetData.validityDirection === 3) ? 1 : -1;
        var directionArrow = getDirectionArrow(assetData.bearing, validityDirection, assetData.lon, assetData.lat);
        assetDirectionLayer.addFeatures(directionArrow);
        var imageIds = assetData.imageIds;
        var icon = getIcon(imageIds);
        // new bus stop marker
        var marker = new OpenLayers.Marker(new OpenLayers.LonLat(assetData.lon, assetData.lat), icon);
        marker.featureContent = assetData.featureData;
        marker.roadDirection = assetData.bearing;
        marker.effectDirection = validityDirection;
        var asset = {};
        asset.marker = marker;
        asset.data = assetData;
        asset.directionArrow = directionArrow;
        var mouseClickFn = mouseClick(asset);
        var mouseUpFn = mouseUp(asset, mouseClickFn);
        var mouseDownFn = mouseDown(asset, mouseUpFn);
        marker.events.register("mousedown", assetLayer, mouseDownFn);
        marker.validityPeriod = assetData.validityPeriod;
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
            if (!_.contains(_.pluck(assetLayer.markers, "id"), asset.id)) {
                assets = assets || {};
                if (assets[asset.id]) {
                    removeAssetFromMap(assets[asset.id]);
                }
                assets[asset.id] = insertAsset(asset);
                var assetIdFromURL = function() {
                    var matches = window.location.hash.match(/\#\/asset\/(\d+)/);
                    if (matches) {
                        return matches[1];
                    }
                };
                if (selectedAsset && selectedAsset.data.id == asset.id) {
                    selectedAsset = assets[asset.id];
                    highlightAsset(selectedAsset);
                }
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
            assets[assetData.id].data = assetData;
            selectedAsset = assets[assetData.id];
            highlightAsset(selectedAsset);
        } else {
            addNewAsset(assetData);
        }
        if (!keepPosition) {
            Oskari.getSandbox().postRequestByName('MapMoveRequest', [selectedAsset.data.lon, selectedAsset.data.lat, 12]);
        }
    };

    var addDirectionArrow = function(bearing, validityDirection, lon, lat) {
        var directionArrow = getDirectionArrow(bearing, validityDirection, lon, lat);
        assetDirectionLayer.addFeatures(directionArrow);
        return directionArrow;
    };

    var handleAssetPropertyValueChanged = function(asset) {
        var turnArrow = function(asset, direction) {
            assetDirectionLayer.destroyFeatures(asset.directionArrow);
            asset.directionArrow.style.rotation = direction;
            assetDirectionLayer.addFeatures(asset.directionArrow);
        };

        if(_.isArray(asset.propertyData)) {
            var validityDirectionProperty = _.find(asset.propertyData, function(property) { return property.publicId === 'vaikutussuunta'; });
            if(_.isObject(validityDirectionProperty) &&
                _.isArray(validityDirectionProperty.values) &&
                _.isObject(validityDirectionProperty.values[0])) {
                var validityDirection = (validityDirectionProperty.values[0].propertyValue === 3) ? 1 : -1;
                turnArrow(selectedAsset, selectedAsset.data.bearing + (90 * validityDirection));
            }
            var assetType = _.find(asset.propertyData, function(property) {
                return property.publicId === 'pysakin_tyyppi';
            });
            if (assetType) {
                var values = _.pluck(assetType.values, 'propertyValue');
                if (values.length === 0) {
                    values.push([unknownAssetType]);
                }
                var imageIds = _.map(values, function(v) {
                    return v + '_' + new Date().getMilliseconds();
                });
                assetLayer.removeMarker(selectedAsset.marker);
                selectedAsset.marker.icon = getIcon(imageIds);
                assetLayer.addMarker(selectedAsset.marker);
                assetLayer.redraw();
            }

        }
    };

    var createNewAsset = function(lonlat){
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
        var directionArrow = addDirectionArrow(bearing, -1, projectionOnNearestLine.x, projectionOnNearestLine.y);
        var assetPosition = { lonLat: projectionLonLat, bearing: bearing, validityDirection: 2 };

        selectedAsset = {directionArrow: directionArrow,
            data: {bearing: bearing,
                position: assetPosition,
                validityDirection: 2,
                lon: projectionOnNearestLine.x,
                lat: projectionOnNearestLine.y,
                roadLinkId: nearestLine.roadLinkId}};
        highlightAsset(selectedAsset);
        var imageIds = [unknownAssetType+'_' + (new Date().getMilliseconds())];
        var icon = getIcon(imageIds);
        var marker = new OpenLayers.Marker(new OpenLayers.LonLat(selectedAsset.data.lon, selectedAsset.data.lat), icon);
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
        overlay.close();
    };

    var addNewAsset = function(asset) {
        var lonLat = { lon : asset.lon, lat : asset.lat};
        var validityDirection = (asset.validityDirection === 3) ? 1 : -1;
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
        if (readOnly) {
            return;
        }
        if (!selectedAsset) {
            return;
        }
        if (selectedAsset.marker && selectedAsset.marker.actionMouseDown) {
            //var pxPosition = this._map.getPixelFromLonLat(new OpenLayers.LonLat(lon, lat));
            var busStopCenter = new OpenLayers.Pixel(pxPosition.x,pxPosition.y);
            var lonlat = map.getLonLatFromPixel(busStopCenter);
            var nearestLine = geometrycalculator.findNearestLine(roadLayer.features, lonlat.lon, lonlat.lat);
            eventbus.trigger('asset:moving', nearestLine);
            var angle = geometrycalculator.getLineDirectionDegAngle(nearestLine);
            selectedAsset.data.bearing = angle;
            selectedAsset.data.roadDirection = angle;
            selectedAsset.directionArrow.style.rotation = angle + (90 * selectedAsset.marker.effectDirection);
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
            Oskari.getSandbox().postRequestByName('MapMoveRequest', [selectedAsset.data.lon, selectedAsset.data.lat, 12]);
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
    eventbus.on('assets:fetched', renderAssets, this);
    eventbus.on('map:moved', function(state) {
        if (8 < state.zoom) {
            backend.getAssets(10, state.bbox);
        } else {
            removeAssetsFromLayer();
        }
    }, this);

    var events = map.events;
    events.register('mousemove', map, function(e) {
        var pixel = new OpenLayers.Pixel(e.xy.x, e.xy.y);
        moveSelectedAsset(pixel);
    },true);

    events.register('click', map, function(e) {
        if (selectedControl === 'Add') {
            var pixel = new OpenLayers.Pixel(e.xy.x, e.xy.y);
            createNewAsset(map.getLonLatFromPixel(pixel));
        }
    });
};
