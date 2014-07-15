(function(root) {
  var zoomLevel;
  root.ApplicationModel = {
    moveMap: function(zoom, bbox) {
      var hasZoomLevelChanged = zoomLevel !== zoom;
      zoomLevel = zoom;
      eventbus.trigger('map:moved', {zoom: zoom, bbox: bbox, hasZoomLevelChanged: hasZoomLevelChanged});
    },
    setZoomLevel: function(level) {
      zoomLevel = level;
    }
  };
})(this);
