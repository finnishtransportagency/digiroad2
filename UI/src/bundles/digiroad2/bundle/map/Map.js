Oskari.clazz.define('Oskari.digiroad2.bundle.map.Map',
    function (config) {
        this.mapModule = null;
        this.pluginName = null;
        this._sandbox = null;
        this._map = null;
        this._supportedFormats = {};
        this._localization = null;
        this._state = undefined;

        function defineDependency(dependencyName, defaultImplementation) {
            var dependency = _.isObject(config) ? config[dependencyName] : null;
            return dependency || defaultImplementation;
        }
    }, {
        __name: 'Map',
        _layerType: 'map',
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
            this.getMapModule().setLayerPlugin('map', this);
        },
        unregister: function () {
            this.getMapModule().setLayerPlugin('map', null);
        },
        onEvent: function (event) {
            return this.eventHandlers[event.getName()].apply(this, [event]);
        },
        init: function (sandbox) {
            eventbus.on('application:initialized', function() {
                this._zoomNotInMessage = this._getNotInZoomRange();
                this._oldZoomLevel = zoomlevels.isInAssetZoomLevel(this._map.getZoom()) ? this._map.getZoom() : -1;
                this._zoomNotInMessage();
                new CoordinateSelector($('.mapplugin.coordinates'));
            }, this);
            eventbus.on('application:readOnly', function(readOnly) {
                this._readOnly = readOnly;
                this._selectControl.unselectAll();
            }, this);
            eventbus.on('asset:moving', function(nearestLine) {
                var nearestFeature = _.find(this._layers.road.features, function(feature) {
                    return feature.id == nearestLine.id;
                });
                this._selectControl.unselectAll();
                this._selectControl.select(nearestFeature);
            }, this);
            eventbus.on('asset:cancelled', function() {
                this._selectControl.unselectAll();
            }, this);
            eventbus.on('asset:unselected validityPeriod:changed layer:selected', function(){
                this._selectControl.unselectAll();
            }, this);

            eventbus.on('tool:changed', function(action) {
                var cursor = {'Select' : 'default', 'Add' : 'crosshair', 'Remove' : 'no-drop'};
                $('.olMap').css('cursor', cursor[action]);
            });

            eventbus.on('coordinates:selected', function(position) {
                this._sandbox.postRequestByName('MapMoveRequest', [position.lon, position.lat, 11]);
            }, this);

            // register domain builder
            var mapLayerService = sandbox.getService('Oskari.mapframework.service.MapLayerService');
            if (mapLayerService) {
                mapLayerService.registerLayerModel('map', 'Oskari.digiroad2.bundle.map.domain.BusStopLayer');
            }
            sandbox.postRequestByName('RearrangeSelectedMapLayerRequest', ['base_35', 0]);

            this.addLayersToMap(Oskari.clazz.create('Oskari.digiroad2.bundle.map.template.Templates'));
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
        _getNotInZoomRange: function() {
            var self = this;
            return function() {
                if (self._oldZoomLevel != self._map.getZoom()) {
                    var dialog = Oskari.clazz.create('Oskari.userinterface.component.Popup');
                    dialog.show('Zoomaa lähemmäksi, jos haluat nähdä pysäkkejä');
                    dialog.fadeout(2000);
                }
            };
        },
        start: function (sandbox) {},
        eventHandlers: {
            'AfterMapMoveEvent': function(event) {
                this._handleRoadsVisibility();
                eventbus.trigger('map:moved', {zoom: this._map.getZoom(), bbox: this._map.getExtent()});
                if (!zoomlevels.isInAssetZoomLevel(this._map.getZoom())) {
                    if(this._zoomNotInMessage) {
                        this._zoomNotInMessage();
                    }
                }
                this._oldZoomLevel = this._map.getZoom();
            }
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
        _handleRoadsVisibility: function() {
            if (this._layers && _.isObject(this._layers.road)) {
                this._layers.road.setVisibility(zoomlevels.isInRoadLinkZoomLevel(this._map.getZoom()));
            }
        },
        _afterMapLayerAddEvent: function (event) {
            this._addMapLayerToMap(event.getMapLayer(), event.getKeepLayersOrder(), event.isBasemap());
        },

        addLayersToMap: function(templates) {
            var me = this;
            var roadLayer = new OpenLayers.Layer.Vector("road", {
                strategies: [new OpenLayers.Strategy.BBOX(), new OpenLayers.Strategy.Refresh()],
                protocol: new OpenLayers.Protocol.HTTP({
                    url: "api/roadlinks",
                    format: new OpenLayers.Format.GeoJSON()
                }),
                styleMap: templates.roadStyles
            });
            roadLayer.setVisibility(false);
            this._selectControl = new OpenLayers.Control.SelectFeature(roadLayer);

            me._map.addLayer(roadLayer);
            this._layers = {road: roadLayer};
            new AssetLayer(this._map, roadLayer);
            new LinearAssetLayer(this._map);
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
