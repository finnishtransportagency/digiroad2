(function(root) {
    root.TrafficSignLayer = function(params) {
        PointAssetLayer.call(this, params);
        var me = this;
        var application= applicationModel,
            map = params.map,
            roadCollection = params.roadCollection,
            selectedAsset = params.selectedAsset;

        // var selectControl = new SelectToolControl(application, vectorLayer, map, false,{
        //     style : function (feature) {
        //         return style.browsingStyleProvider.getStyle(feature);
        //     },
        //     onSelect : pointAssetOnSelect,
        //     draggable : false,
        //     filterGeometry : function(feature){
        //         return feature.getGeometry() instanceof ol.geom.Point;
        //     }
        // });
        //
        this.layerStarted = function(eventListener) {
            this.bindEvents(eventListener);
            showRoadLinkInformation();
        };

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
            var nearestLine = geometrycalculator.findNearestLine(this.excludeRoadByAdminClass(roadCollection.getRoadsForPointAssets()), selectedLon, selectedLat);
            if(nearestLine.end && nearestLine.start){
                var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, { x: selectedLon, y: selectedLat });
                var bearing = geometrycalculator.getLineDirectionDegAngle(nearestLine);
                var administrativeClass = obtainAdministrativeClass(nearestLine);

                var asset = createAssetWithPosition(selectedLat, selectedLon, nearestLine, projectionOnNearestLine, bearing, administrativeClass);

                vectorLayer.getSource().addFeature(createFeature(asset));
                selectedAsset.place(asset);
                mapOverlay.show();
            }
        };
        function show(map) {
            startListeningExtraEvents();
            vectorLayer.setVisible(true);
            roadAddressInfoPopup.start();
            me.show(map);
        }

        function hide() {
            selectedAsset.close();
            vectorLayer.setVisible(false);
            hideReadOnlyLayer();
            roadAddressInfoPopup.stop();
            stopListeningExtraEvents();
            me.stop();
            me.hide();
        }

        return {
            show: show,
            hide: hide,
            minZoomForContent: me.minZoomForContent
        };
    };
})(this);