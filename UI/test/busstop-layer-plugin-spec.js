
var assert = chai.assert;

describe('BusStopLayerPlugin', function(){
    var dataOneBusStopType = ["2"];
    var dataTwoBusStopType = ["2","3"];
    var dataEmptyBusStopType = [];

    describe('#getIconImages()', function(){
        var pluginInstance = null;
        var oneIconImageHtml =
            '<div class="callout">' +
                '<img src="api/images/2.png">' +
                '<div class="arrow-container">' +
                '<div class="arrow"></div>' +
                '</div>' +
                '<div class="dropHandle">' +
                '</div></div>';
        var twoIconImageHtml =
            '<div class="callout">' +
                '<img src="api/images/2.png">' +
                '<img src="api/images/3.png">' +
                '<div class="arrow-container">' +
                '<div class="arrow"></div>' +
                '</div>' +
                '<div class="dropHandle">' +
                '</div></div>';
        var noIconImageHtml =
            '<div class="callout">' +
                '<div class="arrow-container">' +
                '<div class="arrow"></div>' +
                '</div>' +
                '<div class="dropHandle">' +
                '</div></div>';        
        
        before(function(){
            pluginInstance = Oskari.clazz.create('Oskari.digiroad2.bundle.mapbusstop.plugin.BusStopLayerPlugin');
            pluginInstance._initTemplates();
        });        
        
        it('should return one bus stop html by image tag', function(){
            assert.equal(oneIconImageHtml,pluginInstance._getIconImages(dataOneBusStopType).outerHTML);
        });

        it('should return two bus stop html by images tag', function(){
            assert.equal(twoIconImageHtml,pluginInstance._getIconImages(dataTwoBusStopType).outerHTML);
        });

        it('should return html without images', function(){
            assert.equal(noIconImageHtml,pluginInstance._getIconImages(dataEmptyBusStopType).outerHTML);
        });
    });    
    
    describe('when adding a new bus stop', function() {
        var pluginInstance = null;
        var assetCreationData = [];
        var overlays = {};
        var addedFeature = {};
        var destroyedFeature = {};
        var addedMarker = {};

        before(function() {
            pluginInstance = Oskari.clazz.create('Oskari.digiroad2.bundle.mapbusstop.plugin.BusStopLayerPlugin', {
                backend: _.extend({}, window.Backend, {
                    createAsset: function(data, success) {
                        assetCreationData.push(_.extend({}, data, { imageIds: [] }));
                        success( _.extend({}, assetCreationData[0], { id: 123, validityPeriod: 'current'}));
                    },
                    getAsset: function(id, success) {
                        success(_.extend({}, assetCreationData[0], { validityPeriod: 'current' }) );
                    }
                }),
                geometryCalculations: {
                    findNearestLine: function() {
                        return { roadLinkId: 5 };
                    },
                    getLineDirectionDegAngle: function() {
                        return 95;
                    },
                    nearestPointOnLine: function() {
                        return { x: 25.3, y: 44.8 };
                    }
                },
                layers: {
                    road: {features: null},
                    asset: {
                      addMarker: function(marker) { addedMarker = marker; }
                    },
                    assetDirection: {
                        addFeatures: function(feature) { addedFeature = feature; },
                        destroyFeatures: function(feature) { destroyedFeature = feature; },
                        redraw: function() {}
                    }
                },
                oskari: {
                    clazz: {
                        create: function() {
                            return {
                                overlay: function(selector) {
                                    overlays[selector] = this;
                                    this.open = true;
                                },
                                followResizing: function() {},
                                close: function() {
                                    this.open = false;
                                }
                            };
                        }
                    }
                }
            });
            pluginInstance._initTemplates();
            pluginInstance.setMapModule({
                getName: function() { return 'MapModule'; },
                getMap: function() { return {}; }
            });
            pluginInstance.startPlugin({
                register: function() {},
                registerForEventByName: function() {}
            });
            overlays = {};
            pluginInstance._addBusStopEvent({
                getLonLat: function () {
                    return {
                        lon: 30.5,
                        lat: 41.2
                    };
                }
            });
        });

        it('should add direction arrow feature to direction arrow layer', function() {
            assert.equal(addedFeature.style.externalGraphic, 'src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png');
        });

        it('should block map and tools components', function() {
            assert.equal(overlays['#contentMap'].open, true);
            assert.equal(overlays['#maptools'].open, true);
        });

        xdescribe('and when validity direction is changed', function() {
            before(function() {
                addedFeature = {};
                destroyedFeature = {};
            });

            it('should recreate direction arrow', function() {
                assert.equal(addedFeature.style.externalGraphic, 'src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png');
                assert.equal(destroyedFeature.style.externalGraphic, 'src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png');
            });
        });

        xdescribe('and when feature attributes have been collected', function () {
            before(function() {
                assetCreationData = [];
            });

            it('should create asset in back end', function () {
                assert.equal(1, assetCreationData.length);
                var expectedProperties = [{id: "5",
                                           values: [{propertyValue: 0,
                                                     propertyDisplayValue: "textValue"}]},
                                          {id: "1",
                                           values: [{propertyValue: 2,
                                                     propertyDisplayValue: ""}]}];
                assert.deepEqual({ assetTypeId: 10, lon: 25.3, lat: 44.8, roadLinkId: 5, bearing: 95, imageIds: [], properties: expectedProperties}, assetCreationData[0]);
            });

            it('should remove direction arrow feature from direction arrow layer', function() {
                assert.equal(destroyedFeature.style.externalGraphic, 'src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png');
            });

            xit('should show bus stop marker on marker layer', function() {
                assert.equal(addedMarker.id, 123);
            });

            it('should remove overlays', function() {
                assert.equal(overlays['#contentMap'].open, false);
                assert.equal(overlays['#maptools'].open, false);
            });
        });

        xdescribe('and when feature attribute collection has been cancelled', function() {
            before(function() {
                destroyedFeature = {};
            });

            it('should remove direction arrow feature from direction arrow layer', function() {
                assert.equal(destroyedFeature.style.externalGraphic, 'src/resources/digiroad2/bundle/mapbusstop/images/suuntain.png');
            });

            it('should remove overlays', function() {
                assert.equal(overlays['#contentMap'].open, false);
                assert.equal(overlays['#maptools'].open, false);
            });
        });
    });
});