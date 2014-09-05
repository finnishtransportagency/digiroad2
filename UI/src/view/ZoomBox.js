(function (root) {
  root.ZoomBox = function(map, container) {
    var element =
      '<div class="oskariui mapplugin pzbDiv zoombar" data-position="2">' +
        '<div class="pzbDiv-plus"></div>' +
        '<div class="pzbDiv-minus"></div>' +
      '</div>';
    container.append(element);
    container.find('.pzbDiv-plus').click(function() { map.zoomIn(); });
    container.find('.pzbDiv-minus').click(function() { map.zoomOut(); });
  };
})(this);