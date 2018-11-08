(function (root) {
  root.LinkPropertyForm = function(selectedLinkProperty, feedbackCollection) {
    var layer;
    var functionalClasses = [1, 2, 3, 4, 5, 6, 7, 8];
    var authorizationPolicy = new SpeedLimitAuthorizationPolicy();
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

    var accessRightIds = [
      [1, 'Tieto toimitettu, rajoituksia'],
      [2, 'Tieto toimitettu, ei rajoituksia'],
      [3, 'Ei toimitettu']
    ];

    var localizedAccessRightIds = {
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

    var getAccessRight = function(accessRightId) {
      var localizedAccessRightIds = _.find(accessRightIds, function(x) { return x[0] === accessRightId; });
      return localizedAccessRightIds && localizedAccessRightIds[1];
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

    var title = function() {
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
              '<p class="form-control-static"><%- localizedAccessRightIds %></p>' +
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
                '<label class="control-label">Tiekunnan nimi</label>' +
                '<input type="text" class="form-control private-road-association" id="private_road_association" style="display: none">' +
              '</div>' +
              '<div class="form-group editable">' +
                '<label class="control-label">Lisätieto </label>' +
                '<input type="text" class="form-control additional-info" id="additional_info" style="display: none">' +
              '</div>' +
              '<label class="control-label">Käyttöoikeustunnus</label>' +
              '<p class="form-control-static"><%- localizedLinkTypes %></p>' +
              '<select class="form-control access-right-id" style="display: none"><%= accessRightIdsOptionTags %></select>' +
            '</div>' +
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

    var validateAdministrativeClass = function(selectedLinkProperty, authorizationPolicy){
      var selectedAssets = _.filter(selectedLinkProperty.get(), function (selected) {
        return !authorizationPolicy.formEditModeAccess(selected);
      });
      return !_.isEmpty(selectedAssets);
    };

    var showAccessRightsOnForm = function (linkProperty) {
      return linkProperty.administrativeClass === 'Private';
    };

    var validadeSelectedAccessRight = function(selectedLinkProperty){
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
        localizedAccessRightIds: localizedAccessRightIds[linkProperty.accessRightID] || 'Tuntematon',
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
        accessRightID: getAccessRight(linkProperty.accessRightID) || 'Tuntematon',
        privateRoadAssociation: linkProperty.privateRoadAssociation || '-',
        additionalInfo:  linkProperty.additionalInfo || '-'
      });
    };

    var bindEvents = function() {
      var rootElement = $('#feature-attributes');

      var toggleMode = function(readOnly) {
        rootElement.find('.editable .form-control-static').toggle(readOnly);
        rootElement.find('select').toggle(!readOnly);
        rootElement.find('input').toggle(!readOnly);
        rootElement.find('.form-controls').toggle(!readOnly);
        rootElement.find('.editable.private-road').toggle(validadeSelectedAccessRight(selectedLinkProperty));
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

        //TODO: check when backend is done
        var accessRightIdsOptionTags = _.map( accessRightIds, function(value) {
          var selected = value[0] === linkProperty.accessRightID ? " selected" : "";
          return '<option value="' + value[0] + '"' + selected + '>' + value[1] + '</option>' ;
        }).join('');

        var defaultUnknownOptionTag = '<option value="" style="display:none;"></option>';

        var options =  {  imports: {
            trafficDirectionOptionTags: defaultUnknownOptionTag.concat(trafficDirectionOptionTags),
            functionalClassOptionTags: defaultUnknownOptionTag.concat(functionalClassOptionTags),
            linkTypesOptionTags: defaultUnknownOptionTag.concat(linkTypesOptionTags),
            administrativeClassOptionTags : defaultUnknownOptionTag.concat(administrativeClassOptionTags),
            accessRightIdsOptionTags: defaultUnknownOptionTag.concat(accessRightIdsOptionTags)}
        };

        rootElement.html(template(options)(linkProperty));

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
        rootElement.find('.access-right-id').change(function(event) {
          selectedLinkProperty.setAccessRight($(event.currentTarget).find(':selected').attr('value'));
        });
        rootElement.find('.private-road-association').keyup(function(event) {
          selectedLinkProperty.setPrivateRoadAssociation($(event.currentTarget).val());
        });
        rootElement.find('.additional-info').keyup(function(event) {
          selectedLinkProperty.setAdditionalInfo($(event.currentTarget).val());
        });

        toggleMode(validateAdministrativeClass(selectedLinkProperty, authorizationPolicy) || applicationModel.isReadOnly());
        controlAdministrativeClasses(linkProperty.administrativeClass);
      });

      eventbus.on('linkProperties:changed', function() {
        rootElement.find('.link-properties button').attr('disabled', false);
      });

      eventbus.on('linkProperties:unselected', function() {
        rootElement.empty();
      });

      eventbus.on('application:readOnly', function(readOnly){
        toggleMode(validateAdministrativeClass(selectedLinkProperty, authorizationPolicy) || readOnly);
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
