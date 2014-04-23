window.LinearAssetLayer = function(map, backend) {
    backend = backend || Backend;

    var allLinearAssets = {};
    var vectorLayer = new OpenLayers.Layer.Vector("linearAsset", {
        styleMap: new OpenLayers.StyleMap({
            "default": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
                strokeColor: "#B22222",
                strokeWidth: 8
            }))
        })
    });
    vectorLayer.setOpacity(1);

    var isZoomed = function(level) { return 8 < level; };
    var layerIsVisible = function() { return !!vectorLayer.map; };
    var showLayer = function() {
        map.addLayer(vectorLayer);
        vectorLayer.setVisibility(true);
        if (isZoomed(map.getZoom()) && layerIsVisible()) {
            backend.getLinearAssets(map.getExtent());
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
        if (isZoomed(state.zoom) && layerIsVisible()) {
            backend.getLinearAssets(state.bbox);
        } else {
            vectorLayer.removeAllFeatures();
            allLinearAssets = {};
        }
    }, this);

    var drawLinearAssets = function(linearAssets) {
        var features = _.chain(linearAssets)
            .reject(function(linearAsset) { return !!allLinearAssets[linearAsset.id]; })
            .map(function(linearAsset) {
                var points = _.map(linearAsset.points, function (point) {
                    return new OpenLayers.Geometry.Point(point.x, point.y);
                });
                return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), null);
            })
            .value();
        allLinearAssets = _.merge({}, allLinearAssets, _.reduce(linearAssets,
            function (acc, linearAsset) {
                acc[linearAsset.id] = true;
                return acc;
            }, {}));
        vectorLayer.addFeatures(features);
    };

    eventbus.on('linearAssets:fetched', drawLinearAssets, this);
};
