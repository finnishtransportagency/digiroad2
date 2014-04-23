describe('LinearAssetLayer', function () {
    describe('when moving map', function() {
        var layer;
        var vectorLayer;
        var map = {
            getZoom: function() { return 12; },
            addLayer: function(vecLayer) {
                vectorLayer = vecLayer;
                vectorLayer.map = this;
            },
            getExtent: function() { return null; }
        };
        before(function() {
            layer = new LinearAssetLayer(map, {
                getLinearAssets: function(bbox) {
                    eventbus.trigger('linearAssets:fetched', [
                        {id: 1, points: [{x: 0, y: 0}]},
                        {id: 2, points: [{x: 10, y: 10}]}
                    ]);
                }
            });
            eventbus.trigger('layer:selected', 'linearAsset');
            eventbus.trigger('map:moved', {bbox: null});
        });

        it('should contain each speed limit only once', function() {
            console.log('number of features on ' + vectorLayer.id + ' is ' + vectorLayer.features.length);
        });
    });

});
