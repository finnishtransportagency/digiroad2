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
        this._busStopIcon = [];
        this._selectedBusStop = null;
        this._selectedBusStopLayer = null;
        this._roadStyles = null;
        this._roadLines = null;
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

            var size = new OpenLayers.Size(37,34);
            var offset = new OpenLayers.Pixel(-(size.w/2), -size.h);

            this._busStopIcon['7'] = new OpenLayers.Icon('/src/resources/digiroad2/bundle/mapbusstop/images/busstop.png',size,offset);
            this._busStopIcon['2'] = new OpenLayers.Icon('/src/resources/digiroad2/bundle/mapbusstop/images/busstopLocal.png',size,offset);
            this._busStopIcon['null'] = new OpenLayers.Icon('/src/resources/digiroad2/bundle/mapbusstop/images/busstop.png',size,offset);

            var layerModelBuilder = Oskari.clazz.create('Oskari.digiroad2.bundle.mapbusstop.domain.BusStopLayerModelBuilder', sandbox);
            mapLayerService.registerLayerModelBuilder('busstoplayer', layerModelBuilder);
        },
        _initTemplates: function () {
            var me = this;
            _.templateSettings = {
                interpolate: /\{\{(.+?)\}\}/g
            };

            me._featureDataTemplate = _.template('<li>{{name}}<input type="text" name="{{name}}" value="{{value}}"></li>');
            me._streetViewTemplate  =
                _.template('<a target="_blank" href="http://maps.google.com/?ll={{wgs84Y}},{{wgs84X}}&cbll={{wgs84Y}}' +
                    ',{{wgs84X}}&cbp=12,20.09,,0,5&layer=c&t=m">' +
                    '<img src="http://maps.googleapis.com/maps/api/streetview?size=340x100&location={{wgs84Y}}' +
                    ', {{wgs84X}}&fov=110&heading=10&pitch=-10&sensor=false"></a>');
        },
        _initRoadsStyles: function() {
            this._roadStyles = new OpenLayers.StyleMap({
                "default": new OpenLayers.Style(null, {
                    rules: [
                        new OpenLayers.Rule({
                            symbolizer: {
                                "Line": {
                                    strokeWidth: 3,
                                    strokeOpacity: 1,
                                    strokeColor: "#6666aa"
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
            }
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
                this._addMapLayerToMap(layer, true, layer.isBaseLayer());
            }

        },

        /**
         * Handle _afterMapMoveEvent
         * @private
         * @param {Oskari.mapframework.event.common.AfterMapLayerAddEvent}
         *            event
         */
        _afterMapMoveEvent: function (event) {

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
            if (this._selectedBusStop) { // TODO: need check when direction changed!!!
                this._selectedBusStop.effectDirection = this._selectedBusStop.effectDirection * -1;
                this._selectedBusStop.directionArrow.style.rotation =  this._selectedBusStop.roadDirection+ (90  * this._selectedBusStop.effectDirection);
                this._selectedBusStop.directionArrow.move(this._selectedBusStop.lonlat); // need because redraw();
            }
        },
        /**
         * @method _addMapLayerToMap
         * @private
         * Adds a single BusStop layer to this map
         * @param {Oskari.digiroad2.domain.BusStopLayer} layer
         * @param {Boolean} keepLayerOnTop
         * @param {Boolean} isBaseMap
         */
        _addMapLayerToMap: function (layer, keepLayerOnTop, isBaseMap) {
            var me = this;

            if (!layer.isLayerOfType(this._layerType)) {
                return;
            }

            var busStopsRoads = new OpenLayers.Layer.Vector("busStopsRoads_"+ layer.getId(), {
                strategies: [new OpenLayers.Strategy.Fixed()],
                protocol: new OpenLayers.Protocol.HTTP({
                    url: layer.getRoadLinesUrl(),
                    format: new OpenLayers.Format.GeoJSON()
                }),
                styleMap: me._roadStyles
            });



            var directionLayer = new OpenLayers.Layer.Vector("busStopsDirection_" + layer.getId());


            this._map.addLayer(busStopsRoads);
            me._layer["busStopsRoads_"+layer.getId()] = busStopsRoads;

            var busStops = new OpenLayers.Layer.Markers( "busStops_" + layer.getId() );
            me._map.addControl(new OpenLayers.Control.DragFeature(busStops));
            me._map.addLayer(directionLayer);
            me._map.addLayer(busStops);
            me._layer[layer.getId()] = busStops;


            jQuery.getJSON(layer.getLayerUrls()[0], function(data) {
                _.each(data, function (eachData) {
                    //Make the feature a plain OpenLayers marker
                    var angle = (eachData.bearing) ? eachData.bearing + (90 * -1): 90;
                    var directionArrow = new OpenLayers.Feature.Vector(
                        new OpenLayers.Geometry.Point(eachData.lon, eachData.lat),
                        null,
                        {externalGraphic: '/src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png',
                            graphicHeight: 22, graphicWidth: 30, graphicXOffset:-12, graphicYOffset:-12, rotation: angle }
                    );
                    directionLayer.addFeatures(directionArrow);

                    var imageIds = _.chain(eachData.propertyData)
                        .pluck("values")
                        .flatten()
                        .reject(function(propertyValue) { return propertyValue.imageId == null || propertyValue.imageId == undefined })
                        .map(function(propertyValue) { return propertyValue.imageId })
                        .value()

                    me._addBusStop(eachData.id, busStops, new OpenLayers.LonLat(eachData.lon, eachData.lat), eachData.featureData, eachData.busStopType, angle, layer.getId(), directionArrow, directionLayer, imageIds);

                });
            })
                .fail(function() {
                    console.log( "error" );
                });

            me._sandbox.printDebug("#!#! CREATED OPENLAYER.Markers.BusStop for BusStopLayer " + layer.getId());

        },
        //TODO: doc
        _addBusStop: function(id, busStops, ll, data, type, bearing, layerId, directionArrow, directionLayer, imageIds) {
            var me = this;
            // new bus stop marker
            var size = new OpenLayers.Size(37,34);
            var offset = new OpenLayers.Pixel(-(size.w/2), -size.h);

            icon = new OpenLayers.Icon("/api/images/" + imageIds[0], size, offset);
            var busStop = new OpenLayers.Marker(ll, (icon).clone());

            busStop.id = id;
            busStop.featureContent = data;

            var popupId = "busStop";
            var contentItem = this._makeContent(data);

            //add close button
            contentItem.actions[me.getLocalization('close')] = me._makeCloseButton(me.getName(), popupId);

            busStop.blinking = false;
            busStop.blinkInterVal = null;
            busStop.directionArrow = directionArrow;
            busStop.roadDirection = bearing;
            busStop.effectDirection = -1; // 1 or -1

            var busStopClick = me._mouseClick(busStop, contentItem, popupId);
            var mouseUp = me._mouseUp(busStop, busStops,busStopClick, id);
            var mouseDown = me._mouseDown(busStop, busStops, mouseUp, directionLayer);

            busStop.events.register("mousedown", busStops, mouseDown);

            busStop.layerId = layerId;

            busStops.addMarker(busStop);
        },
        _mouseUp: function (busStop, busStops, busStopClick, id) {
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
                busStop.events.unregister("mouseup", busStops, this);

                // Not moved only click
                if (busStop.actionDownX == evt.clientX && busStop.actionDownY == evt.clientY ) {
                    var point = new OpenLayers.Geometry.Point(busStop.lonlat.lon, busStop.lonlat.lat);
                    var wgs84 = OpenLayers.Projection.transform(point, new OpenLayers.Projection("EPSG:3067"), new OpenLayers.Projection("EPSG:4326"));
                    busStopClick(evt, wgs84);
                } else {
                    // todo remove hardcoded asset type id
                    var data = { "assetTypeId" : 10, "lon" : busStop.lonlat.lon, "lat" : busStop.lonlat.lat, "roadLinkId": busStop.roadLinkId, "bearing" : bearing };
                    me._sendData(data, id);
                }

            };
        },
        _sendData: function(data, id) {
            jQuery.ajax({
                contentType: "application/json",
                type: "PUT",
                url: "/api/assets/" + id, //TODO: get prefix from plugin config
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
        _mouseDown: function(busStop, busStops, mouseUp, directionLayer) {
            var me = this;
            return function (evt) {

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

                OpenLayers.Event.stop(evt);
            };
        },
        _mouseClick: function(busStop, contentItem, popupId) {
            var me = this;
            return function (evt, wgs84Point) {

                var content = _.cloneDeep(contentItem);
                content.html= me._streetViewTemplate({ "wgs84X":wgs84Point.x, "wgs84Y":wgs84Point.y})+contentItem.html.join('');

                var requestBuilder = me._sandbox.getRequestBuilder('InfoBox.ShowInfoBoxRequest');
                var request = requestBuilder(popupId, me.getLocalization('title'), [content], busStop.lonlat, true);
                me._sandbox.request(me.getName(), request);

                requestBuilder = me._sandbox.getRequestBuilder('FeatureAttributes.ShowFeatureAttributesRequest');
                request = requestBuilder(busStop.id, busStop.featureContent);
                me._sandbox.request(me.getName(), request);

                OpenLayers.Event.stop(evt);
            };
        },
        _makeCloseButton: function(name, popupId) {
            var me = this;
            return function() {
                var requestBuilder = me._sandbox.getRequestBuilder('InfoBox.HideInfoBoxRequest');
                var request = requestBuilder(popupId);
                me._sandbox.request(name, request);
            };
        },
        _moveSelectedBusStop: function(evt) {
            var me = this;
            if (me._selectedBusStop && me._selectedBusStop.actionMouseDown) {

                var pxPosition = this._map.getPixelFromLonLat(new OpenLayers.LonLat(evt.getLon(), evt.getLat()));

                pxPosition.y = pxPosition.y + this._busStopIcon['null'].size.h/2;

                var busStopCenter = new OpenLayers.Pixel(pxPosition.x,pxPosition.y);
                var lonlat = me._map.getLonLatFromPixel(busStopCenter);

                var nearestLine = geometrycalculator.findNearestLine(me._layer["busStopsRoads_"+me._selectedBusStop.layerId].features, lonlat.lon, lonlat.lat);
                var angle = geometrycalculator.getLineDirectionDegAngle(nearestLine);

                this._selectedBusStop.roadDirection = angle;
                this._selectedBusStop.directionArrow.style.rotation = angle+ (90 * this._selectedBusStop.effectDirection);

                var position = geometrycalculator.nearestPointOnLine(
                    nearestLine,
                    { x: lonlat.lon, y: lonlat.lat});

                var radius =(13-me._map.getZoom())*3.8;

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
                me._selectedBusStop.lonlat = lonlat;
                this._selectedBusStop.directionArrow.move(lonlat);
                this._selectedBusStopLayer.redraw();

            }
        },
        _makeContent: function(data) {
            var tmplItems = _.map(_.pairs(data), function(x) { return { name: x[0], value: x[1] };});
            var htmlContent = _.map(tmplItems, this._featureDataTemplate);

            var contentItem = {
                html : htmlContent,
                actions : {}
            };
            return contentItem;
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
        /*
         _.map(rows, function(row){

         });*/
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
            this._layer[layer.getId()].destroy();
            this._layer["busStopsRoads_"+layer.getId()].destroy();
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

            return [this._layer[layer.getId()]];
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

            this._sandbox.printDebug("Setting Layer Opacity for " + layer.getId() + " to " + layer.getOpacity());
            if (this._layer[layer.getId()] !== null) {
                this._layer[layer.getId()].setOpacity(layer.getOpacity() / 100);
            }
        }
    }, {
        /**
         * @property {String[]} protocol array of superclasses as {String}
         * @static
         */
        'protocol': ["Oskari.mapframework.module.Module", "Oskari.mapframework.ui.module.common.mapmodule.Plugin"]
    });
