(function (root) {
  root.LinkPropertyForm = function() {
    var localizedTypes = {
      PrivateRoad: 'Yksityistie',
      Street: 'Katu',
      Road: 'Maantie'
    };

    var localizedTrafficDirections = {
      BothDirections: 'Molempiin suuntiin',
      AgainstDigitizing: 'Digitointisuuntaa vastaan',
      TowardsDigitizing: 'Digitointisuuntaan',
      NeitherDirection: 'Ei kumpaankaan suuntaan',
      UnknownDirection: 'Tuntematon'
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
            '<label class="control-label">Liikennevirran suunta</label>' +
            '<p class="form-control-static"><%- localizedTrafficDirection %></p>' +
          '</div>' +
        '</div>' +
      '</div>');

    var bindEvents = function() {
      var rootElement = $('#feature-attributes');
      eventbus.on('linkProperties:selected', function(linkProperties) {
        linkProperties.localizedType = localizedTypes[linkProperties.type];
        linkProperties.localizedTrafficDirection = localizedTrafficDirections[linkProperties.trafficDirection];
        rootElement.html(template(linkProperties));
      });
      eventbus.on('linkProperties:unselected', function() {
        rootElement.empty();
      });
    };

    bindEvents();
  };
})(this);
