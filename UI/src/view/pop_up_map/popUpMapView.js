(function(root) {
    root.PopUpMapView = function(map) {
        var isInitialized = false;

        eventbus.on('application:initialized', function() {
            var zoom = zoomlevels.getViewZoom(map);
            applicationModel.setZoomLevel(zoom);
            isInitialized = true;
            eventbus.trigger('map:initialized', map);
        }, this);

        eventbus.on('manoeuvresOnExpiredLinks:fetched', function (pos) {
            map.getView().setCenter([pos.x, pos.y]);
            map.getView().setZoom(12);
        }, this);
    };
})(this);
