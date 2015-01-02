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

    var disabled = 'disabled';
    var template = '' +
      '<header>' +
        '<span>Linkin ID: <%- roadLinkId %></span>' +
        '<div class="road form-controls">' +
          '<button class="save btn btn-primary" ' + disabled + '>Tallenna</button>' +
          '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>' +
        '</div>' +
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
          '</div>' +
          '<div class="form-group editable">' +
            '<label class="control-label">Liikennevirran suunta</label>' +
            '<p class="form-control-static"><%- localizedTrafficDirection %></p>' +
            '<select class="form-control speed-limit" style="display: none"><%= trafficDirectionOptionTags %></select>' +
          '</div>' +
        '</div>' +
      '</div>' +
      '<footer class="road form-controls" style="display: none">' +
        '<button class="save btn btn-primary" ' + disabled + '>Tallenna</button>' +
        '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>' +
      '</footer>';

    var bindEvents = function() {
      var rootElement = $('#feature-attributes');
      var toggleMode = function(readOnly) {
        rootElement.find('.editable .form-control-static').toggle(readOnly);
        rootElement.find('select').toggle(!readOnly);
        rootElement.find('.form-controls').toggle(!readOnly);
      };
      eventbus.on('linkProperties:selected', function(linkProperties) {
        linkProperties.localizedFunctionalClass = localizedFunctionalClasses[linkProperties.functionalClass] || 'Tuntematon';
        linkProperties.localizedType = localizedTypes[linkProperties.type];
        linkProperties.localizedTrafficDirection = localizedTrafficDirections[linkProperties.trafficDirection];
        var trafficDirectionOptionTags = _.map(localizedTrafficDirections, function(value, key) {
          var selected = key === linkProperties.trafficDirection ? " selected" : "";
          return '<option value="' + value + '"' + selected + '>' + value + '</option>';
        }).join('');
        rootElement.html(_.template(template, linkProperties, { imports: { trafficDirectionOptionTags: trafficDirectionOptionTags }}));
        toggleMode(applicationModel.isReadOnly());
      });
      eventbus.on('linkProperties:unselected', function() {
        rootElement.empty();
      });
      eventbus.on('application:readOnly', toggleMode);
    };

    bindEvents();
  };
})(this);
