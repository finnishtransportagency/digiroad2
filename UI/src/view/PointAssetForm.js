(function (root) {
  root.PointAssetForm = {
    initialize: bindEvents
  };

  function bindEvents(selectedAsset, layerName, localizedTexts) {
    var rootElement = $('#feature-attributes');

    function toggleMode(readOnly) {
      rootElement.find('.delete').toggle(!readOnly);
      rootElement.find('.form-controls').toggle(!readOnly);
      rootElement.find('.editable .form-control-static').toggle(readOnly);
      rootElement.find('.editable .form-control').toggle(!readOnly);
    }

    eventbus.on('application:readOnly', toggleMode);

    eventbus.on(layerName + ':selected ' + layerName + ':cancelled', function() {
      renderForm(rootElement, selectedAsset, localizedTexts);
      toggleMode(applicationModel.isReadOnly());
      rootElement.find('.form-controls button').attr('disabled', !selectedAsset.isDirty());
    });

    eventbus.on(layerName + ':changed', function() {
      rootElement.find('.form-controls button').attr('disabled', !selectedAsset.isDirty());
    });

    eventbus.on(layerName + ':unselected ' + layerName + ':creationCancelled', function() {
      rootElement.empty();
    });

    eventbus.on('layer:selected', function(layer, previousLayer) {
      if (layer === layerName) {
        renderLinktoWorkList(layer, localizedTexts);
      } else if (previousLayer === layerName) {
        $('#point-asset-work-list-link').parent().remove();
      }
    });
  }

  function renderForm(rootElement, selectedAsset, localizedTexts) {
    var id = selectedAsset.getId();

    var title = selectedAsset.isNew() ? "Uusi " + localizedTexts.newAssetLabel : 'ID: ' + id;
    var header = '<header><span>' + title + '</span>' + renderButtons() + '</header>';
    var form = renderAssetFormElements(selectedAsset, localizedTexts);
    var footer = '<footer>' + renderButtons() + '</footer>';

    rootElement.html(header + form + footer);

    rootElement.find('input[type="checkbox"]').on('change', function(event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.set({ toBeDeleted: eventTarget.attr('checked') === 'checked' });
    });

    rootElement.find('input[type="text"]').on('input change', function(event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.set({ name: eventTarget.val() });
    });

    rootElement.find('textarea').on('change', function(event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.set({ text: eventTarget.val() });
    });

    rootElement.find('button#change-validity-direction').on('click', function(event) {
      var eventTarget = $(event.currentTarget);
      previousValidityDirection = selectedAsset.get().validityDirection;
      selectedAsset.set({ validityDirection: validitydirections.switchDirection(previousValidityDirection) });
    });

    rootElement.find('select').on('change', function(event) {
      var asset = selectedAsset.get();
      var eventTarget = $(event.currentTarget);
      if (asset.obstacleType) {
        selectedAsset.set({ obstacleType: parseInt(eventTarget.val(), 10) });
      } else if (asset.safetyEquipment) {
        selectedAsset.set({ safetyEquipment: parseInt(eventTarget.val(), 10) });
      }
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

  function renderValueElement(asset) {
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
      { value: 6,  label: 'Levähdysalue' },
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
    var serviceTypeExtensions = {
      6: [
        {value: 1, label: 'Kattava varustelu'},
        {value: 2, label: 'Perusvarustelu'},
        {value: 3, label: 'Yksityinen palvelualue'},
        {value: 4, label: 'Ei lisätietoja'}
      ],
      11: [
        {value: 5, label: 'Merkittävä rautatieasema'},
        {value: 6, label: 'Vähäisempi rautatieasema'},
        {value: 7, label: 'Maanalainen/metroasema'}
      ]
    };

    if (asset.obstacleType) {
      return '' +
        '    <div class="form-group editable">' +
        '      <label class="control-label">' + 'Esterakennelma' + '</label>' +
        '      <p class="form-control-static">' + obstacleTypes[asset.obstacleType] + '</p>' +
        '      <select class="form-control" style="display:none">  ' +
        '        <option value="1" '+ (asset.obstacleType === 1 ? 'selected' : '') +'>Suljettu yhteys</option>' +
        '        <option value="2" '+ (asset.obstacleType === 2 ? 'selected' : '') +'>Avattava puomi</option>' +
        '      </select>' +
        '    </div>';
    } else if (asset.safetyEquipment) {
      return '' +
          '    <div class="form-group editable">' +
          '      <label class="control-label">' + 'Turvavarustus' + '</label>' +
          '      <p class="form-control-static">' + safetyEquipments[asset.safetyEquipment] + '</p>' +
          '      <select class="form-control" style="display:none">  ' +
          '        <option value="1" '+ (asset.safetyEquipment === 1 ? 'selected' : '') +'>Rautatie ei käytössä</option>' +
          '        <option value="2" '+ (asset.safetyEquipment === 2 ? 'selected' : '') +'>Ei turvalaitetta</option>' +
          '        <option value="3" '+ (asset.safetyEquipment === 3 ? 'selected' : '') +'>Valo/äänimerkki</option>' +
          '        <option value="4" '+ (asset.safetyEquipment === 4 ? 'selected' : '') +'>Puolipuomi</option>' +
          '        <option value="5" '+ (asset.safetyEquipment === 5 ? 'selected' : '') +'>Kokopuomi</option>' +
          '      </select>' +
          '    </div>' +
          '    <div class="form-group editable">' +
          '        <label class="control-label">' + 'Nimi' + '</label>' +
          '        <p class="form-control-static">' + (asset.name || '–') + '</p>' +
          '        <input type="text" class="form-control" value="' + (asset.name || '')  + '">' +
          '    </div>';
      } else if (asset.validityDirection) {
        return '' +
            '  <div class="form-group editable">' +
            '      <label class="control-label">' + 'Teksti' + '</label>' +
            '      <p class="form-control-static">' + (asset.text || '–') + '</p>' +
            '      <textarea class="form-control large-input">' + (asset.text || '')  + '</textarea>' +
            '  </div>' +
          '    <div class="form-group editable">' +
          '      <label class="control-label">' + 'Vaikutussuunta' + '</label>' +
          '      <button id="change-validity-direction" class="form-control btn btn-secondary btn-block">Vaihda suuntaa</button>' +
          '    </div>';
    } else if (asset.services) {
      var services = _.map(asset.services, function(service) { return renderService(service, serviceTypes, serviceTypeExtensions); }).join('');

      return '' +
        '    <div class="form-group editable">' +
        '      <label class="asset-label"> Palvelu </label>' +
        '      <ul>' +
               services +
        '      </ul>' +
        '    </div>';
    } else {
      return '';
    }
  }

  function renderService(service, serviceTypes, serviceTypeExtensions) {
    var serviceTypeLabelOptions = _.map(serviceTypes, function(serviceType) {
      return $('<option>', {value: serviceType.value, selected: service.serviceType == serviceType.value, text: serviceType.label})[0].outerHTML;
      //return '<option value="'+ serviceType.value + '" ' + (service.serviceType == serviceType.value ? 'selected' : '') + '>' + serviceType.label +'</option>';
    }).join('');

    return '<li>' +
      '  <div class="form-group editable">' +
      '    <h4> ' + _.find(serviceTypes, { value: service.serviceType }).label + '</h4>' +
      '    <select class="form-control" style="display:none">  ' +
      serviceTypeLabelOptions +
      '    </select>' +
      serviceTypeExtensionElements(service, serviceTypeExtensions) +
      '    <label class="control-label">Palvelun nimi</label>' +
      '    <p class="form-control-static">' + (service.name || '–') + '</p>' +
      '    <input type="text" class="form-control" value="' + (service.name || '')  + '">' +
      '    <label class="control-label">Palvelun lisätieto</label>' +
      '    <p class="form-control-static">' + (service.additionalInfo || '–') + '</p>' +
      '    <textarea class="form-control large-input">' + (service.additionalInfo || '')  + '</textarea>' +
      '  </div>' +
      '</li>';
  }

  function serviceTypeExtensionElements(service, serviceTypeExtensions) {
    if (service.serviceType === 6 || service.serviceType === 11) {
      var extensions = serviceTypeExtensions[service.serviceType];
      var extensionOptions = _.map(extensions, function(extension) {
        return $('<option>', {value: extension.value, text: extension.label, selected: extension.value === service.typeExtension})[0].outerHTML;
      }).join('');

      return '' +
        '<label class="control-label"></label>' +
        '<p class="form-control-static">' + (_.find(extensions, {value: service.typeExtension}).label || '') + '</p>' +
        '<select class="form-control" style="display:none">  ' +
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
      '  <button class="save btn btn-primary" disabled>Tallenna</button>' +
      '  <button class="cancel btn btn-secondary" disabled>Peruuta</button>' +
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
      '<div class="form form-horizontal">' +
      '<a id="point-asset-work-list-link" class="floating-point-assets" href="#work-list/' + layerName + '">Geometrian ulkopuolelle jääneet ' + localizedTexts.manyFloatingAssetsLabel + '</a>' +
      '</div>');
  }
})(this);
