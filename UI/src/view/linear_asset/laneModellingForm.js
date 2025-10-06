(function(root) {
  root.LaneModellingForm = function (formStructure) {
    DynamicAssetForm.call(this, formStructure);
    var dynamicFieldParent = this.DynamicField;
    var self = this;
    var currentFormStructure;
    var defaultFormStructure;
    var isAddByRoadAddressActive = false;
    var lanesAssets;

    var editingRestrictions = new EditingRestrictions();
    var typeId = 450;

  self.DynamicField = function (fieldSettings, isDisabled) {
    dynamicFieldParent.call(this, fieldSettings, isDisabled);
    var me = this;

    me.setSelectedValue = function(setValue, getValue, sideCode){
      var currentPropertyValue = me.hasValue() ?  me.getPropertyValue() : (me.hasDefaultValue() ? me.getPropertyDefaultValue() : me.emptyPropertyValue());

      var editableRoadAddressPublicIds = _.map(roadAddressFormStructure.fields, 'publicId');

      if(isAddByRoadAddressActive && _.includes(editableRoadAddressPublicIds, currentPropertyValue.publicId)){
        lanesAssets.setAddressesValues(currentPropertyValue);
      }else{
        var laneNumber = lanesAssets.getCurrentLaneNumber();
        var properties = _.filter(getValue(laneNumber, sideCode), function(property){ return property.publicId !== currentPropertyValue.publicId; });
        properties.push(currentPropertyValue);
        setValue(laneNumber, {properties: properties}, sideCode);
      }
    };
  };

    self.DateField = function(assetTypeConfiguration, field, isDisabled){
      self.DynamicField.call(this, field, isDisabled);
      var me = this;

      me.editModeRender = function (fieldValue, sideCode, setValue, getValue) {
        var someValue = _.head(fieldValue, function(values) { return values.value ; });
        var value = _.isEmpty(someValue) ? (fieldValue.defaultValue ? fieldValue.defaultValue : '') : someValue.value;

        me.element = $('<div class="form-group"></div>');
        me.element.append($('<label class="control-label"></label>').addClass(me.required() ? 'required' : '').text(field.label));

        var inputLabel = $('<input type="text" ' + me.disabled() + '/>').addClass('form-control')
          .attr('id', field.publicId + sideCode)
          .attr('required', me.required())
          .attr('placeholder',"pp.kk.vvvv")
          .attr('fieldType', fieldValue.type)
          .attr('value', value )
          .attr('name', field.publicId).on('keyup datechange', _.debounce(function (target) {
            // tab press
            if (target.keyCode === 9) {
              return;
            }
            me.setSelectedValue(setValue, getValue, sideCode, false);
          }, 500));

        if (!isDisabled && me.hasDefaultValue() && !value)
          me.setSelectedValue(setValue, getValue, sideCode, true);

        me.element.append(inputLabel);
        return me.element;
      };
    };

    //need to put this here to override the DateField
    self.dynamicFormFields = [
      {name: 'long_text', fieldType: self.TextualLongField},
      {name: 'single_choice', fieldType: self.SingleChoiceField},
      {name: 'date', fieldType: self.DateField},
      {name: 'multiple_choice', fieldType: self.MultiSelectField},
      {name: 'integer', fieldType: self.IntegerField},
      {name: 'number', fieldType: self.NumericalField},
      {name: 'text', fieldType: self.TextualField},
      {name: 'checkbox', fieldType: self.CheckboxField},
      {name: 'read_only_number', fieldType: self.ReadOnlyFields},
      {name: 'read_only_text', fieldType: self.ReadOnlyFields},
      {name: 'time_period', fieldType: self.TimePeriodField},
      {name: 'date_period', fieldType: self.DatePeriodField},
      {name: 'hidden_read_only_number', fieldType: self.HiddenReadOnlyFields}
    ];

  var mainLaneFormStructure = {
    fields : [
      {label: 'Tien numero', type: 'read_only_number', publicId: "roadNumber", weight: 1, cssClass: 'road-number'},
      {label: 'Tieosanumero', type: 'read_only_number', publicId: "roadPartNumber", weight: 2, cssClass: 'road-part-number'},
      {label: 'Ajorata', type: 'read_only_number', publicId: "track", weight: 3, cssClass: 'track'},
      {label: 'Alkuetäisyys', type: 'read_only_number', publicId: "startAddrMValue", weight: 4, cssClass: 'start-addr-m'},
      {label: 'Loppuetäisyys', type: 'read_only_number', publicId: "endAddrMValue", weight: 5, cssClass: 'end-addr-m'},
      {label: 'Pituus', type: 'read_only_number', publicId: "addrLenght", weight: 6, cssClass: 'addr-lenght'},
      {label: 'Hallinnollinen Luokka', type: 'read_only_text', publicId: "administrativeClass", weight: 7, cssClass: 'admin-class'},
      {label: 'Kaista', type: 'read_only_number', publicId: "lane_code", weight: 12, cssClass: 'lane-code'},
      {
        label: 'Kaistan tyyppi', required: 'required', type: 'single_choice', publicId: "lane_type", defaultValue: "1", weight: 13,
        values: [
          {id: 1, label: 'Pääkaista'}
          ]
      },
      {
        label: 'Alkupvm', type: 'date', publicId: "start_date", weight: 14, required: true
      }
    ]
  };

  var roadAddressFormStructure = {
    fields : [
      {label: 'Osa', required: 'required', type: 'number', publicId: "startRoadPartNumber", weight: 8},
      {label: 'Etäisyys', required: 'required', type: 'number', publicId: "startDistance", weight: 9},
      {label: 'Osa', required: 'required', type: 'number', publicId: "endRoadPartNumber", weight: 10},
      {label: 'Etäisyys', required: 'required', type: 'number', publicId: "endDistance", weight: 11}
    ]
  };

    var administrativeClassValues = {
      1: 'Valtion omistama',
      2: 'Kunnan omistama',
      3: 'Yksityisen omistama',
      99: 'Tuntematon'
    };

    function reloadForm(rootElement){
      dateutil.removeDatePickersFromDom();
      rootElement.find('#feature-attributes-header').html(self.renderHeader(self._assetTypeConfiguration.selectedLinearAsset));
      rootElement.find('#feature-attributes-form').html(self.renderForm(self._assetTypeConfiguration.selectedLinearAsset, false));
      rootElement.find('#feature-attributes-form').prepend(self.renderPreview(self._assetTypeConfiguration.selectedLinearAsset));
      rootElement.find('#feature-attributes-form').append(self.renderLaneButtons(self._assetTypeConfiguration.selectedLinearAsset));
      rootElement.find('#feature-attributes-footer').html(self.renderFooter(self._assetTypeConfiguration.selectedLinearAsset));
    }

    function hasRoadAddressInfo(selectedLinks) {
      var roadNumber = Property.pickUniqueValues(selectedLinks, 'roadNumber');
      var roadPartNumber = Property.pickUniqueValues(selectedLinks, 'roadPartNumber');
      var startAddrMValue = Property.pickUniqueValues(selectedLinks, 'startAddrMValue');
      var endAddrMValue = Property.pickUniqueValues(selectedLinks, 'endAddrMValue');
      var info = [roadNumber, roadPartNumber, startAddrMValue, endAddrMValue];

      if (!_.some(info, function(field) { return _.head(field) === undefined; })) return true;
      return false;
    }

    function getLaneCodeValue(lane) {
      return _.head(_.find(lane.properties, {'publicId': 'lane_code'}).values).value;
    }

    var AvailableForms = function(){
      var formFields = {};

      this.addField = function(field, sideCode){
        if(!formFields['sidecode_'+sideCode])
          formFields['sidecode_'+sideCode] = [];
        formFields['sidecode_'+sideCode].push(field);
      };

      this.getAllFields = function(){
        return _.flatten(_.map(formFields, function(form) {
          return form;
        }));
      };

      this.removeFields = function(sideCode){
        if(!formFields['sidecode_'+sideCode])
          throw Error("The form of the sidecode " + sideCode + " doesn't exist");

        formFields['sidecode_'+sideCode] = [];
      };
    };

    var forms = new AvailableForms();

    self.initialize = function(assetTypeConfiguration, feedbackModel){
      var rootElement = $('#feature-attributes');
      self._assetTypeConfiguration = assetTypeConfiguration;
      lanesAssets = self._assetTypeConfiguration.selectedLinearAsset;

      eventbus.on('laneModellingForm: reload', function() {
        reloadForm($('#feature-attributes'));
      });

      function setInitialForm() {
        defaultFormStructure = formStructure;
        currentFormStructure = mainLaneFormStructure;
        isAddByRoadAddressActive = false;
        lanesAssets.setCurrentLane(parseInt(_.min(_.map(self._assetTypeConfiguration.selectedLinearAsset.get(), function (lane) {
          return _.head(_.find(lane.properties, function (property) {
            return property.publicId == "lane_code";
          }).values).value;
        }))));
      }

      new FeedbackDataTool(feedbackModel, assetTypeConfiguration.layerName, assetTypeConfiguration.authorizationPolicy, assetTypeConfiguration.singleElementEventCategory);

      eventbus.on(self.events('selected'), function () {
        setInitialForm();
        reloadForm(rootElement);
      });

      eventbus.on(self.events('unselect', 'cancelled') + 'closeForm', function() {
        rootElement.find('#feature-attributes-header').empty();
        rootElement.find('#feature-attributes-form').empty();
        rootElement.find('#feature-attributes-footer').empty();
        dateutil.removeDatePickersFromDom();
      });

      eventbus.on('layer:selected', function(layer) {
        if(self._assetTypeConfiguration.layerName === layer){
          $('ul[class=information-content]').empty();

          renderLinktoLaneWorkList();
          renderLinktoAutoProcessedLanesWorkList();
        }
      });

      eventbus.on('application:readOnly', function(){
        if(self._assetTypeConfiguration.layerName ===  applicationModel.getSelectedLayer() && !_.isEmpty(self._assetTypeConfiguration.selectedLinearAsset.get())) {
          setInitialForm();
          reloadForm(rootElement);
        }
      });
    };

    this.userInformationLog = function() {
      var selectedAsset = self._assetTypeConfiguration.selectedLinearAsset;
      var authorizationPolicy = self._assetTypeConfiguration.authorizationPolicy;

      var hasMunicipality = function(linearAsset) {
        return _.some(linearAsset.get(), function(asset){
          return authorizationPolicy.hasRightsInMunicipality(asset.municipalityCode);
        });
      };

      var limitedRights = 'Käyttöoikeudet eivät riitä kohteen muokkaamiseen. Voit muokata kohteita vain oman kuntasi alueelta.';
      var noRights = 'Käyttöoikeudet eivät riitä kohteen muokkaamiseen.';
      var stateRoadEditingRestricted = 'Kohteiden muokkaus on estetty, koska kohteita ylläpidetään Tievelho-tietojärjestelmässä.';
      var municipalityRoadEditingRestricted = 'Kunnan kohteiden muokkaus on estetty, koska kohteita ylläpidetään kunnan omassa tietojärjestelmässä.';
      var message = '';

      if (editingRestrictions.hasStateRestriction(selectedAsset.get(), typeId)) {
        message = stateRoadEditingRestricted;
      } else if(editingRestrictions.hasMunicipalityRestriction(selectedAsset.get(), typeId)) {
        message = municipalityRoadEditingRestricted;
      } else if(!authorizationPolicy.isOperator() && (authorizationPolicy.isMunicipalityMaintainer() || authorizationPolicy.isElyMaintainer()) && !hasMunicipality(selectedAsset)) {
        message = limitedRights;
      } else if(!this.checkAuthorizationPolicy(selectedAsset))
        message = noRights;

      if(message) {
        return '' +
            '<div class="form-group user-information">' +
            '<p class="form-control-static user-log-info">' + message + '</p>' +
            '</div>';
      } else
        return '';
    };

    var renderLinktoLaneWorkList = function renderLinktoWorkList() {
        $('ul[class=information-content]').append('' +
            '<li><button id="work-list-link-lanes" class="lane-work-list btn btn-tertiary" onclick=location.href="#work-list/laneChecklist">Tarkistettavien kaistojen lista</button></li>');
    };

    var renderLinktoAutoProcessedLanesWorkList = function renderLinktoWorkList() {
      $('ul[class=information-content]').append('' +
          '<li><button id="work-list-link-auto-processed-lanes" class="auto-processed-lane-work-list btn btn-tertiary" onclick=location.href="#work-list/autoProcessedLanesWorkList">Automaattisesti käsiteltyjen kaistojen lista</button></li>');
    };

    self.renderAvailableFormElements = function(asset, isReadOnly, sideCode, setAsset, getValue, isDisabled, alreadyRendered) {
      function infoLabel(publicId) {
        var info;
        var infoElement;

        switch(publicId) {
          case "roadNumber":
            info = 'Valinnan tieosoitetiedot:';
            break;
          case "startRoadPartNumber":
            info = "Kaista alkaa:";
            break;
          case "endRoadPartNumber":
            info = 'Kaista loppuu:';
            break;
          case "lane_code":
            info = 'Kaistan tiedot:';
            break;
        }

        if(info)
          infoElement = $('<div class="form-group"><label class="info-label">' + info + '</label></div>');

        return infoElement;
      }

      if (alreadyRendered)
        forms.removeFields(sideCode);
      var fieldGroupElement = $('<div class = "input-unit-combination lane-' + lanesAssets.getCurrentLaneNumber() + '" >');

      var formFields = currentFormStructure.fields;
      if (isAddByRoadAddressActive)
        formFields = formFields.concat(roadAddressFormStructure.fields);

      _.each(_.sortBy(formFields, function (field) {
        return field.weight;
      }), function (field) {
        var fieldValues = [];
        if (asset.properties) {
          var existingProperty = _.find(asset.properties, function (property) {
            return property.publicId === field.publicId;
          });
          if (_.isUndefined(existingProperty)) {
            var value;
            var roadPartNumber;
            var selectedLinks = asset.selectedLinks;
            var publicId = field.publicId;
            var hasRoadAddress = hasRoadAddressInfo(selectedLinks);

            // If lane has road address info and is cut, use lane's calculated road address start and end m-values
            // Multiple lane selection for cut lanes is not supported currently and thus chaining road address values for cut lanes is not needed.
            var assetHasRoadAddressAndCut = hasRoadAddress && lanesAssets.isLaneFullLinkLength(asset);
            if(assetHasRoadAddressAndCut) lanesAssets.getAddressValuesForCutLane(asset);

            switch (publicId) {
              case "roadNumber":
              case "roadPartNumber":
                if (hasRoadAddress) {
                  value = Property.pickUniqueValues(selectedLinks, publicId).join(', ');
                }
                break;
              case "startAddrMValue":
                if (assetHasRoadAddressAndCut) {
                  value = asset.startAddrMValue;
                }
                else if (hasRoadAddress) {
                  roadPartNumber = Math.min.apply(null, _.compact(Property.pickUniqueValues(selectedLinks, 'roadPartNumber')));
                  value = Math.min.apply(null, Property.chainValuesByPublicIdAndRoadPartNumber(selectedLinks, roadPartNumber, publicId));
                }
                break;
              case "endAddrMValue":
                if (assetHasRoadAddressAndCut) {
                  value = asset.endAddrMValue;
                }
                else if (hasRoadAddress) {
                  roadPartNumber = Math.max.apply(null, _.compact(Property.pickUniqueValues(selectedLinks, 'roadPartNumber')));
                  value = Math.max.apply(null, Property.chainValuesByPublicIdAndRoadPartNumber(selectedLinks, roadPartNumber, publicId));
                }
                break;
              case "addrLenght":
                if (assetHasRoadAddressAndCut) {
                  value = asset.endAddrMValue - asset.startAddrMValue;
                }
                else if (hasRoadAddress) {
                  var startRoadPartNumber = Math.min.apply(null, _.compact(Property.pickUniqueValues(selectedLinks, 'roadPartNumber')));
                  var endRoadPartNumber = Math.max.apply(null, _.compact(Property.pickUniqueValues(selectedLinks, 'roadPartNumber')));
                  var selectionStartAddrM = Math.min.apply(null, Property.chainValuesByPublicIdAndRoadPartNumber(selectedLinks, startRoadPartNumber, "startAddrMValue"));
                  var selectionEndAddrM = Math.max.apply(null, Property.chainValuesByPublicIdAndRoadPartNumber(selectedLinks, endRoadPartNumber, "endAddrMValue"));
                  value = selectionEndAddrM - selectionStartAddrM;
                }
                break;
              case "administrativeClass":
                value = administrativeClassValues[_.head(selectedLinks)[publicId]];
                break;
              default:
                value = _.head(selectedLinks)[publicId];
            }

            existingProperty = _.isUndefined(value) ? value : {values:[{value: value}]};
          }
          if (!_.isUndefined(existingProperty) && !_.isNull(existingProperty))
            fieldValues = existingProperty.values;
        }
        var dynamicField = _.find(self.dynamicFormFields, function (availableFieldType) {
          return availableFieldType.name === field.type;
        });
        var fieldType = new dynamicField.fieldType(self._assetTypeConfiguration, field, isDisabled);
        forms.addField(fieldType, sideCode);
        var fieldElement = isReadOnly ? fieldType.viewModeRender(field, fieldValues) : fieldType.editModeRender(fieldValues, sideCode, setAsset, getValue);

        var additionalElement = infoLabel(field.publicId);
        fieldGroupElement.append(additionalElement).append(fieldElement);

      });

      return fieldGroupElement;
    };

    self.createHeaderElement = function(selectedAsset) {
      var title = function () {
        var asset = selectedAsset.getCurrentLane();

        return asset.id !== 0 && !selectedAsset.isSplit() ?
            '<span>Kohteen ID: ' + asset.id + '</span>' :
            '<span>' + self._assetTypeConfiguration.title + '</span>';
      };

      return $(title());
    };

    function separateOddEvenNumbers(numbers){
      return _.partition(_.sortBy(numbers), function(number){return number % 2 === 0;});
    }

    var createPreviewHeaderElement = function(laneNumbers) {
      var createNumber = function (number) {
        var previewButton = $('<td class="preview-lane selectable">' + number + '</td>').click(function() {
          var laneNumber = parseInt(number);
          lanesAssets.setCurrentLane(laneNumber);

          if(laneNumber == 1){
            currentFormStructure = mainLaneFormStructure;
          }else{
            newLaneStructure(laneNumber);
          }
          reloadForm($('#feature-attributes'));
        });

        if(number == lanesAssets.getCurrentLaneNumber()){
          return previewButton.addClass("highlight-lane");
        }else{
          return previewButton.addClass("not-highlight-lane");
        }
      };

      var numbersPartitioned = separateOddEvenNumbers(laneNumbers);
      var odd = _.last(numbersPartitioned);
      var even = _.head(numbersPartitioned);

      var preview = function () {
        var previewList = $('<table class="preview">');

        var numberHeaders =$('<tr class="number-header">').append(_.map(_.reverse(even).concat(odd), function (number) {
          return $('<th>' + (number == '1' ? 'Pääkaista' : 'Lisäkaista') + '</th>');
        }));

        var oddListElements = _.map(odd, function (number) {
              return createNumber(number);
            });

        var evenListElements = _.map(even, function (number) {
          return createNumber(number);
        });

        return $('<div class="preview-div">').append(previewList.append(numberHeaders).append($('<tr>').append(evenListElements).append(oddListElements))).append('<hr class="form-break">');
      };

      return preview();
    };

    function innerLanesHaveEndDates(selectedAsset, nextLaneNumber){
      var selection = selectedAsset.selection;
      var allSelectedLanes = _.flatMap(selection, function(selectedLane) {
        if(selectedLane.id === 0) return selectedLane;
        else return selectedAsset.getSelectedLanes(selectedLane);
      });

      var sameSideLanes = _.filter(allSelectedLanes, function (lane) {
        var laneCode = getLaneCodeValue(lane);
        return laneCode !== 1 && ((laneCode % 2) === (nextLaneNumber % 2));
      });

      return _.some(sameSideLanes, function(lane) {
        var endDateProp = _.find(lane.properties, function(prop) {
          return prop.publicId === 'end_date';
        });
        var laneCode = getLaneCodeValue(lane);
        return laneCode < nextLaneNumber && endDateProp !== undefined;
      });
    }

    var createLaneButtons = function(selectedAsset){
      var laneAssets = selectedAsset.get();
      var laneNumbers = _.map(laneAssets, function (lane){
        return _.head(_.find(lane.properties, function (property) {
          return property.publicId == "lane_code";
        }).values).value;
      });

      var numbersPartitioned = separateOddEvenNumbers(laneNumbers);
      var odd = _.last(numbersPartitioned);
      var even = _.head(numbersPartitioned);

      var endDatePresentAlertPopUpOptions = {
        type: "alert",
        yesButtonLbl: 'Ok',
      };
      var endDatePresentAlertMessage = "Huomioi, että yhdellä tai useammalla valittujen tielinkkien sisemmistä kaistoista on asetettu loppupäivänmäärä";

      var addLeftLane = $('<li>').append($('<button class="btn btn-secondary add-to-left">Lisää kaista vasemmalle puolelle</button>').click(function() {
        var nextLaneNumber;
        if(_.isEmpty(even)){
          nextLaneNumber = 2;
        }else{
          nextLaneNumber = parseInt(_.max(even)) + 2;
        }
        if(innerLanesHaveEndDates(selectedAsset, nextLaneNumber)) {
          GenericConfirmPopup(endDatePresentAlertMessage, endDatePresentAlertPopUpOptions);
        }
        selectedAsset.setNewLane(nextLaneNumber);
        selectedAsset.setCurrentLane(nextLaneNumber);
        newLaneStructure(nextLaneNumber);

        reloadForm($('#feature-attributes'));
      }).prop("disabled", !_.isEmpty(even) && _.max(even) == 8 || selectedAsset.isPromotionDirty()));

      var addRightLane = $('<li>').append($('<button class="btn btn-secondary add-to-right">Lisää kaista oikealle puolelle</button>').click(function() {
        var nextLaneNumber = parseInt(_.max(odd)) + 2;
        if(innerLanesHaveEndDates(selectedAsset, nextLaneNumber)) {
          GenericConfirmPopup(endDatePresentAlertMessage, endDatePresentAlertPopUpOptions);
        }
        selectedAsset.setNewLane(nextLaneNumber);
        selectedAsset.setCurrentLane(nextLaneNumber);
        newLaneStructure(nextLaneNumber);

        reloadForm($('#feature-attributes'));
      }).prop("disabled", !_.isEmpty(odd) && _.max(odd) == 9 || selectedAsset.isPromotionDirty()));

      var selectedRoadLink = selectedAsset.getSelectedRoadLink();
      var addByRoadAddress = isAddByRoadAddressActive ? $('<li>') : $('<li>').append($('<button class="btn btn-secondary add-by-road-address">Lisää kaista tieosoitteen avulla</button>').click(function() {
        isAddByRoadAddressActive = true;
        selectedAsset.setInitialRoadFields();

        reloadForm($('#feature-attributes'));
      }).prop("disabled", _.isUndefined(selectedRoadLink.roadNumber) || _.isUndefined(selectedRoadLink.roadPartNumber) ||
        _.isUndefined(selectedRoadLink.startAddrMValue) || selectedRoadLink.administrativeClass != 1 || selectedAsset.configurationIsCut() ||
        !selectedAsset.haveNewLane() || _.filter(laneAssets, function (lane) {return lane.id !==0;}).length > 1));

      return $('<ul class="list-lane-buttons">').append(addByRoadAddress).append(addLeftLane).append(addRightLane);
    };

    self.renderPreview = function(selectedAsset) {
      var laneNumbers = _.map(selectedAsset.get(), function (lane){
        return _.head(_.find(lane.properties, function (property) {
          return property.publicId == "lane_code";
        }).values).value;
      });

      return createPreviewHeaderElement(_.uniq(laneNumbers));
    };

    self.renderLaneButtons = function(selectedAsset) {
      var isReadOnly = self._isReadOnly(selectedAsset);
      var laneButtons = createLaneButtons(selectedAsset);
      //Hide or show elements depending on the readonly mode
      laneButtons.toggle(!isReadOnly);

      return laneButtons;
    };

    self.renderForm = function (selectedAsset, isDisabled) {
      forms = new AvailableForms();
      var isReadOnly = self._isReadOnly(selectedAsset);
      var asset = _.filter(selectedAsset.get(), function (lane){
        return _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && _.head(property.values).value == selectedAsset.getCurrentLaneNumber();
        });
      });

      asset = _.sortBy(asset, function (lane) {
        return lane.marker;
      });

      var body = createBodyElement(selectedAsset.getCurrentLane());

      if(selectedAsset.isSplit()) {

        _.forEach(asset, function (lane) {
          renderFormElements(lane, isReadOnly, lane.marker, selectedAsset.setValue, selectedAsset.getValue, false, body);
          if(!isReadOnly) renderExpireAndDeleteButtonsElement(selectedAsset, body, lane.marker);
          body.find('.form').append('<hr class="form-break">');
        });

      }else{
        renderFormElements(asset[0], isReadOnly, '', selectedAsset.setValue, selectedAsset.getValue, isDisabled, body);

        if(!isReadOnly)
          renderExpireAndDeleteButtonsElement(selectedAsset, body);

        body.find('.form').append('<hr class="form-break">');
      }

      //Hide or show elements depending on the readonly mode
      self.toggleBodyElements(body, isReadOnly);
      return body;
    };

    function renderExpireAndDeleteButtonsElement(selectedAsset, body, sidecode){
      var currentLaneNumber = selectedAsset.getCurrentLaneNumber();

      var deleteLane = $('<button class="btn btn-secondary lane-button delete-lane">Poista kaista</button>').click(function() {
        selectedAsset.removeLane(currentLaneNumber, sidecode);
        prepareLanesStructure();
        reloadForm($('#feature-attributes'));
      });

      var expireLane = $('<button class="btn btn-secondary lane-button expire-lane">Päätä Kaista</button>').click(function() {
        var confirmationPopUpOptions = {
          type: "confirm",
          yesButtonLbl: 'Tallenna',
          noButtonLbl: 'Peruuta',
          successCallback: function() {
            selectedAsset.expireLane(currentLaneNumber, sidecode);
            prepareLanesStructure();
            reloadForm($('#feature-attributes'));
          }
        };
        var confirmationMessage = "Haluatko varmasti päättää kaistan?";

        GenericConfirmPopup(confirmationMessage, confirmationPopUpOptions);
      });

      var promoteToMainLaneButton = $('<button class="btn btn-secondary turn-into-main-lane">Muuta kaista pääkaistaksi</button>').click(function() {
        var laneToPromote = selectedAsset.getLane(currentLaneNumber);
        var selectedLaneGroup = selectedAsset.selection;
        var passedValidations = validatePromotion(laneToPromote, selectedLaneGroup);
        if (!passedValidations) {
          var validationAlertPopUpOptions = {
            type: "alert",
            yesButtonLbl: 'Ok',
          };
          var alertMessage = "Lisäkaistaa ei voida muuttaa pääkaistaksi, koska lisäkaista ei ole koko tielinkin pituinen, tai kaistojen lukumäärä ei salli kyseisen kaistan muuttamista pääkaistaksi";

          GenericConfirmPopup(alertMessage, validationAlertPopUpOptions);
        }
        else{
          var confirmationPopUpOptions = {
            type: "confirm",
            yesButtonLbl: 'OK',
            noButtonLbl: 'Peruuta',
            successCallback: function() {
              selectedAsset.promoteToMainLane(currentLaneNumber);
              currentLaneNumber = 1;
              prepareLanesStructure();
              reloadForm($('#feature-attributes'));
            },
            closeCallback: function() {
              prepareLanesStructure();
              reloadForm($('#feature-attributes'));
            }
          };

          var confirmationMessage = "Olet muuttamassa pääkaistan paikkaa. Tämä muutos vaikuttaa kaikkiin valittujen tielinkkien kaistoihin.";
          GenericConfirmPopup(confirmationMessage, confirmationPopUpOptions);
        }
      });

      function validatePromotion(laneToPromote, laneGroup) {
        var currentMainLane = selectedAsset.getLane(1);
        var isFullLength = laneToPromote.startMeasure === currentMainLane.startMeasure && laneToPromote.endMeasure === currentMainLane.endMeasure;

        var lanesWithOrderNumbers = selectedAsset.getOrderingNumbers(laneGroup);
        var laneToPromoteWithOrderNo = _.find(lanesWithOrderNumbers, function (lane){
          var laneToPromoteLaneCode = getLaneCodeValue(laneToPromote);
          var laneCodeAux = getLaneCodeValue(lane);
          return laneToPromoteLaneCode === laneCodeAux;
        });

        var maxOrderNumber = _.maxBy(lanesWithOrderNumbers, function (lane) {
          return lane.orderNo;
        }).orderNo;

        var minOrderNumber = 1;
        var maxAmountOfLanesOnOneSide = 4;
        var notTooManyLanesOnEitherSide = Math.abs(laneToPromoteWithOrderNo.orderNo - minOrderNumber) <= maxAmountOfLanesOnOneSide &&
            Math.abs(laneToPromoteWithOrderNo.orderNo - maxOrderNumber) <= maxAmountOfLanesOnOneSide;

        return notTooManyLanesOnEitherSide && isFullLength;
      }

      var prepareLanesStructure = function () {
        if(_.isUndefined(selectedAsset.getLane(currentLaneNumber))){
          if(currentLaneNumber == "2"){
            currentLaneNumber = currentLaneNumber - 1;
          }else{
            currentLaneNumber = currentLaneNumber - 2;
          }
        }
        selectedAsset.setCurrentLane(currentLaneNumber);

        if(currentLaneNumber == 1){
          currentFormStructure = mainLaneFormStructure;
        }else{
          newLaneStructure(currentLaneNumber);
        }
      };

      var lane = selectedAsset.getLane(currentLaneNumber, sidecode);

      expireLane.prop('disabled', lane.id === 0);
      deleteLane.prop('disabled', lane.id !== 0);
      promoteToMainLaneButton.prop('disabled', selectedAsset.isDirty());

      if(currentLaneNumber !== 1)
        body.find('.form').append($('<div class="lane-buttons">').append(promoteToMainLaneButton).append(expireLane).append(deleteLane));
    }

    function renderFormElements(asset, isReadOnly, sideCode, setValueFn, getValueFn, isDisabled, body) {
      function setupDatePickers() {
        var $startDateElement = body.find('#start_date' + sideCode);
        var $endDateElement = body.find('#end_date' + sideCode);
        if(!_.isEmpty($startDateElement) && !_.isEmpty($endDateElement))
          dateutil.addTwoDependentDatePickersForLanes($startDateElement, $endDateElement);
      }

      var sideCodeClass = self.generateClassName(sideCode);

      var formGroup = $('' + '<div class="dynamic-form editable form-editable-'+ sideCodeClass +'">' + '</div>');
      setValueFn(lanesAssets.getCurrentLaneNumber(), {properties: asset.properties}, sideCode);

      formGroup.append($('' + self.createSideCodeMarker(sideCode)));
      body.find('.form').append(formGroup);
      body.find('.form-editable-' + sideCodeClass).append(self.renderAvailableFormElements(asset, isReadOnly, sideCode, setValueFn, getValueFn, isDisabled));

      setupDatePickers();
      return body;
    }

    function createBodyElement(selectedAsset) {
      /* Otherwise it is not possible to access this information as in other selectedAsset objects */
      var info = {
        modifiedBy :  selectedAsset.modifiedBy || '',
        modifiedDate : selectedAsset.modifiedAt ? ' ' + selectedAsset.modifiedAt : '',
        createdBy : selectedAsset.createdBy || '',
        createdDate : selectedAsset.createdAt ? ' ' + selectedAsset.createdAt: ''
      };

      return $('<div class="wrapper">' +
          '   <div class="form form-horizontal form-dark asset-factory">' +
          '     <div class="form-group">' +
          '       <p class="form-control-static asset-log-info">Lisätty järjestelmään: ' + self.informationLog(info.createdDate, info.createdBy)+ '</p>' +
          '     </div>' +
          '     <div class="form-group">' +
          '       <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + self.informationLog(info.modifiedDate, info.modifiedBy) + '</p>' +
          '     </div>' +
          self.userInformationLog() +
          '   </div>' +
          '</div>').addClass(applicationModel.isReadOnly() ? "read-only" : "edit-mode");

    }

    self.isSaveable = function(){
      var otherSaveCondition = function() {
        var selectedLanesAsset = self._assetTypeConfiguration.selectedLinearAsset;
        if(isAddByRoadAddressActive && !selectedLanesAsset.haveNewLane())
          return false;
        return self._assetTypeConfiguration.saveCondition(selectedLanesAsset.get());
      };

      var laneTypesDefined = function() {
        var lanes = self._assetTypeConfiguration.selectedLinearAsset.selection;
        return _.every(lanes, function(lane) {
          var laneTypeProp = _.find(lane.properties, function(prop) {
            return prop.publicId == 'lane_type';
          });
          return laneTypeProp.values[0].value !== null;
        });

      };

      return _.every(forms.getAllFields(), function(field){
        return field.isValid();
      }) && otherSaveCondition() && laneTypesDefined();
    };

    self.saveButton = function(assetTypeConfiguration) {
      var selectedLinearAsset = assetTypeConfiguration.selectedLinearAsset;
      var laneNumber = selectedLinearAsset.getCurrentLaneNumber();

      var element = $('<button></button>').addClass('save btn btn-primary').prop('disabled', !selectedLinearAsset.isDirty()).text('Tallenna').on('click', function() {
        var confirmationPopUpOptions = {
          type: "confirm",
          yesButtonLbl: 'Tallenna',
          noButtonLbl: 'Peruuta',
          successCallback: function() {
            selectedLinearAsset.save(isAddByRoadAddressActive);
            selectedLinearAsset.setCurrentLane(parseInt(laneNumber)+1);
            currentFormStructure = mainLaneFormStructure;
          }
        };
        var confirmationMessage = "Ovatko tekemäsi muutokset lopullisia?";

        GenericConfirmPopup(confirmationMessage, confirmationPopUpOptions);
      });

      var updateStatus = function(element) {
          element.prop('disabled', !(self.isSaveable() && selectedLinearAsset.isDirty() && !selectedLinearAsset.lanesCutAreEqual()));
      };

      updateStatus(element);

      eventbus.on(self.events('valueChanged'), function() {
        updateStatus(element);
      });

      return {
        element: element
      };
    };

    self.cancelButton = function(assetTypeConfiguration) {
      var selectedLinearAsset = assetTypeConfiguration.selectedLinearAsset;
      var laneNumber = selectedLinearAsset.getCurrentLaneNumber();

      var element = $('<button></button>').prop('disabled', !selectedLinearAsset.isDirty()).addClass('cancel btn btn-secondary').text('Peruuta').click(function() {
        selectedLinearAsset.setCurrentLane(parseInt(laneNumber)+1);
        currentFormStructure = mainLaneFormStructure;
        selectedLinearAsset.cancel();
      });

      eventbus.on(self.events('valueChanged'), function() {
        if(selectedLinearAsset.isDirty()){
          $('.cancel').prop('disabled', false);
        }else{
          $('.cancel').prop('disabled', true);
        }
      });

      return {
        element: element
      };
    };

    var newLaneStructure = function (laneNumber) {
      var indexOfProperty = _.findIndex(defaultFormStructure.fields, {'publicId': 'lane_code'});
      defaultFormStructure.fields[indexOfProperty] = {label: 'Kaista', type: 'read_only_number', publicId: "lane_code", defaultValue: laneNumber, weight: 12};
      currentFormStructure = defaultFormStructure;
    };

    jQuery.fn.showElement = function(visible) {
      var toggle = visible ? 'visible' : 'hidden';
      return this.css('visibility', toggle);
    };
  };
})(this);
