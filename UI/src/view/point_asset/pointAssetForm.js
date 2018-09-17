(function (root) {
root.PointAssetForm = function() {
  var me = this;
  me.enumeratedPropertyValues = null;
  var pointAsset;
  var roadCollection;
  var applicationModel;
  var backend;
  var saveCondition;

  this.initialize = function(parameters) {
    pointAsset = parameters.pointAsset;
    roadCollection = parameters.roadCollection;
    applicationModel = parameters.applicationModel;
    backend = parameters.backend;
    saveCondition = parameters.saveCondition;
    me.bindEvents(parameters);
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

    backend.getAssetEnumeratedPropertyValues(typeId);

    eventbus.on('application:readOnly', function(readOnly) {
      if(applicationModel.getSelectedLayer() === layerName && (!_.isEmpty(roadCollection.getAll()) && !_.isNull(selectedAsset.getId()))){
        me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, roadCollection) || readOnly);
      }
    });

    eventbus.on(layerName + ':selected ' + layerName + ':cancelled roadLinks:fetched', function() {
      if (!_.isEmpty(roadCollection.getAll()) && !_.isNull(selectedAsset.getId())) {
        me.renderForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
        me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, roadCollection) || applicationModel.isReadOnly());
        rootElement.find('.form-controls button').prop('disabled', !(selectedAsset.isDirty() && saveCondition(selectedAsset)));
        rootElement.find('button#cancel-button').prop('disabled', false);
      }
    });

    eventbus.on(layerName + ':changed', function() {
      rootElement.find('.form-controls button').prop('disabled', !(selectedAsset.isDirty() && saveCondition(selectedAsset)));
      rootElement.find('button#cancel-button').prop('disabled', !(selectedAsset.isDirty()));
    });

    eventbus.on(layerName + ':unselected ' + layerName + ':creationCancelled', function() {
      rootElement.empty();
    });

    eventbus.on('layer:selected', function(layer) {
      if (layer === layerName) {
        me.renderLinktoWorkList(layer, localizedTexts);
      }
    });
  };

  this.renderForm = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {
    var id = selectedAsset.getId();

    var title = selectedAsset.isNew() ? "Uusi " + localizedTexts.newAssetLabel : 'ID: ' + id;
    var header = '<header><span>' + title + '</span>' + me.renderButtons() + '</header>';
    var form = me.renderAssetFormElements(selectedAsset, localizedTexts, collection);
    var footer = '<footer>' + me.renderButtons() + '</footer>';

    rootElement.html(header + form + footer);

    rootElement.find('input[type="checkbox"]').on('change', function (event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.set({toBeDeleted: eventTarget.prop('checked')});
    });

    rootElement.find('input[type="text"]').on('input change', function (event) {
      var eventTarget = $(event.currentTarget);
      var obj = {};
      obj[eventTarget.attr('name') ? eventTarget.attr('name') : 'name' ] = eventTarget.val();
      selectedAsset.set(obj);
    });

    rootElement.find('.linear-asset.form textarea, .form-directional-traffic-sign textarea').on('keyup', function (event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.set({text: eventTarget.val()});
    });

    rootElement.find('.form-traffic-sign input[type=text],.form-traffic-sign select').on('change input', function (event) {
      var eventTarget = $(event.currentTarget);
      var propertyPublicId = eventTarget.attr('id');
      var propertyValue = $(event.currentTarget).val();
      selectedAsset.setPropertyByPublicId(propertyPublicId, propertyValue);
    });

    rootElement.find('button#change-validity-direction').on('click', function() {
      var previousValidityDirection = selectedAsset.get().validityDirection;
      selectedAsset.set({ validityDirection: validitydirections.switchDirection(previousValidityDirection) });
    });

    rootElement.find('.form-railway-crossing select').on('change', function(event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.set({ safetyEquipment: parseInt(eventTarget.val(), 10) });
    });

    rootElement.find('.form-obstacle select').on('change', function(event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.set({ obstacleType: parseInt(eventTarget.val(), 10) });
    });

    rootElement.find('.pointasset button.save').on('click', function() {
      selectedAsset.save();
    });

    rootElement.find('.pointasset button.cancel').on('click', function() {
      selectedAsset.cancel();
    });
  };

  this.renderAssetFormElements = function(selectedAsset, localizedTexts, collection) {
    var asset = selectedAsset.get();

    if (selectedAsset.isNew()) {
      return '' +
        '<div class="wrapper">' +
        '  <div class="form form-horizontal form-dark form-pointasset">' +
        me.renderValueElement(asset, collection) +
        '  </div>' +
        '</div>';
    } else {
      return '' +
        '<div class="wrapper">' +
        '  <div class="form form-horizontal form-dark form-pointasset">' +
        me.renderFloatingNotification(asset.floating, localizedTexts) +
        '    <div class="form-group">' +
        '      <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + (asset.createdBy || '-') + ' ' + (asset.createdAt || '') + '</p>' +
        '    </div>' +
        '    <div class="form-group">' +
        '      <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + (asset.modifiedBy || '-') + ' ' + (asset.modifiedAt || '') + '</p>' +
        '    </div>' +
        me.renderValueElement(asset, collection) +
        '    <div class="form-group form-group delete">' +
        '      <div class="checkbox" >' +
        '        <input type="checkbox">' +
        '      </div>' +
        '      <p class="form-control-static">Poista</p>' +
        '    </div>' +
        '  </div>' +
        '</div>';
    }
  };

  var obstacleTypes = {
    1: 'Suljettu yhteys',
    2: 'Avattava puomi'
  };

  var safetyEquipments = {
    1: 'Rautatie ei käytössä',
    2: 'Ei turvalaitetta',
    3: 'Valo/äänimerkki',
    4: 'Puolipuomi',
    5: 'Kokopuomi'
  };

  var widthLimitReason = [
    { value:1, label: 'Silta'},
    { value:2, label: 'Kokoportaali'},
    { value:3, label: 'Puoliportaali'},
    { value:4, label: 'Kaide'},
    { value:5, label: 'Valaisin'},
    { value:6, label: 'Aita'},
    { value:7, label: 'Maatuki'},
    { value:8, label: 'Liikennevalopylväs'},
    { value:9, label: 'Muu alikulkueste'},
    { value:99, label: 'El tietoa'}
  ];

  var sortAndFilterTrafficSignProperties = function(properties) {
    var propertyOrdering = [
      'trafficSigns_type',
      'trafficSigns_value',
      'trafficSigns_info',
      'counter'];

    return _.sortBy(properties, function(property) {
      return _.indexOf(propertyOrdering, property.publicId);
    }).filter(function(property){
      return _.indexOf(propertyOrdering, property.publicId) >= 0;
    });
  };

  var textHandler = function (property) {
    var propertyValue = (property.values.length === 0) ? '' : property.values[0].propertyValue;
    return '' +
      '    <div class="form-group editable form-traffic-sign">' +
      '        <label class="control-label">' + property.localizedName + '</label>' +
      '        <p class="form-control-static">' + (propertyValue || '–') + '</p>' +
      '        <input type="text" class="form-control" id="' + property.publicId + '" value="' + propertyValue + '">' +
      '    </div>';
  };

  var singleChoiceHandler = function (property, collection) {
    var propertyValue = (property.values.length === 0) ? '' : _.head(property.values).propertyValue;
    var propertyDisplayValue = (property.values.length === 0) ? '' : _.head(property.values).propertyDisplayValue;
    var signTypes = _.map(_.filter(me.enumeratedPropertyValues, function(enumerated) { return enumerated.publicId == 'trafficSigns_type' ; }), function(val) {return val.values; });

    var groups =  collection.getGroup(signTypes);
    var groupKeys = Object.keys(groups);
    var trafficSigns = _.map(groupKeys, function (label) {
      return $('<optgroup label =  "'+ label +'" >'.concat(

        _.map(groups[label], function(group){
          return $('<option>',
            { value: group.propertyValue,
              selected: propertyValue == group.propertyValue,
              text: group.propertyDisplayValue}
          )[0].outerHTML; }))

      )[0].outerHTML;}).join('');

    return '' +
      '    <div class="form-group editable form-traffic-sign">' +
      '      <label class="control-label">' + property.localizedName + '</label>' +
      '      <p class="form-control-static">' + (propertyDisplayValue || '-') + '</p>' +
      '      <select class="form-control" style="display:none" id="' + property.publicId + '">  ' +
      trafficSigns +
      '      </select>' +
      '    </div>';
  };

  var readOnlyHandler = function (property) {
    var propertyValue = (property.values.length === 0) ? '' : property.values[0].propertyValue;
    var displayValue = (property.localizedName) ? property.localizedName : (property.values.length === 0) ? '' : property.values[0].propertyDisplayValue;

    return '' +
      '    <div class="form-group editable form-traffic-sign">' +
      '        <label class="control-label">' + displayValue + '</label>' +
      '        <p class="form-control-static">' + propertyValue + '</p>' +
      '    </div>';
  };

  this.renderValueElement = function(asset, collection) {
    if (asset.obstacleType) {
      return '' +
        '    <div class="form-group editable form-obstacle">' +
        '      <label class="control-label">Esterakennelma</label>' +
        '      <p class="form-control-static">' + obstacleTypes[asset.obstacleType] + '</p>' +
        '      <select class="form-control" style="display:none">  ' +
        '        <option value="1" '+ (asset.obstacleType === 1 ? 'selected' : '') +'>Suljettu yhteys</option>' +
        '        <option value="2" '+ (asset.obstacleType === 2 ? 'selected' : '') +'>Avattava puomi</option>' +
        '      </select>' +
        '    </div>';
    } else if (asset.safetyEquipment) {
      return '' +
        '    <div class="form-group editable form-railway-crossing">' +
        '        <label class="control-label">' + 'Tasoristeystunnus' + '</label>' +
        '        <p class="form-control-static">' + (asset.code || '–') + '</p>' +
        '        <input type="text" class="form-control"  maxlength="15" name="code" value="' + (asset.code || '')  + '">' +
        '    </div>' +
        '    <div class="form-group editable form-railway-crossing">' +
        '      <label class="control-label">Turvavarustus</label>' +
        '      <p class="form-control-static">' + safetyEquipments[asset.safetyEquipment] + '</p>' +
        '      <select class="form-control" style="display:none">  ' +
        '        <option value="1" '+ (asset.safetyEquipment === 1 ? 'selected' : '') +'>Rautatie ei käytössä</option>' +
        '        <option value="2" '+ (asset.safetyEquipment === 2 ? 'selected' : '') +'>Ei turvalaitetta</option>' +
        '        <option value="3" '+ (asset.safetyEquipment === 3 ? 'selected' : '') +'>Valo/äänimerkki</option>' +
        '        <option value="4" '+ (asset.safetyEquipment === 4 ? 'selected' : '') +'>Puolipuomi</option>' +
        '        <option value="5" '+ (asset.safetyEquipment === 5 ? 'selected' : '') +'>Kokopuomi</option>' +
        '      </select>' +
        '    </div>' +
        '    <div class="form-group editable form-railway-crossing">' +
        '        <label class="control-label">' + 'Nimi' + '</label>' +
        '        <p class="form-control-static">' + (asset.name || '–') + '</p>' +
        '        <input type="text" class="form-control" value="' + (asset.name || '')  + '">' +
        '    </div>';
    } else if (asset.validityDirection && !asset.propertyData) {
      return '' +
        '  <div class="form-group editable form-directional-traffic-sign">' +
        '      <label class="control-label">Teksti</label>' +
        '      <p class="form-control-static">' + (asset.text || '–') + '</p>' +
        '      <textarea class="form-control large-input">' + (asset.text || '') + '</textarea>' +
        '  </div>' +
        '    <div class="form-group editable form-directional-traffic-sign edit-only">' +
        '      <label class="control-label">Vaikutussuunta</label>' +
        '      <button id="change-validity-direction" class="form-control btn btn-secondary btn-block">Vaihda suuntaa</button>' +
        '    </div>';
    }else if(asset.limit || asset.limit === 0){
      var selectedReason = _.find(widthLimitReason, { value: asset.reason });
      return '' +
        '  <div class="form-group editable form-heigh">' +
        '      <label class="control-label">Rajoitus</label>' +
        '      <p class="form-control-static">' + (asset.limit ? (asset.limit + ' cm') : '–') + '</p>' +
        '  </div>' + '' +
        (asset.reason ? '<div class="form-group editable form-width">' +
          '      <label class="control-label">Syy</label>' +
          '      <p class="form-control-static">' + selectedReason.label + '</p>' +
          '  </div>': '');
    } else if (asset.propertyData) {
      var allTrafficSignProperties = asset.propertyData;
      var trafficSignSortedProperties = sortAndFilterTrafficSignProperties(allTrafficSignProperties);

      var components = _.reduce(_.map(trafficSignSortedProperties, function (feature) {
        feature.localizedName = window.localizedStrings[feature.publicId];
        var propertyType = feature.propertyType;

        if (propertyType === "text")
          return textHandler(feature);

        if (propertyType === "single_choice")
          return singleChoiceHandler(feature, collection);

        if (propertyType === "read_only_number")
          return readOnlyHandler(feature);

      }), function(prev, curr) { return prev + curr; }, '');

      if(asset.validityDirection)
        return components +
          '    <div class="form-group editable form-directional-traffic-sign edit-only">' +
          '      <label class="control-label">Vaikutussuunta</label>' +
          '      <button id="change-validity-direction" class="form-control btn btn-secondary btn-block">Vaihda suuntaa</button>' +
          '    </div>';

      return components;

    } else {
      return '';
    }
  };

  this.renderButtons = function() {
    return '' +
      '<div class="pointasset form-controls">' +
      '  <button id="save-button" class="save btn btn-primary" disabled>Tallenna</button>' +
      '  <button id ="cancel-button" class="cancel btn btn-secondary" disabled>Peruuta</button>' +
      '</div>';
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

  this.renderLinktoWorkList = function(layerName, localizedTexts) {
    $('#information-content').append('' +
      '<div class="form form-horizontal" data-layer-name="' + layerName + '">' +
      '<a id="point-asset-work-list-link" class="floating-point-assets" href="#work-list/' + layerName + '">Geometrian ulkopuolelle jääneet ' + localizedTexts.manyFloatingAssetsLabel + '</a>' +
      '</div>');
  };

  this.toggleMode = function(rootElement, readOnly) {
    rootElement.find('.delete').toggle(!readOnly);
    rootElement.find('.form-controls').toggle(!readOnly);
    rootElement.find('.editable .form-control-static').toggle(readOnly);
    rootElement.find('.editable .form-control').toggle(!readOnly);
    rootElement.find('.edit-only').toggle(!readOnly);
  };
};
})(this);
