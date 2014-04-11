window.LinearAssetLayer = function(map, roadLayer) {
    var vectorLayer = new OpenLayers.Layer.Vector("linearAsset", {
        styleMap: new OpenLayers.StyleMap({
            "default": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
                strokeColor: "#ff00ff",
                strokeWidth: 8
            }, OpenLayers.Feature.Vector.style["default"])),
            "select": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
                strokeColor: "#3333ff",
                strokeWidth: 12
            }, OpenLayers.Feature.Vector.style.select))
        })
    });
    vectorLayer.setOpacity(1);

    var markerLayer = new OpenLayers.Layer.Markers('marker');

    var markers = [];
    map.events.register('click', map, function(e) {
        if (!markerLayer.map || !vectorLayer.map) {
            return;
        }
        var pixel = new OpenLayers.Pixel(e.xy.x, e.xy.y);
        var lonlat = map.getLonLatFromPixel(pixel);
        var nearestLine = geometrycalculator.findNearestLine(roadLayer.features, lonlat.lon, lonlat.lat);
        var position = geometrycalculator.nearestPointOnLine(nearestLine, { x: lonlat.lon, y: lonlat.lat});
        var marker = new OpenLayers.Marker(new OpenLayers.LonLat(position.x, position.y));
        markers.push(marker);
        markerLayer.addMarker(marker);
        if (markers.length === 2) {
            Backend.createLinearAsset(markers[0].lonlat, markers[1].lonlat);
            _.each(markers, function(marker) {
                markerLayer.removeMarker(marker);
            });
            markers = [];
        }
    });

    eventbus.on('linearAsset:created', function(linearAsset) {
        var startMarker = new OpenLayers.Marker(new OpenLayers.LonLat(_.first(linearAsset.points).lon, _.first(linearAsset.points).lat));
        var endMarker = new OpenLayers.Marker(new OpenLayers.LonLat(_.last(linearAsset.points).lon, _.last(linearAsset.points).lat));
        markerLayer.addMarker(startMarker);
        markerLayer.addMarker(endMarker);
        var points = _.map(linearAsset.points, function(point) {
            return new OpenLayers.Geometry.Point(point.lon, point.lat);
        });
        vectorLayer.addFeatures([new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), null)]);
    });

    eventbus.on('layer:selected', function(layer) {
        if (layer !== 'linearAsset') {
            if (markerLayer.map && vectorLayer.map) {
                //map.removeLayer(markerLayer);
                map.removeLayer(vectorLayer);
            }
        } else {
            map.addLayer(vectorLayer);
            //map.addLayer(markerLayer);

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
        }
    }, this);
};
