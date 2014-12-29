(function (root) {
  root.LinkPropertyForm = function() {
    var localizedFunctionalClasses = {
      1: 'Seudullinen pääkatu / valtatie',
      2: 'Seudullinen pääkatu / kantatie',
      3: 'Alueellinen pääkatu / seututie',
      4: 'Kokoojakatu / yhdystie',
      5: 'Liityntäkatu / tärkeä yksityistie',
      6: 'Muu yksityistie',
      0: 'Kevyen liikenteen väylä'
    };

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
            '<label class="control-label">MML ID</label>' +
            '<p class="form-control-static"><%- mmlId %></p>' +
            '<label class="control-label">Väylätyyppi</label>' +
            '<p class="form-control-static"><%- localizedType %></p>' +
            '<label class="control-label">Toiminnallinen luokka</label>' +
            '<p class="form-control-static"><%- localizedFunctionalClass %></p>' +
            '<label class="control-label">Liikennevirran suunta</label>' +
            '<p class="form-control-static"><%- localizedTrafficDirection %></p>' +
          '</div>' +
        '</div>' +
      '</div>');

    var bindEvents = function() {
      var rootElement = $('#feature-attributes');
      eventbus.on('linkProperties:selected', function(linkProperties) {
        linkProperties.localizedFunctionalClass = localizedFunctionalClasses[linkProperties.functionalClass] || 'Tuntematon';
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
