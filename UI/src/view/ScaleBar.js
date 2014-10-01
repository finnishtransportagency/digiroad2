(function (root) {
  root.ScaleBar = function(map, container) {
    var element = '<div class="scalebar"/>';
    container.append(element);
    map.addControl(new OpenLayers.Control.ScaleLine({
      div: container.find('.scalebar')[0]
    }));
  };
})(this);