(function(root) {
    root.TrafficSignLayer = function(params) {
        PointAssetLayer.call(this, params);

        var me = this;
        var application= applicationModel,
            map = params.map,
            mapOverlay = params.mapOverlay,
            roadCollection = params.roadCollection,
            selectedAsset = params.selectedAsset;

         this.handleMapClick = function (coordinates) {
            if (application.getSelectedTool() === 'Add' && zoomlevels.isInAssetZoomLevel(zoomlevels.getViewZoom(map))) {
                me.createNewAsset(coordinates);
            } else if (selectedAsset.isDirty()) {
                me.displayConfirmMessage();
            }
        };

        this.createNewAsset = function(coordinates) {
            var selectedLon = coordinates.x;
            var selectedLat = coordinates.y;
            var nearestLine = geometrycalculator.findNearestLine(me.excludeRoadByAdminClass(roadCollection.getRoadsForCarPedestrianCycling()), selectedLon, selectedLat);
            if(nearestLine.end && nearestLine.start){
                var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, { x: selectedLon, y: selectedLat });
                var bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
                var administrativeClass = obtainAdministrativeClass(nearestLine);

                var asset = me.createAssetWithPosition(selectedLat, selectedLon, nearestLine, projectionOnNearestLine, bearing, administrativeClass);

                me.vectorLayer.getSource().addFeature(me.createFeature(asset));
                selectedAsset.place(asset);
                mapOverlay.show();
            }
        };

        this.excludeRoads = function(roadCollection, feature) {
           var signType = _.head(_.find(feature.features.getArray()[0].getProperties().propertyData, function(prop) {return prop.publicId === "trafficSigns_type";}).values).propertyValue;
           var roads = _.includes(['70','71','72'], signType) ? roadCollection.getRoadsForCarPedestrianCycling() : roadCollection.getRoadsForPointAssets();
           return me.excludeRoadByAdminClass(roads);
        };

        function obtainAdministrativeClass(asset){
            return selectedAsset.getAdministrativeClass(asset.linkId);
        }

        return {
            show: me.showLayer,
            hide: me.hideLayer,
            minZoomForContent: me.minZoomForContent
        };
    };
})(this);