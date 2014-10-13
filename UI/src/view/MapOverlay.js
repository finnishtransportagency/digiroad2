(function (root) {
  root.MapOverlay = function(container) {
    var element = '<div id="map-overlay" style="display: none"></div>';
    container.append(element);

    var show = function() {
      container.find('#map-overlay').show();
    };

    var hide = function() {
      container.find('#map-overlay').hide();
    };

    return {
      show: show,
      hide: hide
    };
  };
})(this);