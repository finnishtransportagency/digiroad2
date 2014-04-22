window.LinearAssetLayer = function(map) {
    var vectorLayer = new OpenLayers.Layer.Vector("linearAsset", {
        styleMap: new OpenLayers.StyleMap({
            "default": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
                strokeColor: "#B22222",
                strokeWidth: 8
            }))
        })
    });
    vectorLayer.setOpacity(1);

    var isZoomed = function(map) { return 8 < map.getZoom(); };
    var layerIsVisible = function() { return !!vectorLayer.map; };
    var showLayer = function() {
        map.addLayer(vectorLayer);
        vectorLayer.setVisibility(true);
        if (isZoomed(map) && layerIsVisible()) {
            Backend.getLinearAssets(map.getExtent());
        }
    };
    var hideLayer = function() {
        if (layerIsVisible()) {
            map.removeLayer(vectorLayer);
        }
    };

    eventbus.on('layer:selected', function(layer) {
        if (layer === 'linearAsset') {
            showLayer();
        } else {
            hideLayer();
        }
    }, this);

    eventbus.on('map:moved', function(state) {
        if (isZoomed(map) && layerIsVisible()) {
            Backend.getLinearAssets(state.bbox);
        } else {
            vectorLayer.removeAllFeatures();  
        }
    }, this);

    var drawLinearAssets = function(linearAssets) {
        var features = _.map(linearAssets, function(linearAsset) {
            var points = _.map(linearAsset.points, function (point) {
                return new OpenLayers.Geometry.Point(point.x, point.y);
            });
            return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), null);
        });
        vectorLayer.addFeatures(features);
    };

    eventbus.on('linearAssets:fetched', drawLinearAssets, this);
};
