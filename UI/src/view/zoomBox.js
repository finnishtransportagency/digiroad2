(function (root) {
  root.ZoomBox = function(map, container) {
    var element =
      '<div class="zoombar" data-position="2">' +
      '<div class="plus"></div>' +
      '<div class="minus"></div>' +
      '</div>';
    container.append(element);
    container.find('.plus').click(function() {
      var zoom=  zoomlevels.getViewZoom(map);
      map.getView().animate({
        zoom: zoom + 1,
        duration: 150
      });
    });
    container.find('.minus').click(function() {
      var zoomLevel = zoomlevels.getViewZoom(map);
      if (applicationModel.canZoomOut(zoomLevel)) {
        map.getView().animate({
          zoom: zoomLevel -1,
          duration: 150
        });
      } else {
        new Confirm();
      }
    });
  };
})(this);