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
      if(!readOnly && layerName === 'trafficSigns') {
        var assetIsOutsideRoadNetwork =  selectedAsset.get().validityDirection === validitydirections.outsideRoadNetwork;
        rootElement.find('button#change-validity-direction').prop("disabled", assetIsOutsideRoadNetwork);
        rootElement.find('.form-point-asset input#bearing').prop("disabled", !assetIsOutsideRoadNetwork);
        rootElement.find('.form-point-asset input#bearing').prop("valueAsNumber", assetIsOutsideRoadNetwork ? selectedAsset.get().bearing : NaN);
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
      if (layerName === 'trafficSigns') {
        var bearingElement = rootElement.find('.form-point-asset input#bearing');
        rootElement.find('button#save-button').prop('disabled', bearingElement.prop('disabled') === false &&
            (bearingElement.val() < 0 || bearingElement.val() > 360 || _.isEmpty(bearingElement.val())));
      }
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

  var propertyOrdering = ['suggest_box'];

  this.renderValueElement = function(asset, collection, authorizationPolicy) { return me.renderComponents(asset.propertyData, propertyOrdering, authorizationPolicy); };

  this.renderComponents = function (propertyData, propertyOrdering, authorizationPolicy) {
    var sortedProperties = me.sortAndFilterProperties(propertyData, propertyOrdering);

    return _.reduce(_.map(sortedProperties, function (feature) {
      feature.localizedName = window.localizedStrings[feature.publicId];
      var propertyType = feature.propertyType;

      switch (propertyType) {
        case "number":
        case "text": return me.textHandler(feature);
        case "single_choice": return me.singleChoiceHandler(feature);
        case "read_only_number": return me.readOnlyHandler(feature);
        case "date": return me.dateHandler(feature);
        case "checkbox": return feature.publicId === 'suggest_box' ? me.suggestedBoxHandler (feature, authorizationPolicy) : me.checkboxHandler(feature);
      }

    }), function(prev, curr) { return prev + curr; }, '');
  };

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

    me.addingPreBoxEventListeners(rootElement, selectedAsset, id);

    rootElement.find('button#change-validity-direction').on('click', function() {
      var previousValidityDirection = selectedAsset.get().validityDirection;
      selectedAsset.set({ validityDirection: validitydirections.switchDirection(previousValidityDirection) });
      if(me.pointAsset.lanePreview)
        $('.preview-div').replaceWith(me.renderPreview(roadCollection, selectedAsset));
    });

    this.boxEvents(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
  };

  this.addingPreBoxEventListeners = function (rootElement, selectedAsset, id) {
    rootElement.find('#delete-checkbox').on('change', function (event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.set({toBeDeleted: eventTarget.prop('checked')});
    });

    rootElement.find('input[type=checkbox]').not('.suggested-checkbox,#delete-checkbox').on('change', function (event) {
      var eventTarget = $(event.currentTarget);
      var propertyPublicId = eventTarget.attr('id');
      var propertyValue = +eventTarget.prop('checked');
      selectedAsset.setPropertyByPublicId(propertyPublicId, propertyValue);
    });

    rootElement.find('.suggested-checkbox').on('change', function (event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.setPropertyByPublicId(eventTarget.attr('id'), +eventTarget.prop('checked'));

      if(id) {
        me.switchSuggestedValue(true);
        rootElement.find('.suggested-checkbox').prop('checked', false);
      }
    });

    rootElement.find('.editable').not('.suggestion-box').on('change', function() {
      if(id) {
        me.switchSuggestedValue(true);
        rootElement.find('.suggested-checkbox').prop('checked', false);
        selectedAsset.setPropertyByPublicId($('.suggested-checkbox').attr('id'), 0);
      }
    });

    rootElement.find('.point-asset button.save').on('click', function() {
      selectedAsset.save();
    });

    rootElement.find('.point-asset button.cancel').on('click', function() {
      me.switchSuggestedValue(false);
      selectedAsset.cancel();
    });
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

  this.getSuggestedBoxValue = function() {
    return !!parseInt(me.selectedAsset.getByProperty("suggest_box"));
  };

  this.suggestedBoxHandler = function(asset, authorizationPolicy) {
    var suggestedBoxValue = me.getSuggestedBoxValue();
    var suggestedBoxDisabledState = me.getSuggestedBoxDisabledState();

    if(suggestedBoxDisabledState) {
      return me.renderSuggestBoxElement(asset, 'disabled');
    } else if(me.pointAsset.isSuggestedAsset && authorizationPolicy.handleSuggestedAsset(me.selectedAsset, suggestedBoxValue)) {
      var checkedValue = suggestedBoxValue ? 'checked' : '';
      return me.renderSuggestBoxElement(asset, checkedValue);
    } else {
      // empty div placed for correct positioning on the form for some elements to appear before or after the suggestion-box
      return '<div class="form-group editable form-' + me.pointAsset.layerName + ' suggestion-box"></div>';
    }
  };

  this.renderSuggestBoxElement = function(asset, state) {
    return '<div class="form-group editable form-point-asset suggestion-box">' +
        '<label class="control-label">' + asset.localizedName + '</label>' +
        '<p class="form-control-static">'+ _.head(asset.values).propertyDisplayValue +'</p>' +
        '<input type="checkbox" data-grouped-id="'+ asset.groupedId +'" class="form-control suggested-checkbox" name="' + asset.publicId + '" id="' + asset.publicId + (asset.groupedId ? ('-' + asset.groupedId) : '') + '"' + state + '>' +
        '</div>';
  };

  this.boxEvents = function (rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection){};

  this.renderAssetFormElements = function(selectedAsset, localizedTexts, collection, authorizationPolicy) {
    var asset = selectedAsset.get();
    var wrapper = $('<div class="wrapper">');
    var formRootElement = $('<div class="form form-horizontal form-dark form-point-asset">');

    if (selectedAsset.isNew() && !selectedAsset.getConvertedAssetValue()) {
      formRootElement = formRootElement.append(me.renderValueElement(asset, collection, authorizationPolicy));
    } else {
      var deleteCheckbox = $(''+
          '    <div class="form-group form-group delete">' +
          '      <div class="checkbox" >' +
          '        <input id="delete-checkbox" type="checkbox">' +
          '      </div>' +
          '      <p class="form-control-static">Poista</p>' +
          '    </div>' +
          '  </div>' );
      var logInfoGroup = $( '' +
          '    <div class="form-group">' +
          '      <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + this.informationLog(asset.createdAt, asset.createdBy) + '</p>' +
          '    </div>' +
          '    <div class="form-group">' +
          '      <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + this.informationLog(asset.modifiedAt, asset.modifiedBy) + '</p>' +
          '    </div>');

      formRootElement = formRootElement
          .append($(this.renderFloatingNotification(asset.floating, localizedTexts)))
          .append(logInfoGroup)
          .append( $(this.userInformationLog(authorizationPolicy, selectedAsset)))
          .append(me.renderValueElement(asset, collection, authorizationPolicy))
          .append(deleteCheckbox);
    }
    return wrapper.append(formRootElement);
  };

  this.renderButtons = function() {
    return '' +
      '<div class="point-asset form-controls">' +
      '  <button id="save-button" class="save btn btn-primary" disabled>Tallenna</button>' +
      '  <button id ="cancel-button" class="cancel btn btn-secondary" disabled>Peruuta</button>' +
      '</div>';
  };

  this.renderLinktoWorkList = function(layerName, localizedTexts) {
    $('ul[class=information-content]').append('' +
      '<li><button id="point-asset-work-list-link" class="floating-point-assets btn btn-tertiary" onclick=location.href="#work-list/' + layerName + '">Geometrian ulkopuolelle jääneet ' + localizedTexts.manyFloatingAssetsLabel + '</button></li>');
  };

  this.toggleMode = function(rootElement, readOnly) {
    rootElement.find('.delete').toggle(!readOnly);
    rootElement.find('.form-controls').toggle(!readOnly);
    rootElement.find('.editable .form-control-static').toggle(readOnly);
    rootElement.find('.editable .form-control').toggle(!readOnly);
    rootElement.find('.edit-only').toggle(!readOnly);
    rootElement.find('.read-only').toggle(readOnly);
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

  this.extractPropertyValue = function(property) {
    return _.isEmpty(property.values) ? '' : _.head(property.values).propertyValue;
  };

  this.textHandler = function (property) {
    var propertyValue = me.extractPropertyValue(property);
    return '' +
        '    <div class="form-group editable form-point-asset">' +
        '        <label class="control-label">' + property.localizedName + '</label>' +
        '        <p class="form-control-static">' + (propertyValue || '–') + '</p>' +
        '        <input type="text" data-grouped-id="'+ property.groupedId +'" class="form-control" id="' + property.publicId + (property.groupedId ? ('-' + property.groupedId) : '') +'" value="' + propertyValue + '">' +
        '    </div>';
  };

  this.singleChoiceHandler = function (property) {
    var propertyValues = _.head(_.map(_.filter(me.enumeratedPropertyValues, { 'publicId': property.publicId}), function(val) {return val.values; }));

    var propertyValue = me.extractPropertyValue(property);
    var propertyDefaultValue = _.find(me.pointAsset.newAsset.propertyData, ['publicId', property.publicId]).values[0].propertyValue.toString();

    propertyValue = _.isEmpty(propertyValue) ? propertyDefaultValue : propertyValue;

    var sortedPropertyValues = _.sortBy(propertyValues, function(property) {
        return parseFloat(property.propertyValue);
    });

    var selectableValues = _.map(sortedPropertyValues, function (label) {
      return $('<option>',
          { selected: propertyValue == label.propertyValue,
            value: parseFloat(label.propertyValue),
            text: label.propertyDisplayValue}
      )[0].outerHTML; }).join('');
    return '' +
        '    <div class="form-group editable form-point-asset">' +
        '      <label class="control-label">' + property.localizedName + '</label>' +
        '      <p class="form-control-static">' + (_.find(propertyValues, ['propertyValue', propertyValue]).propertyDisplayValue || '-') + '</p>' +
        '      <select class="form-control" data-grouped-id="'+ property.groupedId +'" id=' + property.publicId + (property.groupedId ? ('-' + property.groupedId) : '') +'>' +
        selectableValues +
        '      </select>' +
        '    </div>';
  };

  this.readOnlyHandler = function (property) {
    var propertyValue = me.extractPropertyValue(property);
    var displayValue = property.localizedName ? property.localizedName : _.isEmpty(property.values) ? '' : _.head(property.values).propertyDisplayValue;
    return '' +
        '    <div class="form-group editable form-point-asset">' +
        '        <label class="control-label">' + displayValue + '</label>' +
        '        <p class="form-control-static">' + propertyValue + '</p>' +
        '    </div>';
  };

  this.dateHandler = function(property) {
    var propertyValue = '';

    if ( !_.isEmpty(property.values) && !_.isEmpty(_.head(property.values).propertyDisplayValue) )
      propertyValue = _.head(property.values).propertyDisplayValue;

    return '' +
        '<div class="form-group editable form-point-asset">' +
        '     <label class="control-label">' + property.localizedName + '</label>' +
        '     <p class="form-control-static">' + (propertyValue || '–') + '</p>' +
        '     <input type="text" data-grouped-id="'+ property.groupedId +'" class="form-control" id="' + property.publicId + (property.groupedId ? ('-' + property.groupedId) : '') + '" value="' + propertyValue + '">' +
        '</div>';
  };

  this.checkboxHandler = function(property) {
    var checked = _.head(property.values).propertyValue == "0" ? '' : 'checked';
    return '' +
        '    <div id= "' + property.publicId + '-checkbox-div" class="form-group editable edit-only form-point-asset">' +
        '      <div class="checkbox" >' +
        '        <input data-grouped-id="'+ property.groupedId +'" id="' + property.publicId + (property.groupedId ? ('-' + property.groupedId) : '') + '" type="checkbox"' + checked + '>' +
        '      </div>' +
        '        <label class="' + property.publicId + '-checkbox-label">' + property.localizedName + '</label>' +
        '    </div>';
  };

  var renderInaccurateWorkList= function renderInaccurateWorkList(layerName) {
    $('ul[class=information-content]').append('' +
      '<li><button id="work-list-link-errors" class="wrong-linear-assets btn btn-tertiary" onclick=location.href="#work-list/' + layerName + 'Errors">Laatuvirhelista</button></li>');
  };

  this.getSuggestedBoxDisabledState = function() {
    return $('.suggested-checkbox').is(':disabled');
  };

  this.switchSuggestedValue = function(disabledValue) {
    $('.suggested-checkbox').attr('disabled', disabledValue);
  };

  this.sortAndFilterProperties = function(properties, propertyOrdering) {
    return _.sortBy(properties, function(property) {
      return _.indexOf(propertyOrdering, property.publicId);
    }).filter(function(property){
      return _.includes(propertyOrdering, property.publicId);
    });
  };

  me.renderValidityDirection = function (selectedAsset) {
    if(!_.isUndefined(selectedAsset.validityDirection)){
      return '' +
          '    <div class="form-group editable form-directional-traffic-sign edit-only">' +
          '      <label class="control-label">Vaikutussuunta</label>' +
          '      <button id="change-validity-direction" class="form-control btn btn-secondary btn-block">Vaihda suuntaa</button>' +
          '    </div>';
    }
    else return '';
  };

  this.renderPreview = function(roadCollection, selectedAsset) {
    var asset = selectedAsset.get();
    var lanes;
    if (!asset.floating){
      var laneObject = roadCollection.getRoadLinkByLinkId(asset.linkId);
      if( typeof laneObject === "undefined"){
        return '';
      }
      lanes = laneObject.getData().lanes;
      lanes = laneUtils.filterByValidityDirection(asset.validityDirection, lanes);
    }

    return _.isEmpty(lanes) ? '' : laneUtils.createPreviewHeaderElement(_.uniq(lanes));
  };
};
})(this);