/**
 * @class Oskari.digiroad2.bundle.mapbusstop.plugin.BusStopLayerPlugin
 * Provides functionality to draw bus stops on the map
 */
Oskari.clazz.define('Oskari.digiroad2.bundle.mapbusstop.plugin.BusStopLayerPlugin',
    /**
     * @method create called automatically on construction
     * @static
     */
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

        function defineDependency(dependencyName, defaultImplementation) {
            var dependency = _.isObject(config) ? config[dependencyName] : null;
            return dependency || defaultImplementation;
        }
    }, {
        /** @static @property __name plugin name */
        __name: 'BusStopLayerPlugin',

        /** @static @property _layerType type of layers this plugin handles */
        _layerType: 'busstoplayer',
        _unknownAssetType: '99',
        _selectedValidityPeriods: ['current'],
        _visibilityZoomLevelForRoads : 10,

        /**
         * @method getName
         * @return {String} plugin name
         */
        getName: function () {
            return this.pluginName;
        },
        /**
         * @method getMapModule
         * @return {Oskari.mapframework.ui.module.common.MapModule} reference to map
         * module
         */
        getMapModule: function () {
            return this.mapModule;
        },
        /**
         * @method setMapModule
         * @param {Oskari.mapframework.ui.module.common.MapModule} mapModule
         * Reference to map module
         */
        setMapModule: function (mapModule) {
            this.mapModule = mapModule;
            this.pluginName = mapModule.getName() + this.__name;
        },
        /**
         * @method hasUI
         * This plugin doesn't have an UI that we would want to ever hide so always returns false
         * @return {Boolean}
         */
        hasUI: function () {
            return false;
        },
        /**
         * @method register
         * Interface method for the plugin protocol.
         * Registers self as a layerPlugin to mapmodule with mapmodule.setLayerPlugin()
         */
        register: function () {
            this.getMapModule().setLayerPlugin('busstoplayer', this);
        },
        /**
         * @method unregister
         * Interface method for the plugin protocol
         * Unregisters self from mapmodules layerPlugins
         */
        unregister: function () {
            this.getMapModule().setLayerPlugin('busstoplayer', null);
        },
        /**
         * @method init
         * Interface method for the module protocol.
         *
         * @param {Oskari.mapframework.sandbox.Sandbox} sandbox
         *          reference to application sandbox
         */
        init: function (sandbox) {
            eventbus.on('tool:changed',  this._toolSelectionChange, this);
            eventbus.on('validityPeriod:changed', this._handleValidityPeriodChanged, this);
            eventbus.on('asset:unselected', this._closeAsset, this);
            eventbus.on('assets:fetched', this._renderAssets, this);
            eventbus.on('assetPropertyValue:saved', this._updateAsset, this);
            eventbus.on('assetPropertyValue:changed', this._handleAssetPropertyValueChanged, this);
            eventbus.on('asset:saved', this._handleAssetSaved, this);
            eventbus.on('asset:created', this._handleAssetCreated, this);
            eventbus.on('asset:created', this._removeOverlay, this);
            eventbus.on('asset:cancelled', this._cancelCreate, this);

            // register domain builder
            var mapLayerService = sandbox.getService('Oskari.mapframework.service.MapLayerService');
            if (mapLayerService) {
                mapLayerService.registerLayerModel('busstoplayer', 'Oskari.digiroad2.bundle.mapbusstop.domain.BusStopLayer');
            }
            this._initTemplates();
            var layerModelBuilder = Oskari.clazz.create('Oskari.digiroad2.bundle.mapbusstop.domain.BusStopLayerModelBuilder', sandbox);
            mapLayerService.registerLayerModelBuilder('busstoplayer', layerModelBuilder);
        },
        _initTemplates: function() {
            this.templates = Oskari.clazz.create('Oskari.digiroad2.bundle.mapbusstop.plugin.template.Templates');
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
        stopPlugin: function (sandbox) {
            for (var p in this.eventHandlers) {
                if (this.eventHandlers.hasOwnProperty(p)) {
                    sandbox.unregisterFromEventByName(this, p);
                }
            }
            sandbox.unregister(this);
            this._map = null;
            this._sandbox = null;
        },
        start: function (sandbox) {},
        stop: function (sandbox) {},
        eventHandlers: {
            'AfterMapLayerAddEvent': function (event) {
                this._afterMapLayerAddEvent(event);
            },
            'AfterMapLayerRemoveEvent': function (event) {
                this._afterMapLayerRemoveEvent(event);
            },
            'AfterChangeMapLayerOpacityEvent': function (event) {
                this._afterChangeMapLayerOpacityEvent(event);
            },
            'AfterMapMoveEvent' : function (event) {
                this._afterMapMoveEvent(event);
            },
            'MouseHoverEvent': function (event) {
                this._moveSelectedAsset(event);
            },
            'MapClickedEvent': function (event) {
                if (this._selectedControl === 'Add') {
                    this._addBusStopEvent(event);
                } else if (this._selectedAsset) {
                    eventbus.trigger('asset:unselected', this._selectedAsset.data.id);
                }
            },
            'mapbusstop.ApplicationInitializedEvent': function() {
                this._zoomNotInMessage = this._getNotInZoomRange();
                this._oldZoomLevel = this._isInZoomLevel() ? this._map.getZoom() : -1;
                this._zoomNotInMessage();
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
                // TODO: Replace two overlays with one with selector '#contentMap,#maptools' when oskari overlay supports this.
                var mapOverlay = me._oskari.clazz.create('Oskari.userinterface.component.Overlay');
                mapOverlay.overlay('#contentMap');
                mapOverlay.followResizing(true);

                var toolsOverlay = me._oskari.clazz.create('Oskari.userinterface.component.Overlay');
                toolsOverlay.overlay('#maptools');
                toolsOverlay.followResizing(true);

                return {
                    mapOverlay: mapOverlay,
                    toolsOverlay: toolsOverlay
                };
            };

            this._overlays = applyBlockingOverlays();
        },

        _removeOverlay: function() {
            _.forEach(this._overlays, function(overlay) { overlay.close(); });
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
                {externalGraphic: 'src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png',
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
            this._unhighlightAsset(this._selectedAsset);
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
            me._sandbox.printDebug("#!#! CREATED OPENLAYER.Markers.BusStop for BusStopLayer " + layer.getId());
        },
        _fetchAssets: function() {
            if (this._isInZoomLevel()) {
                this._backend.getAssets(10, this._map.getExtent());
            } else {
                if(this._zoomNotInMessage) {
                    this._zoomNotInMessage();
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
            var busStopClick = this._mouseClick(asset, imageIds);
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
            var me = this;
            return function(evt) {
                var bearing ="0";
                if (me._selectedAsset) {
                    bearing = me._selectedAsset.data.roadDirection;
                }
                // Opacity back
                asset.marker.setOpacity(1);
                asset.marker.actionMouseDown = false;
                // Not need listeners anymore
                me._map.events.unregister("mouseup", me._map, me._mouseUpFunction);
                // Moved update
                if (asset.marker.actionDownX != evt.clientX ||  asset.marker.actionDownY != evt.clientY ) {
                    var data = { "assetTypeId" : typeId, "lon" : asset.marker.lonlat.lon, "lat" : asset.marker.lonlat.lat, "roadLinkId": asset.roadLinkId, "bearing" : bearing };
                    me._backend.updateAsset(id, data);
                }
                var streetViewCoordinates = { lonLat: asset.marker.lonlat };
                busStopClick(evt, streetViewCoordinates);
            };
        },
        _mouseDown: function(asset, busStops, mouseUp) {
            var me = this;
            return function (evt) {
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
        _mouseClick: function(asset, imageIds) {
            var me = this;
            return function (evt, streetViewCoordinates) {
                me._state = null;
                eventbus.trigger('asset:selected', asset.data.id);
                me._backend.getAsset(asset.data.id, function(assetData) {
                    // FIXME: DEPRECATED, USE EVENTS INSTEAD OF CALLBACKS
                    var assetAttributes = _.merge({}, assetData, { id: asset.data.id });
                    streetViewCoordinates.heading = asset.data.roadDirection + (-90 * asset.data.effectDirection);
                    assetAttributes.position = streetViewCoordinates;
                    me._selectedAsset = asset;
                    me._highlightAsset(me._selectedAsset);
                });
                OpenLayers.Event.stop(evt);
            };
        },

        _highlightAsset: function(asset) {
            var arrow = asset.directionArrow;
            arrow.style.backgroundGraphic = 'src/resources/digiroad2/bundle/mapbusstop/images/hover.png';
            arrow.style.backgroundHeight = 68;
            arrow.style.backgroundWidth = 68;
            this._layers.assetDirection.redraw();
        },
        
        _moveSelectedAsset: function(evt) {
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
        getLocalization : function(key) {
            if(this._localization !== undefined) {
                this._localization = Oskari.getLocalization(this.getName());
            }
            if(key) {
                return this._localization[key];
            }
            return this._localization;
        },
        _afterMapLayerRemoveEvent: function (event) {
            var layer = event.getMapLayer();
            if (!layer.isLayerOfType(this._layerType)) {
                return;
            }
            this._removeMapLayerFromMap(layer);
        },

        _removeMapLayerFromMap: function (layer) {
            /* This should free all memory */
            _.each(this._layer[this._layerType +"_"+ layer.getId()], function (tmpLayer) {
                tmpLayer.destroy();
            });
        },

        getOLMapLayers: function (layer) {
            if (!layer.isLayerOfType(this._layerType)) {
                return null;
            }
            return _.values(this._layers);
        },

        _afterChangeMapLayerOpacityEvent: function (event) {
            var layer = event.getMapLayer();
            if (!layer.isLayerOfType(this._layerType))
                return;

            _.each(this._layer[this._layerType +"_"+ layer.getId()], function (tmpLayer) {
                tmpLayer.setOpacity(layer.getOpacity() / 100);
            });
        }
    }, {
        'protocol': ["Oskari.mapframework.module.Module", "Oskari.mapframework.ui.module.common.mapmodule.Plugin"]
    });
