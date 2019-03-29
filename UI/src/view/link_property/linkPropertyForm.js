(function (root) {
  root.LinkPropertyForm = function(selectedLinkProperty, feedbackCollection) {
    var layer;
    var functionalClasses = [1, 2, 3, 4, 5, 6, 7, 8];
    var authorizationPolicy = new LinkPropertyAuthorizationPolicy();
    new FeedbackDataTool(feedbackCollection, 'linkProperty', authorizationPolicy);

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

    var additionalInfoIds = [
      [1, 'Tieto toimitettu, rajoituksia'],
      [2, 'Tieto toimitettu, ei rajoituksia'],
      [99, 'Ei toimitettu']
    ];

    var localizedAdditionalInfoIds = {
      DeliveredWithRestrictions:  'Tieto toimitettu, rajoituksia',
      DeliveredWithoutRestrictions: 'Tieto toimitettu, ei rajoituksia',
      NotDelivered: 'Ei toimitettu'
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
      [14, 'Erikoiskuljetusyhteys ilman puomia'],
      [15, 'Erikoiskuljetusyhteys puomilla'],
      [21, 'Lautta/lossi']
    ];

    var verticalLevelTypes= [
      [-11, 'Tunneli'],
      [-3, 'Alikulku, taso 3'],
      [-2, 'Alikulku, taso 2'],
      [-1, 'Alikulku, taso 1'],
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

    var getAdditionalInfo = function(additionalInfoValue) {
      var additionalInfo = _.find(additionalInfoIds, function(x) { return x[0] === additionalInfoValue; });
      return additionalInfo && additionalInfo[1];
    };

    var checkIfMultiSelection = function(mmlId){
      if(selectedLinkProperty.count() === 1){
        return mmlId;
      }
    };

    var staticField = function(labelText, dataField) {
      return '<div class="form-group">' +
        '<label class="control-label">' + labelText + '</label>' +
        '<p class="form-control-static"><%- ' + dataField + ' %></p>' +
        '</div>';
    };

    var header = function() {
      if (selectedLinkProperty.count() === 1) {
        return '<span>Linkin ID: ' + _.head(selectedLinkProperty.get()).linkId + '</span>';
      } else {
        return '<span>Ominaisuustiedot</span>';
      }
    };

    var buttons =
      '<div class="link-properties form-controls">' +
      '<button class="save btn btn-primary" disabled>Tallenna</button>' +
      '<button class="cancel btn btn-secondary" disabled>Peruuta</button>' +
      '</div>';

    var userInformationLog = function() {
      var hasMunicipality = function (linearAsset) {
        return _.every(linearAsset.get(), function (asset) {
          return authorizationPolicy.hasRightsInMunicipality(asset.municipalityCode);
        });
      };

      var limitedRights = 'Käyttöoikeudet eivät riitä kohteen muokkaamiseen. Voit muokata kohteita vain oman kuntasi alueelta.';
      var noRights = 'Käyttöoikeudet eivät riitä kohteen muokkaamiseen.';
      var message = '';

      if (!authorizationPolicy.isOperator() && (authorizationPolicy.isMunicipalityMaintainer() || authorizationPolicy.isElyMaintainer()) && !hasMunicipality(selectedLinkProperty)) {
        message = limitedRights;
      } else if (!authorizationPolicy.validateMultiple(selectedLinkProperty.get()))
        message = noRights;

      if(message) {
        return '' +
            '<div class="form-group user-information">' +
            '<p class="form-control-static user-log-info">' + message + '</p>' +
            '</div>';
      } else
        return '';
    };

    var template = function(options) {
      return _.template('' +
        '<div class="wrapper read-only">' +
          '<div class="form form-horizontal form-dark">' +
            '<div class="form-group">' +
              '<p class="form-control-static asset-log-info">Muokattu viimeksi: <%- modifiedAt %> / <%- modifiedBy %></p>' +
            '</div>' +
            '<div class="form-group">' +
              '<p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + selectedLinkProperty.count() + '</p>' +
            '</div>' +
            '<div class="form-group">' +
              '<p class="form-control-static asset-log-info">Geometrian lähde: <%- linkSource %></p>' +
            '</div>' +
            userInformationLog() +
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
            '<div class="form-group editable private-road" style="display: none">' +
              '<div class="form-group editable">' +
                '<label class="control-label">Käyttöoikeustunnus</label>' +
                '<p class="form-control-static"><%- accessRightID %></p>' +
                '<input type="text" class="form-control access-right-id"  style="display: none" value="<%- accessRightID %>">' +
              '</div>' +
              '<div class="form-group editable">' +
                '<label class="control-label">Tiekunnan nimi </label>' +
                '<p class="form-control-static"><%- privateRoadAssociation %></p>' +
                '<input type="text" class="form-control private-road-association" style="display: none" value="<%- privateRoadAssociation %>">' +
                '</div>' +
              '<label class="control-label">Lisätieto</label>' +
              '<p class="form-control-static"><%- localizedAdditionalInfoIds %></p>' +
              '<select class="form-control additional-info" style="display: none"><%= additionalInfoOptionTags %></select>' +
            '</div>' +
          '</div>' +
        '</div>', options);
    };

    var footer = function() { return buttons;};

    var renderLinkToIncompleteLinks = function renderLinkToIncompleteLinks() {
      var notRendered = !$('#incomplete-links-link').length;
      if(notRendered) {
        $('ul[class=information-content]').empty();
        $('ul[class=information-content]').append('' +
            '<li><button id="incomplete-links-link" class="incomplete-links btn btn-tertiary" onclick=location.href="#work-list/linkProperty">Korjattavien linkkien lista</button></li>');
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

    var controlAdministrativeClasses = function(administrativeClass) {
      $("#adminClass").prop('disabled', administrativeClass === 'State');
      $("#adminClass").find("option[value = State ]").prop('disabled', true);
    };

    var controlAdministrativeClassesOnToggle = function(selectedLinkProperty) {
      var disabled = !_.isEmpty(_.filter(selectedLinkProperty.get(), function (link) {
        return link.administrativeClass === "State";
      }));
      $("#adminClass").prop('disabled', disabled);
      $("#adminClass").find("option[value = State ]").prop('disabled', true);
    };

    var validateSelectedAccessRight = function(selectedLinkProperty){
     return !_.isEmpty(_.filter(selectedLinkProperty.get(), function (link) {
        return link.administrativeClass === "Private";
      }));
    };

    var constructLinkProperty = function(linkProperty) {
      return  _.assign ( {}, linkProperty, {
        modifiedBy : linkProperty.modifiedBy || '-',
        modifiedAt : linkProperty.modifiedAt || '' ,
        roadNameFi : linkProperty.roadNameFi || '',
        roadNameSe : linkProperty.roadNameSe || '',
        roadNameSm : linkProperty.roadNameSm || '',
        roadNumber : linkProperty.roadNumber || '',
        roadPartNumber : linkProperty.roadPartNumber || '',
        localizedFunctionalClass : _.find(functionalClasses, function(x) { return x === linkProperty.functionalClass; }) || 'Tuntematon',
        localizedAdministrativeClass : localizedAdministrativeClasses[linkProperty.administrativeClass] || 'Tuntematon',
        localizedAdditionalInfoIds: getAdditionalInfo(parseInt(linkProperty.additionalInfo)) || '',
        localizedTrafficDirection : localizedTrafficDirections[linkProperty.trafficDirection] || 'Tuntematon',
        localizedLinkTypes : getLocalizedLinkType(linkProperty.linkType) || 'Tuntematon',
        addressNumbersRight : addressNumberString(linkProperty.minAddressNumberRight, linkProperty.maxAddressNumberRight),
        addressNumbersLeft : addressNumberString(linkProperty.minAddressNumberLeft, linkProperty.maxAddressNumberLeft),
        track : isNaN(parseFloat(linkProperty.track)) ? '' : linkProperty.track,
        startAddrMValue : isNaN(parseFloat(linkProperty.startAddrMValue)) ? '' : linkProperty.startAddrMValue,
        endAddrMValue : isNaN(parseFloat(linkProperty.endAddrMValue)) ? '' : linkProperty.endAddrMValue,
        verticalLevel : getVerticalLevelType(linkProperty.verticalLevel) || '',
        constructionType : getConstructionType(linkProperty.constructionType) || '',
        linkSource : getLinkSource(linkProperty.linkSource) || '',
        mmlId : checkIfMultiSelection(linkProperty.mmlId) || '',
        accessRightID: linkProperty.accessRightID || '',
        privateRoadAssociation: linkProperty.privateRoadAssociation || '',
        additionalInfo: !isNaN(parseInt(linkProperty.additionalInfo)) ? parseInt(linkProperty.additionalInfo) : 99 // Ei toimitettu
      });
    };

    var bindEvents = function() {
      var rootElement = $('#feature-attributes');

      var toggleMode = function(readOnly) {
        rootElement.find('.editable .form-control-static').toggle(readOnly);
        rootElement.find('select').toggle(!readOnly);
        rootElement.find('input').toggle(!readOnly);
        rootElement.find('.form-controls').toggle(!readOnly);
        rootElement.find('.editable.private-road').toggle(validateSelectedAccessRight(selectedLinkProperty));
      };

      eventbus.on('linkProperties:selected linkProperties:cancelled', function(properties) {
        var linkProperty = constructLinkProperty(properties);

        var trafficDirectionOptionTags = _.map(localizedTrafficDirections, function(value, key) {
          var selected = key === linkProperty.trafficDirection ? " selected" : "";
          return '<option value="' + key + '"' + selected + '>' + value + '</option>';
        }).join('');

        var functionalClassOptionTags = _.map(functionalClasses, function(value) {
          var selected = value === linkProperty.functionalClass ? " selected" : "";
          return '<option value="' + value + '"' + selected + '>' + value + '</option>';
        }).join('');

        var linkTypesOptionTags = _.map(linkTypes, function(value) {
          var selected = value[0] === linkProperty.linkType ? " selected" : "";
          return '<option value="' + value[0] + '"' + selected + '>' + value[1] + '</option>';
        }).join('');

        var administrativeClassOptionTags = _.map(administrativeClasses, function(value) {
          var selected = value[0] === linkProperty.administrativeClass ? " selected" : "";
          return '<option value="' + value[0] + '"' + selected + '>' + value[1] + '</option>' ;
        }).join('');

        var additionalInfoOptionTags = _.map( additionalInfoIds, function(value) {
          var selected = value[0] === linkProperty.additionalInfo ? " selected" : "";
          return '<option value="' + value[0] + '"' + selected + '>' + value[1] + '</option>' ;
        }).join('');

        var privateRoadAssociationValueTag = linkProperty.privateRoadAssociation;
        var additionalInfoValueTag = linkProperty.accessRightID;

        var defaultUnknownOptionTag = '<option value="" style="display:none;"></option>';

        var options =  {  imports: {
            trafficDirectionOptionTags: defaultUnknownOptionTag.concat(trafficDirectionOptionTags),
            functionalClassOptionTags: defaultUnknownOptionTag.concat(functionalClassOptionTags),
            linkTypesOptionTags: defaultUnknownOptionTag.concat(linkTypesOptionTags),
            administrativeClassOptionTags : defaultUnknownOptionTag.concat(administrativeClassOptionTags),
            additionalInfoOptionTags: defaultUnknownOptionTag.concat(additionalInfoOptionTags),
            privateRoadAssociationValueTag: defaultUnknownOptionTag.concat(privateRoadAssociationValueTag),
            additionalInfoValueTag: defaultUnknownOptionTag.concat(additionalInfoValueTag)}
        };

        rootElement.find('#feature-attributes-header').html(header());
        rootElement.find('#feature-attributes-form').html(template(options)(linkProperty));
        rootElement.find('#feature-attributes-footer').html(footer());

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
          var administrativeClass = $(event.currentTarget).find(':selected').attr('value');
          selectedLinkProperty.setAdministrativeClass(administrativeClass);
          if(administrativeClass === "Private") {
            $(".private-road").css("display","block");
          } else $(".private-road").css("display","none");
        });
        rootElement.find('.access-right-id').keyup(function(event) {
          selectedLinkProperty.setAccessRightId($(event.currentTarget).val());
        });
        rootElement.find('.private-road-association').keyup(function(event) {
          selectedLinkProperty.setPrivateRoadAssociation($(event.currentTarget).val());
        });
        rootElement.find('.additional-info').change(function(event) {
          selectedLinkProperty.setAdditionalInfo($(event.currentTarget).find(':selected').attr('value'));
        });

        toggleMode(applicationModel.isReadOnly() || !authorizationPolicy.validateMultiple(selectedLinkProperty.get()));
        controlAdministrativeClasses(linkProperty.administrativeClass);
      });

      eventbus.on('linkProperties:changed', function() {
        rootElement.find('.link-properties button').attr('disabled', false);
      });

      eventbus.on('linkProperties:unselected', function() {
        rootElement.find('#feature-attributes-header').empty();
        rootElement.find('#feature-attributes-form').empty();
        rootElement.find('#feature-attributes-footer').empty();
        rootElement.find('li > a[id=feedback-data]').remove();
      });

      eventbus.on('application:readOnly', function(readOnly){
        toggleMode(!authorizationPolicy.validateMultiple(selectedLinkProperty.get()) || readOnly);
        controlAdministrativeClassesOnToggle(selectedLinkProperty);
      });

      eventbus.on('layer:selected', function(layerName) {
        layer = layerName;
        if(layerName === 'linkProperty') {
          renderLinkToIncompleteLinks();
        }
        else {
          $('#incomplete-links-link').parent().remove();
        }
      });

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
