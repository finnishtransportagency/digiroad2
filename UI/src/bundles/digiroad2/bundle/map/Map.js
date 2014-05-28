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
                var nearestFeature = _.find(this.roadLayer.features, function(feature) {
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

            eventbus.on('roadLinks:fetched', function(roadLinks) {
                this.drawRoadLinks(roadLinks);
            }, this);

            eventbus.on('coordinates:selected', function(position) {
                this._sandbox.postRequestByName('MapMoveRequest', [position.lat, position.lon, 11]);
            }, this);

            // register domain builder
            var mapLayerService = sandbox.getService('Oskari.mapframework.service.MapLayerService');
            if (mapLayerService) {
                mapLayerService.registerLayerModel('map', 'Oskari.digiroad2.bundle.map.domain.BusStopLayer');
            }
            sandbox.postRequestByName('RearrangeSelectedMapLayerRequest', ['base_35', 0]);

            this.addLayersToMap(Oskari.clazz.create('Oskari.digiroad2.bundle.map.template.Templates'));
        },

        roadLinkStyle: {
            privateRoad: {
                strokeWidth: 5,
                strokeOpacity: 1,
                strokeColor: "#00ccdd"
            },
            street: {
                strokeWidth: 5,
                strokeOpacity: 1,
                strokeColor: "#ff55dd"
            },
            road: {
                strokeWidth: 5,
                strokeOpacity: 1,
                strokeColor: "#11bb00"
            }
        },

        drawRoadLinks: function(roadLinks) {
            this.roadLayer.removeAllFeatures();
            var self = this;
            var features = _.map(roadLinks, function(roadLink) {
                var points = _.map(roadLink.points, function(point) {
                    return new OpenLayers.Geometry.Point(point.x, point.y);
                });
                return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), roadLink,
                  roadLink.type && self.roadLinkStyle[roadLink.type]
                );
            });
            this.roadLayer.addFeatures(features);
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
                    dialog.show('Zoomaa l&auml;hemm&auml;ksi, jos haluat n&auml;hd&auml; pys&auml;kkej&auml;');
                    dialog.fadeout(2000);
                }
            };
        },
        start: function (sandbox) {},
        eventHandlers: {
            'AfterMapMoveEvent': function(event) {
                if (zoomlevels.isInRoadLinkZoomLevel(this._map.getZoom())) {
                    Backend.getRoadLinks(this._map.getExtent());
                } else {
                    this.roadLayer.removeAllFeatures();
                }

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
            if (_.isObject(this.roadLayer)) {
                this.roadLayer.setVisibility(zoomlevels.isInRoadLinkZoomLevel(this._map.getZoom()));
            }
        },
        _afterMapLayerAddEvent: function (event) {
            this._addMapLayerToMap(event.getMapLayer(), event.getKeepLayersOrder(), event.isBasemap());
        },

        addLayersToMap: function(templates) {
            this.roadLayer = new OpenLayers.Layer.Vector("road", {
                styleMap: templates.roadStyles
            });
            this.roadLayer.setVisibility(false);
            this._selectControl = new OpenLayers.Control.SelectFeature(this.roadLayer);

            this._map.addLayer(this.roadLayer);
            new AssetLayer(this._map, this.roadLayer);
            new LinearAssetLayer(this._map);
        },
        getOLMapLayers: function (layer) {
            if (!layer.isLayerOfType(this._layerType)) {
                return null;
            }
            return [this.roadLayer];
        }
    }, {
        'protocol': ["Oskari.mapframework.module.Module", "Oskari.mapframework.ui.module.common.mapmodule.Plugin"]
    });
