(function() {
  window.zoomlevels = {
    isInRoadLinkZoomLevel: function(zoom) {
      return zoom >= this.minZoomForRoadLinks;
    },
    isInAssetZoomLevel: function(zoom) {
      return zoom >= this.minZoomForAssets;
    },
    getAssetZoomLevelIfNotCloser: function(zoom) {
      return zoom < 10 ? 10 : zoom;
    },
    getViewZoom : function(map){
      return zoomlevels.getViewZoom(map);
    },
    minZoomForAssets: 10,
    minZoomForRoadLinks: 10,
    maxZoomLevel: 12
  };
})();