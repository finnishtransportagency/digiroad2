(function (root) {
  root.LinkPropertyForm = function(selectedLinkProperty) {
    var localizedFunctionalClasses = {
      11: 'Valtatie',
      12: 'Kantatie',
      13: 'Seututie',
      14: 'Yhdystie',
      21: 'Seudullinen pääkatu 1',
      22: 'Seudullinen pääkatu 2',
      23: 'Alueellinen pääkatu',
      24: 'Kokoojakatu',
      25: 'Liityntäkatu',
      35: 'Tärkeä yksityistie',
      36: 'Muu yksityistie',
      40: 'Kevyenliikenteen väylä',
      60: 'Lautta / lossi'
    };

    var localizedTypes = {
      PrivateRoad: 'Yksityistie',
      Street: 'Katu',
      Road: 'Maantie'
    };

    var localizedTrafficDirections = {
      BothDirections: 'Molempiin suuntiin',
      AgainstDigitizing: 'Digitointisuuntaa vastaan',
      TowardsDigitizing: 'Digitointisuuntaan'
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
            '<p class="form-control-static asset-log-info">Muokattu viimeksi: <%- modifiedBy %> <%- modifiedAt %></p>' +
          '</div>' +
          '<div class="form-group">' +
            '<label class="control-label">MML ID</label>' +
            '<p class="form-control-static"><%- mmlId %></p>' +
            '<label class="control-label">Väylätyyppi</label>' +
            '<p class="form-control-static"><%- localizedType %></p>' +
          '</div>' +
          '<div class="form-group editable">' +
            '<label class="control-label">Toiminnallinen luokka</label>' +
            '<p class="form-control-static"><%- localizedFunctionalClass %></p>' +
            '<select class="form-control functional-class" style="display: none"><%= functionalClassOptionTags %></select>' +
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
        linkProperties.modifiedBy = linkProperties.modifiedBy || '-';
        linkProperties.modifiedAt = linkProperties.modifiedAt || '';
        linkProperties.localizedFunctionalClass = localizedFunctionalClasses[linkProperties.functionalClass] || 'Tuntematon';
        linkProperties.localizedType = localizedTypes[linkProperties.type];
        linkProperties.localizedTrafficDirection = localizedTrafficDirections[linkProperties.trafficDirection];
        var trafficDirectionOptionTags = _.map(localizedTrafficDirections, function(value, key) {
          var selected = key === linkProperties.trafficDirection ? " selected" : "";
          return '<option value="' + key + '"' + selected + '>' + value + '</option>';
        }).join('');
        var functionalClassOptionTags = _.map(localizedFunctionalClasses, function(value, key) {
          var selected = key == linkProperties.functionalClass ? " selected" : "";
          return '<option value="' + key + '"' + selected + '>' + value + '</option>';
        }).join('');
        rootElement.html(_.template(template, linkProperties, { imports: { trafficDirectionOptionTags: trafficDirectionOptionTags,
                                                                           functionalClassOptionTags: functionalClassOptionTags }}));
        rootElement.find('.traffic-direction').change(function(event) {
          selectedLinkProperty.get().setTrafficDirection($(event.currentTarget).find(':selected').attr('value'));
        });
        rootElement.find('.functional-class').change(function(event) {
          selectedLinkProperty.get().setFunctionalClass(parseInt($(event.currentTarget).find(':selected').attr('value'), 10));
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
