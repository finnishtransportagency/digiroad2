(function(root) {
  root.ApplicationModel = {
    moveMap: function(zoom, bbox) {
      eventbus.trigger('map:moved', {zoom: zoom, bbox: bbox});
    }
  };
})(this);
