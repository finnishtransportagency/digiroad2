(function (root) {
  root.LinkPropertyForm = function(selectedLinkProperty, feedbackCollection) {
    var layer;
    var typeId = 460;
    var authorizationPolicy = new LinkPropertyAuthorizationPolicy();
    var editingRestrictions = new EditingRestrictions();
    var enumerations = new Enumerations();
    new FeedbackDataTool(feedbackCollection, 'linkProperty', authorizationPolicy);

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

    var linkSources= [
      [1, 'MML'],
      [2, 'Täydentävä geometria']
    ];

    var laneWorkListTDConfirmationPopUp = function (target, selectedValue) {
      return {
        message: "Liikennevirran suunnan muutos aiheuttaa pääkaistojen lakkauttamisen ja uusien generoimisen linkillä," +
            " sekä kohteen nostamisen automaattisesti käsiteltyjen kaistojen työlistalle, jos sillä ei ole lisäkaistoja. " +
            "Jos linkillä on lisäkaistoja, kaistoja ei muokata, mutta kohde nousee kaistojen työlistalle." +
            " Vaikutussuuntaiset viivamaiset kohteet sovitetaan muuttuneelle linkille. ",
        type: "confirm",
        yesButtonLbl: 'Jatka',
        noButtonLbl: 'Peruuta',
        successCallback: function() {
          selectedLinkProperty.setTrafficDirection(selectedValue);
        },
        closeCallback: function() {
          selectedLinkProperty.cancelDirectionChange();
        },
        container: '.container'
      };
    };

    var laneWorkListLinkTypeConfirmationPopUp = function (target, originalValue, selectedValue) {
      var messageText;
      if (originalValue === enumerations.linkTypes.TractorRoad.value) messageText = "Olet muuttamassa tielinkin tyyppiä pois ajopolusta. Linkille generoidaan pääkaistat";
      else if (selectedValue === enumerations.linkTypes.TractorRoad.value) messageText = "Olet muuttamassa tielinkin tyypiksi ajopolku. Linkillä olevat kaistat päätetään.";
      else messageText = "Jos tielinkillä ei ole lisäkaistoja, tielinkin pääkaistat päätetään ja " +
            "sille generoidaan uuden tyypin mukaiset pääkaistat ja kohde nostetaan automaattisesti käsiteltyjen kaistojen työlistalle." +
            " Jos tielinkillä on lisäkaistoja, kaistoja ei muokata ja kohde nostetaan kaistojen työlistalle.";

      return {
        message: messageText,
        type: "confirm",
        yesButtonLbl: 'Jatka',
        noButtonLbl: 'Peruuta',
        successCallback: function() {
          selectedLinkProperty.setLinkType(selectedValue);
        },
        closeCallback: function() {
          selectedLinkProperty.cancel();
        },
        container: '.container'
      };
    };

    var getLocalizedFunctionalClass = function(functionalClass) {
      var localizedFunctionalClass = _.find(enumerations.functionalClasses, function(x) { return x.value === functionalClass; });
      return localizedFunctionalClass && localizedFunctionalClass.value;
    };

    var getLocalizedAdministrativeClass = function(administrativeClass) {
      var localizedAdministrativeClass = _.find(enumerations.administrativeClasses, function(x) { return x.stringValue === administrativeClass; });
      return localizedAdministrativeClass && localizedAdministrativeClass.text;
    };

    var getLocalizedLinkType = function(linkType) {
      var localizedLinkType = _.find(enumerations.linkTypes, function(x) { return x.value === linkType; });
      return localizedLinkType && localizedLinkType.text;
    };

    var getLocalizedTrafficDirection = function(trafficDirection) {
      var localizedTrafficDirection = _.find(enumerations.trafficDirections, function(x) { return x.stringValue === trafficDirection; });
      return localizedTrafficDirection && localizedTrafficDirection.text;
    };

    var getVerticalLevelType = function(verticalLevel) {
      if (typeof verticalLevel === 'string') {
        var multipleLevels = verticalLevel.includes(",");
        if (multipleLevels) {
          return "[useita eri arvoja]";
        }
      }

      var verticalLevelType = _.find(verticalLevelTypes, function(y) { return y[0] === parseInt(verticalLevel); });
      return verticalLevelType && verticalLevelType[1];
    };

    var getConstructionType = function(constructionTypeId){
      var constructionType = _.find(enumerations.constructionTypes, function(constructionType) {
        return constructionType.value === constructionTypeId; });
      return constructionType && constructionType.text;
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
      else{
        return "[useita eri arvoja]";
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
      var stateRoadEditingRestricted = 'Kohteiden muokkaus on estetty, koska kohteita ylläpidetään Tievelho-tietojärjestelmässä.';
      var municipalityRoadEditingRestricted = 'Kunnan kohteiden muokkaus on estetty, koska kohteita ylläpidetään kunnan omassa tietojärjestelmässä.';
      var message = '';

      if (editingRestrictions.hasStateRestriction(selectedLinkProperty.get(), typeId)) {
        message = stateRoadEditingRestricted;
      } else if(editingRestrictions.hasMunicipalityRestriction(selectedLinkProperty.get(), typeId)) {
        message = municipalityRoadEditingRestricted;
      } else if(!authorizationPolicy.isOperator() && (authorizationPolicy.isMunicipalityMaintainer() || authorizationPolicy.isElyMaintainer()) && !hasMunicipality(selectedLinkProperty)) {
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
            staticField('Tiennimi (Suomi)', 'roadNameFi') +
            staticField('Tiennimi (Ruotsi)', 'roadNameSe') +
            staticField('Tiennimi (Pohjoissaame)', 'roadNameSme') +
            staticField('Tiennimi (Inarinsaame)', 'roadNameSmn') +
            staticField('Tiennimi (Koltansaame)', 'roadNameSms') +
            staticField('Tienumero', 'roadNumber') +
            staticField('Tieosanumero', 'roadPartNumber') +
            staticField('Ajorata', 'track') +
            staticField('Tielinkin alkuetäisyys tieosan alusta', 'startAddrMValue') +
            staticField('Tielinkin loppuetäisyys tieosan alusta', 'endAddrMValue') +
            staticField('Osoitenumerot oikealla', 'addressNumbersRight') +
            staticField('Osoitenumerot vasemmalla', 'addressNumbersLeft') +
            staticField('MML ID', 'mmlId') +
            staticField('Linkin tila', 'constructionType') +
	          privateRoadAssociationInfo() +
          '</div>' +
        '</div>', options);
    };

    var privateRoadAssociationInfo = function() {
    	return '' +
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
		      '<div class="form-group editable">' +
		          '<label class="control-label">Lisätieto</label>' +
		          '<p class="form-control-static"><%- localizedAdditionalInfoIds %></p>' +
		          '<select class="form-control additional-info" style="display: none"><%= additionalInfoOptionTags %></select>' +
		      '</div>' +
		      '<p class="private-road-last-modification"> <%- privateRoadLastModifiedInfo %> </p>' +
		    '</div>';
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

    var renderLinktoAssetsOnExpiredLinksWorkList = function renderLinktoWorkList() {
      $('ul[class=information-content]').append('' +
          '<li><button id="work-list-link-assts-on-expired-links" class="assets-on-expired-links-work-list btn btn-tertiary" onclick=location.href="#work-list/assetsOnExpiredLinks">Poistuneilla tielinkeillä olevat kohteet</button></li>');
    };

    var renderLinkToRoadLinkReplacementWorkList = function renderLinktoWorkList() {
      $('ul[class=information-content]').append('' +
          '<li><button id="work-list-link-road-link-replacement" class="road-link-replacement-work-list btn btn-tertiary" onclick=location.href="#work-list/roadLinkReplacementWorkList">Mahdollisesti puuttuvat korvaavuudet</button></li>');
    };

    var trafficDirectionChangePopUpConditional = function(originalValue, selectedValue) {
      return (
          (originalValue === enumerations.trafficDirections.BothDirections.stringValue &&
              (selectedValue === enumerations.trafficDirections.AgainstDigitizing.stringValue ||
                  selectedValue === enumerations.trafficDirections.TowardsDigitizing.stringValue)) ||
          ((originalValue === enumerations.trafficDirections.AgainstDigitizing.stringValue ||
              originalValue === enumerations.trafficDirections.TowardsDigitizing.stringValue) &&
              selectedValue === enumerations.trafficDirections.BothDirections.stringValue)
      );
    };

    var linkTypeChangePopUpConditional = function(originalValue, selectedValue) {
      var twoWayLinkTypeValues = enumerations.twoWayLaneLinkTypes.map(function(linkType) {
        return linkType.value;
      });
      return (
          (twoWayLinkTypeValues.includes(originalValue) && !twoWayLinkTypeValues.includes(selectedValue)) ||
          (twoWayLinkTypeValues.includes(selectedValue) && !twoWayLinkTypeValues.includes(originalValue)) ||
          (originalValue === enumerations.linkTypes.TractorRoad.value || selectedValue === enumerations.linkTypes.TractorRoad.value)
      );
    };

    var addressNumberString = function (minAddressNumber, maxAddressNumber) {
      if (selectedLinkProperty.count() > 1) {
        return "[useita eri arvoja]";
      } else if (!minAddressNumber && !maxAddressNumber) {
        return '';
      } else {
        var min = minAddressNumber || '';
        var max = maxAddressNumber || '';
        return min + '-' + max;
      }
    };

    var controlAdministrativeClasses = function(administrativeClass) {
      $("#adminClass").prop('disabled', administrativeClass === 'State' && !authorizationPolicy.isOperator());
      $("#adminClass").find("option[value = State ]").prop('disabled', !authorizationPolicy.isOperator());
    };

    var controlAdministrativeClassesOnToggle = function(selectedLinkProperty) {
      var disabled = !_.isEmpty(_.filter(selectedLinkProperty.get(), function (link) {
        return link.administrativeClass === "State" && !authorizationPolicy.isOperator();
      }));
      $("#adminClass").prop('disabled', disabled);
      $("#adminClass").find("option[value = State ]").prop('disabled', !authorizationPolicy.isOperator());
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
        roadNameSme : linkProperty.roadNameSme || '',
        roadNameSmn : linkProperty.roadNameSmn || '',
        roadNameSms : linkProperty.roadNameSms || '',
        roadNumber : linkProperty.roadNumber || '',
        roadPartNumber : linkProperty.roadPartNumber || '',
        localizedFunctionalClass : getLocalizedFunctionalClass(linkProperty.functionalClass)|| 'Tuntematon',
        localizedAdministrativeClass : getLocalizedAdministrativeClass(linkProperty.administrativeClass)|| 'Tuntematon',
        localizedAdditionalInfoIds: getAdditionalInfo(parseInt(linkProperty.additionalInfo)) || '',
        localizedTrafficDirection : getLocalizedTrafficDirection(linkProperty.trafficDirection) || 'Tuntematon',
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
        additionalInfo: !isNaN(parseInt(linkProperty.additionalInfo)) ? parseInt(linkProperty.additionalInfo) : 99, // Ei toimitettu
	      privateRoadLastModifiedInfo: _.isUndefined(linkProperty.privateRoadLastModifiedDate) ? '' : 'Muokattu viimeksi:' + linkProperty.privateRoadLastModifiedDate + '/' + linkProperty.privateRoadLastModifiedUser
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

        var trafficDirectionOptionTags = _.map(enumerations.trafficDirections, function(trafficDirection) {
          var selected = trafficDirection.stringValue === linkProperty.trafficDirection ? " selected" : "";
          return '<option value="' + trafficDirection.stringValue + '"' + selected + '>' + trafficDirection.text + '</option>';
        }).join('');

        var functionalClassOptionTags = _.map(enumerations.functionalClasses, function(functionalClass) {
          var selected = functionalClass.value === linkProperty.functionalClass ? " selected" : "";
          return '<option value="' + functionalClass.value + '"' + selected + '>' + functionalClass.value + '</option>';
        }).join('');

        var linkTypesOptionTags = _.map(enumerations.linkTypes, function(linkType) {
          var selected = linkType.value === linkProperty.linkType ? " selected" : "";
          return '<option value="' + linkType.value + '"' + selected + '>' + linkType.text + '</option>';
        }).join('');

        var administrativeClassOptionTags = _.map(enumerations.administrativeClasses, function(administrativeClass) {
          var selected = administrativeClass.stringValue === linkProperty.administrativeClass ? " selected" : "";
          return administrativeClass.visibleInForm ?
            '<option value="' + administrativeClass.stringValue + '"' + selected + '>' + administrativeClass.text + '</option>' :
            '';
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
          var originalDirection = selectedLinkProperty.get()[0].trafficDirection;
          var selectedDirection = $(event.currentTarget).find(':selected').attr('value');
          var laneTDConfirmationOptions = laneWorkListTDConfirmationPopUp(event, selectedDirection);
          if (trafficDirectionChangePopUpConditional(originalDirection, selectedDirection))
            GenericConfirmPopup(laneTDConfirmationOptions.message, laneTDConfirmationOptions);
          else
            selectedLinkProperty.setTrafficDirection(selectedDirection);
        });
        rootElement.find('.functional-class').change(function(event) {
          selectedLinkProperty.setFunctionalClass(parseInt($(event.currentTarget).find(':selected').attr('value'), 10));
        });
        rootElement.find('.link-types').change(function(event) {
          var originalType = selectedLinkProperty.get()[0].linkType;
          var selectedType = parseInt($(event.currentTarget).find(':selected').attr('value'), 10);
          var laneLinkTypeConfirmationOptions = laneWorkListLinkTypeConfirmationPopUp(event, originalType, selectedType);
          if(linkTypeChangePopUpConditional(originalType, selectedType))
            GenericConfirmPopup(laneLinkTypeConfirmationOptions.message, laneLinkTypeConfirmationOptions);
          else
            selectedLinkProperty.setLinkType(selectedType);
        });
        rootElement.find('.administrative-class').change(function(event) {
          var administrativeClass = $(event.currentTarget).find(':selected').attr('value');
          selectedLinkProperty.setAdministrativeClass(administrativeClass);
          if(administrativeClass === "Private") {
            $(".private-road").css("display","block");
          } else $(".private-road").css("display","none");
        });
        rootElement.find('.access-right-id').on('input', function(event) {
          selectedLinkProperty.setAccessRightId($(event.currentTarget).val());
        });
        rootElement.find('.private-road-association').on('input', function(event) {
          selectedLinkProperty.setPrivateRoadAssociation($(event.currentTarget).val());
        });
        rootElement.find('.additional-info').change(function(event) {
          selectedLinkProperty.setAdditionalInfo($(event.currentTarget).find(':selected').attr('value'));
        });

        toggleMode(applicationModel.isReadOnly() || editingRestrictions.hasRestrictions(selectedLinkProperty.get(), typeId) || !authorizationPolicy.validateMultiple(selectedLinkProperty.get()));
        controlAdministrativeClasses(linkProperty.administrativeClass);
      });

      eventbus.on('linkProperties:cancelledDirectionChange', function(properties) {
        $('.traffic-direction').val(properties.trafficDirection);
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
        toggleMode(!authorizationPolicy.validateMultiple(selectedLinkProperty.get()) || editingRestrictions.hasRestrictions(selectedLinkProperty.get(), typeId) || readOnly);
        controlAdministrativeClassesOnToggle(selectedLinkProperty);
      });

      eventbus.on('layer:selected', function(layerName) {
        layer = layerName;
        if(layerName === 'linkProperty') {
          renderLinkToIncompleteLinks();
          renderLinktoAssetsOnExpiredLinksWorkList();
          renderLinkToRoadLinkReplacementWorkList();
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
