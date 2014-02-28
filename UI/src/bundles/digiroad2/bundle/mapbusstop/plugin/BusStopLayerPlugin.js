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
        this._selectedBusStop = null;
        this._roadStyles = null;
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
            var me = this;

            // register domain builder
            var mapLayerService = sandbox.getService('Oskari.mapframework.service.MapLayerService');
            if (mapLayerService) {
                mapLayerService.registerLayerModel('busstoplayer', 'Oskari.digiroad2.bundle.mapbusstop.domain.BusStopLayer');
            }

            me._initTemplates();
            me._initRoadsStyles();

            var layerModelBuilder = Oskari.clazz.create('Oskari.digiroad2.bundle.mapbusstop.domain.BusStopLayerModelBuilder', sandbox);
            mapLayerService.registerLayerModelBuilder('busstoplayer', layerModelBuilder);
        },
        _initTemplates: function () {
            var me = this;
            _.templateSettings = {
                interpolate: /\{\{(.+?)\}\}/g
            };

            me._popupInfoTemplate = _.template('<div class="popupInfoBusStopsIcons">{{busStopsIons}}</div>' +
                                               '<div class="popupInfoChangeDirection">' +
                                                    '<div class="changeDirectionButton">{{changeDirectionButton}}</div>' +
                                               '</div>');
            me._busStopsPopupIcons = _.template('<img src="api/images/{{imageId}}">');
            me._removeAssetTemplate = _.template('<p>Anna viimeinen voimassaolopäivä</p><p><input id="removeAssetDateInput" class="featureAttributeDate" type="text" /></p>');
        },
        _initRoadsStyles: function() {
            this._roadStyles = new OpenLayers.StyleMap({
                "select": new OpenLayers.Style(null, {
                    rules: [
                        new OpenLayers.Rule({
                            symbolizer: {
                                "Line": {
                                    strokeWidth: 6,
                                    strokeOpacity: 1,
                                    strokeColor: "#5eaedf"
                                }
                            }
                        })
                    ]
                }),
                "default": new OpenLayers.Style(null, {
                    rules: [
                        new OpenLayers.Rule({
                            symbolizer: {
                                "Line": {
                                    strokeWidth: 3,
                                    strokeOpacity: 1,
                                    strokeColor: "#a4a4a2"
                                }
                            }
                        })
                    ]
                })
            });
        },
        /**
         * @method startPlugin
         * Interface method for the plugin protocol.
         *
         * @param {Oskari.mapframework.sandbox.Sandbox} sandbox
         *          reference to application sandbox
         */
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
        /**
         * @method stopPlugin
         * Interface method for the plugin protocol
         *
         * @param {Oskari.mapframework.sandbox.Sandbox} sandbox
         *          reference to application sandbox
         */
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
        /**
         * @method start
         * Interface method for the module protocol
         *
         * @param {Oskari.mapframework.sandbox.Sandbox} sandbox
         *          reference to application sandbox
         */
        start: function (sandbox) {},
        /**
         * @method stop
         * Interface method for the module protocol
         *
         * @param {Oskari.mapframework.sandbox.Sandbox} sandbox
         *          reference to application sandbox
         */
        stop: function (sandbox) {},
        /**
         * @property {Object} eventHandlers
         * @static
         */
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
                this._moveSelectedBusStop(event);
            },
            'featureattributes.FeatureAttributeChangedEvent': function (event) {
                if(_.isObject(this._state) && _.isFunction(this._state.featureAttributeChangedHandler)) {
                    this._state.featureAttributeChangedHandler(event);
                } else {
                    this._featureAttributeChangedEvent(event);
                }
            },
            'infobox.InfoBoxClosedEvent': function (event) {
                this._infoBoxClosed(event);
            },
            'actionpanel.ActionPanelToolSelectionChangedEvent': function (event) {
                this._toolSelectionChange(event);
            },'MapClickedEvent': function (event) {
                if (this._selectedControl === 'Add') {
                    this._addBusStopEvent(event);
                }
            },
            'actionpanel.ValidityPeriodChangedEvent': function(event) {
              this._handleValidityPeriodChanged(event.getSelectedValidityPeriods());
            }
        },

        _handleValidityPeriodChanged: function(selectedValidityPeriods) {
          this._selectedValidityPeriods = selectedValidityPeriods;
          var me = this;
          var markers = this._layers.asset.markers;
          _.each(markers, function(marker) {
              if (_.contains(selectedValidityPeriods, marker.validityPeriod)) {
                me._showAsset(marker);
              } else {
                me._hideAsset(marker);
              }
          });
        },

        _hideAsset: function(marker) {
            //TODO: InfoBox is a direct child component of BusStopLayer, so make it so!
            // (get rid of useless request-abstraction)
            if (this._selectedBusStop &&  this._selectedBusStop.id == marker.id) {
                var request = this._sandbox.getRequestBuilder('InfoBox.HideInfoBoxRequest')('busStop');
                this._sandbox.request(this, request);
            }
            this._layers.assetDirection.destroyFeatures(marker.directionArrow);
            marker.display(false);
        },

        _showAsset: function(marker) {
          marker.display(true);
          this._layers.assetDirection.addFeatures(marker.directionArrow);
        },
        _addDirectionArrow: function (bearing, validityDirection, lon, lat) {
            var directionArrow = this._getDirectionArrow(bearing, validityDirection, lon, lat);
            this._layers.assetDirection.addFeatures(directionArrow);
            return directionArrow;
        },
        _addBusStopEvent: function(event){
            var me = this;
            var selectedLon = event.getLonLat().lon;
            var selectedLat = event.getLonLat().lat;
            var features = this._layers.road.features;
            var nearestLine = me._geometryCalculations.findNearestLine(features, selectedLon, selectedLat);
            var bearing = me._geometryCalculations.getLineDirectionDegAngle(nearestLine);
            var directionArrow = me._addDirectionArrow(bearing, -1, selectedLon, selectedLat);
            var assetPosition = { lonLat: event.getLonLat(), bearing: bearing, validityDirection: 2 };

            setPluginState({
                featureAttributeChangedHandler: function(event) {
                    var asset = event.getParameter();
                    if(_.isArray(asset.propertyData)) {
                        var validityDirectionProperty = _.find(asset.propertyData, function(property) { return property.propertyId === 'validityDirection'; });
                        if(_.isObject(validityDirectionProperty) &&
                            _.isArray(validityDirectionProperty.values) &&
                            _.isObject(validityDirectionProperty.values[0])) {
                            var validityDirection = (validityDirectionProperty.values[0].propertyValue === 3) ? 1 : -1;
                            me._layers.assetDirection.destroyFeatures(directionArrow);
                            directionArrow = me._addDirectionArrow(bearing, validityDirection, selectedLon, selectedLat);
                        }
                    }
                }
            });
            
            sendCollectAttributesRequest(assetPosition, attributesCollected, quitAddition);
            var contentItem = me._makeContent([me._unknownAssetType]);
            me._sendPopupRequest('busStop', 'Uusi Pysäkki', -1, contentItem, event.getLonLat(), quitAddition);
            var overlay = applyBlockingOverlay();
            movePopupAboveOverlay();

            function setPluginState(state) { me._state = state; }

            function attributesCollected(attributeCollection) {
                var properties = _.map(attributeCollection, function(attr) {
                  return {id: attr.propertyId,
                          values: attr.propertyValues};
                });
                me._backend.createAsset(
                    {assetTypeId: 10,
                     lon: selectedLon,
                     lat: selectedLat,
                     roadLinkId: nearestLine.roadLinkId,
                     bearing: bearing,
                     properties: properties}, function(asset) {
                       me._addNewAsset(asset);
                     });
                quitAddition();
            }

            function sendCollectAttributesRequest(assetPosition, callback, cancellationCallback) {
                var requestBuilder = me._sandbox.getRequestBuilder('FeatureAttributes.CollectFeatureAttributesRequest');
                var request = requestBuilder(assetPosition, callback, cancellationCallback);
                me._sandbox.request(me.getName(), request);
            }

            function removeInfoBox(infoBoxId) {
                var requestBuilder = me._sandbox.getRequestBuilder('InfoBox.HideInfoBoxRequest');
                var request = requestBuilder(infoBoxId);
                me._sandbox.request(me.getName(), request);
                jQuery('.olPopup').remove();
            }

            function applyBlockingOverlay() {
                var overlay = me._oskari.clazz.create('Oskari.userinterface.component.Overlay');
                overlay.overlay('#contentMap');
                overlay.followResizing(true);
                return overlay;
            }

            function movePopupAboveOverlay() {
                var popupElement = jQuery('.olPopup');
                var contentMapElement = jQuery('#contentMap');
                var overlayElement = contentMapElement.find('.oskarioverlay');
                var popupBoundingRectangle = popupElement[0].getBoundingClientRect();
                var contentMapBoundingRectangle = contentMapElement[0].getBoundingClientRect();
                var popupLeftRelativeToContentMap = popupBoundingRectangle.left - contentMapBoundingRectangle.left;
                var popupTopRelativeToContentMap = popupBoundingRectangle.top - contentMapBoundingRectangle.top;
                var popupZIndex = Number(overlayElement.css('z-index')) + 1;
                var detachedPopup = popupElement.detach();
                detachedPopup.css('left', popupLeftRelativeToContentMap + 'px');
                detachedPopup.css('top', popupTopRelativeToContentMap + 'px');
                detachedPopup.css('z-index', popupZIndex);
                detachedPopup.css('cursor', 'default');
                contentMapElement.append(detachedPopup);
            }

            function quitAddition() {
                me._layers.assetDirection.destroyFeatures(directionArrow);
                removeInfoBox('busStop');
                setPluginState(null);
                overlay.close();
            }
        },
        _addNewAsset: function(asset) {
            var lonLat = { lon : asset.lon, lat : asset.lat};
            var contentItem = this._makeContent(asset.imageIds);
            var validityDirection = (asset.validityDirection === 3) ? 1 : -1;
            var directionArrow = this._addDirectionArrow(asset.bearing, validityDirection, asset.lon, asset.lat);
            this._selectedBusStop = this._addBusStop(asset, this._layers.asset,
                directionArrow, this._layers.assetDirection, validityDirection);
            this._sendPopupRequest("busStop", asset.id, asset.id, contentItem, lonLat);
            this._selectedBusStop.display(false);
            var streetViewCoordinates = {
                lonLat: lonLat,
                heading: asset.bearing + 90
            };
            this._sendShowAttributesRequest(asset.id, streetViewCoordinates);
            this._triggerEvent('mapbusstop.AssetModifiedEvent', asset);
        },

        _triggerEvent: function(key, value) {
            var eventBuilder = this._sandbox.getEventBuilder(key);
            var event = eventBuilder(value);
            this._sandbox.notifyAll(event);
        },

        /**
         * @method onEvent
         * Event is handled forwarded to correct #eventHandlers if found or discarded
         * if not.
         * @param {Oskari.mapframework.event.Event} event a Oskari event object
         */
        onEvent: function (event) {
            return this.eventHandlers[event.getName()].apply(this, [event]);
        },
        /**
         * @method preselectLayers
         * Adds given layers to map if of type WMS
         * @param {Oskari.mapframework.domain.WmsLayer[]} layers
         */
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
                    graphicHeight: 16, graphicWidth: 23, graphicXOffset:-8, graphicYOffset:-8, rotation: angle }
            );
        },
        /**
         * Handle _afterMapMoveEvent
         * @private
         * @param {Oskari.mapframework.event.common.AfterMapLayerAddEvent} event
         */
        _afterMapMoveEvent: function(event) {
            if (this._layers.assetDirection) {
                this._layers.assetDirection.setVisibility(event._zoom >= 8);
            }
            if (_.isObject(this._layers.asset)) {
                this._renderAssets();
            }
        },

        /**
         * Handle _afterMapLayerAddEvent
         * @private
         * @param {Oskari.mapframework.event.common.AfterMapLayerAddEvent} event
         */
        _afterMapLayerAddEvent: function (event) {
            this._addMapLayerToMap(event.getMapLayer(), event.getKeepLayersOrder(), event.isBasemap());
        },
        _featureAttributeChangedEvent: function(event){
            var asset = event.getParameter();
            this._layers.assetDirection.removeFeatures(this._selectedBusStop.directionArrow);
            this._layers.asset.removeMarker(this._selectedBusStop);
            this._addNewAsset(asset);
        },
        _infoBoxClosed: function() {
            if (this._selectedBusStop) {
                this._selectedBusStop.display(true);
            }
        },

        _toolSelectionChange: function(event) {
            this._selectedControl = event.getAction();
            this._selectControl.unselectAll();
        },
        _makeContent: function(imageIds) {
            var contentItem;
            var images = this._makePopupContent(imageIds);
            var htmlContent = this._popupInfoTemplate({busStopsIons : images, changeDirectionButton : "Vaihda suuntaa"});
            contentItem = {
                html: htmlContent,
                actions: {}
            };
            return contentItem;
        },
        _sendPopupRequest:function(id, title, busStopId, content, lonlat, popupClosedCallback) {
            var me = this;
            var requestBuilder = this._sandbox.getRequestBuilder('InfoBox.ShowInfoBoxRequest');
            var request = requestBuilder('busStop', title, [content], lonlat, true);
            this._sandbox.request(this.getName(), request);

            jQuery('.popupInfoChangeDirection').on('click', function() {
                me._directionChange();
            });

            if (_.isFunction(popupClosedCallback)) {
                jQuery('.olPopupCloseBox').on('click', popupClosedCallback);
            }
        },
        _directionChange:function() {
            var eventBuilder = this._sandbox.getEventBuilder('mapbusstop.AssetDirectionChangeEvent');
            var event = eventBuilder({});
            this._sandbox.notifyAll(event);
        },
        /**
         * @method _addMapLayerToMap
         * @private
         * Adds a single BusStop layer to this map
         * @param {Oskari.digiroad2.domain.BusStopLayer} layer
         */
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
                styleMap: me._roadStyles
            });

            this._selectControl = new OpenLayers.Control.SelectFeature(roadLayer);
            var assetDirectionLayer = new OpenLayers.Layer.Vector("assetDirection_" + layer.getId());
            var assetLayer = new OpenLayers.Layer.Markers("asset_" + layer.getId());

            if (this._map.getZoom() > 5) {
                roadLayer.setVisibility(false);
            }

            roadLayer.opacity = layer.getOpacity() / 100;
            assetDirectionLayer.opacity = layer.getOpacity() / 100;
            assetLayer.opacity = layer.getOpacity() / 100;

            me._map.addLayer(roadLayer);
            me._map.addLayer(assetDirectionLayer);
            me._map.addLayer(assetLayer);
            this._layers = {road: roadLayer,
                            assetDirection: assetDirectionLayer,
                            asset: assetLayer};
            this._renderAssets();
            me._sandbox.printDebug("#!#! CREATED OPENLAYER.Markers.BusStop for BusStopLayer " + layer.getId());
        },
        _renderAssets: function() {
            var self = this;
            self._backend.getAssets(10, this._map.getExtent(), function(assets) {
                _.each(assets, function (asset) {
                    if (!_.contains(_.pluck(self._layers.asset.markers, "id"), asset.id)) {
                        var validityDirection = (asset.validityDirection === 3) ? 1 : -1;
                        //Make the feature a plain OpenLayers marker
                        var directionArrow = self._getDirectionArrow(asset.bearing, validityDirection, asset.lon, asset.lat);
                        self._layers.assetDirection.addFeatures(directionArrow);
                        self._addBusStop(asset, self._layers.asset, directionArrow, self._layers.assetDirection, validityDirection);
                    }
                });
            });
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
            var busStop = new OpenLayers.Marker(new OpenLayers.LonLat(assetData.lon, assetData.lat), icon);
            busStop.id = assetData.id;
            busStop.featureContent = assetData.featureData;
            busStop.directionArrow = directionArrow;
            busStop.roadDirection = assetData.bearing;
            busStop.effectDirection = validityDirection; // 1 or -1
            var busStopClick = this._mouseClick(busStop, imageIds);
            var mouseUp = this._mouseUp(busStop, busStops, busStopClick, assetData.id, assetData.assetTypeId);
            var mouseDown = this._mouseDown(busStop, busStops, mouseUp);
            busStop.events.register("mousedown", busStops, mouseDown);
            busStop.validityPeriod = assetData.validityPeriod;
            if (!_.contains(this._selectedValidityPeriods, busStop.validityPeriod)) {
              this._hideAsset(busStop);
            }
            busStops.addMarker(busStop);
            return busStop;
        },
        _mouseUp: function (busStop, busStops, busStopClick, id, typeId) {
            var me = this;
            return function(evt) {
                var bearing ="0";
                if (me._selectedBusStop) {
                    bearing = me._selectedBusStop.roadDirection;
                }
                // Opacity back
                busStop.setOpacity(1);
                busStop.actionMouseDown = false;
                // Not need listeners anymore
                me._map.events.unregister("mouseup", me._map, me._mouseUpFunction);
                // Moved update
                if (busStop.actionDownX != evt.clientX ||  busStop.actionDownY != evt.clientY ) {
                    var data = { "assetTypeId" : typeId, "lon" : busStop.lonlat.lon, "lat" : busStop.lonlat.lat, "roadLinkId": busStop.roadLinkId, "bearing" : bearing };
                    me._sendData(data, id);
                }
                var streetViewCoordinates = { lonLat: busStop.lonlat };
                busStopClick(evt, streetViewCoordinates);
            };
        },
        _remove: function(busStop, removalDate) {
            var propertyValues = [{propertyValue : 0, propertyDisplayValue: removalDate}];
            jQuery.ajax({
                contentType: "application/json",
                type: "PUT",
                url: "api/assets/"+busStop.id+"/properties/validTo/values",
                data: JSON.stringify(propertyValues),
                dataType:"json",
                success: function() {
                    console.log("done");
                },
                error: function() {
                    console.log("error");
                }
            });
        },
        _sendData: function(data, id) {
            jQuery.ajax({
                contentType: "application/json",
                type: "PUT",
                url: "api/assets/" + id,
                data: JSON.stringify(data),
                dataType:"json",
                success: function() {
                    console.log("done");
                },
                error: function() {
                    console.log("error");
                }
            });
        },
        _sendShowAttributesRequest: function(id, point) {
            var requestBuilder = this._sandbox.getRequestBuilder('FeatureAttributes.ShowFeatureAttributesRequest');
            var request = requestBuilder(id, point);
            this._sandbox.request(this.getName(), request);
        },
        _mouseDown: function(busStop, busStops, mouseUp) {
            var me = this;
            return function (evt) {
                if (me._map.getZoom() < 8) {
                    var dialog = Oskari.clazz.create('Oskari.userinterface.component.Popup');
                    dialog.show('Zoomaa lähemmäksi, jos haluat muokata pysäkkejä');
                    dialog.fadeout(3000);
                    return;
                }
                if (me._selectedBusStop) {
                    me._selectedBusStop.display(true);
                }
                me._selectedBusStop = busStop;
                // push marker up
                busStops.removeMarker(busStop);
                busStops.addMarker(busStop);
                // Opacity because we want know what is moving
                busStop.setOpacity(0.6);
                // Mouse need to be down until can be moved
                busStop.actionMouseDown = true;
                //Save orginal position
                busStop.actionDownX = evt.clientX;
                busStop.actionDownY = evt.clientY;
                //register up
                me._map.events.register("mouseup",me._map, mouseUp, true);
                me._mouseUpFunction = mouseUp;
                OpenLayers.Event.stop(evt);
            };
        },
        _mouseClick: function(busStop, imageIds) {
            var me = this;
            return function (evt, streetViewCoordinates) {
                if (me._selectedControl === 'Remove') {
                    var confirm = Oskari.clazz.create('Oskari.userinterface.component.Popup');
                    var okBtn = confirm.createCloseButton("Poista");
                    okBtn.addClass('primary');
                    okBtn.setHandler(function() {
                        me._remove(me._selectedBusStop, dateutil.finnishToIso8601(jQuery('#removeAssetDateInput').val()));
                        confirm.close();
                        me._layers.asset.redraw();
                    });
                    var cancelBtn = confirm.createCloseButton("Peru");
                    confirm.makeModal();
                    confirm.show("Poistetaan käytöstä", me._removeAssetTemplate, [cancelBtn, okBtn]);
                    var removeDateInput = jQuery('#removeAssetDateInput');
                    dateutil.addFinnishDatePicker(removeDateInput.get(0));
                    return;
                }
                me._state = null;
                streetViewCoordinates.heading = busStop.roadDirection + (-90 * busStop.effectDirection);
                me._sendShowAttributesRequest(busStop.id, streetViewCoordinates);
                var contentItem = me._makeContent(imageIds);
                me._sendPopupRequest("busStop", busStop.id, busStop.id, contentItem, busStop.lonlat);
                me._selectedBusStop.display(false);
                OpenLayers.Event.stop(evt);
            };
        },
        _moveSelectedBusStop: function(evt) {
            if (this._map.getZoom() < 10) {
                return;
            }
            if (!this._selectedBusStop) {
                return;
            }
            if (this._selectedControl != 'Select') {
                return;
            }
            if (this._selectedBusStop && this._selectedBusStop.actionMouseDown) {
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
                this._selectedBusStop.roadDirection = angle;
                this._selectedBusStop.directionArrow.style.rotation = angle + (90 * this._selectedBusStop.effectDirection);
                var position = geometrycalculator.nearestPointOnLine(
                    nearestLine,
                    { x: lonlat.lon, y: lonlat.lat});
                lonlat.lon = position.x;
                lonlat.lat = position.y;
                this._selectedBusStop.roadLinkId = nearestLine.roadLinkId;
                this._selectedBusStop.lonlat = lonlat;
                this._selectedBusStop.directionArrow.move(lonlat);
                this._layers.asset.redraw();
            }
        },
        _makePopupContent: function(imageIds) {
            var tmpItems = _.map(imageIds, function(x) { return { imageId: x};});
            return _.map(tmpItems, this._busStopsPopupIcons).join('');
        },
        /**
         * @method getLocalization
         * Returns JSON presentation of bundles localization data for current language.
         * If key-parameter is not given, returns the whole localization data.
         *
         * @param {String} key (optional) if given, returns the value for key
         * @return {String/Object} returns single localization string or JSON object for complete data depending on localization structure and if parameter key is given
         */
        getLocalization : function(key) {
            if(this._localization !== undefined) {
                this._localization = Oskari.getLocalization(this.getName());
            }
            if(key) {
                return this._localization[key];
            }
            return this._localization;
        },
        /**
         * @method _afterMapLayerRemoveEvent
         * Handle AfterMapLayerRemoveEvent
         * @private
         * @param {Oskari.mapframework.event.common.AfterMapLayerRemoveEvent} event
         */
        _afterMapLayerRemoveEvent: function (event) {
            var layer = event.getMapLayer();
            if (!layer.isLayerOfType(this._layerType)) {
                return;
            }
            this._removeMapLayerFromMap(layer);
        },

        /**
         * @method _afterMapLayerRemoveEvent
         * Removes the layer from the map
         * @private
         * @param {Oskari.digiroad2.domain.BusStopLayer} layer
         */
        _removeMapLayerFromMap: function (layer) {
            /* This should free all memory */
            _.each(this._layer[this._layerType +"_"+ layer.getId()], function (tmpLayer) {
                tmpLayer.destroy();
            });
        },
        /**
         * @method getOLMapLayers
         * Returns references to OpenLayers layer objects for requested layer or null if layer is not added to map.
         * @param {Oskari.digiroad2.domain.BusStopLayer} layer
         * @return {OpenLayers.Layer[]}
         */
        getOLMapLayers: function (layer) {
            if (!layer.isLayerOfType(this._layerType)) {
                return null;
            }
            return _.values(this._layers);
        },

        /**
         * @method _afterChangeMapLayerOpacityEvent
         * Handle AfterChangeMapLayerOpacityEvent
         * @private
         * @param {Oskari.mapframework.event.common.AfterChangeMapLayerOpacityEvent} event
         */
        _afterChangeMapLayerOpacityEvent: function (event) {
            var layer = event.getMapLayer();
            if (!layer.isLayerOfType(this._layerType))
                return;

            _.each(this._layer[this._layerType +"_"+ layer.getId()], function (tmpLayer) {
                tmpLayer.setOpacity(layer.getOpacity() / 100);
            });
        }
    }, {
        /**
         * @property {String[]} protocol array of superclasses as {String}
         * @static
         */
        'protocol': ["Oskari.mapframework.module.Module", "Oskari.mapframework.ui.module.common.mapmodule.Plugin"]
    });
