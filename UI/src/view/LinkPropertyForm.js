(function (root) {
  root.LinkPropertyForm = function(selectedLinkProperty) {
    var functionalClasses = [1, 2, 3, 4, 5, 6, 7, 8];

    var localizedAdministrativeClasses = {
      Private: 'Yksityisen omistama',
      Municipality: 'Kunnan omistama',
      State: 'Valtion omistama'
    };

    var administrativeClasses = [
      ['State', 'Valtion omistama'],
      ['Municipality',  'Kunnan omistama'],
      ['Private',  'Yksityisen omistama']
    ];

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

    var verticalLevelTypes= [
      [-11, 'Tunneli'],
      [-1, 'Alikulku'],
      [0, 'Maan pinnalla'],
      [1, 'Silta, Taso 1'],
      [2, 'Silta, Taso 2'],
      [3, 'Silta, Taso 3'],
      [4, 'Silta, Taso 4']
    ];

    var constructionTypes= [
      [0, 'Käytössä'], //In Use
      [1, 'Rakenteilla'], //Under Construction
      [3, 'Suunnitteilla'] //Planned
    ];

    var linkSources= [
      [1, 'MML'],
      [2, 'Täydentävä geometria']
    ];

    var getLocalizedLinkType = function(linkType) {
      var localizedLinkType = _.find(linkTypes, function(x) { return x[0] === linkType; });
      return localizedLinkType && localizedLinkType[1];
    };

    var getVerticalLevelType = function(verticalLevel){
      var verticalLevelType = _.find(verticalLevelTypes, function(y) { return y[0] === verticalLevel; });
      return verticalLevelType && verticalLevelType[1];
    };

    var getConstructionType = function(constructionTypeId){
      var constructionType = _.find(constructionTypes, function(value) { return value[0] === constructionTypeId; });
      return constructionType && constructionType[1];
    };

    var getLinkSource = function(linkSourceId){
      var linkSource = _.find(linkSources, function(value) { return value[0] === linkSourceId; });
      return linkSource && linkSource[1];
    };

    var checkIfMultiSelection = function(mmlId){
      if(selectedLinkProperty.count() == 1){
        return mmlId;
      }
    };

    var staticField = function(labelText, dataField) {
      return '<div class="form-group">' +
               '<label class="control-label">' + labelText + '</label>' +
               '<p class="form-control-static"><%- ' + dataField + ' %></p>' +
             '</div>';
    };

    var title = function() {
      if (selectedLinkProperty.count() == 1) {
        return '<span>Linkin ID: ' + _.first(selectedLinkProperty.get()).linkId + '</span>';
      } else {
        return '<span>Ominaisuustiedot</span>';
      }
    };

    var buttons =
      '<div class="link-properties form-controls">' +
        '<button class="save btn btn-primary" disabled>Tallenna</button>' +
        '<button class="cancel btn btn-secondary" disabled>Peruuta</button>' +
      '</div>';
    var template = function(options) {
      return _.template('' +
        '<header>' +
          title() + buttons +
        '</header>' +
        '<div class="wrapper read-only">' +
          '<div class="form form-horizontal form-dark">' +
            '<div class="form-group">' +
              '<p class="form-control-static asset-log-info">Muokattu viimeksi: <%- modifiedBy %> <%- modifiedAt %></p>' +
            '</div>' +
            '<div class="form-group">' +
              '<p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + selectedLinkProperty.count() + '</p>' +
            '</div>' +
            '<div class="form-group">' +
              '<p class="form-control-static asset-log-info">Geometrian lähde: <%- linkSource %></p>' +
            '</div>' +
            '<div class="form-group editable">' +
              '<label class="control-label">Hallinnollinen luokka</label>' +
              '<p class="form-control-static"><%- localizedAdministrativeClass %></p>' +
              '<select id = "adminClass" class="form-control administrative-class" style="display: none"><%= administrativeClassOptionTags %></select>' +
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
            staticField('Silta, alikulku tai tunneli', 'verticalLevel') +
            staticField('Kuntanumero', 'municipalityCode') +
            staticField('Tien nimi (Suomi)', 'roadNameFi') +
            staticField('Tien nimi (Ruotsi)', 'roadNameSe') +
            staticField('Tien nimi (Saame)', 'roadNameSm') +
            staticField('Tien numero', 'roadNumber') +
            staticField('Tieosanumero', 'roadPartNumber') +
            staticField('Ajorata', 'track') +
            staticField('Etäisyys tieosan alusta', 'startAddrMValue') +
            staticField('Etäisyys tieosan lopusta', 'endAddrMValue') +
            staticField('Osoitenumerot oikealla', 'addressNumbersRight') +
            staticField('Osoitenumerot vasemmalla', 'addressNumbersLeft') +
            staticField('MML ID', 'mmlId') +
            staticField('Linkin tila', 'constructionType') +
          '</div>' +
        '</div>' +
      '<footer>' + buttons + '</footer>', options);
    };

    var renderLinkToIncompleteLinks = function renderLinkToIncompleteLinks() {
      var notRendered = !$('#incomplete-links-link').length;
      if(notRendered) {
        $('#information-content').append('' +
          '<div class="form form-horizontal">' +
              '<a id="incomplete-links-link" class="incomplete-links" href="#work-list/linkProperty">Korjattavien linkkien lista</a>' +
          '</div>');
      }
    };

    var addressNumberString = function(minAddressNumber, maxAddressNumber) {
      if(!minAddressNumber && !maxAddressNumber) {
        return '';
      } else {
        var min = minAddressNumber || '';
        var max = maxAddressNumber || '';
        return min + '-' + max;
      }
    };

    function controlAdministrativeClasses(administrativeClass) {
      $("#adminClass").prop('disabled', administrativeClass == 'State');
      $("#adminClass").find("option[value = State ]").prop('disabled', true);
    }

    var bindEvents = function() {
      var rootElement = $('#feature-attributes');
      var toggleMode = function(readOnly) {
        rootElement.find('.editable .form-control-static').toggle(readOnly);
        rootElement.find('select').toggle(!readOnly);
        rootElement.find('.form-controls').toggle(!readOnly);
      };
      eventbus.on('linkProperties:selected linkProperties:cancelled', function(linkProperties) {
        linkProperties.modifiedBy = linkProperties.modifiedBy || '-';
        linkProperties.modifiedAt = linkProperties.modifiedAt || '';
        linkProperties.localizedFunctionalClass = _.find(functionalClasses, function(x) { return x === linkProperties.functionalClass; }) || 'Tuntematon';
        linkProperties.localizedLinkTypes = getLocalizedLinkType(linkProperties.linkType) || 'Tuntematon';
        linkProperties.localizedAdministrativeClass = localizedAdministrativeClasses[linkProperties.administrativeClass] || 'Tuntematon';
        linkProperties.localizedTrafficDirection = localizedTrafficDirections[linkProperties.trafficDirection] || 'Tuntematon';
        linkProperties.roadNameFi = linkProperties.roadNameFi || '';
        linkProperties.roadNameSe = linkProperties.roadNameSe || '';
        linkProperties.roadNameSm = linkProperties.roadNameSm || '';
        linkProperties.addressNumbersRight = addressNumberString(linkProperties.minAddressNumberRight, linkProperties.maxAddressNumberRight);
        linkProperties.addressNumbersLeft = addressNumberString(linkProperties.minAddressNumberLeft, linkProperties.maxAddressNumberLeft);
        linkProperties.roadNumber = linkProperties.roadNumber || '';
        linkProperties.roadPartNumber = linkProperties.roadPartNumber || '';
        linkProperties.track = isNaN(parseFloat(linkProperties.track)) ? '' : linkProperties.track;
        linkProperties.startAddrMValue = isNaN(parseFloat(linkProperties.startAddrMValue)) ? '' : linkProperties.startAddrMValue;
        linkProperties.endAddrMValue = isNaN(parseFloat(linkProperties.endAddrMValue)) ? '' : linkProperties.endAddrMValue;
        linkProperties.verticalLevel = getVerticalLevelType(linkProperties.verticalLevel) || '';
        linkProperties.constructionType = getConstructionType(linkProperties.constructionType) || '';
        linkProperties.linkSource = getLinkSource(linkProperties.linkSource) || '';
        linkProperties.mmlId = checkIfMultiSelection(linkProperties.mmlId) || '';
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
        var administrativeClassOptionTags = _.map(administrativeClasses, function(value) {
          var selected = value[0] == linkProperties.administrativeClass ? " selected" : "";
          return '<option value="' + value[0] + '"' + selected + '>' + value[1] + '</option>' ;
        }).join('');

        var defaultUnknownOptionTag = '<option value="" style="display:none;"></option>';
        var options =  { imports: { trafficDirectionOptionTags: defaultUnknownOptionTag.concat(trafficDirectionOptionTags),
                                    functionalClassOptionTags: defaultUnknownOptionTag.concat(functionalClassOptionTags),
                                    linkTypesOptionTags: defaultUnknownOptionTag.concat(linkTypesOptionTags),
                                    administrativeClassOptionTags : defaultUnknownOptionTag.concat(administrativeClassOptionTags)}};
        rootElement.html(template(options)(linkProperties));
        rootElement.find('.traffic-direction').change(function(event) {
          selectedLinkProperty.setTrafficDirection($(event.currentTarget).find(':selected').attr('value'));
        });
        rootElement.find('.functional-class').change(function(event) {
          selectedLinkProperty.setFunctionalClass(parseInt($(event.currentTarget).find(':selected').attr('value'), 10));
        });
        rootElement.find('.link-types').change(function(event) {
          selectedLinkProperty.setLinkType(parseInt($(event.currentTarget).find(':selected').attr('value'), 10));
        });
        rootElement.find('.administrative-class').change(function(event) {
          selectedLinkProperty.setAdministrativeClass($(event.currentTarget).find(':selected').attr('value'));
        });
        toggleMode(applicationModel.isReadOnly());
        controlAdministrativeClasses(linkProperties.administrativeClass);
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
        if(layer === 'linkProperty') {
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
