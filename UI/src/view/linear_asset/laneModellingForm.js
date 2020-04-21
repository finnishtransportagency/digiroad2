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

      if(isAddByRoadAddressActive && (currentPropertyValue.publicId == "end_road_part_number" ||  currentPropertyValue.publicId == "end_distance")){
        lanesAssets.setEndAddressesValues(currentPropertyValue);
      }else{
        var laneNumber = lanesAssets.getCurrentLaneNumber();
        var properties = _.filter(getValue(laneNumber, sideCode), function(property){ return property.publicId !== currentPropertyValue.publicId; });
        properties.push(currentPropertyValue);
        setValue(laneNumber, {properties: properties}, sideCode);
      }
    };
  };

  var mainLaneFormStructure = {
    fields : [
      {
        label: 'Kaista', type: 'read_only_number', publicId: "lane_code", weight: 6
      },
      {
        label: 'Kaistan tyypi', required: 'required', type: 'single_choice', publicId: "lane_type", defaultValue: "1", weight: 7,
        values: [
          {id: 1, label: 'Pääkaista'}
          ]
      },
      {
        label: 'Kaista jatkuvuus', required: 'required', type: 'single_choice', publicId: "lane_continuity", defaultValue: "1", weight: 8,
        values: [
          {id: 1, label: 'Jatkuva'},
          {id: 2, label: 'Jatkuu toisella kaistanumerolla'},
          {id: 3, label: 'Kääntyvä'},
          {id: 4, label: 'Päättyvä'},
          {id: 5, label: 'Jatkuva, osoitettu myös oikealle kääntyville'},
          {id: 6, label: 'Jatkuva, osoitettu myös vasemmalle kääntyville'},
        ]
      },
      {
        label: 'Kaista ominaisuustieto', type: 'text', publicId: "lane_information", weight: 9
      }
    ]
  };

  var roadAddressFormStructure = {
    fields : [
      {
        label: 'Tie', type: 'read_only_number', publicId: "initial_road_number", weight: 1
      },
      {
        label: 'Osa', type: 'read_only_number', publicId: "initial_road_part_number", weight: 2
      },
      {
        label: 'Etäisyys', type: 'read_only_number', publicId: "initial_distance", weight: 3
      },
      {
        label: 'Osa', required: 'required', type: 'number', publicId: "end_road_part_number", weight: 4
      },
      {
        label: 'Etäisyys', required: 'required', type: 'number', publicId: "end_distance", weight: 5
      }
    ]
  };

    function reloadForm(rootElement){
      rootElement.find('#feature-attributes-header').html(self.renderHeader(self._assetTypeConfiguration.selectedLinearAsset));
      rootElement.find('#feature-attributes-form').html(self.renderForm(self._assetTypeConfiguration.selectedLinearAsset, false));
      rootElement.find('#feature-attributes-form').prepend(self.renderPreview(self._assetTypeConfiguration.selectedLinearAsset));
      rootElement.find('#feature-attributes-form').append(self.renderLaneButtons(self._assetTypeConfiguration.selectedLinearAsset));
    }

    var AvailableForms = function(){
      var formFields = {};

      this.addField = function(field, sideCode){
        if(!formFields['sidecode_'+sideCode])
          formFields['sidecode_'+sideCode] = [];
        formFields['sidecode_'+sideCode].push(field);
      };

      this.getFields = function(sideCode){
        if(!formFields['sidecode_'+sideCode])
          Error("The form of the sidecode " + sideCode + " doesn't exist");
        return formFields['sidecode_'+sideCode];
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
        var isDisabled = false;
        setInitialForm();
        rootElement.find('#feature-attributes-header').html(self.renderHeader(self._assetTypeConfiguration.selectedLinearAsset));
        rootElement.find('#feature-attributes-form').html(self.renderForm(self._assetTypeConfiguration.selectedLinearAsset, isDisabled));
        rootElement.find('#feature-attributes-form').prepend(self.renderPreview(self._assetTypeConfiguration.selectedLinearAsset));
        rootElement.find('#feature-attributes-form').append(self.renderLaneButtons(self._assetTypeConfiguration.selectedLinearAsset));
        rootElement.find('#feature-attributes-footer').html(self.renderFooter(self._assetTypeConfiguration.selectedLinearAsset));
      });

      eventbus.on(self.events('unselect', 'cancelled'), function() {
        rootElement.find('#feature-attributes-header').empty();
        rootElement.find('#feature-attributes-form').empty();
        rootElement.find('#feature-attributes-footer').empty();
      });

      eventbus.on('closeForm', function() {
        rootElement.find('#feature-attributes-header').empty();
        rootElement.find('#feature-attributes-form').empty();
        rootElement.find('#feature-attributes-footer').empty();
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
        if(self._assetTypeConfiguration.layerName ===  applicationModel.getSelectedLayer() && self._assetTypeConfiguration.selectedLinearAsset.count() !== 0) {
          var isDisabled = false;
          setInitialForm();
          rootElement.find('#feature-attributes-header').html(self.renderHeader(self._assetTypeConfiguration.selectedLinearAsset));
          rootElement.find('#feature-attributes-form').html(self.renderForm(self._assetTypeConfiguration.selectedLinearAsset, isDisabled));
          rootElement.find('#feature-attributes-form').prepend(self.renderPreview(self._assetTypeConfiguration.selectedLinearAsset));
          rootElement.find('#feature-attributes-form').append(self.renderLaneButtons(self._assetTypeConfiguration.selectedLinearAsset));
          rootElement.find('#feature-attributes-footer').html(self.renderFooter(self._assetTypeConfiguration.selectedLinearAsset));
        }
      });
    };

    self.renderAvailableFormElements = function(asset, isReadOnly, sideCode, setAsset, getValue, isDisabled, alreadyRendered) {
      function infoLabel(publicId) {
        var info;
        var infoElement;

        switch(publicId) {
          case "lane_code":
            info = 'Kaistan lisäys:';
            break;
          case "end_road_part_number":
            info = 'Kaistan loppuu:';
            break;
          case "initial_road_number":
            info = 'Kaistan alkaa:';
            break;
        }

        if(info)
          infoElement = $('<div class="form-group"><label class="control-label" style="text-align: left;">' + info + '</label></div>');

        if(publicId == "lane_code" && isAddByRoadAddressActive)
          return infoElement.prepend($('<hr class="form-break">'));

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
          if (!_.isUndefined(existingProperty))
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

    var createPreviewHeaderElement = function(laneNumbers) {
      var createNumber = function (number) {
        var previewButton = $('<td class="preview-lane">' + number + '</td>').click(function() {
          $(".preview .lane").removeClass("not-highlight-lane highlight-lane").addClass("not-highlight-lane");
          $(this).removeClass("not-highlight-lane").addClass("highlight-lane");
          var laneNumber = parseInt(number);
          lanesAssets.setCurrentLane(laneNumber);

          if(laneNumber.toString()[1] == "1"){
            currentFormStructure = mainLaneFormStructure;
          }else{
            defaultFormStructure.fields[0] = {label: 'Kaista', type: 'read_only_number', publicId: "lane_code", defaultValue: laneNumber, weight: 6};
            currentFormStructure = defaultFormStructure;
          }
          reloadForm($('#feature-attributes'));
        });

        if(number == lanesAssets.getCurrentLaneNumber()){
          return previewButton.addClass("highlight-lane");
        }else{
          return previewButton.addClass("not-highlight-lane");
        }
      };

      var numbers = _.sortBy(laneNumbers);

      var odd = _.filter(numbers, function (number) {
        return number % 2 !== 0;
      });
      var even = _.filter(numbers, function (number) {
        return number % 2 === 0;
      });

      var preview = function () {
        var previewList = $('<table class="preview">');

        var numberHeaders =$('<tr style="font-size: 10px;">').append(_.map(_.reverse(even).concat(odd), function (number) {
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

      var odd =_.filter(laneNumbers, function (number) {
        return number % 2 !== 0;
      });

      var even = _.filter(laneNumbers, function (number) {
        return number % 2 === 0;
      });

      var addLeftLane = $('<li>').append($('<button class="btn btn-secondary">Lisää kaista vasemmalle puolelle</button>').click(function() {
        $(".preview .lane").css('border', '1px solid white');

        var nextLaneNumber;
        if(_.isEmpty(even)){
          nextLaneNumber = parseInt(odd.toString()[0] + '2');
        }else{
          nextLaneNumber = parseInt(_.max(even)) + 2;
        }

        selectedAsset.setNewLane(nextLaneNumber);
        selectedAsset.setCurrentLane(nextLaneNumber);

        defaultFormStructure.fields[0] = {label: 'Kaista', type: 'read_only_number', publicId: "lane_code", defaultValue: nextLaneNumber, weight: 6};
        currentFormStructure = defaultFormStructure;

        reloadForm($('#feature-attributes'));
      }).prop("disabled", !_.isEmpty(even) && _.max(even).toString()[1] == 8));

      var addRightLane = $('<li>').append($('<button class="btn btn-secondary">Lisää kaista oikealle puolelle</button>').click(function() {
        $(".preview .lane").css('border', '1px solid white');

        var nextLaneNumber = parseInt(_.max(odd)) + 2;

        selectedAsset.setNewLane(nextLaneNumber);
        selectedAsset.setCurrentLane(nextLaneNumber);

        defaultFormStructure.fields[0] = {label: 'Kaista', type: 'read_only_number', publicId: "lane_code", defaultValue: nextLaneNumber, weight: 6};
        currentFormStructure = defaultFormStructure;

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

    self.renderForm = function (selectedAsset, isDisabled, isMassUpdate) {
      forms = new AvailableForms();
      var isReadOnly = self._isReadOnly(selectedAsset);
      var asset = _.filter(selectedAsset.get(), function (lane){
        return _.find(lane.properties, function (property) {
          return property.publicId == "lane_code" && _.head(property.values).value == selectedAsset.getCurrentLaneNumber();
        });
      });

      var body = createBodyElement();

      if(selectedAsset.isSplit()) {
        //Render form A
        renderFormElements(asset[0], isReadOnly, 'A', selectedAsset.setValue, selectedAsset.getValue, false, body);

        if(!isReadOnly)
          renderExpireAndDeleteButtonsElement(selectedAsset, body, 'A');

        body.find('.form').append('<hr class="form-break">');
        //Render form B
        renderFormElements(asset[1], isReadOnly, 'B', selectedAsset.setValue, selectedAsset.getValue, false, body);

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
        selectedAsset.expireLane(currentLaneNumber, sidecode);
        prepareLanesStructure();
        reloadForm($('#feature-attributes'));
      });

      var prepareLanesStructure = function () {
        if(_.isUndefined(selectedAsset.getLane(currentLaneNumber))){
          if(currentLaneNumber.toString()[1] == "2"){
            currentLaneNumber = parseInt(currentLaneNumber.toString()[0] + '1');
          }else{
            currentLaneNumber = currentLaneNumber-2;
          }
          selectedAsset.setCurrentLane(currentLaneNumber);
        }

        if(currentLaneNumber.toString()[1] == "1"){
          currentFormStructure = mainLaneFormStructure;
        }else{
          defaultFormStructure.fields[0] = {label: 'Kaista', type: 'read_only_number', publicId: "lane_code", defaultValue: currentLaneNumber, weight: 6};
          currentFormStructure = defaultFormStructure;
        }
      };

      var lane = selectedAsset.getLane(currentLaneNumber, sidecode);
      expireLane.prop('disabled', lane.id === 0);

      if(currentLaneNumber.toString()[1] !== "1")
        body.find('.form').append($('<div class="lane-buttons">').append(expireLane).append(deleteLane));
    }

    function renderFormElements(asset, isReadOnly, sideCode, setValueFn, getValueFn, isDisabled, body) {
      var sideCodeClass = self.generateClassName(sideCode);

      var formGroup = $('' + '<div class="dynamic-form editable form-editable-'+ sideCodeClass +'">' + '</div>');
      setValueFn(lanesAssets.getCurrentLaneNumber(), {properties: asset.properties}, sideCode);

      formGroup.append($('' + self.createSideCodeMarker(sideCode)));
      body.find('.form').append(formGroup);
      body.find('.form-editable-' + sideCodeClass).append(self.renderAvailableFormElements(asset, isReadOnly, sideCode, setValueFn, getValueFn, isDisabled));

      // var $startElement = body.find('#start_date' + sideCode);
      // var $endElement = body.find('#end_date' + sideCode);
      // dateutil.addTwoDependentDatePickers($startElement, $endElement);

      return body;
    }

    function createBodyElement() {
      return $('<div class="wrapper read-only">' +
        '   <div class="form form-horizontal form-dark asset-factory">' +
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
        selectedLinearAsset.save(isAddByRoadAddressActive);
        selectedLinearAsset.setCurrentLane(parseInt(laneNumber.toString()[0] + '1'));
        currentFormStructure = mainLaneFormStructure;
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

    jQuery.fn.showElement = function(visible) {
      var toggle = visible ? 'visible' : 'hidden';
      return this.css('visibility', toggle);
    };
  };
})(this);