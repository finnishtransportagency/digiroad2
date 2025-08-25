(function(root) {
    root.TrafficRegulationLayer = function(params) {
        PointAssetLayer.call(this, params);

        var me = this;
        var application= applicationModel,
            map = params.map,
            mapOverlay = params.mapOverlay,
            roadCollection = params.roadCollection,
            selectedAsset = params.selectedAsset,
            layerName = params.layerName,
            typeId = params.typeId,
            authorizationPolicy = params.authorizationPolicy,
            collection = params.collection;

         this.handleMapClick = function (coordinates) {
            if (application.getSelectedTool() === 'Add' && zoomlevels.isInAssetZoomLevel(zoomlevels.getViewZoom(map))) {
                me.createNewAsset(coordinates);
            } else if (selectedAsset.isDirty()) {
                me.displayConfirmMessage();
            }
        };

        var editingRestrictions = new EditingRestrictions();

        this.createNewAsset = function(coordinates) {
            var selectedLon = coordinates.x;
            var selectedLat = coordinates.y;
            var nearestLine = geometrycalculator.findNearestLine(me.excludeRoadByAdminClass(roadCollection.getRoadsForCarPedestrianCycling()), selectedLon, selectedLat);
            if(nearestLine.end && nearestLine.start){
                var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, { x: selectedLon, y: selectedLat });
                var bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
                var administrativeClass = obtainAdministrativeClass(nearestLine);
                var municipalityCode = obtainMunicipalityCode(nearestLine);

                if (administrativeClass === 'State' && editingRestrictions.pointAssetHasRestriction(municipalityCode, administrativeClass, typeId)) {
                    me.displayAssetCreationRestricted('Kohteiden lisääminen on estetty, koska kohteita ylläpidetään Tievelho-tietojärjestelmässä.');
                    return;
                }

                if (administrativeClass === 'Municipality' && editingRestrictions.pointAssetHasRestriction(municipalityCode, administrativeClass, typeId)) {
                    me.displayAssetCreationRestricted('Kunnan kohteiden lisääminen on estetty, koska kohteita ylläpidetään kunnan omassa tietojärjestelmässä.');
                    return;
                }

                if (administrativeClass === 'Municipality' && authorizationPolicy.isElyMaintainer()) {
                    me.displayAssetCreationRestricted('Käyttöoikeudet eivät riitä kohteen lisäämiseen. ELY-ylläpitäjänä et voi lisätä kohteita kunnan omistamalle katuverkolle.');
                    return;
                }

                var asset = me.createAssetWithPosition(selectedLat, selectedLon, nearestLine, projectionOnNearestLine, bearing, administrativeClass);

                me.vectorLayer.getSource().addFeature(me.createFeature(asset));
                selectedAsset.place(asset);
                mapOverlay.show();
            }
        };

        this.excludeRoads = function(roadCollection, feature) {
            var roads = roadCollection.getRoadsForCarPedestrianCycling();
            if (layerName == 'trafficSigns') {
                var signType = _.head(_.find(feature.features.getArray()[0].getProperties().propertyData, function(prop) {return prop.publicId === "trafficSigns_type";}).values).propertyValue;
                if(!collection.isAllowedSignInPedestrianCyclingLinks(signType))
                    roads = roadCollection.getRoadsForPointAssets();
            }
            return me.excludeRoadByAdminClass(roads);
        };

        function obtainAdministrativeClass(asset){
            return selectedAsset.getAdministrativeClass(asset.linkId);
        }

        function obtainMunicipalityCode(asset) {
            return selectedAsset.getMunicipalityCodeByLinkId(asset.linkId);
        }

        return {
            show: me.showLayer,
            hide: me.hideLayer,
            minZoomForContent: me.minZoomForContent
        };
    };
})(this);