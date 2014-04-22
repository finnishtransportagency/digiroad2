window.LinearAssetLayer = function(map) {
    var vectorLayer = new OpenLayers.Layer.Vector("linearAsset", {
        styleMap: new OpenLayers.StyleMap({
            "default": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
                strokeColor: "#B22222",
                strokeWidth: 8
            }, OpenLayers.Feature.Vector.style["default"]))
        })
    });
    vectorLayer.setOpacity(1);

    eventbus.on('layer:selected', function(layer) {
        if (layer !== 'linearAsset') {
            if (vectorLayer.map) {
                map.removeLayer(vectorLayer);
            }
        } else {
            map.addLayer(vectorLayer);
            vectorLayer.setVisibility(true);
            if (8 < map.getZoom() && vectorLayer.map) {
              Backend.getLinearAssets(map.getExtent());
            }
        }
    }, this);

    eventbus.on('map:moved', function(state) {
        if (8 < state.zoom && vectorLayer.map) {
            Backend.getLinearAssets(state.bbox);
        } else {
            vectorLayer.removeAllFeatures();  
        }
    }, this);

    eventbus.on('linearAssets:fetched', function(linearAssets) {
        var features = _.map(linearAssets, function(linearAsset) {
            var points = _.map(linearAsset.points, function (point) {
                return new OpenLayers.Geometry.Point(point.x, point.y);
            });
            return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), null);
        });
        vectorLayer.addFeatures(features);
    }, this);

};
