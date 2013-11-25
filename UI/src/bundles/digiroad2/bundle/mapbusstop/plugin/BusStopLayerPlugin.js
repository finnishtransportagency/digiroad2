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

            //this._featureDataTemplate = _.template('<li>{{name}}<input type="text" name="{{name}}" value="{{value}}"</li>');
            me._initTemplates();

            var size = new OpenLayers.Size(37,34);
            var offset = new OpenLayers.Pixel(-(size.w/2), -size.h);

            this._busStopIcon['7'] = new OpenLayers.Icon('/src/resources/digiroad2/bundle/mapbusstop/images/busstop.png',size,offset);
            this._busStopIcon['2'] = new OpenLayers.Icon('/src/resources/digiroad2/bundle/mapbusstop/images/busstopLocal.png',size,offset);
            this._busStopIcon['null'] = new OpenLayers.Icon('/src/resources/digiroad2/bundle/mapbusstop/images/busstop.png',size,offset);


        },
        _initTemplates: function () {
            var me = this;
            _.templateSettings = {
                interpolate: /\{\{(.+?)\}\}/g
            };

            me._featureDataTemplate = _.template('<li>{{name}}<input type="text" name="{{name}}" value="{{value}}"></li>');
            me._streetViewTemplate  =
                _.template('<a target="_blank" href="http://maps.google.com/?cbll={{wgs84Y}}' +
                           ',{{wgs84X}}&cbp=12,20.09,,0,5&layer=c">' +
                           '<img src="http://maps.googleapis.com/maps/api/streetview?size=340x100&location={{wgs84Y}}' +
                           ', {{wgs84X}}&fov=110&heading=10&pitch=-10&sensor=false"></a>');

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

            var styles = new OpenLayers.StyleMap({
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
                }),
                "select": new OpenLayers.Style(null, {
                    rules: [
                        new OpenLayers.Rule({
                            symbolizer: {
                                "Line": {
                                    strokeWidth: 3,
                                    strokeOpacity: 1,
                                    strokeColor: "#0000ff"
                                }
                            }
                        })
                    ]
                }),
                "temporary": new OpenLayers.Style(null, {
                    rules: [
                        new OpenLayers.Rule({
                            symbolizer: {
                                "Line": {
                                    strokeWidth: 3,
                                    strokeOpacity: 1,
                                    strokeColor: "#0000ff"
                                }
                            }
                        })
                    ]
                })
            });

           //TODO: url need to be get from config
            var busStopsRoads = new OpenLayers.Layer.Vector("busStopsRoads_"+ layer.getId(), {
                strategies: [new OpenLayers.Strategy.Fixed()],
                protocol: new OpenLayers.Protocol.HTTP({
                    url: "/data/dummy/busstopsRoads.json",
                    format: new OpenLayers.Format.GeoJSON()
                }),
                styleMap: styles
            });

            this._map.addLayer(busStopsRoads);

            var busStops = new OpenLayers.Layer.Markers( "busStops_" + layer.getId() );

            me._map.addControl(new OpenLayers.Control.DragFeature(busStops));

            this._map.addLayer(busStops);
            me._layer[layer.getId()] = busStops;


            // TODO: url usage layer.getLayerUrls()[0];
            // TODO: make API url configurable
            jQuery.getJSON( "/api/busstops", function(data) {
            //jQuery.getJSON( "/data/dummy/busstops.json", function(data) {

                _.each(data, function (eachData) {
                    me._addBusStop(eachData.id, busStops, new OpenLayers.LonLat(eachData.lon, eachData.lat), eachData.featureData, eachData.busStopType, busStopsRoads);
                });
            })
            .fail(function() {
                console.log( "error" );
            });

            //this._sandbox.printDebug("#!#! CREATED OPENLAYER.Markers.BusStop for BusStopLayer " + layer.getId());


        },
        //TODO: doc
        _addBusStop: function(id, busStops, ll, data, type, lns) {
            var me = this;
            var lines = lns;

            // new bus stop marker
            var busStop = new OpenLayers.Marker(ll, (this._busStopIcon["2"]).clone());

            if (!type) {
                busStop = new OpenLayers.Marker(ll, (this._busStopIcon[type]).clone());
            }

            var popupId = "busStop";

            //content
            var contentItem = this._makeContent(data);

            //close button
            contentItem.actions[me.getLocalization('close')] = function() {
                var requestBuilder = me._sandbox.getRequestBuilder('InfoBox.HideInfoBoxRequest');
                var request = requestBuilder(popupId);
                me._sandbox.request(me.getName(), request);
            };


            var busStopClick = function (evt, wgs84Point) {

                var content = _.cloneDeep(contentItem);

                content.html= me._streetViewTemplate({ "wgs84X":wgs84Point.x, "wgs84Y":wgs84Point.y})+
                              contentItem.html.join('');

                var requestBuilder = me._sandbox.getRequestBuilder('InfoBox.ShowInfoBoxRequest');
                var request = requestBuilder(popupId, me.getLocalization('title'), [content], busStop.lonlat, true);
                me._sandbox.request(me.getName(), request);
                OpenLayers.Event.stop(evt);
            };


            var moveBusStop = function(evt) {
                if (busStop.actionMouseDown) {
                    var busStopCenter = new OpenLayers.Pixel(evt.clientX - busStop.icon.size.w/4,evt.clientY + busStop.icon.size.h/4);
                    var lonlat = me._map.getLonLatFromPixel(busStopCenter);

                    var nearestLine = me._findNearestLine(lines.features, lonlat.lon,lonlat.lat);
                    var position = me._nearestPointOnLine(
                        nearestLine,
                        lonlat);

                    var distance = 20;


                    if (Math.abs(lonlat.lon-position.x) + Math.abs(lonlat.lat-position.y) < distance) {
                        lonlat.lon = position.x;
                        lonlat.lat = position.y;
                        busStop.roadLinkId = nearestLine.id;
                    }

                    busStop.lonlat = lonlat;
                    busStops.redraw();

                }
                OpenLayers.Event.stop(evt);
            };


            var mouseUp = function(evt) {
                // Opacity back
                busStop.setOpacity(1);
                busStop.actionMouseDown = false;

                // Not need listeners anymore
                busStop.events.unregister("mousemove", busStops, moveBusStop);
                busStop.events.unregister("mouseup", busStops, mouseUp);

                // Not moved only click
                if (busStop.actionDownX == evt.clientX && busStop.actionDownY == evt.clientY ) {
                    var point = new OpenLayers.Geometry.Point(busStop.lonlat.lon, busStop.lonlat.lat);
                    var wgs84 = OpenLayers.Projection.transform(point, new OpenLayers.Projection("EPSG:3067"), new OpenLayers.Projection("EPSG:4326"));
                    busStopClick(evt, wgs84);
                } else {
                    console.log('['+Math.round(busStop.lonlat.lon) + ', ' + Math.round(busStop.lonlat.lat)+']');
                    console.dir(busStop);
                    var data = {"lon" : busStop.lonlat.lon, "lat" : busStop.lonlat.lat, "roadLinkId": busStop.roadLinkId };

                    jQuery.ajax({
                        type: "PUT",
                        url: "/api/busstops/" + id  +"/", //TODO: get prefix from plugin config
                        data: data,
                        dataType:"json",
                        success: function() {
                            console.log("done");
                        },
                        error: function() {
                            console.log("error");
                        }
                    });
                }

            };

            var mouseDown = function(evt) {
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
                busStop.events.register("mousemove", busStops, moveBusStop);
                busStop.events.register("mouseup", busStops, mouseUp);

                OpenLayers.Event.stop(evt);
            };

            busStop.events.register("mousedown", busStops, mouseDown);

            busStops.addMarker(busStop);

        },
        _findNearestLine: function(features, x, y) {
            var me = this;
            var distance  = -1;
            var nearest = {};
            // TODO: ugly plz, use lodash
            for(var i = 0; i < features.length; i++) {
                for(var j = 0; j < features[i].geometry.components.length-1; j++) {
                    var currentDistance = geometrycalculator.getDistanceFromLine(
                        features[i].geometry.components[j].x,
                        features[i].geometry.components[j].y,
                        features[i].geometry.components[j+1].x,
                        features[i].geometry.components[j+1].y,
                        x, y);
                    if ( distance == -1 || distance > currentDistance) {
                        nearest.startX = features[i].geometry.components[j].x;
                        nearest.startY = features[i].geometry.components[j].y;
                        nearest.endX = features[i].geometry.components[j+1].x;
                        nearest.endY = features[i].geometry.components[j+1].y;
                        nearest.id = features[i].id;
                        distance = currentDistance;
                    }
                }
            }
            return nearest;
        },
        _getDistance: function(x1, y1, x2, y2, x3, y3) {
            var px = x2-x1;
            var py = y2-y1;

            var something = px*px + py*py;

            var u =  ((x3 - x1) * px + (y3 - y1) * py) / something;

            if (u > 1) {
                u = 1;
            } else if (u < 0) {
                u = 0;
            }

            x = x1 + u * px;
            y = y1 + u * py;

            dx = x - x3;
            dy = y - y3;

            dist = Math.sqrt(dx*dx + dy*dy);

            return dist;

        },
        _nearestPointOnLine: function(line, point) {

            var apx = point.lon - line.startX;
            var apy = point.lat - line.startY;
            var abx = line.endX - line.startX;
            var aby = line.endY - line.startY;

            var ab2 = abx * abx + aby * aby;
            var ap_ab = apx * abx + apy * aby;
            var t = ap_ab / ab2;


            if (t < 0) {
                t = 0;
            } else if (t > 1) {
                t = 1;
            }

            var position = {};
            position.x = line.startX + abx * t;
            position.y = line.startY + aby * t;

            return position;
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
