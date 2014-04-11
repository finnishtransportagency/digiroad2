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

    /*
    var ps =
       [
           {x : 481349.287847403, y : 7058093.55030325 },
           {x : 481258.904494213, y : 7058055.88364716 },
           {x : 481191.01491005, y : 7058030.24648423 },
           {x : 481121.768581706, y : 7058000.06091471 },
           {x : 481121.277426107, y : 7057999.83968453 },
           {x : 481089.664745324, y : 7057948.82679076 },
           {x : 481046.962375822, y : 7057874.82960257 },
           {x : 480982.619740889, y : 7057763.32292473 },
           {x : 480954.56763462, y : 7057710.06707628 },
           {x : 480950.105112279, y : 7057697.01800722 },
           {x : 480947.390521922, y : 7057683.30463252 },
           {x : 480947.20962496, y : 7057668.41786393 },
           {x : 480950.717754849, y : 7057647.02698879 },
           {x : 480960.014550626, y : 7057618.93259807 },
           {x : 480981.696517209, y : 7057568.10515926 },
           {x : 480994.698308893, y : 7057538.81179895 },
           {x : 481008.291665501, y : 7057489.0418168 },
           {x : 481017.746416066, y : 7057462.19821206 },
           {x : 481042.012478839, y : 7057416.84760376 },
           {x : 481077.95228973, y : 7057391.95658962 },
           {x : 481099.229829926, y : 7057375.80246978 }
           //{x : 481191.014838579, y : 7058030.24645724 },
           //{x : 481121.769009764, y : 7058000.06110131}
       ];
       */


    //var s = '481349.287847403,7058093.55030325,481258.904494213,7058055.88364716,481191.01491005,7058030.24648423,481191.014838579,7058030.24645724,481121.769009764,7058000.06110131,481121.768581706,7058000.06091471,481121.277426107,7057999.83968453,481089.664745324,7057948.82679076,481046.962375822,7057874.82960257,480982.619740889,7057763.32292473,480954.56763462,7057710.06707628,480950.105112279,7057697.01800722,480947.390521922,7057683.30463252,480947.20962496,7057668.41786393,480950.717754849,7057647.02698879,480960.014550626,7057618.93259807,480981.696517209,7057568.10515926,480994.698308893,7057538.81179895,481008.291665501,7057489.0418168,481017.746416066,7057462.19821206,481042.012478839,7057416.84760376,481077.95228973,7057391.95658962,481099.229829926,7057375.80246978'
    var s ='292545.465942562,6824449.3763405,292537.36220989,6824447.14768917,292530.875310699,6824446.07626144,292517.699818906,6824443.85193657,292503.884685422,6824441.10446331,292493.333123626,6824437.88088131';
    var coords = _.map(s.split(','), function(s) { return parseFloat(s); });
    var partition = function(n, step, coll) {
        var f = function(acc, n, step, coll) {
            if (coll.length < 1) {
                return acc;
            }
            var head = _.take(coll, n);
            var tail = _.drop(coll, step);
            acc.push(head);
            return f(acc, n, step, tail);
        }
        return f([], n, step, coll);
    }
    var ps = _.map(partition(2, 2, coords), function(tuple) { return {x: tuple[0], y: tuple[1]} });


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


            var points = _.map(ps, function (point) {
                return new OpenLayers.Geometry.Point(point.x, point.y);
            });

            var line = new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), null);

            vectorLayer.addFeatures([line]);
            vectorLayer.setVisibility(true);
        }
    }, this);
};
