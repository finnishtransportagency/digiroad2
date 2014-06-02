(function() {
    window.zoomlevels = {
        isInRoadLinkZoomLevel: function (zoom) { return zoom >= this.minZoomForRoadLinks; },
        isInAssetZoomLevel: function(zoom) { return zoom >= 9; },
        getAssetZoomLevelIfNotCloser: function(zoom) { return zoom < 10 ? 10 : zoom; },
        minZoomForRoadLinks: 10
    };
})();