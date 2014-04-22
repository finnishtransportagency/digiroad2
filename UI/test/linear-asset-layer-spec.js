describe('LinearAssetLayer', function () {

    describe('when moving map', function() {
        var layer;
        var vectorLayer;
        var map = {
            getZoom: function() { return 12; },
            addLayer: function(layer) {
                vectorLayer = layer;
            }
        };
        before(function() {
            layer = new LinearAssetLayer(map);
            eventbus.trigger('layer:selected', 'linearAsset');
            eventbus.trigger('map:moved');
        });

        it('should contain each speed limit only once', function() {
            console.log(vectorLayer.features);
        });
    });
});
