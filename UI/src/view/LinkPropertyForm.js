(function (root) {
  root.LinkPropertyForm = function(selectedLinkProperty) {
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
    var buttons =
      '<div class="link-properties form-controls">' +
        '<button class="save btn btn-primary" ' + disabled + '>Tallenna</button>' +
        '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>' +
      '</div>';
    var template = '' +
      '<header>' +
        '<span>Linkin ID: <%- roadLinkId %></span>' + buttons +
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
            '<select class="form-control traffic-direction" style="display: none"><%= trafficDirectionOptionTags %></select>' +
          '</div>' +
        '</div>' +
      '</div>' +
      '<footer>' + buttons + '</footer>';

    var bindEvents = function() {
      var rootElement = $('#feature-attributes');
      var toggleMode = function(readOnly) {
        rootElement.find('.editable .form-control-static').toggle(readOnly);
        rootElement.find('select').toggle(!readOnly);
        rootElement.find('.form-controls').toggle(!readOnly);
      };
      eventbus.on('linkProperties:selected linkProperties:cancelled linkProperties:saved', function(linkProperties) {
        linkProperties.localizedFunctionalClass = localizedFunctionalClasses[linkProperties.functionalClass] || 'Tuntematon';
        linkProperties.localizedType = localizedTypes[linkProperties.type];
        linkProperties.localizedTrafficDirection = localizedTrafficDirections[linkProperties.trafficDirection];
        var trafficDirectionOptionTags = _.map(localizedTrafficDirections, function(value, key) {
          var selected = key === linkProperties.trafficDirection ? " selected" : "";
          return '<option value="' + key + '"' + selected + '>' + value + '</option>';
        }).join('');
        rootElement.html(_.template(template, linkProperties, { imports: { trafficDirectionOptionTags: trafficDirectionOptionTags }}));
        rootElement.find('.traffic-direction').change(function(event) {
          selectedLinkProperty.setTrafficDirection($(event.currentTarget).find(':selected').attr('value'));
        });
        toggleMode(applicationModel.isReadOnly());
      });
      eventbus.on('linkProperties:changed', function() {
        rootElement.find('.link-properties button').attr('disabled', false);
      });
      eventbus.on('linkProperties:unselected', function() {
        rootElement.empty();
      });
      eventbus.on('application:readOnly', toggleMode);
      rootElement.on('click', '.link-properties button.save', function() {
        selectedLinkProperty.save();
      });
      rootElement.on('click', '.link-properties button.cancel', function() {
        selectedLinkProperty.cancel();
      });
    };

    bindEvents();
  };
})(this);
