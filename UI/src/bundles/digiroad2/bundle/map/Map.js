Oskari.clazz.define('Oskari.digiroad2.bundle.map.Map',
    function () {
        this.mapModule = null;
        this.pluginName = null;
        this._sandbox = null;
        this._map = null;
        this._supportedFormats = {};
        this._localization = null;
        this._state = undefined;
    }, {
        __name: 'Map',
        _layerType: 'map',
        _unknownAssetType: '99',
        _selectedValidityPeriods: ['current'],
        _visibilityZoomLevelForRoads : 10,
        _centerMarkerLayer : null,
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
                new CoordinateSelector($('.mapplugin.coordinates'), this._map.getMaxExtent());
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
            eventbus.on('coordinates:selected coordinates:marked', function(position) {
                this._sandbox.postRequestByName('MapMoveRequest', [position.lon, position.lat, zoomlevels.getAssetZoomLevelIfNotCloser(this._map.getZoom())]);
            }, this);
            eventbus.on('coordinates:marked', function(position) {
                this._drawCenterMarker(position);
            }, this);
            eventbus.on('roadLinks:fetched', function(roadLinks) {
                this.drawRoadLinks(roadLinks);
            }, this);

            // register domain builder
            var mapLayerService = sandbox.getService('Oskari.mapframework.service.MapLayerService');
            if (mapLayerService) {
                mapLayerService.registerLayerModel('map', 'Oskari.digiroad2.bundle.map.domain.BusStopLayer');
            }
            sandbox.postRequestByName('RearrangeSelectedMapLayerRequest', ['base_35', 0]);

            this.addLayersToMap(Oskari.clazz.create('Oskari.digiroad2.bundle.map.template.Templates'));
        },
        _drawCenterMarker: function(position) {
            var size = new OpenLayers.Size(16,16);
            var offset = new OpenLayers.Pixel(-(size.w/2), -size.h/2);
            var icon = new OpenLayers.Icon('./images/center-marker.png',size,offset);

            this._centerMarkerLayer.clearMarkers();
            var marker = new OpenLayers.Marker(new OpenLayers.LonLat(position.lat, position.lon), icon);
            this._centerMarkerLayer.addMarker(marker);
        },

        drawRoadLinks: function(roadLinks) {
            this.roadLayer.removeAllFeatures();
            var features = _.map(roadLinks, function(roadLink) {
                var points = _.map(roadLink.points, function(point) {
                    return new OpenLayers.Geometry.Point(point.x, point.y);
                });
                return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), roadLink);
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
                    dialog.show('Zoomaa l&auml;hemm&auml;ksi, jos haluat n&auml;hd&auml; kohteita');
                    dialog.fadeout(2000);
                }
            };
        },
        start: function (sandbox) {},
        changeRoadsWidthByZoomLevel : function() {
            var widthBase = 2 + (this._map.getZoom() - zoomlevels.minZoomForRoadLinks);
            var roadWidth = widthBase * widthBase;
            this.roadLayer.styleMap.styles.default.defaultStyle.strokeWidth = roadWidth;
            this.roadLayer.styleMap.styles.select.defaultStyle.strokeWidth = roadWidth;
        },
        eventHandlers: {
            'AfterMapMoveEvent': function() {
                if (zoomlevels.isInRoadLinkZoomLevel(this._map.getZoom())) {
                    this.changeRoadsWidthByZoomLevel();
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

            this._centerMarkerLayer = new OpenLayers.Layer.Markers('centerMarker');
            this._layers = {road: this.roadLayer};
            this._map.addLayer(this.roadLayer);

            new AssetLayer(this._map, this.roadLayer);
            new LinearAssetLayer(this._map);
            this._map.addLayer(this._centerMarkerLayer);

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
