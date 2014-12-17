(function (root) {
  root.LinkPropertyForm = function() {
    var template = _.template('<header><span>Linkin ID: <%- roadLinkId %></span></header>');

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
