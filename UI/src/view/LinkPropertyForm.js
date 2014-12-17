(function (root) {
  root.LinkPropertyForm = function() {
    var template = function(linkProperties) {
      var title = '<span>Linkin ID: ' + linkProperties.roadLinkId + '</span>';
      return '<header>' + title + '</header>';
    };

    var bindEvents = function() {
      var rootElement = $('#feature-attributes');
      eventbus.on('linkProperties:selected', function(linkProperties) {
        rootElement.html(template(linkProperties));
      });
      eventbus.on('linkProperties:unselected', function() {
        rootElement.empty();
      });
    };

    bindEvents();
  };
})(this);
