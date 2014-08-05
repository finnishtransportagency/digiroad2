define(['chai', 'LinearAssetLayer', 'zoomlevels'], function(chai, LinearAssetLayer) {
    var assert = chai.assert;

    describe('LinearAssetLayer', function () {
        describe('when moving map', function() {
            var layer;
            var vectorLayer;
            var map = {
                getZoom: function() { return 9; },
                addLayer: function(vecLayer) {
                    vectorLayer = vecLayer;
                    vectorLayer.map = this;
                },
                getExtent: function() { return null; }
            };
            before(function() {
                layer = new LinearAssetLayer(map, {
                    getLinearAssets: function() {
                        eventbus.trigger('linearAssets:fetched', [
                            {id: 1, points: [{x: 0, y: 0}]},
                            {id: 2, points: [{x: 10, y: 10}]}
                        ]);
                    }
                });
                eventbus.trigger('layer:selected', 'linearAsset');
                eventbus.trigger('map:moved', {selectedLayer: 'linearAsset', bbox: null, zoom: 9});
            });

            it('should contain each speed limit only once', function() {
                var getFirstPointOfFeature = function(feature) {
                    return feature.geometry.getVertices()[0];
                };

                assert.equal(vectorLayer.features.length, 2);
                assert.equal(getFirstPointOfFeature(vectorLayer.features[0]).x, 0);
                assert.equal(getFirstPointOfFeature(vectorLayer.features[0]).y, 0);
                assert.equal(getFirstPointOfFeature(vectorLayer.features[1]).x, 10);
                assert.equal(getFirstPointOfFeature(vectorLayer.features[1]).y, 10);
            });

            describe('and zooming out', function() {
                before(function() {
                    eventbus.trigger('map:moved', {selectedLayer: 'linearAsset', bbox: null, zoom: 8});
                });

                it('should not contain speed limits', function() {
                    assert.equal(vectorLayer.features.length, 0);
                });

                describe('and zooming in', function() {
                    before(function() {
                        eventbus.trigger('map:moved', {selectedLayer: 'linearAsset', bbox: null, zoom: 9});
                    });

                    it('should contain speed limits', function() {
                        assert.equal(vectorLayer.features.length, 2);
                    });
                });
            });
        });
    });
});
