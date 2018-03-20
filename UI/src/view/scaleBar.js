(function (root) {
  root.ScaleBar = function(map, container) {
    var element = '<div class="scalebar"/>';
    container.append(element);
    map.addControl(new ol.control.ScaleLine({
      target: container.find('.scalebar')[0],
      className: 'olScaleLine'
    }));
  };
})(this);