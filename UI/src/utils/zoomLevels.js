(function() {
    window.zoomlevels = {
        isInRoadLinkZoomLevel: function (zoom) { return zoom >= 10; },
        isInAssetZoomLevel: function(zoom) { return zoom >= 9; },
        getAssetZoomLevelIfNotCloser: function(zoom) { return zoom < 10 ? 10 : zoom; }
    };
})();