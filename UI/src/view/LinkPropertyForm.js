(function (root) {
  root.LinkPropertyForm = function(selectedLinkProperty) {
    var functionalClasses = [1, 2, 3, 4, 5, 6, 7, 8];

    var localizedAdministrativeClasses = {
      Private: 'Yksityisen omistama',
      Municipality: 'Kunnan omistama',
      State: 'Valtion omistama'
    };

    var localizedTrafficDirections = {
      BothDirections: 'Molempiin suuntiin',
      AgainstDigitizing: 'Digitointisuuntaa vastaan',
      TowardsDigitizing: 'Digitointisuuntaan'
    };

    var linkTypes = [
      [1, 'Moottoritie'],
      [2, 'Moniajoratainen tie'],
      [3, 'Yksiajoratainen tie'],
      [4, 'Moottoriliikennetie'],
      [5, 'Kiertoliittymä'],
      [6, 'Ramppi'],
      [7, 'Levähdysalue'],
      [8, 'Kevyen liikenteen väylä'],
      [9, 'Jalankulkualue'],
      [10, 'Huolto- tai pelastustie'],
      [11, 'Liitännäisliikennealue'],
      [12, 'Ajopolku'],
      [13, 'Huoltoaukko moottoritiellä'],
      [21, 'Lautta/lossi']
    ];

    var getLocalizedLinkType = function(linkType) {
      var localizedLinkType = _.find(linkTypes, function(x) { return x[0] === linkType; });
      return localizedLinkType && localizedLinkType[1];
    };

    var disabled = 'disabled';
    var buttons =
      '<div class="link-properties form-controls">' +
        '<button class="save btn btn-primary" ' + disabled + '>Tallenna</button>' +
        '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>' +
      '</div>';
    var template = '' +
      '<header>' +
        '<span>Linkin MML ID: <%- mmlId %></span>' + buttons +
      '</header>' +
      '<div class="wrapper read-only">' +
        '<div class="form form-horizontal form-dark">' +
          '<div class="form-group">' +
            '<p class="form-control-static asset-log-info">Muokattu viimeksi: <%- modifiedBy %> <%- modifiedAt %></p>' +
          '</div>' +
          '<div class="form-group">' +
            '<label class="control-label">Kuntanumero</label>' +
            '<p class="form-control-static"><%- municipalityCode %></p>' +
          '</div>' +
          '<div class="form-group">' +
            '<label class="control-label">Hallinnollinen luokka</label>' +
            '<p class="form-control-static"><%- localizedAdministrativeClass %></p>' +
          '</div>' +
          '<div class="form-group editable">' +
            '<label class="control-label">Toiminnallinen luokka</label>' +
            '<p class="form-control-static"><%- localizedFunctionalClass %></p>' +
            '<select class="form-control functional-class" style="display: none"><%= functionalClassOptionTags %></select>' +
            '<label class="control-label">Liikennevirran suunta</label>' +
            '<p class="form-control-static"><%- localizedTrafficDirection %></p>' +
            '<select class="form-control traffic-direction" style="display: none"><%= trafficDirectionOptionTags %></select>' +
            '<label class="control-label">Tielinkin tyyppi</label>' +
            '<p class="form-control-static"><%- localizedLinkTypes %></p>' +
            '<select class="form-control link-types" style="display: none"><%= linkTypesOptionTags %></select>' +
          '</div>' +
        '</div>' +
      '</div>' +
      '<footer>' + buttons + '</footer>';

    var renderLinkToIncompleteLinks = function renderLinkToIncompleteLinks() {
      var notRendered = !$('#incomplete-links-link').length;
      if(notRendered) {
        $('#information-content').append('' +
          '<div class="form form-horizontal">' +
              '<a id="incomplete-links-link" class="incomplete-links" href="incomplete_links.html">Korjattavien linkkien lista</a>' +
          '</div>');
      }
    };

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
        linkProperties.localizedFunctionalClass = _.find(functionalClasses, function(x) { return x === linkProperties.functionalClass; }) || 'Tuntematon';
        linkProperties.localizedLinkTypes = getLocalizedLinkType(linkProperties.linkType) || 'Tuntematon';
        linkProperties.localizedAdministrativeClass = localizedAdministrativeClasses[linkProperties.administrativeClass] || 'Tuntematon';
        linkProperties.localizedTrafficDirection = localizedTrafficDirections[linkProperties.trafficDirection] || 'Tuntematon';
        var trafficDirectionOptionTags = _.map(localizedTrafficDirections, function(value, key) {
          var selected = key === linkProperties.trafficDirection ? " selected" : "";
          return '<option value="' + key + '"' + selected + '>' + value + '</option>';
        }).join('');
        var functionalClassOptionTags = _.map(functionalClasses, function(value) {
          var selected = value == linkProperties.functionalClass ? " selected" : "";
          return '<option value="' + value + '"' + selected + '>' + value + '</option>';
        }).join('');
        var linkTypesOptionTags = _.map(linkTypes, function(value) {
          var selected = value[0] == linkProperties.linkType ? " selected" : "";
          return '<option value="' + value[0] + '"' + selected + '>' + value[1] + '</option>';
        }).join('');
        var defaultUnknownOptionTag = '<option value="" style="display:none;"></option>';
        rootElement.html(_.template(template, linkProperties, { imports: { trafficDirectionOptionTags: defaultUnknownOptionTag.concat(trafficDirectionOptionTags),
                                                                           functionalClassOptionTags: defaultUnknownOptionTag.concat(functionalClassOptionTags),
                                                                           linkTypesOptionTags: defaultUnknownOptionTag.concat(linkTypesOptionTags) }}));
        rootElement.find('.traffic-direction').change(function(event) {
          selectedLinkProperty.get().setTrafficDirection($(event.currentTarget).find(':selected').attr('value'));
        });
        rootElement.find('.functional-class').change(function(event) {
          selectedLinkProperty.get().setFunctionalClass(parseInt($(event.currentTarget).find(':selected').attr('value'), 10));
        });
        rootElement.find('.link-types').change(function(event) {
          selectedLinkProperty.get().setLinkType(parseInt($(event.currentTarget).find(':selected').attr('value'), 10));
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


      eventbus.on('layer:selected', function(layer) {
        if(layer === 'linkProperties') {
          renderLinkToIncompleteLinks();
        }
        else {
          $('#incomplete-links-link').parent().remove();
        }
      });

    };

    bindEvents();
  };
})(this);
