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

            var sandboxName = (this.config ? this.config.sandbox : null) || 'sandbox';

            // register domain builder
            var mapLayerService = sandbox.getService('Oskari.mapframework.service.MapLayerService');
            if (mapLayerService) {
                mapLayerService.registerLayerModel('busstoplayer', 'Oskari.digiroad2.bundle.mapbusstop.domain.BusStopLayer');
            }

            var size = new OpenLayers.Size(37,34);
            var offset = new OpenLayers.Pixel(-(size.w/2), -size.h);
            this._busStopIcon = new OpenLayers.Icon('/src/resources/digiroad2/bundle/mapbusstop/images/busstop.png',size,offset);


            _.templateSettings = {
                interpolate: /\{\{(.+?)\}\}/g
            };
            this._featureDataTemplate = _.template('<li>{{name}}<input type="text" name="{{name}}" value="{{value}}"</li>');


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


            var busStops = new OpenLayers.Layer.Markers( "busStops_" + layer.getId() );

            this._map.addLayer(busStops);
            me._layer[layer.getId()] = busStops;


            // TODO: url usage layer.getLayerUrls()[0];
            jQuery.getJSON( "/data/dummy/busstops.json", function(data) {
                _.each(data, function (eachData) {
                    me._addBusStop(busStops, new OpenLayers.LonLat(eachData.lon, eachData.lat), eachData.featureData);
                });
            })
            .fail(function() {
                console.log( "error" );
            });

            //this._sandbox.printDebug("#!#! CREATED OPENLAYER.Markers.BusStop for BusStopLayer " + layer.getId());


        },
        //TODO: doc
        _addBusStop: function(busStops, ll, data) {
            var me = this;

            // new bus stop marker
            var busStop = new OpenLayers.Marker(ll, this._busStopIcon.clone());
            busStops.addMarker(busStop);
            var popupId = "busStop";

            //content
            var contentItem = this._makeContent(data);

            //var contentItem = makeBusStopsFeatureContent(data);

            //close button
            contentItem.actions[me.getLocalization('close')] = function() {
                var requestBuilder = me._sandbox.getRequestBuilder('InfoBox.HideInfoBoxRequest');
                var request = requestBuilder(popupId);
                me._sandbox.request(me.getName(), request);
            };

            // click
            var busstopClick = function (evt) {
                var requestBuilder = me._sandbox.getRequestBuilder('InfoBox.ShowInfoBoxRequest');
                var request = requestBuilder(popupId, me.getLocalization('title'), [contentItem], ll, true);
                me._sandbox.request(me.getName(), request);
                OpenLayers.Event.stop(evt);
            };
            busStop.events.register("mousedown", busStops, busstopClick);

            busStops.addMarker(busStop);

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
