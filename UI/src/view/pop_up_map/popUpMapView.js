(function(root) {
    root.PopUpMapView = function(map) {
        var isInitialized = false;

        eventbus.on('application:initialized', function() {
            var zoom = zoomlevels.getViewZoom(map);
            applicationModel.setZoomLevel(zoom);
            isInitialized = true;
            eventbus.trigger('map:initialized', map);
        }, this);

        eventbus.on('coordinates:selected', function(position) {
            if (geometrycalculator.isInBounds(map.getProperties().extent, position.lon, position.lat)) {
                map.getView().setCenter([position.lon, position.lat]);
                map.getView().setZoom(zoomlevels.getAssetZoomLevelIfNotCloser(zoomlevels.getViewZoom(map)));
            } else {

            }
        }, this);

        map.on('moveend', function(event) {
            var target = document.getElementById(map.getTarget());
            target.style.cursor = '';
            applicationModel.moveMap(zoomlevels.getViewZoom(map), map.getView().calculateExtent(map.getSize()), map.getView().getCenter());
        });

        map.on('pointermove', function(event) {
            var pixel = map.getEventPixel(event.originalEvent);
            var hit = map.hasFeatureAtPixel(pixel);
            var target = document.getElementById(map.getTarget());
            target.style.cursor = hit ? 'pointer' : (target.style.cursor === 'move' ? target.style.cursor : '');
            eventbus.trigger('map:mouseMoved', event);
        }, true);

        map.on('singleclick', function(event) {
            eventbus.trigger('map:clicked', { x: event.coordinate.shift(), y: event.coordinate.shift() });
        });

        map.on('pointerdrag', function(event) {
            var target = document.getElementById(map.getTarget());
            target.style.cursor = 'move';
        });
    };
})(this);
