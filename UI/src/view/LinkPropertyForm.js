(function (root) {
  root.LinkPropertyForm = function() {
    var localizedTypes = {
      PrivateRoad: 'Yksityistie',
      Street: 'Katu',
      Road: 'Maantie'
    };

    var template = _.template('' +
      '<header>' +
        '<span>Linkin ID: <%- roadLinkId %></span>' +
      '</header>' +
      '<div class="wrapper read-only">' +
        '<div class="form form-horizontal form-dark">' +
          '<div class="form-group">' +
            '<label class="control-label">Väylätyyppi</label>' +
            '<p class="form-control-static"><%- localizedType %></p>' +
            '<label class="control-label">Toiminnallinen luokka</label>' +
            '<p class="form-control-static"><%- functionalClass %></p>' +
          '</div>' +
        '</div>' +
      '</div>');

    var bindEvents = function() {
      var rootElement = $('#feature-attributes');
      eventbus.on('linkProperties:selected', function(linkProperties) {
        linkProperties.localizedType = localizedTypes[linkProperties.type];
        rootElement.html(template(linkProperties));
      });
      eventbus.on('linkProperties:unselected', function() {
        rootElement.empty();
      });
    };

    bindEvents();
  };
})(this);
