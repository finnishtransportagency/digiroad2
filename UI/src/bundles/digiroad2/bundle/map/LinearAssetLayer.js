
window.LinearAssetLayer = function(map, roadLayer) {

    var selectControl;
    var modifyControls;

    var vectorLayer = new OpenLayers.Layer.Vector("linearAsset", {
        styleMap: new OpenLayers.StyleMap({
            "default": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
                strokeColor: "#333399",
                strokeWidth: 8
            }, OpenLayers.Feature.Vector.style["default"])),
            "select": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
                strokeColor: "#3333ff",
                strokeWidth: 12
            }, OpenLayers.Feature.Vector.style["select"]))
        })
    });
    vectorLayer.setOpacity(0.5);

    var selectedMarker;

    var roads;
    $.getJSON('api/roadlinks', function(roadLinks) {
        roads = _.chain(roadLinks.features)
            .map(function(feature) {
                return {id: feature.properties.roadLinkId,
                    coordinates: feature.geometry.coordinates};
            })
            .reduce(function(acc, roadLink) {
                acc[roadLink.id] = roadLink.coordinates;
                return acc;
            }, {})
            .value();
    });


    var linearAssets = {'jokutie': {
        "properties": {
            "roadLinkId": 388553092
        },
        "coordinates": [
            [374567.584, 6677273.13],
            [374567.58, 6677272.457]
        ]
    },
        'kerakuja': {
        "properties": {
            "roadLinkId": 388553164
        },
        "coordinates": [


            [374795.627, 6677289.193],
            [374804.107, 6677291.107]

        ]
    }};

    var markerLayer = new OpenLayers.Layer.Markers('marker');

    map.addLayer(vectorLayer);
    map.addLayer(markerLayer);

    var selectedLinearAsset = {};
    var selectedMarker = null;

    var drawLinearAssets = function(linearAssets) {
        vectorLayer.removeAllFeatures();
        markerLayer.clearMarkers();
        var features = _.map(linearAssets, function(linearAsset, id) {
            var points = _.map(linearAsset.coordinates, function(coordinate) {
                return new OpenLayers.Geometry.Point(coordinate[0], coordinate[1]);
            });

            var first = points[0];
            var last = _.last(points);

            var firstMarker = new OpenLayers.Marker(new OpenLayers.LonLat(first.x, first.y));
            markerLayer.addMarker(firstMarker);
            firstMarker.events.register("mousedown", markerLayer, function() {
                selectedLinearAsset.markers = [firstMarker, lastMarker];
                selectedLinearAsset.id = id;
                selectedLinearAsset.roadLinkId = linearAsset.properties.roadLinkId;
                selectedMarker = firstMarker;
            });

            var lastMarker = new OpenLayers.Marker(new OpenLayers.LonLat(last.x, last.y));
            markerLayer.addMarker(lastMarker);
            lastMarker.events.register("mousedown", markerLayer, function() {
                selectedLinearAsset.markers = [firstMarker, lastMarker];
                selectedLinearAsset.id = id;
                selectedLinearAsset.roadLinkId = linearAsset.properties.roadLinkId;
                selectedMarker = lastMarker;
            });

            return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), null);
        });
        vectorLayer.addFeatures(features);
    }

    drawLinearAssets(linearAssets);

    var drawMarker = function(marker, nearestLine, lonlat) {
        eventbus.trigger('linearAsset:moving', nearestLine);
        var position = geometrycalculator.nearestPointOnLine(
            nearestLine,
            { x: lonlat.lon, y: lonlat.lat});
        lonlat.lon = position.x;
        lonlat.lat = position.y;
        // TODO: Is it questionable that a `draw`-method modified state?
        marker.lonlat = lonlat;
        markerLayer.redraw();
    }

    var path = null;

    map.events.register('mousemove', map, function(e) {
        if (selectedLinearAsset.id) {
            var pixel = new OpenLayers.Pixel(e.xy.x, e.xy.y);
            var lonlat = map.getLonLatFromPixel(pixel);
            var nearestLine = geometrycalculator.findNearestLine(roadLayer.features, lonlat.lon, lonlat.lat);
            if (nearestLine.roadLinkId === selectedLinearAsset.roadLinkId) {
                var oldPosition = new OpenLayers.Geometry.Point(selectedMarker.lonlat.lon, selectedMarker.lonlat.lat);
                drawMarker(selectedMarker, nearestLine, lonlat);
                if (path) {
                    path.geometry.components.push(new OpenLayers.Geometry.Point(lonlat.lon, lonlat.lat));
                } else {
                    var point = new OpenLayers.Geometry.Point(lonlat.lon, lonlat.lat);
                    path = new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString([oldPosition, point]), null);
                    vectorLayer.addFeatures(path);
                }
                vectorLayer.redraw();
            }
        }
    }, true);

    map.events.register('mouseup', map, function(e) {
        if (selectedLinearAsset.id) {
            var startMarkerLine = geometrycalculator.findNearestLine(roadLayer.features, selectedLinearAsset.markers[0].lonlat.lon, selectedLinearAsset.markers[0].lonlat.lat);
            var roadLink = roads[startMarkerLine.roadLinkId];
            var startMarkerFound = false;
            var endMarkerLine = geometrycalculator.findNearestLine(roadLayer.features, selectedLinearAsset.markers[1].lonlat.lon, selectedLinearAsset.markers[1].lonlat.lat);
            var token = false;
            var points = [];
            _.forEach(roadLink, function(coordinate) {
                if (!token) {
                    if (!startMarkerFound && (coordinate[0] === startMarkerLine.start.x && coordinate[1] === startMarkerLine.start.y)) {
                        points.push([selectedLinearAsset.markers[0].lonlat.lon, selectedLinearAsset.markers[0].lonlat.lat])
                        startMarkerFound = true;
                    } else if (coordinate[0] === endMarkerLine.start.x && coordinate[1] === endMarkerLine.start.y) {
                        token = true;
                        points.push([selectedLinearAsset.markers[1].lonlat.lon, selectedLinearAsset.markers[1].lonlat.lat])
                    } else if (startMarkerFound) {
                        points.push(coordinate);
                    }
                }
            });


            linearAssets[selectedLinearAsset.id].coordinates = points;
            drawLinearAssets(linearAssets);



            selectedLinearAsset = {id: null, markers: null};
            selectedMarker = null;
            if (path) {
                vectorLayer.removeFeatures([path]);
                path = null;
            }
        }
    }, true);

    //for(var key in modifyControls) {
        //map.addControl(modifyControls[key]);
    //}
    // no harm in activating straight away
    //modifyControls.modify.activate();
}

