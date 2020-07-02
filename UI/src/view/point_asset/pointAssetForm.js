(function (root) {
root.PointAssetForm = function() {
  var me = this;
  me.enumeratedPropertyValues = null;
  me.pointAsset = null;
  me.roadCollection = null;
  me.applicationModel = null;
  me.backend= null;
  me.saveCondition= null;
  me.feedbackCollection= null;

  this.initialize = function(parameters) {
    me.pointAsset = parameters.pointAsset;
    me.roadCollection = parameters.roadCollection;
    me.applicationModel = parameters.applicationModel;
    me.backend = parameters.backend;
    me.saveCondition = parameters.saveCondition;
    me.feedbackCollection = parameters.feedbackCollection;
    me.bindEvents(parameters);
    me.selectedAsset = parameters.pointAsset.selectedPointAsset;
  };

  this.bindEvents = function(parameters) {
    var rootElement = $('#feature-attributes');
    var typeId = parameters.pointAsset.typeId;
    var selectedAsset = parameters.pointAsset.selectedPointAsset;
    var collection  = parameters.pointAsset.collection;
    var layerName = parameters.pointAsset.layerName;
    var localizedTexts = parameters.pointAsset.formLabels;
    var authorizationPolicy = parameters.pointAsset.authorizationPolicy;
    new FeedbackDataTool(parameters.feedbackCollection, layerName, authorizationPolicy);

    eventbus.on('assetEnumeratedPropertyValues:fetched', function(event) {
      if(event.assetType === typeId)
        me.enumeratedPropertyValues = event.enumeratedPropertyValues;
    });

    parameters.backend.getAssetEnumeratedPropertyValues(typeId);

    eventbus.on('application:readOnly', function(readOnly) {
      if(me.applicationModel.getSelectedLayer() === layerName && (!_.isEmpty(me.roadCollection.getAll()) && !_.isNull(selectedAsset.getId()))){
        me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, me.roadCollection) || readOnly);
      }
    });

    eventbus.on(layerName + ':selected ' + layerName + ':cancelled roadLinks:fetched', function() {
      if (!_.isEmpty(me.roadCollection.getAll()) && !_.isNull(selectedAsset.getId())) {
        me.renderForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, me.roadCollection, collection);
        me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, me.roadCollection) || me.applicationModel.isReadOnly());
        rootElement.find('.form-controls button').prop('disabled', !(selectedAsset.isDirty() && me.saveCondition(selectedAsset, authorizationPolicy)));
        rootElement.find('button#cancel-button').prop('disabled', false);
      }
    });

    eventbus.on(layerName + ':changed', function() {
      rootElement.find('.form-controls button').prop('disabled', !(selectedAsset.isDirty() && me.saveCondition(selectedAsset, authorizationPolicy)));
      rootElement.find('button#cancel-button').prop('disabled', !(selectedAsset.isDirty()));
      rootElement.find('button#save-button').prop('disabled', !(selectedAsset.isDirty() && me.saveCondition(selectedAsset, authorizationPolicy)));
    });

    eventbus.on(layerName + ':unselected ' + layerName + ':creationCancelled', function() {
      rootElement.find("#feature-attributes-header").empty();
      rootElement.find("#feature-attributes-form").empty();
      rootElement.find("#feature-attributes-footer").empty();
    });

    eventbus.on('layer:selected', function(layer) {
      if (layer === layerName) {
        $('ul[class=information-content]').empty();
        me.renderLinktoWorkList(layer, localizedTexts);
        if(parameters.pointAsset.hasInaccurate){
          renderInaccurateWorkList(layer);
        }
      }
    });
  };

  this.renderValueElement = function(asset, collection) { return ''; };

  this.renderForm = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {
    var id = selectedAsset.getId();

    var title = selectedAsset.isNew() ? "Uusi " + localizedTexts.newAssetLabel : 'ID: ' + id;
    var header = '<span>' + title + '</span>';
    var form = me.renderAssetFormElements(selectedAsset, localizedTexts, collection, authorizationPolicy);
    var footer = me.renderButtons();

    rootElement.find("#feature-attributes-header").html(header);
    rootElement.find("#feature-attributes-form").html(form);
    rootElement.find("#feature-attributes-footer").html(footer);
    if(me.pointAsset.lanePreview)
      rootElement.find("#feature-attributes-form").prepend(me.renderPreview(roadCollection, selectedAsset));

    rootElement.find('#delete-checkbox').on('change', function (event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.set({toBeDeleted: eventTarget.prop('checked')});
    });

    rootElement.find('.suggested-checkbox').on('change', function (event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.setPropertyByPublicId($('.suggested-checkbox').attr('name'), +eventTarget.prop('checked'));

      if(id) {
        me.switchSuggestedValue(true);
        rootElement.find('.suggested-checkbox').prop('checked', false);
      }
    });

    rootElement.find('.editable').not('.suggestion-box').on('change', function() {
      if(id) {
        me.switchSuggestedValue(true);
        rootElement.find('.suggested-checkbox').prop('checked', false);
        selectedAsset.setPropertyByPublicId($('.suggested-checkbox').attr('name'), 0);
      }
    });

    rootElement.find('input[type="text"]').on('input change', function (event) {
      var eventTarget = $(event.currentTarget);
      var obj = {};
      obj[eventTarget.attr('name') ? eventTarget.attr('name') : 'name' ] = eventTarget.val();
      selectedAsset.setPropertyByPublicId(eventTarget.attr('name'), eventTarget.val());

      if(id) {
        me.switchSuggestedValue(true);
        rootElement.find('.suggested-checkbox').prop('checked', false);
        selectedAsset.setPropertyByPublicId($('.suggested-checkbox').attr('name'), 0);
      }
    });

    rootElement.find('button#change-validity-direction').on('click', function() {
      var previousValidityDirection = selectedAsset.get().validityDirection;
      selectedAsset.set({ validityDirection: validitydirections.switchDirection(previousValidityDirection) });
      if(me.pointAsset.lanePreview)
        $('.preview-div').replaceWith(me.renderPreview(roadCollection, selectedAsset));
    });

    rootElement.find('.pointasset button.save').on('click', function() {
      selectedAsset.save();
    });

    rootElement.find('.pointasset button.cancel').on('click', function() {
      me.switchSuggestedValue(false);
      selectedAsset.cancel();
    });


    this.boxEvents(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
  };

  this.userInformationLog = function(authorizationPolicy, asset) {
    var limitedRights = 'Käyttöoikeudet eivät riitä kohteen muokkaamiseen. Voit muokata kohteita vain oman kuntasi alueelta.';
    var noRights = 'Käyttöoikeudet eivät riitä kohteen muokkaamiseen.';
    var message = '';

    if(!authorizationPolicy.isOperator() && (authorizationPolicy.isMunicipalityMaintainer() || authorizationPolicy.isElyMaintainer()) && !authorizationPolicy.hasRightsInMunicipality(asset.getMunicipalityCode())) {
      message = limitedRights;
    } else if(!authorizationPolicy.formEditModeAccess(asset, me.roadCollection))
      message = noRights;

    if(message) {
      return '' +
          '<div class="form-group user-information">' +
          '<p class="form-control-static user-log-info">' + message + '</p>' +
          '</div>';
    } else
      return '';
  };

  this.informationLog = function (date, username) {
    return date ? (date + ' / ' + username) : '-';
  };

  var getSuggestedBoxValue = function() {
    return !!parseInt(me.selectedAsset.getByProperty("suggest_box"));
  };

  var suggestedAssetCheckBox = function(selectedAsset, authorizationPolicy) {
    var suggestedBoxValue = getSuggestedBoxValue();
    var suggestedBoxDisabledState = getSuggestedBoxDisabledState();

    if(suggestedBoxDisabledState) {
      var disabledValue = 'disabled';
      return me.renderSuggestBoxElement(disabledValue);
    } else if(me.pointAsset.isSuggestedAsset && authorizationPolicy.handleSuggestedAsset(selectedAsset, suggestedBoxValue)) {
      var checkedValue = suggestedBoxValue ? 'checked' : '';
      return me.renderSuggestBoxElement(checkedValue);
    } else {
      return '';
    }
  };

  this.boxEvents = function (rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection){};

  this.renderAssetFormElements = function(selectedAsset, localizedTexts, collection, authorizationPolicy) {
    var asset = selectedAsset.get();

    if (selectedAsset.isNew()) {
      return '' +
        '<div class="wrapper">' +
        '  <div class="form form-horizontal form-dark form-pointasset">' +
        me.renderValueElement(asset, collection) +
        suggestedAssetCheckBox(selectedAsset, authorizationPolicy) +
        '  </div>' +
        '</div>';
    } else {
      return '' +
        '<div class="wrapper">' +
        '  <div class="form form-horizontal form-dark form-pointasset">' +
        me.renderFloatingNotification(asset.floating, localizedTexts) +
        '    <div class="form-group">' +
        '      <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + me.informationLog(asset.createdAt, asset.createdBy) + '</p>' +
        '    </div>' +
        '    <div class="form-group">' +
        '      <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + me.informationLog(asset.modifiedAt, asset.modifiedBy) + '</p>' +
        '    </div>' +
        me.userInformationLog(authorizationPolicy, selectedAsset) +
        me.renderValueElement(asset, collection, authorizationPolicy) +
        suggestedAssetCheckBox(selectedAsset, authorizationPolicy) +
        '    <div class="form-group form-group delete">' +
        '      <div class="checkbox">' +
        '        <input type="checkbox" id="delete-checkbox">' +
        '      </div>' +
        '      <p class="form-control-static">Poista</p>' +
        '    </div>' +
        '  </div>' +
        '</div>';
    }
  };

  this.renderValueElement = function(asset, collection) { return ''; };

  this.renderButtons = function() {
    return '' +
      '<div class="pointasset form-controls">' +
      '  <button id="save-button" class="save btn btn-primary" disabled>Tallenna</button>' +
      '  <button id ="cancel-button" class="cancel btn btn-secondary" disabled>Peruuta</button>' +
      '</div>';
  };

  this.renderLinktoWorkList = function(layerName, localizedTexts) {
    $('ul[class=information-content]').append('' +
      '<li><button id="point-asset-work-list-link" class="floating-point-assets btn btn-tertiary" onclick=location.href="#work-list/' + layerName + '">Geometrian ulkopuolelle jääneet ' + localizedTexts.manyFloatingAssetsLabel + '</button></li>');
  };

  this.renderSuggestBoxElement = function(inputProperty) {
    return '<div class="form-group editable form-' + me.pointAsset.layerName + ' suggestion-box">' +
            '<label class="control-label">Vihjetieto</label>' +
            '<p class="form-control-static">' + 'Kyllä' + '</p>' +
            '<input type="checkbox" class="form-control suggested-checkbox" name="suggest_box"' + inputProperty + '>' +
           '</div>';
  };

  this.toggleMode = function(rootElement, readOnly) {
    rootElement.find('.delete').toggle(!readOnly);
    rootElement.find('.form-controls').toggle(!readOnly);
    rootElement.find('.editable .form-control-static').toggle(readOnly);
    rootElement.find('.editable .form-control').toggle(!readOnly);
    rootElement.find('.edit-only').toggle(!readOnly);
    var element = rootElement.find('#wrongSideInfo');
    if (!_.isUndefined(element)){
      if (readOnly) element.show();
      else element.hide();
    }
  };

  this.renderFloatingNotification = function(floating, localizedTexts) {
    if (floating) {
      return '' +
        '<div class="form-group form-notification">' +
        ' <p>Kadun tai tien geometria on muuttunut, tarkista ja korjaa ' + localizedTexts.singleFloatingAssetLabel + ' sijainti</p>' +
        '</div>';
    } else {
      return '';
    }
  };

  var renderInaccurateWorkList= function renderInaccurateWorkList(layerName) {
    $('ul[class=information-content]').append('' +
      '<li><button id="work-list-link-errors" class="wrong-linear-assets btn btn-tertiary" onclick=location.href="#work-list/' + layerName + 'Errors">Laatuvirhelista</button></li>');
  };

  var getSuggestedBoxDisabledState = function() {
    return $('.suggested-checkbox').is(':disabled');
  };

  this.switchSuggestedValue = function(disabledValue) {
    $('.suggested-checkbox').attr('disabled', disabledValue);
  };

  this.renderPreview = function(roadCollection, selectedAsset) {
    var asset = selectedAsset.get();
    var lanes;
    if (!asset.floating){
      lanes = roadCollection.getRoadLinkByLinkId(asset.linkId).getData().lanes;
      lanes = laneUtils.filterByValidityDirection(asset.validityDirection, lanes);
    }

    return _.isEmpty(lanes) ? '' : laneUtils.createPreviewHeaderElement(_.uniq(lanes));
  };

};
})(this);
