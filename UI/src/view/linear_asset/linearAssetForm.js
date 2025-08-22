(function (root) {
  root.LinearAssetForm = {
    initialize: bindEvents
  };

  var infoContent = $('ul[class=information-content]');
  var editingRestrictions = new EditingRestrictions();

  function bindEvents(linearAsset, formElements, feedbackModel) {

    var selectedLinearAsset = linearAsset.selectedLinearAsset,
      eventCategory = linearAsset.singleElementEventCategory,
      newTitle = linearAsset.newTitle,
      title = linearAsset.title,
      authorizationPolicy = linearAsset.authorizationPolicy,
      layerName = linearAsset.layerName,
      typeId = linearAsset.typeId,
      isVerifiable = linearAsset.isVerifiable,
      hasInaccurate = linearAsset.hasInaccurate;
      new FeedbackDataTool(feedbackModel, linearAsset.layerName, authorizationPolicy, eventCategory);

    var rootElement = $('#feature-attributes');

    eventbus.on(events('selected', 'cancelled'), function() {
      rootElement.find('#feature-attributes-header').html(header(selectedLinearAsset, title, newTitle));
      rootElement.find('#feature-attributes-form').html(template(selectedLinearAsset, formElements, authorizationPolicy, typeId));
      rootElement.find('#feature-attributes-footer').html(footer(selectedLinearAsset, isVerifiable));

      if (selectedLinearAsset.isSplitOrSeparated()) {
        formElements.bindEvents(rootElement.find('.form-elements-container'), selectedLinearAsset, 'a');
        formElements.bindEvents(rootElement.find('.form-elements-container'), selectedLinearAsset, 'b');
      } else {
        formElements.bindEvents(rootElement.find('.form-elements-container'), selectedLinearAsset);
      }

      rootElement.find('#separate-limit').on('click', function() { selectedLinearAsset.separate(); });
      rootElement.find('.form-controls.linear-asset button.save').on('click', function() {
        if(selectedLinearAsset.getCreatedBy() === "automatic_trafficSign_created")
            new GenericConfirmPopup("Liikennemerkkiin on liitetty myös viivamainen tietolaji. Muokkaa tai poista myös viivamainen tietolaji", {type: 'alert'});
        selectedLinearAsset.save();
      });
      rootElement.find('.form-controls.linear-asset button.cancel').on('click', function() { selectedLinearAsset.cancel(); });
      rootElement.find('.form-controls.linear-asset button.verify').on('click', function() { selectedLinearAsset.verify(); });
      toggleMode(applicationModel.isReadOnly() || !validateAccess(selectedLinearAsset, authorizationPolicy) || editingRestrictions.hasRestrictions(selectedLinearAsset.get(), typeId) || editingRestrictions.elyUserRestrictionOnMunicipalityAsset(authorizationPolicy, selectedLinearAsset.get()));
    });

    eventbus.on(events('unselect'), function() {
      rootElement.find('#feature-attributes-header').empty();
      rootElement.find('#feature-attributes-form').empty();
      rootElement.find('#feature-attributes-footer').empty();
    });

    eventbus.on('closeForm', function() {
      rootElement.find('#feature-attributes-header').empty();
      rootElement.find('#feature-attributes-form').empty();
      rootElement.find('#feature-attributes-footer').empty();
    });

    eventbus.on('application:readOnly', function(readOnly){
      if(layerName ===  applicationModel.getSelectedLayer()) {
        toggleMode(!validateAccess(selectedLinearAsset, authorizationPolicy) || readOnly || editingRestrictions.hasRestrictions(selectedLinearAsset.get(), typeId) || editingRestrictions.elyUserRestrictionOnMunicipalityAsset(authorizationPolicy, selectedLinearAsset.get()));
      }
    });
    eventbus.on(events('valueChanged'), function(selectedLinearAsset) {
      rootElement.find('.form-controls.linear-asset button.save').attr('disabled', !selectedLinearAsset.isSaveable());
      rootElement.find('.form-controls.linear-asset button.cancel').attr('disabled', false);
      rootElement.find('.form-controls.linear-asset button.verify').attr('disabled', selectedLinearAsset.isSaveable());
    });

    function toggleMode(readOnly) {
      rootElement.find('.editable .form-control-static').toggle(readOnly);
      rootElement.find('.editable .edit-control-group').toggle(!readOnly);
      rootElement.find('.form-controls').toggle(!readOnly);
      rootElement.find('#separate-limit').toggle(!readOnly);
      rootElement.find('.read-only-title').toggle(readOnly);
      rootElement.find('.edit-mode-title').toggle(!readOnly);
      handleSuggestionBox();
    }

    function handleSuggestionBox() {
      if((applicationModel.isReadOnly() && (_.isNull(selectedLinearAsset.getId()) || !selectedLinearAsset.isSuggested()))) {
        rootElement.find('.suggestion').hide();
      } else if(selectedLinearAsset.isSplitOrSeparated()) {
        rootElement.find('.suggestion').hide();
      } else
        rootElement.find('.suggestion').show();
    }

    function events() {
      return _.map(arguments, function(argument) { return eventCategory + ':' + argument; }).join(' ');
    }

    eventbus.on(eventCategory + ':selected', function(){
      handleSuggestionBox();
    });

    eventbus.on('layer:selected', function(layer) {
      if(layerName === layer){
        $('ul[class=information-content]').empty();
        if(isVerifiable){
          renderLinktoWorkList(layer);
        }
        if(hasInaccurate){
          renderInaccurateWorkList(layer);
        }
      }
    });
  }

  function header(selectedLinearAsset, title, newTitle) {
    var generateTitle = function () {
      if (selectedLinearAsset.isUnknown() || selectedLinearAsset.isSplit()) {
        return '<span class="read-only-title">' + title + '</span>' +
          '<span class="edit-mode-title">' + newTitle + '</span>';
      } else {
        if (selectedLinearAsset.count() === 1) {
          return '<span>Kohteen ID: ' + selectedLinearAsset.getId() + '</span>';
        } else {
          return '<span>' + title + '</span>';
        }
      }
    };
    return generateTitle();
  }

  var buttons = function(selectedLinearAsset, isVerifiable) {
    var disabled = selectedLinearAsset.isDirty() ? '' : 'disabled';
    return [(isVerifiable && !_.isNull(selectedLinearAsset.getId()) && selectedLinearAsset.count() === 1) ? '<button class="verify btn btn-primary">Merkitse tarkistetuksi</button>' : '',
      '<button class="save btn btn-primary" disabled> Tallenna</button>',
      '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');
  };


  function template(selectedLinearAsset, formElements, authorizationPolicy, typeId) {
    var modifiedBy = selectedLinearAsset.getModifiedBy() || '-';
    var modifiedDateTime = selectedLinearAsset.getModifiedDateTime() ? ' ' + selectedLinearAsset.getModifiedDateTime() : '';
    var createdBy = selectedLinearAsset.getCreatedBy() || '-';
    var createdDateTime = selectedLinearAsset.getCreatedDateTime() ? ' ' + selectedLinearAsset.getCreatedDateTime() : '';
    var verifiedBy = selectedLinearAsset.getVerifiedBy();
    var verifiedDateTime = selectedLinearAsset.getVerifiedDateTime();

    var separatorButton = function() {
      if (selectedLinearAsset.isSeparable()) {
        return '<div class="form-group editable">' +
        '<label class="control-label"></label>' +
        '<button class="cancel btn btn-secondary" id="separate-limit">Jaa kaksisuuntaiseksi</button>' +
        '</div>';
      } else {
        return '';
      }
    };

    var limitValueButtons = function() {
      var separateValueElement =
        formElements.singleValueElement(selectedLinearAsset.getValue(), "a", selectedLinearAsset.getId()) +
        formElements.singleValueElement(selectedLinearAsset.getValue(), "b", selectedLinearAsset.getId());
      var valueElements = selectedLinearAsset.isSplitOrSeparated() ?
        separateValueElement :
        formElements.singleValueElement(selectedLinearAsset.getValue(), "", selectedLinearAsset.getId());
      return '' +
        '<div class="form-elements-container">' +
        valueElements +
        '</div>';
    };



    var verifiedFields = function(isVerifiable) {
      return (isVerifiable && verifiedBy && verifiedDateTime) ? '<div class="form-group">' +
      '<p class="form-control-static asset-log-info">Tarkistettu: ' + informationLog(verifiedDateTime, verifiedBy) + '</p>' +
      '</div>' : '';
    };

    var userInformationLog = function() {
      var hasMunicipality = function (linearAsset) {
        return _.some(linearAsset.get(), function (asset) {
          return authorizationPolicy.hasRightsInMunicipality(asset.municipalityCode);
        });
      };

      var limitedRights = 'Käyttöoikeudet eivät riitä kohteen muokkaamiseen. Voit muokata kohteita vain oman kuntasi alueelta.';
      var noRights = 'Käyttöoikeudet eivät riitä kohteen muokkaamiseen.';
      var stateRoadEditingRestricted = 'Kohteiden muokkaus on estetty, koska kohteita ylläpidetään Tievelho-tietojärjestelmässä.';
      var municipalityRoadEditingRestricted = 'Kunnan kohteiden muokkaus on estetty, koska kohteita ylläpidetään kunnan omassa tietojärjestelmässä.';
      var elyUserRestriction = 'Käyttöoikeudet eivät riitä kohteen muokkaamiseen. ELY-ylläpitäjänä et voi muokata kohteita kunnan omistamalla katuverkolla.';
      var message = '';

      if (editingRestrictions.hasStateRestriction(selectedLinearAsset.get(), typeId)) {
        message = stateRoadEditingRestricted;
      } else if(editingRestrictions.hasMunicipalityRestriction(selectedLinearAsset.get(), typeId)) {
        message = municipalityRoadEditingRestricted;
      } else if(!authorizationPolicy.isOperator() && (authorizationPolicy.isMunicipalityMaintainer() || authorizationPolicy.isElyMaintainer()) && !hasMunicipality(selectedLinearAsset)) {
        message = limitedRights;
      } else if (editingRestrictions.elyUserRestrictionOnMunicipalityAsset(authorizationPolicy, selectedLinearAsset.get())) {
        message = elyUserRestriction;
      } else if (!authorizationPolicy.validateMultiple(selectedLinearAsset.get()))
        message = noRights;

      if(message) {
        return '' +
            '<div class="form-group user-information">' +
            '<p class="form-control-static user-log-info">' + message + '</p>' +
            '</div>';
      } else
        return '';
    };

    var informationLog = function (date, username) {
      return date ? (date + ' / ' + username) : '-';
    };

    return '<div class="wrapper read-only">' +
             '<div class="form form-horizontal form-dark linear-asset">' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + informationLog(createdDateTime, createdBy) + '</p>' +
               '</div>' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Muokattu viimeksi: ' + informationLog(modifiedDateTime, modifiedBy)  + '</p>' +
               '</div>' +
               verifiedFields() +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + selectedLinearAsset.count() + '</p>' +
               '</div>' +
               userInformationLog() +
               limitValueButtons() +
               separatorButton() +
             '</div>' +
           '</div>';
  }

  function footer(selectedLinearAsset, isVerifiable) {
    return '<div class="linear-asset form-controls" style="display: none">' +
    buttons(selectedLinearAsset, isVerifiable) +
    '</div>';
  }

  var renderLinktoWorkList = function renderLinktoWorkList(layerName) {
    var textName;
    switch(layerName) {
      case "maintenanceRoad":
        textName = "Tarkistamattomien huoltoteiden lista";
            break;
      default:
        textName = "Vanhentuneiden kohteiden lista";
    }
    $('ul[class=information-content]').append('<li><button id="unchecked-links" class="unchecked-linear-assets btn btn-tertiary" onclick=location.href="#work-list/' + layerName + '">' + textName + '</button></li>');
  };

  var renderInaccurateWorkList= function renderInaccurateWorkList(layerName) {
    $('ul[class=information-content]').append('<li><button id="work-list-link-errors" class="wrong-linear-assets btn btn-tertiary" onclick=location.href="#work-list/' + layerName + 'Errors">Laatuvirhelista</button></li>');
  };

  function validateAccess(selectedLinearAsset, authorizationPolicy){
    return authorizationPolicy.validateMultiple(selectedLinearAsset.get());
  }

})(this);
