(function (root) {
    root.WorkListPopUpMap = function(backend, item) {

        var setupMap = function(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters, roadCollection, verificationInfoCollection, assetConfiguration, isExperimental, clusterDistance) {
            var tileMaps = new TileMapCollection(map, "");

            var map = createOpenLayersMap(startupParameters, tileMaps.layers);

            var mapPluginsContainer = $('#pop-up-map-plugins');
            new ScaleBar(map, mapPluginsContainer);
            new TileMapSelector(mapPluginsContainer);
            new LoadingBarDisplay(map, mapPluginsContainer);

            if (withTileMaps) { new TileMapCollection(map); }
            var roadLayer = new RoadLayer(map, models.roadCollection);
            new ManoeuvreForm(models.selectedManoeuvreSource, new FeedbackModel(backend, assetConfiguration, models.selectedManoeuvreSource));


            //TODO Use layers later for drawing selected manouver
            var manoeuvreLayer = new ManoeuvreLayer(applicationModel, map, roadLayer, models.selectedManoeuvreSource, models.manoeuvresCollection, models.roadCollection,  new TrafficSignReadOnlyLayer({ layerName: 'manoeuvre', map: map, backend: backend }),  new LinearSuggestionLabel() );

            new PopUpMapView(map);
            manoeuvreLayer.refreshWorkListView(item.assetId);
            manoeuvreLayer.show(map);

            backend.getUserRoles();
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

        function openMapPopup(item) {
            var modal = $('<div id="mapModal" class="modal">')
                .css({
                    position: 'fixed',
                    top: '0',
                    left: '0',
                    width: '100%',
                    height: '100%',
                    backgroundColor: 'rgba(0,0,0,0.5)',
                    zIndex: '1000',
                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center'
                });

            var modalContent = $('<div class="modal-content">')
                .css({
                    width: '80%',
                    height: '80%',
                    backgroundColor: '#fff',
                    padding: '20px',
                    position: 'relative'
                });

            var closeButton = $('<button>Sulje kartta</button>')
                .attr('id', 'closeMapButton')
                .addClass('btn header-link-btn') // You can add classes here as needed
                .css({
                    position: 'absolute',
                    top: '10px',
                    right: '10px',
                    zIndex: '1001'
                })
                .click(function() {
                    modal.remove();
                });


            var mapDiv = $('<div id="pop-up-mapdiv">').css({
                width: '100%',
                height: '100%'
            });

            var pluginsDiv = $('<div>', { id: 'pop-up-map-plugins' });
            modalContent
                .append(closeButton)
                .append(pluginsDiv)
                .append(mapDiv);
            modal.append(modalContent);
            $('body').append(modal);

            var models = window.models;
            var linearAssets = window.linearAssets;
            var pointAssets = window.pointAssets;
            var withTileMaps = true;
            var startupParameters = {lon:390000, lat: 6900000, zoom: 2};
            var roadCollection = window.roadCollection;
            var verificationInfoCollection = window.verificationCollection;
            var assetConfiguration = window.assetConfiguration;
            var isExperimental = false;
            var clusterDistance = 60;

            var map = setupMap(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters, roadCollection, verificationInfoCollection, assetConfiguration, isExperimental, clusterDistance);
            map.setTarget('pop-up-mapdiv');
            return map;
        }

        return openMapPopup(item);

    };
})(this);