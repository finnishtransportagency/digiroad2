(function (root) {
    root.WorkListPopUpMap = function(backend, item, workListSource) {

        // Helper for getting the asset type
        function getAssetTypeByTypeId(typeId) {
            var enumerations = new Enumerations();
            const assetTypes = enumerations.assetTypes;
            // Iterate over values and find the matching typeId
            for (const key in assetTypes) {
                if (assetTypes[key].typeId === typeId) {
                    return assetTypes[key];
                }
            }
            // fallback if not found
            return assetTypes.UnknownAssetTypeId;
        }

        var assetType = item.assetType;
        var assetTypeEnum = getAssetTypeByTypeId(assetType);

        // Data fetching logic
        // Manoeuvres on expired links has its own logic.
        // Assets on expired links have their own logic.
        if (workListSource === "manoeuvreSamuutusWorkList") {
            var roadCollection = new RoadCollection(backend);
            var verificationCollection = new AssetsVerificationCollection(backend);
            var manoeuvresCollection = new ManoeuvresCollection(backend, roadCollection, verificationCollection);
            var selectedManoeuvreSource = new SelectedManoeuvreSource(manoeuvresCollection);
            var models = {
                selectedManoeuvreSource: selectedManoeuvreSource,
                manoeuvresCollection: manoeuvresCollection
            };
            openMapPopup();
            return;

        } else if (workListSource === "assetsOnExpiredLinksWorkList") {
            if (assetTypeEnum) {
                var popUpCollection = new PopUpCollection(backend);
                switch (assetTypeEnum.geometryType) {
                    case ('point'): {
                        popUpCollection.fetch(item.id, function (err, data) {
                            if (err) {
                                console.error('Error fetching data:', err);
                                callback(err);
                            } else {
                                // open map and render points
                                var point = data[0].point[0];
                                openMapPopup(data);
                                eventbus.trigger('pointAssetsOnExpiredLinks:fetched', point);
                            }
                        });
                        return;
                    }
                    case ('linear'): {
                        popUpCollection.fetch(item.id, function (err, data) {
                            if (err) {
                                console.error('Error fetching data:', err);
                                callback(err);
                            } else {
                                // open map and render points
                                var geometry = data[0].geometry[0];
                                openMapPopup(data);
                                eventbus.trigger('linearAssetsOnExpiredLinks:fetched', geometry);
                            }
                        });
                        return;
                    }
                }
            }
        } else {
            console.error('Unable to set map, check workListSource');
        }


        // Create HTML for the pop up
        function openMapPopup(data) {
            var modal = $('<div id="mapModal" class="modal">');
            var modalContent = $('<div class="modal-content">');

            var closeButton = $('<button>Sulje kartta</button>')
                .attr('id', 'closeMapButton')
                .addClass('btn header-link-btn');

            var mapDiv = $('<div id="pop-up-mapdiv">');

            var pluginsDiv = $('<div>', { id: 'pop-up-map-plugins' });
            pluginsDiv.append('<div id="pop-up-municipality-name-container"></div>');

            var legend;

            if (workListSource === "assetsOnExpiredLinksWorkList") {
                legend = new PopUpAssetLegend();
            } else if (workListSource === "manoeuvreSamuutusWorkList") {
                legend = new PopUpManoeuvreBox();
            } else {
                console.error("Unable to create legend. Check Work List source!");
            }

            modalContent
                .append(closeButton)
                .append(pluginsDiv)
                .append(mapDiv)
                .append(legend);
            modal.append(modalContent);
            $('body').append(modal);

            var startupParameters = { lon: 390000, lat: 6900000, zoom: 2 };

            var map = setupMap(backend, startupParameters, data);
            map.setTarget('pop-up-mapdiv');
            return map;
        }

        // Open layers map creation
        function createOpenLayersMap(startupParameters, layers) {
            var map = new ol.Map({
                target: 'pop-up-mapdiv',
                layers: layers,
                view: new ol.View({
                    center: [startupParameters.lon, startupParameters.lat],
                    projection: 'EPSG:3067',
                    zoom: startupParameters.zoom,
                    resolutions: [2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25, 0.125, 0.0625]
                }),
                interactions: ol.interaction.defaults({
                    mouseWheelZoom: false
                }),
            });
            map.setProperties({extent : [-548576, 6291456, 1548576, 8388608]});
            map.addInteraction(new ol.interaction.DragPan({
                condition: function (mapBrowserEvent) {
                    var originalEvent = mapBrowserEvent.originalEvent;
                    return (!originalEvent.altKey && !originalEvent.shiftKey);
                }
            }));

            map.addInteraction(new ol.interaction.MouseWheelZoom({
                condition: function(event) {
                    var deltaY = event.originalEvent.deltaY;
                    if (deltaY > 0) {
                        // Wheel scrolled down (Zoom out)
                        return applicationModel.handleZoomOut(map);
                    } else if (deltaY < 0) {
                        // Wheel scrolled up (Zoom in)
                        return applicationModel.handleZoomIn(map);
                    } else {
                        // no zoom -> no reason to block
                        return true;
                    }
                }
            }));
            return map;
        }

        // Add data to the map
        function setupMap(backend, startupParameters, data) {
            var tileMaps = new TileMapCollection(map, "");
            var map = createOpenLayersMap(startupParameters, tileMaps.layers);

            var mapPluginsContainer = $('#pop-up-map-plugins');
            new ScaleBar(map, mapPluginsContainer);

            var roadLayer = new RoadLayer(map, roadCollection);
            var layer;

            if (workListSource === "assetsOnExpiredLinksWorkList") {
                layer = new PopUpAssetLayer(
                    map,
                    data
                );
            } else if (workListSource === "manoeuvreSamuutusWorkList") {
                layer = new PopUpManoeuvreLayer(
                    map,
                    roadLayer,
                    models.manoeuvresCollection,
                    new LinearSuggestionLabel()
                );
                layer.refreshWorkListView(item.assetId);
            } else {
                console.error("Unable to create layer. Check Work List source!");
            }

            new PopUpMapView(map, backend, layer);
            return map;
        }
    };
})(this);
