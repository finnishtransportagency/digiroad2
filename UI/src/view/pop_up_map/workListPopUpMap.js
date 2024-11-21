(function (root) {
    root.WorkListPopUpMap = function(backend, item) {

        var roadCollection = new RoadCollection(backend);
        var verificationCollection = new AssetsVerificationCollection(backend);
        var manoeuvresCollection = new ManoeuvresCollection(backend, roadCollection, verificationCollection);
        var selectedManoeuvreSource = new SelectedManoeuvreSource(manoeuvresCollection);
        var models = {
            selectedManoeuvreSource: selectedManoeuvreSource,
            manoeuvresCollection: manoeuvresCollection
        };


        var setupMap = function(backend, startupParameters) {
            var tileMaps = new TileMapCollection(map, "");
            var map = createOpenLayersMap(startupParameters, tileMaps.layers);

            var mapPluginsContainer = $('#pop-up-map-plugins');
            new ScaleBar(map, mapPluginsContainer);

            var roadLayer = new RoadLayer(map, roadCollection);
            var manoeuvreLayer = new PopUpManoeuvreLayer(
                map,
                roadLayer,
                models.manoeuvresCollection,
                new LinearSuggestionLabel()
            );

            new PopUpMapView(map, backend, manoeuvreLayer);
            manoeuvreLayer.refreshWorkListView(item.assetId);

            return map;
        };

        var createOpenLayersMap = function(startupParameters, layers) {
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
        };

        function openMapPopup() {
            var modal = $('<div id="mapModal" class="modal">');

            var modalContent = $('<div class="modal-content">');

            var closeButton = $('<button>Sulje kartta</button>')
                .attr('id', 'closeMapButton')
                .addClass('btn header-link-btn');

            var mapDiv = $('<div id="pop-up-mapdiv">');

            var pluginsDiv = $('<div>', { id: 'pop-up-map-plugins' });
            pluginsDiv.append('<div id="pop-up-municipality-name-container"></div>');

            var popUpManoeuvreBox = new PopUpManoeuvreBox();
            modalContent
                .append(closeButton)
                .append(pluginsDiv)
                .append(mapDiv)
                .append(popUpManoeuvreBox);
            modal.append(modalContent);
            $('body').append(modal);

            var startupParameters = { lon: 390000, lat: 6900000, zoom: 2 };

            var map = setupMap(backend, startupParameters);
            map.setTarget('pop-up-mapdiv');
            return map;
        }

        return openMapPopup();

    };
})(this);