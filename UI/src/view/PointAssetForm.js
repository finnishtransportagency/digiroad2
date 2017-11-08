(function (root) {
  root.PointAssetForm = {
    initialize: bindEvents
  };

  function bindEvents(selectedAsset, layerName, localizedTexts, editConstrains, roadCollection, applicationModel) {
    var rootElement = $('#feature-attributes');

    eventbus.on('application:readOnly', function(readOnly) {
      if(applicationModel.getSelectedLayer() == layerName && (!_.isEmpty(roadCollection.getAll()) && !_.isNull(selectedAsset.getId()))){
        toggleMode(rootElement, (editConstrains && editConstrains(selectedAsset)) || readOnly);
        //TODO: add form configurations to asset-type-layer-specifications.js to avoid if-clauses
        if (layerName == 'servicePoints' && isSingleService(selectedAsset)){
          rootElement.find('button.delete').hide();
        }
      }
    });

    eventbus.on(layerName + ':selected ' + layerName + ':cancelled roadLinks:fetched', function() {
      if (!_.isEmpty(roadCollection.getAll()) && !_.isNull(selectedAsset.getId())) {
        renderForm(rootElement, selectedAsset, localizedTexts, editConstrains, roadCollection);
        toggleMode(rootElement, editConstrains(selectedAsset) || applicationModel.isReadOnly());
        if (layerName == 'servicePoints') {
          rootElement.find('button#save-button').prop('disabled', true);
          rootElement.find('button#cancel-button').prop('disabled', false);
          if(isSingleService(selectedAsset)){
            rootElement.find('button.delete').hide();
          }
        } else {
          rootElement.find('.form-controls button').prop('disabled', !selectedAsset.isDirty());
        }
      }
    });

    eventbus.on(layerName + ':changed', function() {
      rootElement.find('.form-controls button').prop('disabled', !selectedAsset.isDirty());
    });

    eventbus.on(layerName + ':unselected ' + layerName + ':creationCancelled', function() {
      rootElement.empty();
    });

    eventbus.on('layer:selected', function(layer) {
      if (layer === layerName && layerName !== 'servicePoints') {
        renderLinktoWorkList(layer, localizedTexts);
      } else {
        $('#information-content .form[data-layer-name="' + layerName +'"]').remove();
      }
    });
  }

  function renderForm(rootElement, selectedAsset, localizedTexts, editConstrains, roadCollection) {
    var id = selectedAsset.getId();

  var title = selectedAsset.isNew() ? "Uusi " + localizedTexts.newAssetLabel : 'ID: ' + id;
  var header = '<header><span>' + title + '</span>' + renderButtons() + '</header>';
  var form = renderAssetFormElements(selectedAsset, localizedTexts);
  var footer = '<footer>' + renderButtons() + '</footer>';

  rootElement.html(header + form + footer);

  rootElement.find('input[type="checkbox"]').on('change', function (event) {
    var eventTarget = $(event.currentTarget);
    selectedAsset.set({toBeDeleted: eventTarget.prop('checked')});
  });

  rootElement.find('input[type="text"]').on('input change', function (event) {
    var eventTarget = $(event.currentTarget);
    selectedAsset.set({name: eventTarget.val()});
  });

  rootElement.find('.linear-asset.form textarea, .form-directional-traffic-sign textarea').on('keyup', function (event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.set({text: eventTarget.val()});
  });

  rootElement.find('.form-service textarea').on('input change', function (event) {
    var serviceId = parseInt($(event.currentTarget).data('service-id'), 10);
    selectedAsset.set({services: modifyService(selectedAsset.get().services, serviceId, {additionalInfo: $(event.currentTarget).val()})});
  });

  rootElement.find('.service-name').on('input change', function (event) {
    var serviceId = parseInt($(event.currentTarget).data('service-id'), 10);
    selectedAsset.set({services: modifyService(selectedAsset.get().services, serviceId, {name: $(event.currentTarget).val()})});
  });

  rootElement.find('.service-parking-place-count').on('input change', function (event) {
    var serviceId = parseInt($(event.currentTarget).data('service-id'), 10);
    selectedAsset.set({services: modifyService(selectedAsset.get().services, serviceId, {parkingPlaceCount: parseInt($(event.currentTarget).val(), 10)})});
  });

  rootElement.find('.form-service').on('change', '.select-service-type', function (event) {
    var newServiceType = parseInt($(event.currentTarget).val(), 10);
    var serviceId = parseInt($(event.currentTarget).data('service-id'), 10);
    var services = modifyService(selectedAsset.get().services, serviceId, {serviceType: newServiceType});
    selectedAsset.set({services: services});
    renderForm(rootElement, selectedAsset, localizedTexts, editConstrains, roadCollection);
    toggleMode(rootElement, editConstrains(selectedAsset) || applicationModel.isReadOnly());
    rootElement.find('.form-controls button').prop('disabled', !selectedAsset.isDirty());
    if(services.length < 2){
      rootElement.find('button.delete').hide();
    }
  });

    function modifyService(services, id, modifications) {
      return _.map(services, function(service) {
        if (service.id === id) {
          checkTypeExtension(service, modifications);
          return _.merge({}, service, modifications);
        }
        return service;
      });
    }

    function checkTypeExtension(service, modifications)  {
        var serviceType = modifications.serviceType ? modifications.serviceType : service.serviceType;
          if(!serviceTypeExtensions[serviceType])
            delete service.typeExtension;
    }

    rootElement.find('.form-service').on('change', '.new-service select', function (event) {
      var newServiceType = parseInt($(event.currentTarget).val(), 10);
      var assetId = selectedAsset.getId();
      var services = selectedAsset.get().services;
      var generatedId = services.length;
      var newServices = services.concat({id: generatedId, assetId: assetId, serviceType: newServiceType});
      selectedAsset.set({services: newServices});
      renderForm(rootElement, selectedAsset, localizedTexts, editConstrains, roadCollection);
      toggleMode(rootElement, editConstrains(selectedAsset) || applicationModel.isReadOnly());
      rootElement.find('.form-controls button').prop('disabled', !selectedAsset.isDirty());
      if(newServices.length < 2){
        rootElement.find('button.delete').hide();
      }
    });

    rootElement.on('click', 'button.delete', function (evt) {
      var existingService = $(evt.target).closest('.service-point');
      $(evt.target).parent().parent().remove();
      var serviceId =  parseInt(existingService.find('input[type="text"]').attr('data-service-id'), 10);
      var services = selectedAsset.get().services;
      var newServices = _.reject(services, { id: serviceId });
      if(newServices.length < 2){
        rootElement.find('button.delete').hide();
      }
      selectedAsset.set({ services: newServices });
    });

    rootElement.find('.form-traffic-sign input[type=text],.form-traffic-sign select').on('change', function (event) {
      var eventTarget = $(event.currentTarget);
      var propertyPublicId = eventTarget.attr('id');
      var propertyValue = $(event.currentTarget).val();
      selectedAsset.setPropertyByPublicId(propertyPublicId, propertyValue);
    });

    rootElement.find('.form-service').on('change', '.select-service-type-extension', function(event) {
      var serviceId = parseInt($(event.currentTarget).data('service-id'), 10);
      var newTypeExtension = parseInt($(event.currentTarget).val(), 10);
      selectedAsset.set({services: modifyService(selectedAsset.get().services, serviceId, {typeExtension: newTypeExtension})});
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
  }

  function renderAssetFormElements(selectedAsset, localizedTexts) {
    var asset = selectedAsset.get();

    if (selectedAsset.isNew()) {
      return '' +
        '<div class="wrapper">' +
        '  <div class="form form-horizontal form-dark form-pointasset">' +
             renderValueElement(asset) +
        '  </div>' +
        '</div>';
    } else {
      return '' +
        '<div class="wrapper">' +
        '  <div class="form form-horizontal form-dark form-pointasset">' +
             renderFloatingNotification(asset.floating, localizedTexts) +
        '    <div class="form-group">' +
        '      <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + (asset.createdBy || '-') + ' ' + (asset.createdAt || '') + '</p>' +
        '    </div>' +
        '    <div class="form-group">' +
        '      <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + (asset.modifiedBy || '-') + ' ' + (asset.modifiedAt || '') + '</p>' +
        '    </div>' +
             renderValueElement(asset) +
        '    <div class="form-group form-group delete">' +
        '      <div class="checkbox" >' +
        '        <input type="checkbox">' +
        '      </div>' +
        '      <p class="form-control-static">Poista</p>' +
        '    </div>' +
        '  </div>' +
        '</div>';
    }
  }

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

  var serviceTypes = [
    { value: 4,  label: 'Tulli' },
    { value: 5,  label: 'Rajanylityspaikka' },
    { value: 6,  label: 'Lepoalue' },
    { value: 8,  label: 'Lentokenttä' },
    { value: 9,  label: 'Laivaterminaali' },
    { value: 10, label: 'Taksiasema' },
    { value: 11, label: 'Rautatieasema' },
    { value: 12, label: 'Pysäköintialue' },
    { value: 13, label: 'Autojen lastausterminaali' },
    { value: 14, label: 'Kuorma-autojen pysäköintialue' },
    { value: 15, label: 'Pysäköintitalo' },
    { value: 16, label: 'Linja-autoasema' }
  ];

  var commonServiceExtension = [
    {value: 1, label: 'Kattava varustelu'},
    {value: 2, label: 'Perusvarustelu'},
    {value: 3, label: 'Yksityinen palvelualue'},
    {value: 4, label: 'Ei tietoa'}
  ];

  var serviceTypeExtensions = {
    6: commonServiceExtension,
    12: commonServiceExtension,
    14: commonServiceExtension,
    11: [
      {value: 5, label: 'Merkittävä rautatieasema'},
      {value: 6, label: 'Vähäisempi rautatieasema'},
      {value: 7, label: 'Maanalainen/metroasema'}
    ]
  };

  var signTypes = [
    {value: 1, label:'Nopeusrajoitus'},
    {value: 2,  label: 'Nopeusrajoitus Päättyy'},
    {value: 3, label:'Nopeusrajoitusalue'},
    {value: 4, label:'Nopeusrajoitusalue Päättyy'},
    {value: 5, label:'Taajama'},
    {value: 6, label:'Taajama Päättyy'},
    {value: 7, label:'Suojatie'},
    {value: 8, label:'Suurin Sallittu Pituus'},
    {value: 9, label:'Varoitus'},
    {value: 10, label:'Vasemmalle Kääntyminen Kielletty'},
    {value: 11, label:'Oikealle Kääntyminen Kielletty'},
    {value: 12, label:'U-Käännös Kielletty'}
  ];

  var sortAndFilterTrafficSignProperties = function(properties) {
    var propertyOrdering = [
      'trafficSigns_type',
      'trafficSigns_value',
      'trafficSigns_info'];

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

  var singleChoiceHandler = function (property) {
    var propertyValue = (property.values.length === 0) ? '' : _.first(property.values).propertyValue;
    var propertyDisplayValue = (property.values.length === 0) ? '' : _.first(property.values).propertyDisplayValue;
    var trafficSignOptions = _.map(signTypes, function(signType) {
      return $('<option>', {value: signType.value, selected: propertyValue == signType.value, text: signType.label})[0].outerHTML;
    }).join('');
    return '' +
        '    <div class="form-group editable form-traffic-sign">' +
        '      <label class="control-label">' + property.localizedName + '</label>' +
        '      <p class="form-control-static">' + (propertyDisplayValue || '-') + '</p>' +
        '      <select class="form-control" style="display:none" id="' + property.publicId + '">  ' +
        trafficSignOptions +
        '      </select>' +
        '    </div>';
  };

  function renderValueElement(asset) {
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
            '      <textarea class="form-control large-input">' + (asset.text || '')  + '</textarea>' +
            '  </div>' +
          '    <div class="form-group editable form-directional-traffic-sign edit-only">' +
          '      <label class="control-label">Vaikutussuunta</label>' +
          '      <button id="change-validity-direction" class="form-control btn btn-secondary btn-block">Vaihda suuntaa</button>' +
          '    </div>';
    } else if (asset.services) {
      var services = _(asset.services)
        .sortByAll('serviceType', 'id')
        .map(renderService)
        .join('');

      return '' +
          '    <div class="form-group editable form-service">' +
          '      <ul>' +
          services +
          renderNewServiceElement() +
          '      </ul>' +
          '    </div>';
    } else if (asset.propertyData) {
      var allTrafficSignProperties = asset.propertyData;
      var trafficSignSortedProperties = sortAndFilterTrafficSignProperties(allTrafficSignProperties);

      var components = _.reduce(_.map(trafficSignSortedProperties, function (feature) {
        feature.localizedName = window.localizedStrings[feature.publicId];
        var propertyType = feature.propertyType;

        if (propertyType === "text")
          return textHandler(feature);

        if (propertyType === "single_choice")
          return singleChoiceHandler(feature);

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
  }

  function renderService(service) {
    var serviceTypeLabelOptions = _.map(serviceTypes, function(serviceType) {
      return $('<option>', {value: serviceType.value, selected: service.serviceType == serviceType.value, text: serviceType.label})[0].outerHTML;
    }).join('');

    var selectedServiceType = _.find(serviceTypes, { value: service.serviceType });
    var parkingPlaceElements = '' +
      '<label class="control-label">Pysäköintipaikkojen lukumäärä</label>' +
      '<p class="form-control-static">' + (service.parkingPlaceCount || '–') + '</p>' +
      '<input type="text" class="form-control service-parking-place-count" data-service-id="' + service.id + '" value="' + (service.parkingPlaceCount || '')  + '">';

    return '<li>' +
      '  <div class="form-group service-point editable">' +
        '  <div class="form-group">' +
      '      <button class="delete btn-delete">x</button>' +
      '      <h4 class="form-control-static"> ' + (selectedServiceType ? selectedServiceType.label : '') + '</h4>' +
      '      <select class="form-control select-service-type" style="display:none" data-service-id="' + service.id + '">  ' +
      '        <option disabled selected>Lisää tyyppi</option>' +
             serviceTypeLabelOptions +
      '      </select>' +
      '    </div>' +
           serviceTypeExtensionElements(service, serviceTypeExtensions) +
      '    <label class="control-label">Palvelun nimi</label>' +
      '    <p class="form-control-static">' + (service.name || '–') + '</p>' +
      '    <input type="text" class="form-control service-name" data-service-id="' + service.id + '" value="' + (service.name || '')  + '">' +
      '    <label class="control-label">Palvelun lisätieto</label>' +
      '    <p class="form-control-static">' + (service.additionalInfo || '–') + '</p>' +
      '    <textarea class="form-control large-input" data-service-id="' + service.id + '">' + (service.additionalInfo || '')  + '</textarea>' +
           (showParkingPlaceCount(selectedServiceType) ? parkingPlaceElements : '') +
      '  </div>' +
      '</li>';
  }

  function showParkingPlaceCount(selectedServiceType) {
    return (selectedServiceType.value == 12 || selectedServiceType.value == 15 || selectedServiceType.value == 14);
  }

  function renderNewServiceElement() {
    var serviceTypeLabelOptions = _.map(serviceTypes, function(serviceType) {
      return $('<option>', {value: serviceType.value, text: serviceType.label})[0].outerHTML;
    }).join('');

    return '' +
      '<li><div class="form-group new-service">' +
      '  <select class="form-control select">' +
      '    <option class="empty" disabled selected>Lisää uusi palvelu</option>' +
      serviceTypeLabelOptions +
      '  </select>' +
      '</div></li>';
  }

  function serviceTypeExtensionElements(service, serviceTypeExtensions) {
    var extensions = serviceTypeExtensions[service.serviceType];
    if (extensions) {
      var extensionOptions = _.map(extensions, function(extension) {
        return $('<option>', {value: extension.value, text: extension.label, selected: extension.value === service.typeExtension})[0].outerHTML;
      }).join('');
      var currentExtensionType = _.find(extensions, {value: service.typeExtension});
      return '' +
        '<label class="control-label">Tarkenne</label>' +
        '<p class="form-control-static">' + (currentExtensionType ? currentExtensionType.label : '–') + '</p>' +
        '<select class="form-control select-service-type-extension" style="display:none" data-service-id="' + service.id + '">  ' +
        '  <option disabled selected>Lisää tarkenne</option>' +
           extensionOptions +
        '</select>';
    } else {
      return '';
    }
  }

  function renderButtons() {
    return '' +
      '<div class="pointasset form-controls">' +
      '  <button id="save-button" class="save btn btn-primary" disabled>Tallenna</button>' +
      '  <button id ="cancel-button" class="cancel btn btn-secondary" disabled>Peruuta</button>' +
      '</div>';
  }

  function renderFloatingNotification(floating, localizedTexts) {
    if (floating) {
      return '' +
        '<div class="form-group form-notification">' +
        ' <p>Kadun tai tien geometria on muuttunut, tarkista ja korjaa ' + localizedTexts.singleFloatingAssetLabel + ' sijainti</p>' +
        '</div>';
    } else {
      return '';
    }
  }

  function renderLinktoWorkList(layerName, localizedTexts) {
    $('#information-content').append('' +
      '<div class="form form-horizontal" data-layer-name="' + layerName + '">' +
      '<a id="point-asset-work-list-link" class="floating-point-assets" href="#work-list/' + layerName + '">Geometrian ulkopuolelle jääneet ' + localizedTexts.manyFloatingAssetsLabel + '</a>' +
      '</div>');
  }

  function isSingleService(selectedAsset){
    return selectedAsset.get().services.length < 2;
  }

  function toggleMode(rootElement, readOnly) {
    rootElement.find('.delete').toggle(!readOnly);
    rootElement.find('.form-controls').toggle(!readOnly);
    rootElement.find('.editable .form-control-static').toggle(readOnly);
    rootElement.find('.editable .form-control').toggle(!readOnly);
    rootElement.find('.edit-only').toggle(!readOnly);
  }
})(this);
