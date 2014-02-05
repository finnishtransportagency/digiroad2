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
        this._layer = {};
        this._localization = null;
        this._selectedBusStop = null;
        this._selectedBusStopLayer = null;
        this._roadStyles = null;
        this._selectedControl = 'Select';
        this._selectedLayerId = "235";
    }, {
        /** @static @property __name plugin name */
        __name: 'BusStopLayerPlugin',

        /** @static @property _layerType type of layers this plugin handles */
        _layerType: 'busstoplayer',

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
         * @param {Oskari.mapframework.ui.module.common.MapModule} reference to map
         * module
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
            var sandboxName = (this.config ? this.config.sandbox : null) || 'sandbox';

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
            me._busStopsPopupIcons = _.template('<img src="/api/images/{{imageId}}">');
            me._removeAssetTemplate = _.template('<div>Aseta poistopäivämäärä:</div><div><input id="removeAssetDateInput" class="featureAttributeDate" type="text" />&nbsp;<span class="attributeFormat">pp.kk.vvvv</span></div>');
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
                sandbox.registerForEventByName(this, p);
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
                sandbox.unregisterFromEventByName(this, p);
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
                this._featureAttributeChangedEvent(event);
            },
            'infobox.InfoBoxClosedEvent': function (event) {
                this._infoBoxClosed(event);
            },
            'actionpanel.ActionPanelToolSelectionChangedEvent': function (event) {
                this._toolSelectionChange(event);
            },'MapClickedEvent': function (event) {
                this._addBusStopEvent(event);
            }
        },
        _addBusStopEvent: function(event){
            var me = this;
            if (this._selectedControl == 'Add') {
                var nearestLine = geometrycalculator.findNearestLine(this._layer[this._layerType + "_" + this._selectedLayerId][0].features, event.getLonLat().lon, event.getLonLat().lat);
                var bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
                var data = { "assetTypeId" : 10, "lon" : event.getLonLat().lon, "lat" : event.getLonLat().lat, "roadLinkId": nearestLine.roadLinkId, "bearing" : bearing };
                jQuery.ajax({
                    contentType: "application/json",
                    type: "PUT",
                    url: "/api/asset",
                    data: JSON.stringify(data),
                    dataType:"json",
                    success: function(asset) {
                        me._addNewAsset(asset);
                    },
                    error: function() {
                        console.log("error");
                    }
                });
            }
        },
        _addNewAsset: function(asset) {
            var imageIds = ["99"];
            var lonLat = { lon : asset.lon, lat : asset.lat};
            var contentItem = this._makeContent(imageIds);
            var angle = this._getAngleFromBearing(asset.bearing, 1);
            var directionArrow = this._getDirectionArrow(angle, asset.lon, asset.lat);
            this._layer[this._layerType + "_" + this._selectedLayerId][1].addFeatures(directionArrow);
            this._selectedBusStop = this._addBusStop(asset.id, this._layer[this._layerType + "_" +this._selectedLayerId][2],
                new OpenLayers.LonLat(lonLat.lon, lonLat.lat), asset.featureData, asset.bearing, this._selectedLayerId,
                directionArrow, this._layer[this._layerType + "_" +this._selectedLayerId][1], imageIds, asset.assetTypeId, 1);
            this._selectedBusStopLayer = this._layer[this._layerType + "_" +this._selectedLayerId][2];
            this._sendPopupRequest("busStop", asset.id, contentItem, lonLat);
            this._selectedBusStop.display(false);
            var point = new OpenLayers.Geometry.Point(asset.lon, asset.lat);
            var wgs84 = OpenLayers.Projection.transform(point, new OpenLayers.Projection("EPSG:3067"), new OpenLayers.Projection("EPSG:4326"));
            wgs84.heading = asset.bearing + 90;
            this._sendShowAttributesRequest(asset.id, wgs84);
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
            var sandbox = this._sandbox;
            for (var i = 0; i < layers.length; i++) {
                var layer = layers[i];
                var layerId = layer.getId();

                if (!layer.isLayerOfType(this._layerType)) {
                    continue;
                }
                sandbox.printDebug("preselecting " + layerId);
                this._addMapLayerToMap(layer);
            }
        },
        _getAngleFromBearing: function(bearing, validityDirection) {
            return (bearing) ? bearing + (90 * validityDirection): 90;
        },
        _getDirectionArrow: function(angle, lon, lat) {
            return new OpenLayers.Feature.Vector(
                new OpenLayers.Geometry.Point(lon, lat),
                null,
                {externalGraphic: '/src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png',
                    graphicHeight: 16, graphicWidth: 23, graphicXOffset:-8, graphicYOffset:-8, rotation: angle }
            );
        },
        /**
         * Handle _afterMapMoveEvent
         * @private
         * @param {Oskari.mapframework.event.common.AfterMapLayerAddEvent}
         *            event
         */
        _afterMapMoveEvent: function (event) {
            _.forEach(this._layer, function (layer) {
               if(event._zoom < 8) {
                   layer[1].setVisibility(false);
               } else {
                   layer[1].setVisibility(true);
               }
            });
        },

        /**
         * Handle _afterMapLayerAddEvent
         * @private
         * @param {Oskari.mapframework.event.common.AfterMapLayerAddEvent}
         *            event
         */
        _afterMapLayerAddEvent: function (event) {
            this._addMapLayerToMap(event.getMapLayer(), event.getKeepLayersOrder(), event.isBasemap());
        },
        _featureAttributeChangedEvent: function(event){
            if (this._selectedBusStop) {
                var parameters = event.getParameter();
                if (parameters) {
                    var displayValue = parameters[0].propertyDisplayValue;
                    if(displayValue == "Pysäkin tyyppi") {
                        this._handleBusStopTypes(parameters);
                    } else if (displayValue == "Vaikutussuunta") {
                        this._changeDirection();
                    }
                }
            }
        },
        _infoBoxClosed: function(event) {
            if (this._selectedBusStop) {
                this._selectedBusStop.display(true);
            }
        },
        _toolSelectionChange: function(event) {
            this._selectedControl = event.getAction();
        },_handleBusStopTypes: function(parameters) {
            var imageIds;
            var contentItem;
            var me = this;

            imageIds = _.map(parameters, function (x) {
                return x.propertyValue+"_";
            });
            contentItem = this._makeContent(imageIds);
            this._sendPopupRequest("busStop", me._selectedBusStop.id, contentItem, me._selectedBusStop.lonlat);

            var lonlat = this._selectedBusStop.lonlat;
            var id = this._selectedBusStop.id;
            var featureData = this._selectedBusStop.featureContent;
            var bearing =  this._selectedBusStop.roadDirection;
            var directionArrow = this._selectedBusStop.directionArrow;
            var directionLayer = this._layer[this._layerType +"_"+ this._selectedBusStop.layerId][1];
            var validityDirection = this._selectedBusStop.effectDirection;
            var effectDirection = this._selectedBusStop.effectDirection;
            var roadDirection = this._selectedBusStop.roadDirection;
            var layer = _.find(this._selectedBusStopLayer.markers, function(marker) {
                return marker.id == me._selectedBusStop.id;
            });
            this._layer[this._layerType +"_"+ this._selectedBusStop.layerId][2].removeMarker(layer);
            me._addBusStop(id, this._selectedBusStopLayer, lonlat, featureData, bearing,
                this._selectedBusStop.layerId, directionArrow, directionLayer, imageIds, 10, validityDirection);

            var point = new OpenLayers.Geometry.Point(lonlat.lon, lonlat.lat);
            var wgs84 = OpenLayers.Projection.transform(point, new OpenLayers.Projection("EPSG:3067"), new OpenLayers.Projection("EPSG:4326"));
            wgs84.heading = roadDirection + (90  * effectDirection);
            me._sendShowAttributesRequest(id, wgs84);
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
        _sendPopupRequest:function(id, busStopId, content, lonlat) {
            var me = this;
            var requestBuilder = this._sandbox.getRequestBuilder('InfoBox.ShowInfoBoxRequest');
            var request = requestBuilder("busStop", busStopId, [content], lonlat, true);
            this._sandbox.request(this.getName(), request);

            var point = new OpenLayers.Geometry.Point(lonlat.lon, lonlat.lat);
            var wgs84 = OpenLayers.Projection.transform(point, new OpenLayers.Projection("EPSG:3067"), new OpenLayers.Projection("EPSG:4326"));

            jQuery(".popupInfoChangeDirection").on("click", function() {
                me._directionChange(busStopId, wgs84);
            });
        },
        _directionChange:function(id, point) {
            this._changeDirection();
            var me = this;
            var value = (this._selectedBusStop.effectDirection == 1) ? 3:2;
            var propertyValues = [{propertyValue : value, propertyDisplayValue: "Vaikutussuunta"}];

            jQuery.ajax({
                contentType: "application/json",
                type: "PUT",
                url: "/api/assets/"+id+"/properties/validityDirection/values",
                data: JSON.stringify(propertyValues),
                dataType:"json",
                success: function() {
                    point.heading = me._selectedBusStop.roadDirection+ (90  * -me._selectedBusStop.effectDirection);
                    var requestBuilder = me._sandbox.getRequestBuilder('FeatureAttributes.ShowFeatureAttributesRequest');
                    var request = requestBuilder(id, point);
                    me._sandbox.request(me.getName(), request);
                    console.log("done");
                },
                error: function() {
                    console.log("error");
                }
            });
        },
        _changeDirection: function() {
            this._selectedBusStop.effectDirection = this._selectedBusStop.effectDirection == 1 ? -1 : 1;
            this._selectedBusStop.directionArrow.style.rotation =  this._selectedBusStop.roadDirection+ (90  * this._selectedBusStop.effectDirection);
            this._selectedBusStop.directionArrow.move(this._selectedBusStop.lonlat); // need because redraw();
        },
        /**
         * @method _addMapLayerToMap
         * @private
         * Adds a single BusStop layer to this map
         * @param {Oskari.digiroad2.domain.BusStopLayer} layer
         * @param {Boolean} keepLayerOnTop
         * @param {Boolean} isBaseMap
         */
        _addMapLayerToMap: function (layer) {
            var me = this;
            if (!layer.isLayerOfType(this._layerType)) {
                return;
            }
            var layers = [];
            var busStopsRoads = new OpenLayers.Layer.Vector("busStopsRoads_"+ layer.getId(), {
                strategies: [new OpenLayers.Strategy.BBOX(), new OpenLayers.Strategy.Refresh()],
                protocol: new OpenLayers.Protocol.HTTP({
                    url: layer.getRoadLinesUrl(),
                    format: new OpenLayers.Format.GeoJSON()
                }),
                styleMap: me._roadStyles
            });

            this._selectControl = new OpenLayers.Control.SelectFeature(busStopsRoads);
            var directionLayer = new OpenLayers.Layer.Vector("busStopsDirection_" + layer.getId());
            var busStops = new OpenLayers.Layer.Markers("busStops_" + layer.getId());

            if (this._map.getZoom() > 5) {
                busStopsRoads.setVisibility(false);
            }

            busStopsRoads.opacity = layer.getOpacity() / 100;
            directionLayer.opacity = layer.getOpacity() / 100;
            busStops.opacity = layer.getOpacity() / 100;

            me._map.addLayer(busStopsRoads);
            me._map.addLayer(directionLayer);
            me._map.addLayer(busStops);
            layers.push(busStopsRoads);
            layers.push(directionLayer);
            layers.push(busStops);

            me._layer[this._layerType +"_"+ layer.getId()] = layers;

             jQuery.getJSON(layer.getLayerUrls()[0], function(data) {
                _.each(data, function (eachData) {

                    var validityDirectionProperty = _.find(eachData.propertyData, function(property) {
                        return property.propertyId == "validityDirection";
                    });

                    var validityDirection = (validityDirectionProperty.values[0].propertyValue == 3) ? 1 : -1;
                    //Make the feature a plain OpenLayers marker
                    var angle = me._getAngleFromBearing(eachData.bearing, validityDirection);
                    var directionArrow = me._getDirectionArrow(angle, eachData.lon, eachData.lat);
                    directionLayer.addFeatures(directionArrow);

                    var imageIds = _.chain(eachData.propertyData)
                        .pluck("values")
                        .flatten()
                        .reject(function(propertyValue) { return propertyValue.imageId === null || propertyValue.imageId === undefined; })
                        .map(function(propertyValue) { return propertyValue.imageId; })
                        .value();

                    me._addBusStop(eachData.id, busStops, new OpenLayers.LonLat(eachData.lon, eachData.lat),
                        eachData.featureData, eachData.bearing, layer.getId(), directionArrow, directionLayer, imageIds,
                    eachData.assetTypeId, validityDirection);

                });
            })
                .fail(function() {
                    console.log( "error" );
                });
            me._sandbox.printDebug("#!#! CREATED OPENLAYER.Markers.BusStop for BusStopLayer " + layer.getId());
        },
        _addBusStop: function(id, busStops, ll, data, bearing, layerId, directionArrow, directionLayer, imageIds, typeId,
                              validityDirection ) {
            var me = this;
            // new bus stop marker
            var size = new OpenLayers.Size(28, 16 * imageIds.length);
            var offset = new OpenLayers.Pixel(-(size.w/2+1), -size.h-5);
            var icon = new OpenLayers.Icon("", size, offset);
            icon.imageDiv.className = "callout-wrapper";
            icon.imageDiv.removeChild(icon.imageDiv.getElementsByTagName("img")[0]);
            icon.imageDiv.setAttribute("style", "");
            var callout = document.createElement("div");
            callout.className = "callout";
            var arrowContainer = document.createElement("div");
            arrowContainer.className = "arrow-container";
            var arrow = document.createElement("div");
            arrow.className = "arrow";
            icon.imageDiv.appendChild(callout);
            _.each(imageIds, function (imageId) {
                var img = document.createElement("img");
                img.setAttribute("src", "/api/images/" + imageId + ".png");
                callout.appendChild(img);
            });
            arrowContainer.appendChild(arrow);
            callout.appendChild(arrowContainer);

            var busStop = new OpenLayers.Marker(ll, icon);
            busStop.id = id;
            busStop.featureContent = data;
            busStop.blinking = false;
            busStop.blinkInterVal = null;
            busStop.directionArrow = directionArrow;
            busStop.roadDirection = bearing;
            busStop.effectDirection = validityDirection; // 1 or -1

            var busStopClick = me._mouseClick(busStop, imageIds);
            var mouseUp = me._mouseUp(busStop, busStops,busStopClick, id, typeId);
            var mouseDown = me._mouseDown(busStop, busStops, mouseUp);
            busStop.events.register("mousedown", busStops, mouseDown);
            busStop.layerId = layerId;
            busStops.addMarker(busStop);
            return busStop;
        },
        _mouseUp: function (busStop, busStops, busStopClick, id, typeId) {
            var me = this;
            return function(evt) {
                if (me._selectedBusStop && me._selectedBusStop.blinking) {
                    clearInterval(me._selectedBusStop.blinkInterVal);
                    busStop.blinkInterVal = setInterval(function(){me._busStopBlink(busStop);}, 600);
                }
                var bearing ="0";
                if (me._selectedBusStop) {
                    bearing = me._selectedBusStop.roadDirection;
                }
                // Opacity back
                busStop.setOpacity(1);
                busStop.actionMouseDown = false;
                // Not need listeners anymore
                busStop.events.unregister("mouseup", busStops, me._mouseUpFunction);
                // Not moved only click
                if (busStop.actionDownX == evt.clientX && busStop.actionDownY == evt.clientY ) {
                    var point = new OpenLayers.Geometry.Point(busStop.lonlat.lon, busStop.lonlat.lat);
                    var wgs84 = OpenLayers.Projection.transform(point, new OpenLayers.Projection("EPSG:3067"), new OpenLayers.Projection("EPSG:4326"));
                    busStopClick(evt, wgs84);
                } else {
                    var data = { "assetTypeId" : typeId, "lon" : busStop.lonlat.lon, "lat" : busStop.lonlat.lat, "roadLinkId": busStop.roadLinkId, "bearing" : bearing };
                    me._sendData(data, id);
                }
            };
        },
        _remove: function(busStop, removalDate) {
            var propertyValues = [{propertyValue : 0, propertyDisplayValue: removalDate}];
            jQuery.ajax({
                contentType: "application/json",
                type: "PUT",
                url: "/api/assets/"+busStop.id+"/properties/validTo/values",
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
                url: "/api/assets/" + id,
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
                    return;
                }
                if (me._selectedBusStop) {
                    me._selectedBusStop.display(true);
                }
                me._selectedBusStop = busStop;
                me._selectedBusStopLayer = busStops;
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
                //register move and up
                busStop.events.register("mouseup", busStops, mouseUp);
                me._mouseUpFunction = mouseUp;
                OpenLayers.Event.stop(evt);
            };
        },
        _mouseClick: function(busStop, imageIds) {
            var me = this;
            return function (evt, point) {
                if (me._selectedControl === 'Remove') {
                    var confirm = Oskari.clazz.create('Oskari.userinterface.component.Popup');
                    var okBtn = confirm.createCloseButton("Poista");
                    okBtn.addClass('primary');
                    okBtn.setHandler(function() {
                        me._remove(me._selectedBusStop, dateutil.finnishToIso8601(jQuery('#removeAssetDateInput').val()));
                        confirm.close();
                        me._selectedBusStopLayer.redraw();
                    });
                    var cancelBtn = confirm.createCloseButton("Peru");
                    confirm.makeModal();
                    confirm.show("Poistetaan käytöstä", me._removeAssetTemplate, [cancelBtn, okBtn]);

                    var removeDateInput = jQuery('#removeAssetDateInput');
                    removeDateInput.val(dateutil.todayInFinnishFormat());
                    dateutil.addFinnishDatePicker(removeDateInput.get(0));

                    return;
                }
                point.heading = busStop.roadDirection+ (90  * -busStop.effectDirection);
                me._sendShowAttributesRequest(busStop.id, point);
                var contentItem = me._makeContent(imageIds);
                me._sendPopupRequest("busStop", busStop.id, contentItem, busStop.lonlat);
                me._selectedBusStop.display(false);
                OpenLayers.Event.stop(evt);
            };
        },
        _moveSelectedBusStop: function(evt) {
            if (this._map.getZoom() < 10) {
                return;
            }
            if (!this._selectedBusStop) {
                return null;
            }
            if (this._selectedControl != 'Select') {
                return;
            }
            if (this._selectedBusStop && this._selectedBusStop.actionMouseDown) {
                var me = this;
                var pxPosition = this._map.getPixelFromLonLat(new OpenLayers.LonLat(evt.getLon(), evt.getLat()));
                pxPosition.y = pxPosition.y + 34/2; // FIXME: read actual size from current asset
                var busStopCenter = new OpenLayers.Pixel(pxPosition.x,pxPosition.y);
                var lonlat = this._map.getLonLatFromPixel(busStopCenter);
                var nearestLine = geometrycalculator.findNearestLine(this._layer[this._layerType +"_"+ this._selectedBusStop.layerId][0].features, lonlat.lon, lonlat.lat);
                var nearestFeature = _.find(this._layer[this._layerType +"_"+ this._selectedBusStop.layerId][0].features, function(feature) {
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
                var radius =(13-this._map.getZoom())*3.8;

                if (geometrycalculator.isInCircle(lonlat.lon, lonlat.lat, radius, position.x, position.y)) {
                    lonlat.lon = position.x;
                    lonlat.lat = position.y;
                    this._selectedBusStop.roadLinkId = nearestLine.roadLinkId;
                    if (this._selectedBusStop.blinking) {
                        clearInterval(this._selectedBusStop.blinkInterVal);
                        this._selectedBusStop.setOpacity(0.6);
                        this._selectedBusStop.blinking = false;
                    }
                } else if(!this._selectedBusStop.blinking) {
                    //blink
                    this._selectedBusStop.blinking = true;
                    this._selectedBusStop.blinkInterVal = setInterval(function(){me._busStopBlink(me._selectedBusStop);}, 600);
                }
                this._selectedBusStop.lonlat = lonlat;
                this._selectedBusStop.directionArrow.move(lonlat);
                this._selectedBusStopLayer.redraw();
            }
        },
        _makePopupContent: function(imageIds) {
            var tmpItems = _.map(imageIds, function(x) { return { imageId: x};});
            var htmlContent = _.map(tmpItems, this._busStopsPopupIcons).join('');
            return htmlContent;
        },
        _busStopBlink: function(busStop) {
            if (busStop.blink) {
                busStop.setOpacity(0.3);
                busStop.blink = false;
            } else {
                busStop.setOpacity(0.9);
                busStop.blink = true;
            }
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
         * @param {Oskari.mapframework.event.common.AfterMapLayerRemoveEvent}
         *            event
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
            return this._layer[this._layerType +"_"+ layer.getId()];
        },

        /**
         * @method _afterChangeMapLayerOpacityEvent
         * Handle AfterChangeMapLayerOpacityEvent
         * @private
         * @param {Oskari.mapframework.event.common.AfterChangeMapLayerOpacityEvent}
         *            event
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
