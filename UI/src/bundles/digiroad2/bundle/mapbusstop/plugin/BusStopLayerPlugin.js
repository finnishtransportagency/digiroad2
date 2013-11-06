/**
 * @class Oskari.digiroad2.bundle.mapbusstop.plugin.BusStopLayerPlugin
 * Provides functionality to draw Stats layers on the map
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


            var busStop = new OpenLayers.Layer.Markers( "busStops_" + layer.getId() );

            this._map.addLayer(busStop);
            me._layer[layer.getId()] = busStop;

            // TODO: url usage layer.getLayerUrls()[0];
            jQuery.getJSON( "/data/dummy/busstops.json", function(data) {
                for(var i=0; i<data.length; i++) {
                    busStop.addMarker(new OpenLayers.Marker(new OpenLayers.LonLat(data[i].lon, data[i].lat)));
                }

            })
            .fail(function(data) {
                console.log( "error" );
            });

            //this._sandbox.printDebug("#!#! CREATED OPENLAYER.Markers.BusStop for BusStopLayer " + layer.getId());


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