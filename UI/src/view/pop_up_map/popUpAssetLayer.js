(function(root) {
    root.PopUpAssetLayer = function(map, assets) {
        var features = []; // For storing the features


        // Create the assets and store them in the features array
        // There are PointAssets and LinearAssets
        // They can be identified from the data they hold
        // PointAsset has a point + linkGeometry where the point lies
        // LinearAsset has only the linkGeometry
        _.each(assets, function(asset) {
            var hasPoint = asset.point && asset.point.length > 0;
            var hasAssetGeometry = asset.assetGeometry && asset.assetGeometry.length > 0;
            var hasLinkGeometry = asset.linkGeometry && asset.linkGeometry.length > 0;

            // Draw the underlying link line if linkGeometry exists
            if (hasLinkGeometry) {
                var linkCoords = asset.linkGeometry.map(function(p) {
                    return [p.x, p.y];
                });
                var optionalLineFeature = new ol.Feature({
                    geometry: new ol.geom.LineString(linkCoords),
                    assetId: asset.id,
                    linkId: asset.linkId
                });
                optionalLineFeature.set('isLinkLine', true);
                features.push(optionalLineFeature);
            }

            // Linear asset
            if (hasAssetGeometry) {
                var coords = asset.assetGeometry.map(function(p) {
                    return [p.x, p.y];
                });
                var lineFeature = new ol.Feature({
                    geometry: new ol.geom.LineString(coords),
                    assetId: asset.id,
                    linkId: asset.linkId
                });
                features.push(lineFeature);

            } else if (hasPoint) { // Point Asset
                var pointCoords = [asset.point[0].x, asset.point[0].y];
                var pointFeature = new ol.Feature({
                    geometry: new ol.geom.Point(pointCoords),
                    assetId: asset.id,
                    linkId: asset.linkId,
                    sideCode: asset.sideCode,
                    bearing: asset.bearing
                });
                features.push(pointFeature);
            } else {
                console.warn('Asset missing linkGeometry or point:', asset);
            }
        });

        /**
         * Styles for the features
         * There are two types of features:
         * PointAssets have geomType of "Point"
         * and
         * LinearAssets have geomType of "LineString"
         */

        var source = new ol.source.Vector({ features: features });

        // Create a vector layer and add the features to it
        var layer = new ol.layer.Vector({
            source: source,
            style: function (feature) {
                var geomType = feature.getGeometry().getType();
                var sideCode = feature.get('sideCode');
                var bearing = feature.get('bearing');
                var rotation = (bearing - 90) * Math.PI / 180;

                //Use sideCode to flip direction
                if (sideCode === 3) {
                    rotation = rotation + Math.PI; // opposite direction
                }

                // Different styles for point vs line
                if (geomType === 'Point') {
                    return new ol.style.Style({
                        image: new ol.style.Icon({
                            src: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow.svg',
                            scale: 0.8,
                            rotation: rotation,
                            rotateWithView: true,
                            anchor: [0.5, 0.5]
                        })
                    });
                } else if (geomType === 'LineString') {
                    // Highlight the tied link differently if it's just for a point asset
                    var color = feature.get('isLinkLine') ? '#8c8c88' : '#ff000099';
                    var zIndex = feature.get('isLinkLine') ? 0 : 1;
                    return new ol.style.Style({
                        stroke: new ol.style.Stroke({
                            color: color,
                            width: feature.get('isLinkLine') ? 10 : 5,
                            zIndex: zIndex
                        })
                    });
                }
            }
        });

        map.addLayer(layer);

        // Zoom to fit features
        if (features.length > 0) {
            var extent = source.getExtent();
            map.getView().fit(extent, { padding: [20, 20, 20, 20], maxZoom: 17 });
        }

        // Cleanup / visibility
        return {
            layer: layer,
            hide: function() {
                layer.setVisible(false);
            }
        };
    };
})(this);
