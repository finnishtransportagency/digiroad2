Oskari.clazz.define('Oskari.digiroad2.bundle.assetlayer.AssetLayer',
    function (config) {
        this.mapModule = null;
        this.pluginName = null;
        this._sandbox = null;
        this._map = null;
        this._supportedFormats = {};
        this._localization = null;
        this._state = undefined;
        this._layers = defineDependency('layers', {});
        this._selectedControl = 'Select';
        this._backend = defineDependency('backend', window.Backend);
        this._geometryCalculations = defineDependency('geometryCalculations', window.geometrycalculator);
        this._oskari = defineDependency('oskari', window.Oskari);
        this._readOnly = true;

        function defineDependency(dependencyName, defaultImplementation) {
            var dependency = _.isObject(config) ? config[dependencyName] : null;
            return dependency || defaultImplementation;
        }
    }, {
        __name: 'AssetLayer',
        _layerType: 'assetlayer',
        _unknownAssetType: '99',
        _selectedValidityPeriods: ['current'],
        _visibilityZoomLevelForRoads : 10,
        getName: function () {
            return this.pluginName;
        },
        getMapModule: function () {
            return this.mapModule;
        },
        setMapModule: function (mapModule) {
            this.mapModule = mapModule;
            this.pluginName = mapModule.getName() + this.__name;
        },
        hasUI: function () {
            return false;
        },
        register: function () {
            this.getMapModule().setLayerPlugin('assetlayer', this);
        },
        unregister: function () {
            this.getMapModule().setLayerPlugin('assetlayer', null);
        },
        init: function (sandbox) {
            eventbus.on('tool:changed',  this._toolSelectionChange, this);
            eventbus.on('validityPeriod:changed', this._handleValidityPeriodChanged, this);
            eventbus.on('asset:selected', function(data) {
                this._backend.getAsset(data.id);
            }, this);
            eventbus.on('asset:selected', function(data, keepPosition) {
                this._selectedAsset = this._assets[data.id];
                this._highlightAsset(this._selectedAsset);
                if (!keepPosition) {
                    var sandbox = Oskari.getSandbox(),
                        requestBuilder = sandbox.getRequestBuilder('MapMoveRequest'),
                        request;
                    request = requestBuilder(this._selectedAsset.data.lon, this._selectedAsset.data.lat, 12);
                    sandbox.request(this.getName(), request);
                }
            }, this);

            eventbus.on('asset:unselected', this._closeAsset, this);
            eventbus.on('assets:fetched', this._renderAssets, this);
            eventbus.on('assetPropertyValue:saved', this._updateAsset, this);
            eventbus.on('assetPropertyValue:changed', this._handleAssetPropertyValueChanged, this);
            eventbus.on('asset:saved', this._handleAssetSaved, this);
            eventbus.on('asset:created', this._handleAssetCreated, this);
            eventbus.on('asset:fetched', this._handleAssetFetched, this);
            eventbus.on('asset:created', this._removeOverlay, this);
            eventbus.on('asset:cancelled', this._cancelCreate, this);
            eventbus.on('application:initialized', function() {
                this._zoomNotInMessage = this._getNotInZoomRange();
                this._oldZoomLevel = this._isInZoomLevel() ? this._map.getZoom() : -1;
                this._zoomNotInMessage();
            }, this);
            eventbus.on('application:readOnly', function(readOnly) {
                this._readOnly = readOnly;
            }, this);

            // register domain builder
            var mapLayerService = sandbox.getService('Oskari.mapframework.service.MapLayerService');
            if (mapLayerService) {
                mapLayerService.registerLayerModel('assetlayer', 'Oskari.digiroad2.bundle.assetlayer.domain.BusStopLayer');
            }
            this._initTemplates();
            var layerModelBuilder = Oskari.clazz.create('Oskari.digiroad2.bundle.assetlayer.domain.BusStopLayerModelBuilder', sandbox);
            mapLayerService.registerLayerModelBuilder('assetlayer', layerModelBuilder);
        },
        _initTemplates: function() {
            this.templates = Oskari.clazz.create('Oskari.digiroad2.bundle.assetlayer.plugin.template.Templates');
        },
        startPlugin: function (sandbox) {
            this._sandbox = sandbox;
            this._map = this.getMapModule().getMap();
            sandbox.register(this);
            for (var p in this.eventHandlers) {
                if (this.eventHandlers.hasOwnProperty(p)) {
                    sandbox.registerForEventByName(this, p);
                }
            }
        },
        start: function (sandbox) {},
        eventHandlers: {
            'AfterMapLayerAddEvent': function (event) {
                this._afterMapLayerAddEvent(event);
            },
            'AfterMapMoveEvent' : function (event) {
                this._afterMapMoveEvent(event);
            },
            'MouseHoverEvent': function (event) {
                this._moveSelectedAsset(event);
            },
            'MapClickedEvent': function(event) {
                if (this._selectedControl === 'Add') {
                    this._addBusStopEvent(event);
                } else if (this._selectedAsset) {
                    // FIXME: Currently this breaks selection on Firefox after initial zoom
//                    eventbus.trigger('asset:unselected', this._selectedAsset.data.id);
                }
            }
        },
        _removeAssetFromMap: function(asset) {
            this._layers.assetDirection.removeFeatures(asset.directionArrow);
            this._layers.asset.removeMarker(asset.marker);
        },
        _cancelCreate: function() {
            this._removeOverlay();
            this._removeAssetFromMap(this._selectedAsset);
        },
        _getNotInZoomRange: function() {
            var self = this;
            return function() {
                if (self._oldZoomLevel != self._map.getZoom()) {
                    var dialog = Oskari.clazz.create('Oskari.userinterface.component.Popup');
                    dialog.show('Zoomaa lähemmäksi, jos haluat muokata pysäkkejä');
                    dialog.fadeout(2000);
                }
            };
        },
        _updateAsset: function(asset) {
            this._removeAssetFromMap(this._selectedAsset);
            this._addNewAsset(asset);
        },
        _handleValidityPeriodChanged: function(selectedValidityPeriods) {
            this._selectControl.unselectAll();
            this._selectedValidityPeriods = selectedValidityPeriods;
            var me = this;
            var assets = this._assets;
            _.each(assets, function(asset) {
                if (_.contains(selectedValidityPeriods, asset.data.validityPeriod)) {
                    me._showAsset(asset);
                } else {
                    me._hideAsset(asset);
                }
            });
        },
        _handleAssetCreated: function(asset) {
            this._removeAssetFromMap(this._selectedAsset);
            this._addNewAsset(asset);
        },
        _handleAssetSaved: function(asset) {
            this._selectedAsset.data = asset;
            this._assets[asset.id] = this._selectedAsset;
        },
        _handleAssetFetched: function(assetData) {
            this._selectedAsset.data = assetData;
        },
        _hideAsset: function(asset) {
            this._layers.assetDirection.destroyFeatures(asset.directionArrow);
            asset.marker.display(false);
        },

        _showAsset: function(asset) {
          asset.marker.display(true);
          this._layers.assetDirection.addFeatures(asset.directionArrow);
        },
        _addDirectionArrow: function (bearing, validityDirection, lon, lat) {
            var directionArrow = this._getDirectionArrow(bearing, validityDirection, lon, lat);
            this._layers.assetDirection.addFeatures(directionArrow);
            return directionArrow;
        },
        _handleAssetPropertyValueChanged: function(asset) {
            var self = this;
            var turnArrow = function(asset, direction) {
                self._layers.assetDirection.destroyFeatures(asset.directionArrow);
                asset.directionArrow.style.rotation = direction;
                self._layers.assetDirection.addFeatures(asset.directionArrow);
            };

            if(_.isArray(asset.propertyData)) {
                var validityDirectionProperty = _.find(asset.propertyData, function(property) { return property.propertyId === 'validityDirection'; });
                if(_.isObject(validityDirectionProperty) &&
                    _.isArray(validityDirectionProperty.values) &&
                    _.isObject(validityDirectionProperty.values[0])) {
                    var validityDirection = (validityDirectionProperty.values[0].propertyValue === 3) ? 1 : -1;
                    turnArrow(this._selectedAsset, this._selectedAsset.data.bearing + (90 * validityDirection));
                }
                var assetType = _.find(asset.propertyData, function(property) {
                    return property.propertyId === '200';
                });
                if (assetType) {
                    var values = _.pluck(assetType.values, 'propertyValue');
                    if (values.length === 0) {
                        values.push(['99']);
                    }
                    var imageIds = _.map(values, function(v) {
                        return v + '_' + new Date().getMilliseconds();
                    });
                    self._layers.asset.removeMarker(self._selectedAsset.marker);
                    self._selectedAsset.marker.icon = self._getIcon(imageIds);
                    self._layers.asset.addMarker(self._selectedAsset.marker);
                    self._layers.asset.redraw();
                }

            }
        },
        _addBusStopEvent: function(event){
            var me = this;
            if (this._selectedAsset) {
                eventbus.trigger('asset:unselected', this._selectedAsset.data.id);
            }
            var selectedLon = event.getLonLat().lon;
            var selectedLat = event.getLonLat().lat;
            var features = this._layers.road.features;
            var nearestLine = me._geometryCalculations.findNearestLine(features, selectedLon, selectedLat);
            var projectionOnNearestLine = me._geometryCalculations.nearestPointOnLine(nearestLine, { x: selectedLon, y: selectedLat });
            var projectionLonLat = {
                lon: projectionOnNearestLine.x,
                lat: projectionOnNearestLine.y
            };
            var bearing = me._geometryCalculations.getLineDirectionDegAngle(nearestLine);
            var directionArrow = me._addDirectionArrow(bearing, -1, projectionOnNearestLine.x, projectionOnNearestLine.y);
            var assetPosition = { lonLat: projectionLonLat, bearing: bearing, validityDirection: 2 };

            this._selectedAsset = {directionArrow: directionArrow,
                                   data: {bearing: bearing,
                                          position: assetPosition,
                                          validityDirection: 2,
                                          lon: projectionOnNearestLine.x,
                                          lat: projectionOnNearestLine.y,
                                          roadLinkId: nearestLine.roadLinkId}};
            this._highlightAsset(this._selectedAsset);
            var imageIds = ['99_' + (new Date().getMilliseconds())];
            var icon = this._getIcon(imageIds);
            var marker = new OpenLayers.Marker(new OpenLayers.LonLat(this._selectedAsset.data.lon, this._selectedAsset.data.lat), icon);
            this._layers.asset.addMarker(marker);
            this._selectedAsset.marker = marker;
            eventbus.trigger('asset:placed', this._selectedAsset.data);

            var applyBlockingOverlays = function() {
                var overlay = me._oskari.clazz.create('Oskari.userinterface.component.Overlay');
                overlay.overlay('#contentMap,#maptools');
                overlay.followResizing(true);
                return overlay;
            };

            this._overlay = applyBlockingOverlays();
        },

        _removeOverlay: function() {
            this._overlay.close();
        },

        _addNewAsset: function(asset) {
            var lonLat = { lon : asset.lon, lat : asset.lat};
            var validityDirection = (asset.validityDirection === 3) ? 1 : -1;
            var directionArrow = this._addDirectionArrow(asset.bearing, validityDirection, asset.lon, asset.lat);
            this._selectedAsset = this._addBusStop(asset, this._layers.asset,
                directionArrow, this._layers.assetDirection, validityDirection);
            this._assets[asset.id] = this._selectedAsset;
            asset.position = {
                lonLat: lonLat,
                heading: asset.bearing + 90
            };
            this._highlightAsset(this._selectedAsset);
        },
        onEvent: function (event) {
            return this.eventHandlers[event.getName()].apply(this, [event]);
        },
        preselectLayers: function (layers) {
            for (var i = 0; i < layers.length; i++) {
                var layer = layers[i];
                if (!layer.isLayerOfType(this._layerType)) {
                    continue;
                }
                this._addMapLayerToMap(layer);
            }
        },
        _getAngleFromBearing: function(bearing, validityDirection) {
            return (bearing) ? bearing + (90 * validityDirection): 90;
        },
        _getDirectionArrow: function(bearing, validityDirection, lon, lat) {
            var angle = this._getAngleFromBearing(bearing, validityDirection);
            return new OpenLayers.Feature.Vector(
                new OpenLayers.Geometry.Point(lon, lat),
                null,
                {externalGraphic: 'src/resources/digiroad2/bundle/assetlayer/images/suuntain.png',
                 graphicHeight: 16, graphicWidth: 23, graphicXOffset:-8, graphicYOffset:-8, rotation: angle}
            );
        },
        _afterMapMoveEvent: function() {
            if (_.isObject(this._layers.asset)) {
                this._fetchAssets();
            }
            this._handleRoadsVisibility();
        },
        _isInRoadLinkZoomLevel: function() {
            return this._map.getZoom() >= this._visibilityZoomLevelForRoads;
        },
        _handleRoadsVisibility: function() {
            if (_.isObject(this._layers.road)) {
                this._layers.road.setVisibility(this._isInRoadLinkZoomLevel());
            }
        },
        _afterMapLayerAddEvent: function (event) {
            this._addMapLayerToMap(event.getMapLayer(), event.getKeepLayersOrder(), event.isBasemap());
        },

        _closeAsset: function(id) {
            if (this._selectedAsset) {
                this._unhighlightAsset(this._selectedAsset);
            }
            this._selectedAsset = null;
            this._selectControl.unselectAll();
        },
        
        _unhighlightAsset: function(asset) {
            var arrow = asset.directionArrow;
            arrow.style.backgroundGraphic = null;
            arrow.style.backgroundHeight = null;
            arrow.style.backgroundWidth = null;
            this._layers.assetDirection.redraw();
        },

        _toolSelectionChange: function(action) {
            this._selectedControl = action;
            this._selectControl.unselectAll();
        },
        _addMapLayerToMap: function (layer) {
            var me = this;
            if (!layer.isLayerOfType(this._layerType)) {
                return;
            }
            var roadLayer = new OpenLayers.Layer.Vector("road_" + layer.getId(), {
                strategies: [new OpenLayers.Strategy.BBOX(), new OpenLayers.Strategy.Refresh()],
                protocol: new OpenLayers.Protocol.HTTP({
                    url: layer.getRoadLinesUrl(),
                    format: new OpenLayers.Format.GeoJSON()
                }),
                styleMap: me.templates.roadStyles
            });
            roadLayer.setVisibility(this._isInRoadLinkZoomLevel());
            this._selectControl = new OpenLayers.Control.SelectFeature(roadLayer);
            var assetDirectionLayer = new OpenLayers.Layer.Vector("assetDirection_" + layer.getId());
            var assetLayer = new OpenLayers.Layer.Markers("asset_" + layer.getId());
            roadLayer.opacity = layer.getOpacity() / 100;
            assetDirectionLayer.opacity = layer.getOpacity() / 100;
            assetLayer.opacity = layer.getOpacity() / 100;

            me._map.addLayer(roadLayer);
            me._map.addLayer(assetDirectionLayer);
            me._map.addLayer(assetLayer);
            this._layers = {road: roadLayer,
                            assetDirection: assetDirectionLayer,
                            asset: assetLayer};
            this._fetchAssets();
        },
        _fetchAssets: function() {
            if (this._isInZoomLevel()) {
                this._backend.getAssets(10, this._map.getExtent());
            } else {
                if(this._zoomNotInMessage) {
                    this._zoomNotInMessage();
                }
                if (this._selectedAsset) {
                    eventbus.trigger('asset:unselected', this._selectedAsset.data.id);
                }
                this._removeAssetsFromLayer();
            }
            this._oldZoomLevel = this._map.getZoom();
        },
        _renderAssets: function(assets) {
            var self = this;
            if (self._isInZoomLevel()) {
                self._layers.asset.setVisibility(true);
                _.each(assets, function(asset) {
                    if (!_.contains(_.pluck(self._layers.asset.markers, "id"), asset.id)) {
                        var validityDirection = (asset.validityDirection === 3) ? 1 : -1;
                        //Make the feature a plain OpenLayers marker
                        var directionArrow = self._getDirectionArrow(asset.bearing, validityDirection, asset.lon, asset.lat);
                        self._layers.assetDirection.addFeatures(directionArrow);
                        self._assets = self._assets || {};
                        if (self._assets[asset.id]) {
                            self._removeAssetFromMap(self._assets[asset.id]);
                        }
                        self._assets[asset.id] = self._addBusStop(asset, self._layers.asset, directionArrow, self._layers.assetDirection, validityDirection);
                        var assetIdFromURL = function() {
                            var matches = window.location.hash.match(/\#\/asset\/(\d+)/);
                            if (matches) {
                                return matches[1];
                            }
                        };
                        if (self._selectedAsset && self._selectedAsset.data.id == asset.id) {
                            self._selectedAsset = self._assets[asset.id];
                            self._highlightAsset(self._selectedAsset);
                        }
                    }
                });

            }
        },
        _isInZoomLevel: function() {
            return this._map.getZoom() > 8;
        },
        _removeAssetsFromLayer: function() {
            this._layers.assetDirection.removeAllFeatures();
            this._layers.asset.clearMarkers();
        },
        _getIcon: function(imageIds) {
            var size = new OpenLayers.Size(28, 16 * imageIds.length);
            var offset = new OpenLayers.Pixel(-(size.w/2+1), -size.h-5);
            var icon = new OpenLayers.Icon("", size, offset);
            icon.imageDiv.className = "callout-wrapper";
            icon.imageDiv.removeChild(icon.imageDiv.getElementsByTagName("img")[0]);
            icon.imageDiv.setAttribute("style", "");
            icon.imageDiv.appendChild(this._getIconImages(imageIds));
            return icon;
        },
        _getIconImages: function(imageIds) {
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
        },
        _addBusStop: function(assetData, busStops, directionArrow, directionLayer, validityDirection) {
            var imageIds = assetData.imageIds;
            var icon = this._getIcon(imageIds);
            // new bus stop marker
            var marker = new OpenLayers.Marker(new OpenLayers.LonLat(assetData.lon, assetData.lat), icon);
            marker.featureContent = assetData.featureData;
            marker.roadDirection = assetData.bearing;
            marker.effectDirection = validityDirection;
            var asset = {};
            asset.marker = marker;
            asset.data = assetData;
            asset.directionArrow = directionArrow;
            var busStopClick = this._mouseClick(asset);
            var mouseUp = this._mouseUp(asset, busStops, busStopClick, assetData.id, assetData.assetTypeId);
            var mouseDown = this._mouseDown(asset, busStops, mouseUp);
            marker.events.register("mousedown", busStops, mouseDown);
            marker.validityPeriod = assetData.validityPeriod;
            if (!_.contains(this._selectedValidityPeriods, assetData.validityPeriod)) {
              this._hideAsset(asset);
            }
            busStops.addMarker(marker);
            return asset;
        },
        _mouseUp: function(asset, busStops, busStopClick, id, typeId) {
            var self = this;
            return function(evt) {
                OpenLayers.Event.stop(evt);
                var bearing ="0";
                if (self._selectedAsset) {
                    bearing = self._selectedAsset.data.roadDirection;
                }
                // Opacity back
                asset.marker.setOpacity(1);
                asset.marker.actionMouseDown = false;
                // Not need listeners anymore
                self._map.events.unregister("mouseup", self._map, self._mouseUpFunction);
                // Moved update
                if (!self._readOnly && (asset.marker.actionDownX != evt.clientX ||  asset.marker.actionDownY != evt.clientY)) {
                    var data = { "assetTypeId" : typeId, "lon" : asset.marker.lonlat.lon, "lat" : asset.marker.lonlat.lat, "roadLinkId": asset.roadLinkId, "bearing" : bearing };
                    self._backend.updateAsset(id, data);
                }
                var streetViewCoordinates = { lonLat: asset.marker.lonlat };
                busStopClick(evt, streetViewCoordinates);
            };
        },
        _mouseDown: function(asset, busStops, mouseUp) {
            var me = this;
            return function (evt) {
                OpenLayers.Event.stop(evt);
                if (me._selectedAsset) {
                    eventbus.trigger('asset:unselected', me._selectedAsset.data.id);
                }
                me._selectedAsset = asset;
                // push marker up
                busStops.removeMarker(asset.marker);
                busStops.addMarker(asset.marker);
                // Opacity because we want know what is moving
                asset.marker.setOpacity(0.6);
                // Mouse need to be down until can be moved
                asset.marker.actionMouseDown = true;
                //Save original position
                asset.marker.actionDownX = evt.clientX;
                asset.marker.actionDownY = evt.clientY;
                //register up
                me._map.events.register("mouseup",me._map, mouseUp, true);
                me._mouseUpFunction = mouseUp;
                OpenLayers.Event.stop(evt);
            };
        },
        _mouseClick: function(asset) {
            var me = this;
            return function (evt) {
                OpenLayers.Event.stop(evt);
                me._state = null;
                window.location.hash = '#/asset/' + asset.data.externalId + '?keepPosition=true';
            };
        },

        _highlightAsset: function(asset) {
            var arrow = asset.directionArrow;
            arrow.style.backgroundGraphic = 'src/resources/digiroad2/bundle/assetlayer/images/hover.png';
            arrow.style.backgroundHeight = 68;
            arrow.style.backgroundWidth = 68;
            this._layers.assetDirection.redraw();
        },
        
        _moveSelectedAsset: function(evt) {
            if (this._readOnly) {
                return;
            }

            if (this._map.getZoom() < 10) {
                return;
            }
            if (!this._selectedAsset) {
                return;
            }
            if (this._selectedControl != 'Select') {
                return;
            }
            if (this._selectedAsset.marker && this._selectedAsset.marker.actionMouseDown) {
                var pxPosition = this._map.getPixelFromLonLat(new OpenLayers.LonLat(evt.getLon(), evt.getLat()));
                var busStopCenter = new OpenLayers.Pixel(pxPosition.x,pxPosition.y);
                var lonlat = this._map.getLonLatFromPixel(busStopCenter);
                var nearestLine = geometrycalculator.findNearestLine(this._layers.road.features, lonlat.lon, lonlat.lat);
                var nearestFeature = _.find(this._layers.road.features, function(feature) {
                   return feature.id == nearestLine.id;
                });
                this._selectControl.unselectAll();
                this._selectControl.select(nearestFeature);
                var angle = geometrycalculator.getLineDirectionDegAngle(nearestLine);
                this._selectedAsset.data.bearing = angle;
                this._selectedAsset.data.roadDirection = angle;
                this._selectedAsset.directionArrow.style.rotation = angle + (90 * this._selectedAsset.marker.effectDirection);
                var position = geometrycalculator.nearestPointOnLine(
                    nearestLine,
                    { x: lonlat.lon, y: lonlat.lat});
                lonlat.lon = position.x;
                lonlat.lat = position.y;
                this._selectedAsset.roadLinkId = nearestLine.roadLinkId;
                this._selectedAsset.marker.lonlat = lonlat;
                this._selectedAsset.directionArrow.move(lonlat);
                this._layers.asset.redraw();
            }
        },
        getOLMapLayers: function (layer) {
            if (!layer.isLayerOfType(this._layerType)) {
                return null;
            }
            return _.values(this._layers);
        }
    }, {
        'protocol': ["Oskari.mapframework.module.Module", "Oskari.mapframework.ui.module.common.mapmodule.Plugin"]
    });
