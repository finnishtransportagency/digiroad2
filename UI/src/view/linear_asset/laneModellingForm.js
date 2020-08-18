(function(root) {
  root.LaneModellingForm = function (formStructure) {
    DynamicAssetForm.call(this, formStructure);
    var dynamicFieldParent = this.DynamicField;
    var self = this;
    var currentFormStructure;
    var defaultFormStructure;
    var isAddByRoadAddressActive = false;
    var lanesAssets;

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

        me.element = $('' +
          '<div class="form-group">' +
          '<label class="control-label">' + field.label + '</label>' +
          '</div>');

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
      {label: 'Tien numero', type: 'read_only_number', publicId: "roadNumber", weight: 1},
      {label: 'Tieosanumero', type: 'read_only_number', publicId: "roadPartNumber", weight: 2},
      {label: 'Ajorata', type: 'read_only_number', publicId: "track", weight: 3},
      {label: 'Etäisyys tieosan alusta', type: 'read_only_number', publicId: "startAddrMValue", weight: 4},
      {label: 'Etäisyys tieosan lopusta', type: 'read_only_number', publicId: "endAddrMValue", weight: 5},
      {label: 'Hallinnollinen Luokka', type: 'read_only_text', publicId: "administrativeClass", weight: 6},
      {label: 'Kaista', type: 'read_only_number', publicId: "lane_code", weight: 11},
      {
        label: 'Kaistan tyypi', required: 'required', type: 'single_choice', publicId: "lane_type", defaultValue: "1", weight: 12,
        values: [
          {id: 1, label: 'Pääkaista'}
          ]
      }
    ]
  };

  var roadAddressFormStructure = {
    fields : [
      {label: 'Osa', required: 'required', type: 'number', publicId: "startRoadPartNumber", weight: 7},
      {label: 'Etäisyys', required: 'required', type: 'number', publicId: "startDistance", weight: 8},
      {label: 'Osa', required: 'required', type: 'number', publicId: "endRoadPartNumber", weight: 9},
      {label: 'Etäisyys', required: 'required', type: 'number', publicId: "endDistance", weight: 10}
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

          if(self._assetTypeConfiguration.isVerifiable)
            self.renderLinkToWorkList(layer);
          if(self._assetTypeConfiguration.hasInaccurate)
            self.renderInaccurateWorkList(layer);
        }
      });

      eventbus.on('application:readOnly', function(){
        if(self._assetTypeConfiguration.layerName ===  applicationModel.getSelectedLayer() && !_.isEmpty(self._assetTypeConfiguration.selectedLinearAsset.get())) {
          setInitialForm();
          reloadForm(rootElement);
        }
      });
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

            switch(publicId) {
              case "roadNumber":
              case "roadPartNumber":
                value = Property.pickUniqueValues(selectedLinks, publicId).join(', ');
                break;
              case "startAddrMValue":
                  roadPartNumber = Math.min.apply(null, _.compact(Property.pickUniqueValues(selectedLinks, 'roadPartNumber')));
                  value = Math.min.apply(null, Property.chainValuesByPublicIdAndRoadPartNumber(selectedLinks, roadPartNumber, publicId));
                break;
              case "endAddrMValue":
                  roadPartNumber = Math.max.apply(null, _.compact(Property.pickUniqueValues(selectedLinks, 'roadPartNumber')));
                  value = Math.max.apply(null, Property.chainValuesByPublicIdAndRoadPartNumber(selectedLinks, roadPartNumber, publicId));
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

          if(laneNumber.toString()[1] == "1"){
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
          return $('<th>' + (number.toString()[1] == '1' ? 'Pääkaista' : 'Lisäkaista') + '</th>');
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

      var addLeftLane = $('<li>').append($('<button class="btn btn-secondary">Lisää kaista vasemmalle puolelle</button>').click(function() {
        var nextLaneNumber;
        if(_.isEmpty(even)){
          nextLaneNumber = parseInt(odd.toString()[0] + '2');
        }else{
          nextLaneNumber = parseInt(_.max(even)) + 2;
        }

        selectedAsset.setNewLane(nextLaneNumber);
        selectedAsset.setCurrentLane(nextLaneNumber);
        newLaneStructure(nextLaneNumber);

        reloadForm($('#feature-attributes'));
      }).prop("disabled", !_.isEmpty(even) && _.max(even).toString()[1] == 8));

      var addRightLane = $('<li>').append($('<button class="btn btn-secondary">Lisää kaista oikealle puolelle</button>').click(function() {
        var nextLaneNumber = parseInt(_.max(odd)) + 2;

        selectedAsset.setNewLane(nextLaneNumber);
        selectedAsset.setCurrentLane(nextLaneNumber);
        newLaneStructure(nextLaneNumber);

        reloadForm($('#feature-attributes'));
      }).prop("disabled", !_.isEmpty(odd) && _.max(odd).toString()[1] == 9));

      var selectedRoadLink = selectedAsset.getSelectedRoadlink();
      var addByRoadAddress = isAddByRoadAddressActive ? $('<li>') : $('<li>').append($('<button class="btn btn-secondary">Lisää kaista tieosoitteen avulla</button>').click(function() {
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

      var body = createBodyElement(selectedAsset.getCurrentLane());

      if(selectedAsset.isSplit()) {
        //Render form A
        renderFormElements(_.find(asset,{'marker': 'A'}), isReadOnly, 'A', selectedAsset.setValue, selectedAsset.getValue, false, body);

        if(!isReadOnly)
          renderExpireAndDeleteButtonsElement(selectedAsset, body, 'A');

        body.find('.form').append('<hr class="form-break">');
        //Render form B
        renderFormElements(_.find(asset,{'marker': 'B'}), isReadOnly, 'B', selectedAsset.setValue, selectedAsset.getValue, false, body);

        if(!isReadOnly)
          renderExpireAndDeleteButtonsElement(selectedAsset, body, 'B');

        body.find('.form').append('<hr class="form-break">');
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

      var deleteLane = $('<button class="btn btn-secondary lane-button">Poista kaista</button>').click(function() {
        selectedAsset.removeLane(currentLaneNumber, sidecode);
        prepareLanesStructure();
        reloadForm($('#feature-attributes'));
      });

      var expireLane = $('<button class="btn btn-secondary lane-button">Päätä Kaista</button>').click(function() {
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

      var prepareLanesStructure = function () {
        if(_.isUndefined(selectedAsset.getLane(currentLaneNumber))){
          if(currentLaneNumber.toString()[1] == "2"){
            currentLaneNumber = parseInt(currentLaneNumber.toString()[0] + '1');
          }else{
            currentLaneNumber -= 2;
          }
        }

        selectedAsset.setCurrentLane(currentLaneNumber);

        if(currentLaneNumber.toString()[1] == "1"){
          currentFormStructure = mainLaneFormStructure;
        }else{
          newLaneStructure(currentLaneNumber);
        }
      };

      var lane = selectedAsset.getLane(currentLaneNumber, sidecode);

      expireLane.prop('disabled', lane.id === 0);
      deleteLane.prop('disabled', lane.id !== 0);

      if(currentLaneNumber.toString()[1] !== "1")
        body.find('.form').append($('<div class="lane-buttons">').append(expireLane).append(deleteLane));
    }

    function renderFormElements(asset, isReadOnly, sideCode, setValueFn, getValueFn, isDisabled, body) {
      function setupDatePickers() {
        var $startDateElement = body.find('#start_date' + sideCode);
        var $endDateElement = body.find('#end_date' + sideCode);
        if(!_.isEmpty($startDateElement) && !_.isEmpty($endDateElement))
          dateutil.addTwoDependentDatePickers($startDateElement, $endDateElement);
      }

      var sideCodeClass = self.generateClassName(sideCode);

      var formGroup = $('<div class="dynamic-form editable form-editable-'+ sideCodeClass +'"></div>');
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

      return $('<div class="wrapper read-only">' +
          '   <div class="form form-horizontal form-dark asset-factory">' +
          '     <div class="form-group">' +
          '       <p class="form-control-static asset-log-info">Lisätty järjestelmään: ' + self.informationLog(info.createdDate, info.createdBy)+ '</p>' +
          '     </div>' +
          '     <div class="form-group">' +
          '       <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + self.informationLog(info.modifiedDate, info.modifiedBy) + '</p>' +
          '     </div>' +
          self.userInformationLog() +
          '   </div>' +
          '</div>');

    }

    self.isSaveable = function(){
      var otherSaveCondition = function() {
        var selectedLanesAsset = self._assetTypeConfiguration.selectedLinearAsset;
        if(isAddByRoadAddressActive && !selectedLanesAsset.haveNewLane())
          return false;
        return self._assetTypeConfiguration.saveCondition(selectedLanesAsset.get());
      };

      return _.every(forms.getAllFields(), function(field){
        return field.isValid();
      }) && otherSaveCondition();
    };

    self.saveButton = function(assetTypeConfiguration) {
      var selectedLinearAsset = assetTypeConfiguration.selectedLinearAsset;
      var laneNumber = selectedLinearAsset.getCurrentLaneNumber();

      var element = $('<button />').addClass('save btn btn-primary').prop('disabled', !selectedLinearAsset.isDirty()).text('Tallenna').on('click', function() {
        var confirmationPopUpOptions = {
          type: "confirm",
          yesButtonLbl: 'Tallenna',
          noButtonLbl: 'Peruuta',
          successCallback: function() {
            selectedLinearAsset.save(isAddByRoadAddressActive);
            selectedLinearAsset.setCurrentLane(parseInt(laneNumber.toString()[0] + '1'));
            currentFormStructure = mainLaneFormStructure;
          }
        };
        var confirmationMessage = "Ovatko tekemäsi muutokset lopullisia? Kaistatieto lähetetään Tierekisteriin.";

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

      var element = $('<button />').prop('disabled', !selectedLinearAsset.isDirty()).addClass('cancel btn btn-secondary').text('Peruuta').click(function() {
        selectedLinearAsset.setCurrentLane(parseInt(laneNumber.toString()[0] + '1'));
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
      defaultFormStructure.fields[indexOfProperty] = {label: 'Kaista', type: 'read_only_number', publicId: "lane_code", defaultValue: laneNumber, weight: 11};
      currentFormStructure = defaultFormStructure;
    };

    jQuery.fn.showElement = function(visible) {
      var toggle = visible ? 'visible' : 'hidden';
      return this.css('visibility', toggle);
    };
  };
})(this);