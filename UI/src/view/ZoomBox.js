(function (root) {
  root.ZoomBox = function(map, container) {
    var element =
      '<div class="zoombar" data-position="2">' +
        '<div class="plus"></div>' +
        '<div class="minus"></div>' +
      '</div>';
    container.append(element);
    container.find('.plus').click(function() { map.zoomIn(); });
    container.find('.minus').click(function() {
      if (applicationModel.canZoomOut()) {
        map.zoomOut();
      } else {
        new Confirm();
      }
    });
  };
})(this);