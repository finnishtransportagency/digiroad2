window.LinearAssetLayer = function(map) {
    var vectorLayer = new OpenLayers.Layer.Vector("linearAsset", {
        styleMap: new OpenLayers.StyleMap({
            "default": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
                strokeColor: "#B22222",
                strokeWidth: 8
            }, OpenLayers.Feature.Vector.style["default"])),
            "select": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
                strokeColor: "#4B0082",
                strokeWidth: 8
            }, OpenLayers.Feature.Vector.style.select))
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
            var controls = {
                select: new OpenLayers.Control.SelectFeature(vectorLayer, {
                    standalone: true
                })
            };
            for (var key in controls) {
                map.addControl(controls[key]);
            }
            controls.select.activate();
            if (8 < map.getZoom() && vectorLayer.map) {
              Backend.getLinearAssets(666, map.getExtent());
            }
        }
    }, this);

    eventbus.on('map:moved', function(state) {
        if (8 < state.zoom && vectorLayer.map) {
            Backend.getLinearAssets(666, state.bbox);
        } else {
            vectorLayer.removeAllFeatures();  
        }
    }, this);

    eventbus.on('linearAssets:fetched', function(linearAssets) {
        var sorted = _.sortBy(linearAssets, 'id');
        var features = _.map(linearAssets, function(linearAsset) {
            var points = _.map(linearAsset.points, function (point) {
                return new OpenLayers.Geometry.Point(point.x, point.y);
            });
            return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), null);
        });
        vectorLayer.addFeatures(features);
    }, this);

};
