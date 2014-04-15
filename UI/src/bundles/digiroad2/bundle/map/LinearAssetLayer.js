window.LinearAssetLayer = function(map, roadLayer) {
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
            // TODO: Move to backend
            $.get('api/linearassets', function(linearAssets) {
                var features = _.map(linearAssets, function(linearAsset) {
                    var points = _.map(linearAsset.points, function (point) {
                        return new OpenLayers.Geometry.Point(point.x, point.y);
                    });
                    return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), null);
                });
                vectorLayer.addFeatures(features);
            });

            vectorLayer.setVisibility(true);

            var controls = {
                select: new OpenLayers.Control.SelectFeature(vectorLayer, {
                    standalone: true
                })
            };

            for(var key in controls) {
                map.addControl(controls[key]);
            }
            controls.select.activate();

        }
    }, this);


};
