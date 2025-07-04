(function(root) {
  root.ServicePointForm = function() {
    PointAssetForm.call(this);
    var me = this;
    var editingRestrictions = new EditingRestrictions();
    var typeId = 250;

    this.initialize = function(parameters) {
      me.pointAsset = parameters.pointAsset;
      me.roadCollection = parameters.roadCollection;
      me.applicationModel = parameters.applicationModel;
      me.backend = parameters.backend;
      me.saveCondition = parameters.saveCondition;
      me.feedbackCollection = parameters.feedbackCollection;
      me.selectedAsset = me.pointAsset.selectedPointAsset;
      me.bindEvents(parameters);
    };

    var serviceTypes = [
      { value: 12, label: 'Pysäköintialue' },
      { value: 15, label: 'Pysäköintitalo' },
      { value: 11, label: 'Rautatieasema' },
      { value: 16, label: 'Linja-autoasema' },
      { value: 8,  label: 'Lentokenttä' },
      { value: 9,  label: 'Laivaterminaali' },
      { value: 10, label: 'Taksiasema' },
      { value: 6,  label: 'Lepoalue' },
      { value: 4,  label: 'Tulli' },
      { value: 5,  label: 'Rajanylityspaikka' },
      { value: 13, label: 'Autojen lastausterminaali' },
      { value: 14, label: 'Linja- ja kuorma-autojen pysäköintialue' },
      { value: 18, label: 'E18 rekkaparkki' },
      { value: 19, label: 'Tierumpu' }
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
      18: commonServiceExtension,
      11: [
        {value: 5, label: 'Merkittävä rautatieasema'},
        {value: 6, label: 'Vähäisempi rautatieasema'},
        {value: 7, label: 'Maanalainen/metroasema'}
      ]
    };

    var propertyOrdering = ['suggest_box'];

    this.bindEvents = function(parameters) {
      var rootElement = $('#feature-attributes');
      var typeId = me.pointAsset.typeId;
      var selectedAsset = me.pointAsset.selectedPointAsset;
      var collection  = me.pointAsset.collection;
      var layerName = me.pointAsset.layerName;
      var localizedTexts = me.pointAsset.formLabels;
      var authorizationPolicy = me.pointAsset.authorizationPolicy;
      new FeedbackDataTool(parameters.feedbackCollection, layerName, authorizationPolicy);

      eventbus.on('assetEnumeratedPropertyValues:fetched', function(event) {
        if(event.assetType === typeId)
          me.enumeratedPropertyValues = event.enumeratedPropertyValues;
      });

      me.backend.getAssetEnumeratedPropertyValues(typeId);

      eventbus.on('application:readOnly', function(readOnly) {
        if(me.applicationModel.getSelectedLayer() === layerName && (!_.isEmpty(me.roadCollection.getAll()) && !_.isNull(selectedAsset.getId()))){
          me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, me.roadCollection) || readOnly ||
              editingRestrictions.pointAssetHasRestriction(selectedAsset.getMunicipalityCode(), 'State', typeId) || editingRestrictions.pointAssetHasRestriction(selectedAsset.getMunicipalityCode(), 'Municipality', typeId));
          if (isSingleService(selectedAsset)){
            rootElement.find('button.delete').hide();
          }
        }
      });

      eventbus.on(layerName + ':selected ' + layerName + ':cancelled roadLinks:fetched', function() {
        if (!_.isEmpty(me.roadCollection.getAll()) && !_.isNull(selectedAsset.getId())) {
          me.renderForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, me.roadCollection, collection);
          me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, me.roadCollection) || me.applicationModel.isReadOnly() ||
              editingRestrictions.pointAssetHasRestriction(selectedAsset.getMunicipalityCode(), 'State', typeId) || editingRestrictions.pointAssetHasRestriction(selectedAsset.getMunicipalityCode(), 'Municipality', typeId));
          rootElement.find('button#save-button').prop('disabled', true);
          rootElement.find('button#cancel-button').prop('disabled', false);
          if(isSingleService(selectedAsset)){
              rootElement.find('button.delete').hide();
          }
        }
      });

      eventbus.on(layerName + ':changed', function() {
        rootElement.find('.form-controls button').prop('disabled', !(selectedAsset.isDirty() && me.saveCondition(selectedAsset, authorizationPolicy)));
        rootElement.find('button#cancel-button').prop('disabled', !(selectedAsset.isDirty()));
      });

      eventbus.on(layerName + ':unselected ' + layerName + ':creationCancelled', function() {
        rootElement.find('#feature-attributes-header').empty();
        rootElement.find('#feature-attributes-form').empty();
        rootElement.find('#feature-attributes-footer').empty();
      });

      eventbus.on('layer:selected', function() {
        if(layerName === applicationModel.getSelectedLayer())
        $('ul[class=information-content]').empty();
      });
    };

    this.userInformationLog = function(authorizationPolicy, asset) {

      var limitedRights = 'Käyttöoikeudet eivät riitä kohteen muokkaamiseen. Voit muokata kohteita vain oman kuntasi alueelta.';
      var noRights = 'Käyttöoikeudet eivät riitä kohteen muokkaamiseen.';
      var editingRestricted = 'Kohteiden muokkaus on estetty, koska kohteita ylläpidetään Tievelho-tietojärjestelmässä tai kunnan omassa tietojärjestelmässä.';
      var message = '';

      if (editingRestrictions.pointAssetHasRestriction(asset.getMunicipalityCode(), 'State', typeId) || editingRestrictions.pointAssetHasRestriction(asset.getMunicipalityCode(), 'Municipality', typeId)) {
        message = editingRestricted;
      } else if(!authorizationPolicy.isOperator() && (authorizationPolicy.isMunicipalityMaintainer() || authorizationPolicy.isElyMaintainer()) && !authorizationPolicy.hasRightsInMunicipality(asset.getMunicipalityCode())) {
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

    this.renderValueElement = function(asset, collection, authorizationPolicy) {
      var components = me.renderComponents(asset.propertyData, propertyOrdering, authorizationPolicy);
      var services = _(asset.services)
        .sortBy('serviceType', 'id')
        .map(renderService)
        .join('');

      return '' +
        '    <div class="form-group editable form-service">' +
        '      <ul>' +
        services +
        renderNewServiceElement() +
        '      </ul>' +
        '    </div>' +
          components;
    };

    this.boxEvents = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy) {

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

      rootElement.find('.service-weightLimit').on('input change', function (event) {
        var serviceId = parseInt($(event.currentTarget).data('service-id'), 10);
        selectedAsset.set({services: modifyService(selectedAsset.get().services, serviceId, {weightLimit: parseInt($(event.currentTarget).val(), 10)})});
      });

      rootElement.find('.form-service').on('change', '.select-service-type', function (event) {
        var newServiceType = parseInt($(event.currentTarget).val(), 10);
        var serviceId = parseInt($(event.currentTarget).data('service-id'), 10);
        var services = modifyService(selectedAsset.get().services, serviceId, {serviceType: newServiceType, isAuthorityData: isAuthorityData(newServiceType)});

        selectedAsset.set({services: services});
        me.renderForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, me.roadCollection);
        me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, me.roadCollection) || me.applicationModel.isReadOnly() ||
            editingRestrictions.pointAssetHasRestriction(selectedAsset.getMunicipalityCode(), 'State', typeId) || editingRestrictions.pointAssetHasRestriction(selectedAsset.getMunicipalityCode(), 'Municipality', typeId));
        rootElement.find('.form-controls button').prop('disabled', !(selectedAsset.isDirty() && me.saveCondition(selectedAsset, authorizationPolicy)));
        if(services.length < 2){
          rootElement.find('button.delete').hide();
        }
      });

      rootElement.find('.form-service').on('change', '.new-service select', function (event) {
        var newServiceType = parseInt($(event.currentTarget).val(), 10);
        var assetId = selectedAsset.getId();
        var services = selectedAsset.get().services;
        var generatedId = services.length;
        var newServices = services.concat({id: generatedId, assetId: assetId, serviceType: newServiceType, isAuthorityData: isAuthorityData(newServiceType)});
        selectedAsset.set({services: newServices});
        me.renderForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, me.roadCollection);
        me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, me.roadCollection) || me.applicationModel.isReadOnly() ||
            editingRestrictions.pointAssetHasRestriction(selectedAsset.getMunicipalityCode(), 'State', typeId) || editingRestrictions.pointAssetHasRestriction(selectedAsset.getMunicipalityCode(), 'Municipality', typeId));
        rootElement.find('.form-controls button').prop('disabled', !(selectedAsset.isDirty() && me.saveCondition(selectedAsset, authorizationPolicy)));
        if(newServices.length < 2){
          rootElement.find('button.delete').hide();
        }
      });

      rootElement.find('.form-service').on('change', '.select-service-type-extension', function(event) {
        var serviceId = parseInt($(event.currentTarget).data('service-id'), 10);
        var newTypeExtension = parseInt($(event.currentTarget).val(), 10);
        selectedAsset.set({services: modifyService(selectedAsset.get().services, serviceId, {typeExtension: newTypeExtension})});
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

    };

    var renderService = function (service) {
      var serviceTypeLabelOptions = _.map(serviceTypes, function(serviceType) {
        return $('<option>', {value: serviceType.value, "data-value": TextUtils.toDataValue(serviceType.label), selected: service.serviceType === serviceType.value, text: serviceType.label})[0].outerHTML;
      }).join('');

      var selectedServiceType = _.find(serviceTypes, { value: service.serviceType });
      var parkingPlaceElements = '' +
        '<div><label class="control-label">Pysäköintipaikkojen lukumäärä</label>' +
        '<p class="form-control-static">' + (service.parkingPlaceCount || '–') + '</p>' +
        '<input type="text" class="form-control service-parking-place-count" data-service-id="' + service.id + '" value="' + (service.parkingPlaceCount || '')  + '"></div>';

      var weightElement = '' +
        '<div><label class="control-label">Painorajoitus</label>' +
        '<p class="form-control-static">' + (_.isUndefined(service.weightLimit) ? '–' : service.weightLimit + ' Kg') + '</p>' +
        '<input type="text" class="form-control service-weightLimit" data-service-id="' + service.id + '" value="' + (service.weightLimit || '')  + '">' +
        '<span class="form-control kg-unit-addon">Kg</span></div>';

      var nameElement = '' +
        '<div><label class="control-label">Palvelun nimi</label>' +
        '<p class="form-control-static">' + (service.name || '–') + '</p>'+
        '<input type="text" class="form-control service-name" data-service-id="' + service.id + '" value="' + (service.name || '')  + '"></div>';

      return '<li>' +
        '   <div class="form-group service-point editable">' +
        '   <div class="form-group">' +
        '      <button class="delete btn-delete">x</button>' +
        '      <h4 class="form-control-static"> ' + (selectedServiceType ? selectedServiceType.label : '') + '</h4>' +
        '      <select class="form-control select-service-type" data-service-id="' + service.id + '">  ' +
        '        <option disabled selected>Lisää tyyppi</option>' +
        serviceTypeLabelOptions +
        '      </select>' +
        '    </div>' +
        serviceTypeExtensionElements(service, serviceTypeExtensions) +
        (!isCulvert(selectedServiceType) ? nameElement : '') +
        '<div>' +
        '    <label class="control-label">Palvelun lisätieto</label>' +
        '    <p class="form-control-static">' + (service.additionalInfo || '–') + '</p>' +
        '    <textarea class="form-control large-input" data-service-id="' + service.id + '">' + (service.additionalInfo || '')  + '</textarea>' +
        '</div><div>' +
        '    <label class="control-label">Viranomaisdataa</label>' +
        '    <p class="form-control-readOnly">'+ (service.isAuthorityData ?  'Kyllä' : 'Ei') +'</p>' +
        '</div>' +
        (showParkingPlaceCount(selectedServiceType) ? parkingPlaceElements : '') +
        (isCulvert(selectedServiceType) ? weightElement : '') +
        '</li>';
    };

    function checkTypeExtension(service, modifications)  {
      var serviceType = modifications.serviceType ? modifications.serviceType : service.serviceType;
      if(!serviceTypeExtensions[serviceType])
        delete service.typeExtension;
    }

    function checkWeightField(service, modifications)  {
      var serviceType = modifications.serviceType ? modifications.serviceType : service.serviceType;
      if(!isCulvert(serviceType))
        delete service.weightLimit;
    }

    function checkNameField(service, modifications)  {
      var serviceType = modifications.serviceType ? modifications.serviceType : service.serviceType;
      if(isCulvert(serviceType))
        delete service.name;
    }

    function isCulvert(selectedServiceType) {
      return (selectedServiceType.value ? selectedServiceType.value : selectedServiceType) === 19;
    }

    function showParkingPlaceCount(selectedServiceType) {
      return (selectedServiceType.value === 12 || selectedServiceType.value === 15 || selectedServiceType.value === 14);
    }

    function renderNewServiceElement() {
      var serviceTypeLabelOptions = _.map(serviceTypes, function(serviceType) {
        return $('<option>', {value: serviceType.value, text: serviceType.label, "data-value": TextUtils.toDataValue(serviceType.label)})[0].outerHTML;
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
          return $('<option>', {value: extension.value, text: extension.label, "data-value": TextUtils.toDataValue(extension.label), selected: extension.value === service.typeExtension})[0].outerHTML;
        }).join('');
        var currentExtensionType = _.find(extensions, {value: service.typeExtension});
        return '' +
          '<div><label class="control-label">Tarkenne</label>' +
          '<p class="form-control-static">' + (currentExtensionType ? currentExtensionType.label : '–') + '</p>' +
          '<select class="form-control select-service-type-extension" data-service-id="' + service.id + '">  ' +
          '  <option disabled selected>Lisää tarkenne</option>' +
          extensionOptions +
          '</select></div>';
      } else {
        return '';
      }
    }

    function modifyService(services, id, modifications) {
      return _.map(services, function(service) {
        if (service.id === id) {
          checkTypeExtension(service, modifications);
          checkWeightField(service, modifications);
          checkNameField(service, modifications);
          return _.merge({}, service, modifications);
        }
        return service;
      });
    }

    function isSingleService(selectedAsset){
      return selectedAsset.get().services.length < 2;
    }

    function isAuthorityData(selectedServiceType) {
      return !(selectedServiceType === 10 || selectedServiceType === 17 || selectedServiceType === 19);
    }

  };
})(this);