(function(root) {

  var poistaSelected = false;

  var ValidationErrorLabel = function() {
    var element = $('<span class="validation-error">Pakollisia tietoja puuttuu</span>');

    var updateVisibility = function() {
      if (selectedMassTransitStopModel.isDirty() && selectedMassTransitStopModel.requiredPropertiesMissing()) {
        element.show();
      } else {
        element.hide();
      }
    };

    updateVisibility();

    eventbus.on('asset:moved assetPropertyValue:changed', function() {
      updateVisibility();
    }, this);

    return {
      element: element
    };
  };

  var InvalidCombinationError = function() {
    var element = $('<span id="comboBoxErrors" class="validation-fatal-error"></span>');
    var updateVisibility = function() {
      if (selectedMassTransitStopModel.isDirty() && selectedMassTransitStopModel.hasMixedVirtualAndRealStops()) {
        element.text('Virtuaalipysäkkiä ei voi yhdistää muihin pysäkkityyppeihin');
        element.show();
      } else if(selectedMassTransitStopModel.isDirty() && !selectedMassTransitStopModel.hasMixedVirtualAndRealStops() && selectedMassTransitStopModel.pikavuoroIsAlone()){
        element.text('Pikavuoro tulee valita yhdessä toisen pysäkkityypin kanssa');
        element.show();
      } else {
        element.hide();
      }
    };

    updateVisibility();

    eventbus.on('asset:moved assetPropertyValue:changed', function() {
      updateVisibility();
    }, this);

    return {
      element: element
    };
  };

  var validatePlatformNumberMaxSize = function (target) {
    var propertyValue = target.currentTarget.value;
    if (propertyValue.length > 3) {
      target.currentTarget.value = propertyValue.substring(0, 3);
    }
  };

  var SaveButton = function(isTerminalActive) {
    var deleteMessage = isTerminalActive ? 'valitsemasi terminaalipysäkin' : 'pysäkin';
    var element = $('<button />').addClass('save btn btn-primary').text('Tallenna').click(function () {
      if (poistaSelected) {
        new GenericConfirmPopup('Haluatko varmasti poistaa ' + deleteMessage + '?', {
          successCallback: function () {
            element.prop('disabled', true);
            selectedMassTransitStopModel.deleteMassTransitStop(poistaSelected);
          }
        });
      } else {
          if(selectedMassTransitStopModel.validateDirectionsForSave()){
              selectedMassTransitStopModel.save();
          }else{
              new GenericConfirmPopup('Pysäkin vaikutussuunta on yksisuuntaisen tielinkin ajosuunnan vastainen. Pysäkkiä ei tallennettu.',
                  {type: 'alert'});
          }
      }
    });
    var updateStatus = function() {
      if (selectedMassTransitStopModel.isDirty() && !selectedMassTransitStopModel.requiredPropertiesMissing() && !selectedMassTransitStopModel.hasMixedVirtualAndRealStops() && !selectedMassTransitStopModel.pikavuoroIsAlone()){
        element.prop('disabled', false);
      } else if(poistaSelected) {
        element.prop('disabled', false);
      } else {
        element.prop('disabled', true);
      }
    };

    updateStatus();

    eventbus.on('checkBoxPoistaChanged', function(){
      updateStatus();
    }, this);

    eventbus.on('asset:moved assetPropertyValue:changed', function() {
      updateStatus();
    }, this);

    return {
      element: element
    };
  };

  var CancelButton = function() {
    var element = $('<button />').prop('disabled', !selectedMassTransitStopModel.isDirty()).addClass('cancel btn btn-secondary').text('Peruuta').click(function() {
      $("#feature-attributes").empty();
      selectedMassTransitStopModel.cancel();
    });

    eventbus.on('asset:moved assetPropertyValue:changed', function() {
      element.prop('disabled', false);
    }, this);

    return {
      element: element
    };
  };

  root.MassTransitStopForm = {
    initialize: function (backend) {
      var enumeratedPropertyValues = null;
      var readOnly = true;
      poistaSelected = false;
      var streetViewHandler;
      var isTRMassTransitStop = false;
      var isTerminalBusStop = false;

      var MStopDeletebutton = function(readOnly) {

        var removalForm = $('<div class="checkbox"> <label>Poista<input type="checkbox" id = "removebox"></label></div>');
        removalForm.find("input").on('click', function(){
          poistaSelected = !poistaSelected;
          eventbus.trigger('checkBoxPoistaChanged');
        });

        return removalForm;
      };

      var renderAssetForm = function() {
        poistaSelected = false;
        var container = $("#feature-attributes").empty();
        var header = busStopHeader();
        readOnly = controlledByTR();
        var wrapper;
        if(readOnly){
          wrapper = $('<div />').addClass('wrapper read-only');
        } else {
          wrapper = $('<div />').addClass('wrapper edit-mode');
        }
        streetViewHandler = getStreetView();
        wrapper.append(streetViewHandler.render())
          .append($('<div />').addClass('form form-horizontal form-dark').attr('role', 'form').append(getAssetForm()));

        var featureAttributesElement = container.append(header).append(wrapper);
        addDatePickers();

        var saveBtn = new SaveButton(isTerminalBusStop);
        var cancelBtn = new CancelButton();
        var validationErrorLabel = new ValidationErrorLabel();

        featureAttributesElement.append($('<footer />')
            .addClass('mass-transit-stop')
            .addClass('form-controls')
            .append(validationErrorLabel.element)
            .append(saveBtn.element)
            .append(cancelBtn.element));

        if (readOnly) {
          $('#feature-attributes .form-controls').hide();
          wrapper.addClass('read-only');
          wrapper.removeClass('edit-mode');
        }

        function busStopHeader(asset) {
          var buttons = $('<div/>').addClass('mass-transit-stop').addClass('form-controls')
            .append(new ValidationErrorLabel().element)
            .append(new SaveButton(isTerminalBusStop).element)
            .append(new CancelButton().element);

          var header = $('<header/>');

          if (_.isNumber(selectedMassTransitStopModel.get('nationalId'))) {
            header.append('<span>Valtakunnallinen ID: ' + selectedMassTransitStopModel.get('nationalId') + '</span>');
          } else if (isTerminalBusStop) {
            header.append('<span class="terminal-header"> Uusi terminaalipys&auml;kki</span>');
          } else {
            header.append('<span>Uusi pys&auml;kki</span>');
          }
          header.append(buttons);
          return header;
        }
      };

      var getStreetView = function() {
        var model = selectedMassTransitStopModel;
        var render = function() {
          var wgs84 = proj4('EPSG:3067', 'WGS84', [model.get('lon'), model.get('lat')]);
          var heading= (model.get('validityDirection') === validitydirections.oppositeDirection ? model.get('bearing') - 90 : model.get('bearing') + 90);
          return $(streetViewTemplates(wgs84[0],wgs84[1],heading)(
            {
            wgs84X: wgs84[0],
            wgs84Y: wgs84[1],
            heading: (model.get('validityDirection') === validitydirections.oppositeDirection ? model.get('bearing') - 90 : model.get('bearing') + 90)
          })).addClass('street-view');
        };

        var update = function(){
          $('.street-view').empty().append(render());
        };

        return {
          render: render,
          update: update
        };
      };

      var addDatePickers = function () {
        var $validFrom = $('#ensimmainen_voimassaolopaiva');
        var $validTo = $('#viimeinen_voimassaolopaiva');
        var $inventoryDate = $('#inventointipaiva');

        if ($validFrom.length > 0 && $validTo.length > 0) {
          dateutil.addDependentDatePickers($validFrom, $validTo, $inventoryDate);
        }
      };

      var createTerminalWrapper = function(property) {
        var wrapper = createFormRowDiv();
        wrapper.append(createTerminalLabelElement(property));
        return wrapper;
      };

      var createTerminalLabelElement = function(property) {
        var label = $('<label />').addClass('control-terminal-label').text(property.localizedName);
        if (property.required) {
          label.addClass('required');
        }
        return label;
      };

      var createWrapper = function(property) {
        var wrapper = createFormRowDiv();
        wrapper.append(createLabelElement(property));
        return wrapper;
      };

      var createFormRowDiv = function() {
        return $('<div />').addClass('form-group');
      };

      var createLabelElement = function(property) {
        var label = $('<label />').addClass('control-label').text(property.localizedName);
        if (property.required) {
          label.addClass('required');
        }
        return label;
      };

      var readOnlyHandler = function(property){
        var outer = createFormRowDiv();
        var propertyVal = !_.isEmpty(property.values) ? property.values[0].propertyDisplayValue : '';
        if (property.propertyType === 'read_only_text' && property.publicId != 'yllapitajan_koodi' && property.publicId != 'liitetty_terminaaliin') {
          outer.append($('<p />').addClass('form-control-static asset-log-info').text(property.localizedName + ': ' + propertyVal));
        } else {
          outer.append(createLabelElement(property));
          outer.append($('<p />').addClass('form-control-static').text(propertyVal));
        }
        return outer;
      };

      var textHandler = function(property){
        return createWrapper(property).append(createTextElement(readOnly, property));
      };

      var createTextElement = function(readOnly, property) {
        var element;
        var elementType;

        if (readOnly) {
          elementType = $('<p />').addClass('form-control-static');
          element = elementType;

          if (property.values[0]) {
            element.text(property.values[0].propertyDisplayValue);
          } else {
            element.addClass('undefined').html('Ei m&auml;&auml;ritetty');
          }
        } else {
          elementType = property.propertyType === 'long_text' ?
            $('<textarea />').addClass('form-control') : $('<input type="text"/>').addClass('form-control').attr('id', property.publicId);
          element = elementType.bind('input', function(target){
            if (property.publicId === 'laiturinumero')
              validatePlatformNumberMaxSize(target);
            selectedMassTransitStopModel.setProperty(property.publicId, [{ propertyValue: target.currentTarget.value, propertyDisplayValue: target.currentTarget.value  }], property.propertyType, property.required);
          });

          if(property.values[0]) {
            element.val(property.values[0].propertyDisplayValue);
          }
        }

        return element;
      };

      var singleChoiceHandler = function(property, choices){
        return createWrapper(property).append(createSingleChoiceElement(readOnly, property, choices));
      };

      var createSingleChoiceElement = function(readOnly, property, choices) {
        var element;
        var enumValues = _.find(choices, function(choice){
          return choice.publicId === property.publicId;
        }).values;

        if(!isBusStopMaintainer && property.publicId == 'tietojen_yllapitaja'){
          enumValues = _.filter(enumValues, function(value){
            return value.propertyValue != '2';
          });
          if(selectedMassTransitStopModel.isAdminClassState()){
            enumValues = _.filter(enumValues, function(value){
              return value.propertyValue != '3';
            });
          }
        }

        var isTRReadOnlyEquipment = isTRMassTransitStop && isReadOnlyEquipment(property);

        if (readOnly || isTRReadOnlyEquipment) {
          element = $('<p />').addClass('form-control-static');

          if (property.values && property.values[0]) {
            element.text(property.values[0].propertyDisplayValue);
          } else {
            element.addClass('undefined').html('Ei m&auml;&auml;ritetty');
          }
        } else {
          element = $('<select />').addClass('form-control').change(function(x){
            selectedMassTransitStopModel.setProperty(property.publicId, [{ propertyValue: x.currentTarget.value}], property.propertyType, property.required);
          });

          element = _.reduce(enumValues, function(element, value) {
            var option = $('<option>').text(value.propertyDisplayValue).attr('value', value.propertyValue);
            element.append(option);
            return element;
          }, element);

          if(property.values && property.values[0]) {
            element.val(property.values[0].propertyValue);
          } else {
            element.val('99');
          }
        }

        return element;
      };

      var directionChoiceHandler = function(property){
        if (!readOnly) {
          return createWrapper(property).append(createDirectionChoiceElement(readOnly, property));
        }
      };

      var createDirectionChoiceElement = function(property) {
        var element = $('<button />').addClass('btn btn-secondary btn-block').text('Vaihda suuntaa').click(function(){
          selectedMassTransitStopModel.switchDirection();
          streetViewHandler.update();
        });

        if(!selectedMassTransitStopModel.validateDirectionsForCreation()){
          element.attr("disabled", true);
          element.attr('title','Pysäkin suuntaa ei voi vaihtaa, koska pysäkki on yksisuuntaisella tielinkillä.');
        }

        if(property.values && property.values[0]) {
          validityDirection = property.values[0].propertyValue;
        }

        return element;
      };

      var dateHandler = function(property){
        return createWrapper(property).append(createDateElement(readOnly, property));
      };

      var notificationHandler = function(property) {
        if (property.enabled) {
          var row = createFormRowDiv().addClass('form-notification');
          row.append($('<p />').text(property.text));
          return row;
        } else {
          return [];
        }
      };

      var createDateElement = function(readOnly, property) {
        var element;
        if (readOnly) {
          element = $('<p />').addClass('form-control-static');

          if (property.values[0]) {
            element.text(dateutil.iso8601toFinnish(property.values[0].propertyDisplayValue));
          } else {
            element.addClass('undefined').html('Ei m&auml;&auml;ritetty');
          }
        } else {
          var hideInventoryDate = property.publicId === "inventointipaiva" && !isTRMassTransitStop ? "style='visibility:hidden'":"";
          element = $('<input type="text"' +  hideInventoryDate + '/>').addClass('form-control').attr('id', property.publicId ).on('keyup datechange', _.debounce(function(target){
            // tab press
            if(target.keyCode === 9){
              return;
            }
            var propertyValue = _.isEmpty(target.currentTarget.value) ? '' : dateutil.finnishToIso8601(target.currentTarget.value);
            selectedMassTransitStopModel.setProperty(property.publicId, [{ propertyValue: propertyValue, propertyDisplayValue: propertyValue }], property.propertyType, property.required);
          }, 500));

          if (property.values[0]) {
            element.val(dateutil.iso8601toFinnish(property.values[0].propertyDisplayValue));
          }
        }

        return element;
      };

      var terminalMultiChoiceHandler = function (property) {
        property.localizedName = "Liitetyt Pysakit";
        return createTerminalWrapper(property).append(createTerminalMultiChoiceElement(readOnly, property));
      };

      var createTerminalMultiChoiceElement = function (readOnly, property) {
        var element;
        var enumValues = property.values;

        if (readOnly) {
          element = $('<ul />');
        } else {
          element = $('<div />');
        }

        element.addClass('choice-terminal-group');

        element = _.reduce(enumValues, function (element, value) {
          if (readOnly) {
            if (value.checked) {
              var item = $('<li />');
              item.text(value.propertyDisplayValue);
              element.append(item);
            }
          } else {
            var container = $('<div class="checkbox" />');
            var input = $('<input type="checkbox" />').change(function (evt) {
              value.checked = evt.currentTarget.checked;
              var values = _.chain(enumValues)
                  .filter(function (value) {
                    return value.checked;
                  })
                  .map(function (value) {
                    return {
                      propertyValue: parseInt(value.propertyValue, 10),
                      propertyDisplayValue: value.propertyDisplayValue,
                      checked: true
                    };
                  })
                  .value();
              if (_.isEmpty(values)) {
                values.push({propertyValue: 99});
              }
              selectedMassTransitStopModel.setProperty(property.publicId, values, property.propertyType);
            });

            input.prop('checked', value.checked);

            var label = $('<label />').text(value.propertyDisplayValue);
            element.append(container.append(label.append(input)));
          }

          return element;
        }, element);

        if ((!readOnly) && (applicationModel.getSelectedTool() == 'AddTerminal')) {
          var anyValueChecked = _.some(property.values, function (value) {
            return value.checked === true;
          });
          if (!anyValueChecked) {
            var terminalDefaultValue = {propertyValue: 99};
            selectedMassTransitStopModel.setProperty(property.publicId, terminalDefaultValue, property.propertyType, true);
            selectedMassTransitStopModel.setProperty("pysakin_tyyppi", [{propertyValue: 6, propertyDisplayValue: "", checked: true}], "multiple_choice", true);
          }
        }
        return element;
      };

      var multiChoiceHandler = function(property, choices){
        var choiceValidation = new InvalidCombinationError();
        return createWrapper(property).append(createMultiChoiceElement(readOnly, property, choices).append(choiceValidation.element));
      };

      var createMultiChoiceElement = function(readOnly, property, choices) {
        var element;
        var currentValue = _.cloneDeep(property);
        var enumValues = _.chain(choices)
          .filter(function(choice){
            return choice.publicId === property.publicId;
          })
          .pluck('values')
          .flatten()
          .filter(function(x) { return !(x.propertyValue === '99' || x.propertyValue === '6'); })
          .value();

        if (readOnly) {
          element = $('<ul />');
        } else {
          element = $('<div />');
        }

        element.addClass('choice-group');

        element = _.reduce(enumValues, function(element, value) {
          value.checked = _.any(currentValue.values, function (prop) {
            return prop.propertyValue == value.propertyValue;
          });

          if (readOnly) {
            if (value.checked) {
              var item = $('<li />');
              item.text(value.propertyDisplayValue);

              element.append(item);
            }
          } else {
            var container = $('<div class="checkbox" />');
            var input = $('<input type="checkbox" />').change(function (evt) {
              value.checked = evt.currentTarget.checked;
              var values = _.chain(enumValues)
                .filter(function (value) {
                  return value.checked;
                })
                .map(function (value) {
                  return { propertyValue: parseInt(value.propertyValue, 10), propertyDisplayValue: value.propertyDisplayValue, checked: true };
                })
                .value();
              if (_.isEmpty(values)) { values.push({ propertyValue: 99 }); }
              selectedMassTransitStopModel.setProperty(property.publicId, values, property.propertyType);
            });

            input.prop('checked', value.checked);

            var label = $('<label />').text(value.propertyDisplayValue);
            element.append(container.append(label.append(input)));
          }

          return element;
        }, element);

        return element;
      };

      var sortAndFilterProperties = function(properties) {
        var propertyOrdering = [
          'lisatty_jarjestelmaan',
          'muokattu_viimeksi',
          'nimi_suomeksi',
          'nimi_ruotsiksi',
          'osoite_suomeksi',
          'osoite_ruotsiksi',
          'tietojen_yllapitaja',
          'yllapitajan_tunnus',
          'yllapitajan_koodi',
          'matkustajatunnus',
          'laiturinumero', //Platform Number
          'liitetty_terminaaliin',
          'maastokoordinaatti_x',
          'maastokoordinaatti_y',
          'maastokoordinaatti_z',
          'liikennointisuunta',
          'vaikutussuunta',
          'liikennointisuuntima',
          'ensimmainen_voimassaolopaiva',//begin date
          'viimeinen_voimassaolopaiva',//end date
          'inventointipaiva',//Inventory date
          'pysakin_tyyppi',
          'korotettu',
          'katos',
          'mainoskatos',
          'roska_astia',
          'pyorateline',
          'valaistus',
          'penkki',
          'aikataulu',
          'sahkoinen_aikataulunaytto',
          'esteettomyys_liikuntarajoitteiselle',
          'saattomahdollisuus_henkiloautolla',
          'liityntapysakointipaikkojen_maara',
          'liityntapysakoinnin_lisatiedot',
          'pysakin_omistaja',
          'palauteosoite',
          'lisatiedot'];

        return _.sortBy(properties, function(property) {
          return _.indexOf(propertyOrdering, property.publicId);
        }).filter(function(property){
          return _.indexOf(propertyOrdering, property.publicId) >= 0;
        });
      };

      var sortAndFilterTerminalProperties = function(properties) {
        var propertyOrdering = [
          'lisatty_jarjestelmaan',
          'muokattu_viimeksi',
          'nimi_suomeksi',
          'nimi_ruotsiksi',
          'liitetyt_pysakit'];

        return _.sortBy(properties, function(property) {
          return _.indexOf(propertyOrdering, property.publicId);
        }).filter(function(property){
          return _.indexOf(propertyOrdering, property.publicId) >= 0;
        });
      };

      var floatingStatus = function(selectedAssetModel) {
        var text;
        switch (selectedMassTransitStopModel.getFloatingReason()){
          case '2': //NoRoadLinkFound
          case '4': //DistanceToRoad
          case '5': //NoReferencePointForMValue
          case '6': //DirectionNotMatch
            text = 'Kadun tai tien geometria on muuttunut...';
            break;
          case '1': //RoadOwnerChanged
            text = 'Kadun tai tien hallinnollinen luokka on muuttunut. Tarkista ja korjaa pysäkin sijainti.';
            break;
          case '3': //DifferentMunicipalityCode
            text = 'Kadun tai tien omistava kunta on vaihtunut. Tarkista ja korjaa pysäkin sijainti.';
            break;
          case '7': //TerminalChildless
              text = 'Kyseisellä terminaalipysäkillä ei ole yhtään liitettyä pysäkkiä.';
              break;
          default:
            text = 'Kadun tai tien geometria on muuttunut, tarkista ja korjaa pysäkin sijainti.';
        }

        return [{
          propertyType: 'notification',
          enabled: selectedMassTransitStopModel.get('floating'),
          text: text
        }];
      };

      var getAssetForm = function() {
        var allProperties = selectedMassTransitStopModel.getProperties();
        var properties;

        if (isTerminalBusStop) {
          properties = sortAndFilterTerminalProperties(allProperties);
        } else {
          properties = sortAndFilterProperties(allProperties);
          setIsTRMassTransitStopValue(allProperties); // allProperties contains linkin_hallinnollinen_luokka property
          disableFormIfTRMassTransitStopHasEndDate(properties);
        }

        var contents = _.take(properties, 2)
          .concat(floatingStatus(selectedMassTransitStopModel))
          .concat(_.drop(properties, 2));
        var components =_.map(contents, function(feature){
          feature.localizedName = window.localizedStrings[feature.publicId];
          var propertyType = feature.propertyType;
          if ((propertyType === "text" && feature.publicId != 'inventointipaiva') || propertyType === "long_text" ) {
            return textHandler(feature);
          } else if (propertyType === "read_only_text" || propertyType === 'read-only') {
            return readOnlyHandler(feature);
          } else if (feature.publicId === 'vaikutussuunta') {
            return directionChoiceHandler(feature);
          } else if (propertyType === "single_choice") {
            return singleChoiceHandler(feature, enumeratedPropertyValues);
          } else if (feature.propertyType === "multiple_choice" && isTerminalBusStop) {
            return terminalMultiChoiceHandler(feature);
          } else if (feature.propertyType === "multiple_choice") {
            return multiChoiceHandler(feature, enumeratedPropertyValues);
          } else if (propertyType === "date" || feature.publicId == 'inventointipaiva') {
            return dateHandler(feature);
          } else if (propertyType === 'notification') {
            return notificationHandler(feature);
          }  else {
            feature.propertyValue = 'Ei toteutettu';
            return $(featureDataTemplateNA(feature));
          }
        });

        var assetForm = $('<div />').append(components);

        if(selectedMassTransitStopModel.getId() && !readOnly) {
          var stopDeleteButton = MStopDeletebutton(readOnly);
          assetForm.append(stopDeleteButton);
        }

        return assetForm;
      };

      function setIsTRMassTransitStopValue(properties) {
        var isAdministratorELY = selectedMassTransitStopModel.isAdministratorELY(properties);
        var isAdministratorHSL = selectedMassTransitStopModel.isAdministratorHSL(properties);
        var isAdminClassState = selectedMassTransitStopModel.isAdminClassState(properties);

        isTRMassTransitStop = isAdministratorELY || (isAdministratorHSL && isAdminClassState);
      }

      function disableFormIfTRMassTransitStopHasEndDate(properties) {

        var expiryDate = selectedMassTransitStopModel.getEndDate();
        var todaysDate = moment().format('YYYY-MM-DD');

        var isBusStopExpired = _.some(properties, function (property) {
          return todaysDate > expiryDate && property.publicId === 'viimeinen_voimassaolopaiva' &&
            _.some(property.values, function (value) {
              return value.propertyValue !== "";
            });
        });

        if (isBusStopExpired && isTRMassTransitStop)  {
          readOnly = true;
        }

        if(!isBusStopExpired && isTRMassTransitStop && !isBusStopMaintainer){
          readOnly = true;
        }

      }

      function streetViewTemplates(longi,lati,heading) {
            var streetViewTemplate  = _.template(
                '<a target="_blank" href="//maps.google.com/?ll=<%= wgs84Y %>,<%= wgs84X %>&cbll=<%= wgs84Y %>,<%= wgs84X %>&cbp=12,<%= heading %>.09,,0,5&layer=c&t=m">' +
                '<img id="streetViewTemplatesgooglestreetview" alt="Google StreetView-n&auml;kym&auml;" src="">' +
                '</a>');
        backend.getMassTransitStopStreetViewUrl(lati,longi,heading);
        return streetViewTemplate;
      }

      var featureDataTemplateNA = _.template('<div class="formAttributeContentRow">' +
        '<div class="formLabels"><%= localizedName %></div>' +
        '<div class="featureAttributeNA"><%= propertyValue %></div>' +
        '</div>');

      var closeAsset = function() {
        $("#feature-attributes").html('');
        dateutil.removeDatePickersFromDom();
      };

      var renderLinktoWorkList = function renderLinktoWorkList() {
        var notRendered = !$('#asset-work-list-link').length;
        if(notRendered) {
          $('#information-content').append('' +
            '<div class="form form-horizontal">' +
            '<a id="asset-work-list-link" class="floating-stops" href="#work-list/massTransitStop">Geometrian ulkopuolelle jääneet pysäkit</a>' +
            '</div>');
        }
      };

      var isBusStopMaintainer = false;

      var bindExternalEventHandlers = function () {
        eventbus.on('roles:fetched', function (roles) {
          if (_.contains(roles, 'busStopMaintainer')) {
            isBusStopMaintainer = true;
          }
        });
      };

      bindExternalEventHandlers();

      var controlledByTR = function () {
        if(applicationModel.isReadOnly()){
          return true;
        }

        var properties = selectedMassTransitStopModel.getProperties();

        var owner = _.find(properties, function(property) {
          return property.publicId === "tietojen_yllapitaja"; });

        var condition = typeof owner != 'undefined' &&
          typeof owner.values != 'undefined' &&  owner.values > 0 && _.contains(_.map(owner.values, function (value) {
          return value.propertyValue;
        }), "2");

         if(isBusStopMaintainer === true && condition) {
           eventbus.trigger('application:controledTR',condition);
           return false;
         } else {
           eventbus.trigger('application:controledTR',false);
           return condition;
         }
      };

      var isReadOnlyEquipment = function(property) {
        return property.publicId === 'aikataulu' || property.publicId === 'roska_astia' || property.publicId === 'pyorateline' || property.publicId === 'valaistus' || property.publicId === 'penkki';
      };

      eventbus.on('asset:modified', function(){
        renderAssetForm();
      });

      eventbus.on('layer:selected application:initialized', function() {
        if(applicationModel.getSelectedLayer() === 'massTransitStop') {
          renderLinktoWorkList();
        }
        else {
          $('#asset-work-list-link').parent().remove();
        }
      });

      eventbus.on('application:readOnly', function(data) {
        if(selectedMassTransitStopModel.getId() !== undefined) {
          readOnly = controlledByTR();
        } else {
          readOnly = data;
        }
      });

      eventbus.on('asset:closed', closeAsset);

      eventbus.on('enumeratedPropertyValues:fetched', function(values) {
        enumeratedPropertyValues = values;
      });

      eventbus.on('asset:moved', function() {
        streetViewHandler.update();
      });

      eventbus.on('textElementValue:set', function (newTextValue, textId) {
        $('input[id='+textId+']').val(newTextValue);
      });

      eventbus.on('assetPropertyValue:changed', function (event) {
        var property = event.propertyData;

        // Handle form reload after administrator change
        if (property.publicId === 'tietojen_yllapitaja' && (_.find(property.values, function (value) {return value.propertyValue == '2';}))) {
          isTRMassTransitStop = true;
          property.propertyType = "single_choice";
          renderAssetForm();
        }
        if (property.publicId === 'tietojen_yllapitaja' && (_.find(property.values, function (value) {return value.propertyValue != '2';}))) {
          isTRMassTransitStop = false;
          property.propertyType = "single_choice";
          renderAssetForm();
        }
        // Prevent getAssetForm() branching to 'Ei toteutettu'
        if (isReadOnlyEquipment(property)) {
          property.propertyType = "single_choice";
        }

        if (property.publicId === 'tietojen_yllapitaja' && isTRMassTransitStop && event.id){
          new GenericConfirmPopup(
              'Olet siirtämässä pysäkin ELYn ylläpitoon! Huomioithan, että osa pysäkin varustetiedoista saattaa kadota tallennuksen yhteydessä.',
              {type: 'alert'});
        }
      });

      eventbus.on('terminalBusStop:selected', function(value) {
        isTerminalBusStop = value;
      });
      backend.getEnumeratedPropertyValues();
    }
  };
})(this);

