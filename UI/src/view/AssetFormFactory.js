(function(root) {

  var DynamicField = function (fieldSettings, setValueFn) {
    var me = this;
    var setValueFn;
    var fieldSettings;
    var $element;

    var createPropertyValue = function(value){
      return {
            publicId: fieldSettings.publicId,
            propertyType: fieldSettings.type,
            required : fieldSettings.required,
            values: [ { value: value } ]
        };
    };

    me.getViewValue = function(){
      var propertyValues = [];
      //TODO Convert html values into a property value list
      var value = $($element).find('input').val();

      return createPropertyValue(value)
    };

    me.setViewValue = function(property){
      //TODO Sets the properties values into existing html elements
    };

    me.isValid = function(property){

    };

    me.compare = function(porperty1, property2){

    };

    me.hasValue = function(){

    };

    me.viewModeRender = function (field, propertyValues) {
      var value = _.first(propertyValues, function(propertyValue) { return propertyValue.value ; });
      var _value = value ? value.value : '-';

      var defaultValue = field.defaultValue;
      if(defaultValue && !value)
        _value = defaultValue;

      return $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <p class="form-control-static">' + _value + '</p>' +
        '</div>'
      );
    };

    me.editModeRender = function (currentValue, sideCode, setValue, asset){};

    //TODO we can change this name to for example setSelectedValue
    me.inputElementHandler = function(assetTypeConfiguration, inputValue, field, setValue, asset){

      if(!asset)
        asset = assetTypeConfiguration.selectedLinearAsset.get()[0];

      var value = _.cloneDeep(asset.value);

      if(!value || !value.properties)
        value = { properties: [] };

      var properties = _.find(value.properties, function(property){ return property.publicId === field.publicId; });

      var values = [];
      var exists;
      //TODO override this on each base classes
      if(!_.isObject(inputValue))
        values.push({ value : inputValue });

      else {
        if(properties) {
            exists = _.some(properties.values, function (val) { return val.value === inputValue.value; });
        }
        if(!exists)
          _.forEach(inputValue, function(input){ values.push({ value : input }); });
        else{
            values = _.filter(values, function(val) { return  val.value !== inputValue.value; });
        }
      }

      if(properties) {
        properties.values = values;
      }
      else {
        value.properties.push({
          publicId: field.publicId,
          propertyType: field.type,
          required : field.required,
          values: values
        });
      }
      setValue(value);
    };

    me.setSelectedValue = function(){
      setValueFn(me.getViewValue());
  };
  };

    var TextualField = function(){
        DynamicField.call(this);
        var me = this;
        var className = assetTypeConfiguration.className;

        me.editModeRender = function (field, fieldValue, assetValue, sideCode, setValue, asset) {
            var disabled = _.isUndefined(assetValue) ? 'disabled' : '';
            var required = _.isUndefined(field.required) ? '' : 'required';
            var defaultValue = field.defaultValue;

            var value = _.first(fieldValue, function(values) { return values.value ; });
            var _value = value ? value.value : '';
            if(defaultValue && _.isEmpty(_value))
                _value = defaultValue;

            var element = $('' +
                '<div class="form-group">' +
                '   <label class="control-label">' + field.label + '</label>' +
                '   <input type="text" fieldType = "' + field.type + '" '+required+' name="' + field.publicId + '" class="form-control ' + className + '" ' + disabled  + ' value="' + _value + '" >' +
                '</div>');

            element.find('input[type=text]').on('keyup', function(){
                me.inputElementHandler(assetTypeConfiguration, $(this).val(), field, setValue, asset);
            });
            return element;
        };
    };

  var TextualLongField = function(){
    DynamicField.call(this);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.editModeRender = function (field, fieldValue, assetValue, sideCode, setValue, asset) {
      var disabled = _.isUndefined(assetValue) ? 'disabled' : '';
      var required = _.isUndefined(field.required) ? '' : 'required';
      var value = _.first(fieldValue, function(values) { return values.value ; });
      var _value = value ? value.value : '';

      var defaultValue = field.defaultValue;
      if(defaultValue && _.isEmpty(_value))
        _value = defaultValue;

      var element = $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <textarea fieldType = "' + field.type + '" '+required+' name="' + field.publicId + '" class="form-control ' + className + '" ' + disabled + '>' + _value  + '</textarea>' +
        '</div>');

      element.find('textarea').on('keyup', function(){
        me.inputElementHandler(assetTypeConfiguration, $(this).val(), field, setValue, asset);
      });
      return element;
    };
  };

  var NumericalField = function(){
    DynamicField.call(this);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.editModeRender = function (field, fieldValue, assetValue, sideCode, setValue, asset) {
      var disabled = _.isUndefined(assetValue) ? 'disabled' : '';

      var value = _.first(fieldValue, function(values) { return values.value ; });
      var _value = value ? value.value : field.defaultValue ? field.defaultValue : '';
      var required = _.isUndefined(field.required) ? '' : 'required';

      var unit = _.isUndefined(field.unit) ? '' :  '<span class="input-group-addon ' + className + '">' + field.unit + '</span>';

      var element =   $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <input type="text" name="' + field.publicId + '" fieldType = "' + field.type + '" ' +required+ ' class="form-control" value="' + _value + '"  id="' + className + '" '+ disabled+'>' +
        unit +
        '</div>');

      element.find('input').on('keyup', function(){
        var isNumber = !isNaN($(this).val());
        assetTypeConfiguration.selectedLinearAsset.setValidValues(isNumber);
        me.inputElementHandler(assetTypeConfiguration, $(this).val(), field, setValue, asset);
      });
      return element;
    };
  };

  var ReadOnlyFields = function(){
    DynamicField.call(this);
    var me = this;

    me.editModeRender = function (field, fieldValue) {
      var value = _.first(fieldValue, function(values) { return values.value ; });
      var _value = value ? value.value : '-';
      return $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <p class="form-control-readOnly">' + _value + '</p>' +
        '</div>'
      );
    };
  };

  var IntegerField = function(){
    DynamicField.call(this);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.editModeRender = function (field, fieldValue, assetValue, sideCode, setValue, asset) {
      var value = _.first(fieldValue, function(values) { return values.value ; });
      var _value = value ? value.value : '';
      var disabled = _.isUndefined(assetValue) ? 'disabled' : '';
      var required = _.isUndefined(field.required) ? '' : 'required';
      var unit = _.isUndefined(field.unit) ? '' :  '<span class="input-group-addon ' + className + '">' + field.unit + '</span>';

      var defaultValue = field.defaultValue;
      if(defaultValue && _.isEmpty(_value))
        _value = defaultValue;

      var element =   $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <input type="text" name="' + field.publicId + '" '+required+' class="form-control"  fieldType = "' + field.type + '" value="' + _value + '"  id="' + className + '" '+ disabled+'>' +
        unit +
        '</div>');

      element.find('input[type=text]').on('keyup', function(){
        var value = $(this).val();
        var isNumber = !isNaN(value);
        var isInteger = isNumber ? Number(value) === parseInt(value, 10) : false;
        assetTypeConfiguration.selectedLinearAsset.setValidValues(isInteger);
        me.inputElementHandler(assetTypeConfiguration, value, field, setValue, asset);
      });
      return element;
    };
  };

  var SingleChoiceField = function () {
    DynamicField.call(this);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.editModeRender = function (field, fieldValue, assetValue, sideCode, setValue, asset) {
      fieldValue = _.first(fieldValue, function(curr) { return curr.value; });
      var template =  _.template(
        '<div class="form-group">' +
        '<label class="control-label">'+ field.label +'</label>' +
        '  <select <%- disabled %> class="form-control <%- className %>" name ="<%- name %>" fieldType ="<%- fieldType %>" <%- required %>><option value="" selected disabled hidden></option><%= optionTags %> </select>' +
        '</div>');

      var required = _.isUndefined(field.required) ? '' : 'required';
      var disabled = _.isUndefined(assetValue) ? 'disabled' : '';

      var selectedValue = _.isEmpty(fieldValue) ? (field.defaultValue ? field.defaultValue : '') : fieldValue.value;

      var optionTags = _.map(field.values, function(value) {
        var selected = value.id.toString() === selectedValue ? " selected" : "";
        return '<option value="' + value.id + '"' + selected + '>' + value.label + '</option>';
      }).join('');

      var element = $(template({className: className, optionTags: optionTags, disabled: disabled, name: field.publicId, fieldType: field.type, required: required}));

      element.find('select').on('change', function(){
        me.inputElementHandler(assetTypeConfiguration, $(this).val(), field, setValue, asset);
      });

      return element;
    };

    me.viewModeRender = function (field, currentValue) {
      var value = _.first(currentValue, function(values) { return values.value ; });
      var _value = value ? value.value : field.defaultValue ? field.defaultValue : '-';

      // var defaultValue = field.defaultValue;
      // if(defaultValue && !value)
      //   _value = defaultValue;

      var someValue = _.find(field.values, function(value) { return value.id.toString() === _value.toString() ; });
      var printValue = _.isUndefined(someValue) ? _value : someValue.label;

      return $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <p class="form-control-static">' + printValue + '</p>' +
        '</div>'
      );
    };
  };

  var MultiSelectField = function () {
    DynamicField.call(this);
    var me = this;

    me.editModeRender = function (field, fieldValue, assetValue, sideCode, setValue, asset) {
      var disabled = _.isEmpty(assetValue) ? 'disabled' : '';
      fieldValue = _.map(fieldValue, function(curr) { return curr.value; });
      var template =  _.template(
        '<div class="form-group">' +
        '<label class="control-label">'+ field.label+'</label>' +
        '<div class="choice-group"> ' +
        ' <%= divCheckBox %>' +
        '</div>'+
        '</div>');

      var required = _.isUndefined(field.required) ? '' : 'required';

        var defaultValue = field.defaultValue;
        if(defaultValue && _.isEmpty(fieldValue))
            fieldValue = defaultValue;

      var checkedValue = _.isEmpty(fieldValue) ? (field.defaultValue ? String(field.defaultValue) : '') : fieldValue;

      var divCheckBox = _.map(field.values, function(value) {
        var checked =  _.find(checkedValue, function (checkedValue) {return checkedValue === String(value.id);}) ? " checked" : "";
        return '' +
          '<div class = "checkbox">' +
          ' <label>'+ value.label + '<input type = "checkbox" fieldType = "' + field.type + '" '+required+' class="multiChoice-'+sideCode+'"  name = "'+field.publicId+'" value="'+value.id+'" '+ disabled+' ' + checked + '></label>' +
          '</div>';
      }).join('');

      var element =  $(template({divCheckBox: divCheckBox}));

      element.find('input').on('click', function(){
        var val = [];
        $('.multiChoice-'+sideCode+':checked').each(function(i){
          val[i] = $(this).val();
        });
        me.inputElementHandler(assetTypeConfiguration, val, field, setValue, asset);
      });

      return element;
    };

    me.viewModeRender = function (field, currentValue) {
      var values =  _.map(currentValue, function (values) { return values.value ; });

      var defaultValue = field.defaultValue;
      if(defaultValue && _.isEmpty(values))
        values = defaultValue;

        var item = _.map(values, function (value) {
            var label = _.find(field.values, function (fieldValue) {return fieldValue.id.toString() === value.toString();}).label;
           return '<li>' + label + '</li>';
        }).join('');

        var template = _.template('<div class="form-group">' +
            '   <label class="control-label">' + field.label + '</label>' +
            '<ul class="choice-group">' +
            ' <%= item %>  ' +
            '</ul>' +
            '</div>' );

        return $(template({item: item}));
    };
  };

  var DateField = function(){
    DynamicField.call(this);
    var me = this;

    me.editModeRender = function (field, fieldValue, assetValue, sideCode, setValue, asset) {
      var someValue = _.first(fieldValue, function(values) { return values.value ; });
      var disabled = _.isUndefined(assetValue) ? 'disabled' : '';
      var required = !_.isUndefined(field.required);
      var value = _.isEmpty(someValue) ? (field.defaultValue ? field.defaultValue : '') : someValue.value;

      var addDatePickers = function (field, html) {
        var $dateElement = html.find('#' + field.publicId);
        dateutil.addDependentDatePicker($dateElement);
      };

      var datePicker = $('' +
        '<div class="form-group">' +
        '<label class="control-label">' + field.label + '</label>' +
        '</div>');

      var elements = $('<input type="text"/>').addClass('form-control')
                                                .attr('id', field.publicId)
                                                .attr('required', required)
                                                .attr('placeholder',"pp.kk.vvvv")
                                                .attr('disabled', disabled)
                                                .attr('fieldType', field.type)
                                                .attr('value', value )
                                                .attr('name', field.publicId).on('keyup datechange', _.debounce(function (target) {
        // tab press
        if (target.keyCode === 9) {
          return;
        }
        var propertyValue = _.isEmpty(target.currentTarget.value) ? '' : dateutil.finnishToIso8601(target.currentTarget.value);
        me.inputElementHandler(assetTypeConfiguration, propertyValue, field, setValue, asset);
      }, 500));

      datePicker.append(elements);
      addDatePickers(field, datePicker);
      return datePicker;
    };


    me.viewModeRender = function (field, currentValue) {
      var first = _.first(currentValue, function(values) { return values.value ; });

      var value =  first ? first.value : '';
      return $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <p class="form-control-static">' + value + '</p>' +
        '</div>'
      );
    };
  };

  var CheckboxField = function() {
    DynamicField.call(this);
    var me = this;

    me.editModeRender = function (field, fieldValue, assetValue, sideCode, setValue, asset) {

      var disabled = _.isEmpty(assetValue) ? 'disabled' : '';
      var defaultValue = _.isUndefined(field.defaultValue) ? 0 : field.defaultValue;

      var value = _.isEmpty(fieldValue) ? defaultValue : parseInt(fieldValue[0].value);
      var required = _.isUndefined(field.required) ? '' : 'required';
      var checked = !!parseInt(value) ? 'checked' : '';

      var element = $('' +
        '<div class="form-group">' +
        '<label class="control-label">'+ field.label+'</label>' +
        '<div class="choice-group"> ' +
        '<input type = "checkbox" fieldType = "' + field.type + '" '+required+' class="multiChoice" name = "' + field.publicId + '" value=' +String(value)+' '+disabled+' '+checked+'>' +
        '</div>'+
        '</div>');

      element.find('input').on('click', function(){
        var val  = $(this).prop('checked') ? 1: 0;
        element.find('input').attr('value', val);
        me.inputElementHandler(assetTypeConfiguration, val, field, setValue, asset);
      });

      return element;
    };

    me.viewModeRender = function (field, currentValue) {
      var curr = _.isEmpty(currentValue) ? '' : currentValue[0].value;

      var template = _.template('<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <p class="form-control-static">' +
        ' <%= divCheckBox %>  ' +
        '</p>' +
        '</div>' );

      var someValue = _.find(field.values, function(value) {return String(value.id) === curr; });
      var value = someValue ? someValue.label : '-';
      return $(template({divCheckBox : value}));
    };
  };

  root.dynamicFormFields = [
      {name: 'long_text', fieldType: TextualLongField},
      {name: 'single_choice', fieldType: SingleChoiceField},
      {name: 'date', fieldType: DateField},
      {name: 'multiple_choice', fieldType: MultiSelectField},
      {name: 'integer', fieldType: IntegerField},
      {name: 'number', fieldType: NumericalField},
      {name: 'text', fieldType: TextualField},
      {name: 'checkbox', fieldType: CheckboxField},
      {name: 'read_only_number', fieldType: ReadOnlyFields},
      {name: 'read_only_text', fieldType: ReadOnlyFields}
  ];

  var SaveButton = function(assetTypeConfiguration, formStructure) {

    var element = $('<button />').addClass('save btn btn-primary').prop('disabled', !assetTypeConfiguration.selectedLinearAsset.isDirty()).text('Tallenna').on('click', function() {
      assetTypeConfiguration.selectedLinearAsset.save();
    });

    var updateStatus = function(element) {
     if(!assetTypeConfiguration.selectedLinearAsset.requiredPropertiesMissing(formStructure) && assetTypeConfiguration.selectedLinearAsset.hasValidValues() && !assetTypeConfiguration.selectedLinearAsset.isSplitOrSeparatedEqual())
       element.prop('disabled',!assetTypeConfiguration.selectedLinearAsset.isSaveable());
     else{
       element.prop('disabled', true);
       }
    };

    updateStatus(element);

    eventbus.on(events('valueChanged'), function() {
      updateStatus(element);
    });

    return {
      element: element
    };

    function events() {
      return _.map(arguments, function(argument) { return assetTypeConfiguration.singleElementEventCategory + ':' + argument; }).join(' ');
    }

  };

  var CancelButton = function(assetTypeConfiguration) {

    var element = $('<button />').prop('disabled', !assetTypeConfiguration.selectedLinearAsset.isDirty()).addClass('cancel btn btn-secondary').text('Peruuta').click(function() {
      assetTypeConfiguration.selectedLinearAsset.cancel();
    });

    eventbus.on(events('valueChanged'), function() {
      var cancel = $('.cancel').prop('disabled', false);
    });


    function events() {
      return _.map(arguments, function(argument) { return assetTypeConfiguration.singleElementEventCategory + ':' + argument; }).join(' ');
    }

    return {
      element: element
    };
  };

  var VerificationButton = function(assetTypeConfiguration) {

    var visible = (assetTypeConfiguration.isVerifiable && !_.isNull(assetTypeConfiguration.selectedLinearAsset.getId()) && assetTypeConfiguration.selectedLinearAsset.count() === 1);

    var element = visible ? $('<button />').prop('disabled', assetTypeConfiguration.selectedLinearAsset.isSaveable()).addClass('verify btn btn-primary').text('Merkitse tarkistetuksi').click(function() {
      assetTypeConfiguration.selectedLinearAsset.verify();
    }) : '';

    var updateStatus = function() {
      if(!_.isEmpty(element))
        element.prop('disabled', assetTypeConfiguration.selectedLinearAsset.isSaveable());
    };

    updateStatus();

    eventbus.on(events('valueChanged'), function() {
      updateStatus();
    });

    function events() {
      return _.map(arguments, function(argument) { return assetTypeConfiguration.singleElementEventCategory + ':' + argument; }).join(' ');
    }

    return {
      element: element
    };
  };

  //TODO change the name to DynamicAssetForm
  root.AssetFormFactory = function (formStructure) {
    var me = this;
    var _assetTypeConfiguration;
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
          //TODO GET ALL FIELDS
      };
    };

    var forms = new AvailableForms();

    me.initialize = function(assetTypeConfiguration){
      var rootElement = $('#feature-attributes');
      _assetTypeConfiguration = assetTypeConfiguration;

      eventbus.on(events('selected', 'cancelled'), function () {
        rootElement.html(me.renderForm(assetTypeConfiguration.selectedLinearAsset));

        if (assetTypeConfiguration.selectedLinearAsset.isSplitOrSeparated()) {
          me.bindEvents(rootElement, assetTypeConfiguration, 'a');
          me.bindEvents(rootElement, assetTypeConfiguration, 'b');
        } else {
          me.bindEvents(rootElement, assetTypeConfiguration, '');
        }
      });

      eventbus.on(events('unselect'), function() {
        rootElement.empty();
      });

      eventbus.on('layer:selected', function(layer) {
        if(assetTypeConfiguration.isVerifiable && assetTypeConfiguration.layerName === layer){
            renderLinkToWorkList(layer);
        }
        else {
          $('#information-content .form[data-layer-name="' + assetTypeConfiguration.layerName +'"]').remove();
        }
      });

      eventbus.on('application:readOnly', function(){
        if(assetTypeConfiguration.layerName ===  applicationModel.getSelectedLayer() && assetTypeConfiguration.selectedLinearAsset.count() !== 0) {
          rootElement.html(me.renderForm(assetTypeConfiguration.selectedLinearAsset));
          me.bindEvents(rootElement, assetTypeConfiguration, '');
        }
      });

      function events() {
        return _.map(arguments, function(argument) { return _assetTypeConfiguration.singleElementEventCategory + ':' + argument; }).join(' ');
      }
    };

    me.renderFormElements = function(asset, isReadOnly, sideCode, setAsset) {

      var fieldGroupElement = $('<div class = "input-unit-combination" >');
      _.each(_.sortBy(formStructure.fields, function(field){ return field.weight; }), function (field) {
        var fieldValues = [];
        var assetValues = asset.value;
        if (assetValues) {
          var existingProperty = _.find(asset.value.properties, function (property) { return property.publicId === field.publicId; });
          if(!_.isUndefined(existingProperty))
            fieldValues = existingProperty.values;
        }
        var dynamicField = _.find(dynamicFormFields, function (availableFieldType) { return availableFieldType.name === field.type; });
        //TODO I think this is possible otherwise we can have a factory function on the dynamic field configurations
        var fieldType = new dynamicField.fieldType();
        var fieldElement = isReadOnly ? fieldType.viewModeRender(field, fieldValues) : fieldType.editModeRender(field, fieldValues, assetValues, sideCode, setAsset, asset);
        //TODO We can add the new instance of the field here
        forms.addField(fieldType, sideCode);

        fieldGroupElement.append(fieldElement);

      });
      return fieldGroupElement;
    };

    me.renderForm = function (selectedAsset) {
      forms = new AvailableForms();
      var isReadOnly = isReadOnly(selectedAsset);
      var asset = selectedAsset.get();

      var body = createBodyElement(selectedAsset);

      if(selectedAsset.isSplitOrSeparated()) {
        //Render form A
        renderFormElements(asset[0], isReadOnly, 'a', selectedAsset.setAValue);

        //Remder form B
        renderFormElements(asset[1], isReadOnly, 'b', selectedAsset.setBValue);
      }
      else
      {
        renderFormElements(asset[0], isReadOnly, '', selectedAsset.setValue);
      }

      //Render separate button if is separable asset type
      renderSeparateButtonElement(selectedAsset);

      //Hide or show elements depending on the readonly mode
      toggleBodyElements(body, isReadOnly);
      return body;
    };

    //TODO check if this is only used here if so we could
    me.bindEvents = function(rootELement, assetTypeConfiguration, sideCode, selectedLinearAsset) {
          var inputElement = rootELement.find('.form-editable-' + generateClassName(sideCode));
          var toggleElement = rootELement.find('.radio input.' + generateClassName(sideCode));
          var valueRemovers = {
              a: assetTypeConfiguration.selectedLinearAsset.removeAValue,
              b: assetTypeConfiguration.selectedLinearAsset.removeBValue
          };
          var valueSetters = {
              a: assetTypeConfiguration.selectedLinearAsset.setAValue,
              b: assetTypeConfiguration.selectedLinearAsset.setBValue
          };
          var removeValue = selectedLinearAsset ? selectedLinearAsset.removeValue : valueRemovers[sideCode] || assetTypeConfiguration.selectedLinearAsset.removeValue;
          var setValue = selectedLinearAsset ? selectedLinearAsset.setValue : valueSetters[sideCode] ||  assetTypeConfiguration.selectedLinearAsset.setValue;
          toggleElement.on('change', function(event) {
              var disabled = $(event.currentTarget).val() === 'disabled';
              var input = inputElement.find('.form-control, .choice-group .multiChoice-'+sideCode).not('.edit-control-group.choice-group');
              input.attr('disabled', disabled);
              if (disabled) {
                  removeValue();
              }
              else{
                  setValue(extractInputValues(sideCode));
              }
          });
      };

    function isReadOnly(selectedAsset){
        return checkEditConstrains(selectedAsset) || applicationModel.isReadOnly();
    }

    function renderSeparateButtonElement(selectedAsset, isReadOnly){
        var assetTypeConfiguration = _assetTypeConfiguration;
        if(selectedAsset.isSeparable() && !isReadOnly){
          var separateElement = $(''+
              '<div class="form-group editable">' +
              '  <label class="control-label"></label>' +
              '  <button class="cancel btn btn-secondary" id="separate-limit">Jaa kaksisuuntaiseksi</button>' +
              '</div>');

          separateElement.find('#separate-limit').on('click', function() { assetTypeConfiguration.selectedLinearAsset.separate(); });

          body.find('.form').append(separateElement);
        }
    }

    function renderFormElements(asset, isReadOnly, sideCode, setValueFn) {
      var assetTypeConfiguration = _assetTypeConfiguration;
      var sideCodeClass = generateClassName(sideCode);

      var unit = assetTypeConfiguration.unit ? asset.value ? asset.value + ' ' + assetTypeConfiguration.unit : '-' : asset.value ? 'on' : 'ei ole';

      var formGroup = $('' +
        '<div class="form-group editable form-editable-'+ sideCodeClass +'">' +
        '  <label class="control-label">' + assetTypeConfiguration.editControlLabels.title + '</label>' +
        '  <p class="form-control-static ' + assetTypeConfiguration.className + '" style="display:none;">' + unit.replace(/[\n\r]+/g, '<br>') + '</p>' +
        '</div>');

      formGroup.append($('' +
        createSideCodeMarker(sideCode) +
        '<div class="edit-control-group choice-group">' +
        '  <div class="radio">' +
        '    <label>' + assetTypeConfiguration.editControlLabels.disabled +
        '      <input ' +
        '      class="' + sideCodeClass + '" ' +
        '      type="radio" name="' + sideCodeClass + '" ' +
        '      value="disabled" ' + _.isUndefined(asset.value) ? 'checked' : '' + '/>' +
        '    </label>' +
        '  </div>' +
        '  <div class="radio">' +
        '    <label>' + assetTypeConfiguration.editControlLabels.enabled +
        '      <input ' +
        '      class="' + sideCodeClass + '" ' +
        '      type="radio" name="' + sideCodeClass + '" ' +
        '      value="enabled" ' + _.isUndefined(asset.value) ? '' : 'checked' + '/>' +
        '    </label>' +
        '  </div>' +
        '</div>'));

      body.find('.form').append(formGroup);
      body.find('.form-editable-' + sideCodeClass).append(me.renderFormElements(asset, isReadOnly, sideCode, setValueFn));
    }

    function renderLinkToWorkList(layerName) {
        $('#information-content').append('' +
            '<div class="form form-horizontal" data-layer-name="' + layerName + '">' +
            '<a id="unchecked-links" class="unchecked-linear-assets" href="#work-list/' + layerName + '">Vanhentuneiden kohteiden lista</a>' +
            '</div>');
    }

    function createSideCodeMarker(sideCode) {
      if (_.isUndefined(sideCode) || sideCode === '')
        return '';

      return '<span class="marker">' + sideCode + '</span>';
    }

    function createBodyElement(selectedAsset) {
      var assetTypeConfiguration = _assetTypeConfiguration;
      var info = {
        modifiedBy :  selectedAsset.getModifiedBy() || '-',
        modifiedDate : selectedAsset.getModifiedDateTime() ? ' ' + asset.getModifiedDateTime() : '',
        createdBy : selectedAsset.getCreatedBy() || '-',
        createdDate : selectedAsset.getCreatedDateTime() ? ' ' + asset.getCreatedDateTime() : '',
        verifiedBy : selectedAsset.getVerifiedBy(),
        verifiedDateTime : selectedAsset.getVerifiedDateTime()
      };

      var verifiedFields = function() {
        return (selectedAsset.isVerifiable && info.verifiedBy && info.verifiedDateTime) ?
          '<div class="form-group">' +
          '   <p class="form-control-static asset-log-info">Tarkistettu: ' + info.verifiedBy + ' ' + info.verifiedDateTime + '</p>' +
          '</div>' : '';
      };

      var title = function () {
        if(selectedAsset.isUnknown() || selectedAsset.isSplit()) {
          return '<span class="read-only-title" style="display: block">' +assetTypeConfiguration.title + '</span>' +
            '<span class="edit-mode-title" style="display: block">' + assetTypeConfiguration.newTitle + '</span>';
        }
        return selectedAsset.count() === 1 ?
          '<span>Segmentin ID: ' + asset.getId() + '</span>' : '<span>' + assetTypeConfiguration.title + '</span>';

      };

      var body = $('<header>' + title() + '<div class="linear-asset-header form-controls"></div></header>' +
          '<div class="wrapper read-only">' +
          '   <div class="form form-horizontal form-dark asset-factory">' +
          '     <div class="form-group">' +
          '       <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + info.createdBy + info.createdDate + '</p>' +
          '     </div>' +
          '     <div class="form-group">' +
          '       <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + info.modifiedBy + info.modifiedDate + '</p>' +
          '     </div>' +
                verifiedFields() +
          '     <div class="form-group">' +
          '       <p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + selectedAsset.count() + '</p>' +
          '     </div>' +
          '   </div>' +
          '</div>' +
          '<footer >' +
          '   <div class="linear-asset-footer form-controls" style="display: none">' +
          '  </div>' +
          '</footer>'
      );

      body.find('.linear-asset-header').append( new SaveButton(assetTypeConfiguration, formStructure).element).append(new CancelButton(assetTypeConfiguration).element);
      body.find('.linear-asset-footer').append( new VerificationButton(assetTypeConfiguration).element).append( new SaveButton(assetTypeConfiguration, formStructure).element).append(new CancelButton(assetTypeConfiguration).element);
      return body;
    }

    function toggleBodyElements(rootElement, isReadOnly) {
      rootElement.find('.form-controls').toggle(!isReadOnly);
      rootElement.find('.editable .form-control-static').toggle(isReadOnly);
      rootElement.find('.editable .edit-control-group').toggle(!isReadOnly);
      rootElement.find('.read-only-title').toggle(isReadOnly);
      rootElement.find('.edit-mode-title').toggle(!isReadOnly);
    }

    function generateClassName(sideCode) {
      return sideCode ? _assetTypeConfiguration.className + '-' + sideCode : _assetTypeConfiguration.className;
    }

    function checkEditConstrains(selectedAsset){
      var assetTypeConfiguration = _assetTypeConfiguration;

      var editConstrains = assetTypeConfiguration.editConstrains || function() { return false; };

      var selectedAssets = _.filter(selectedAsset.get(), function (asset) {
        return editConstrains(asset);
      });
      return !_.isEmpty(selectedAssets);
    }

    //TODO this should not be done here
    function extractInputValues(input) {
        var value = {};

        _.each(input, function(element){
            var $element = $(element);
            var publicId = $element.attr('name');
            var type = $element.attr('type');
            var required = $element.attr('required');

            if(!value || !value.properties)
                value = { properties: [] };

            //TODO here we should iterate all the available fields and then get the specified DynamicFieldType and call the getViewValue
            var dynamicField = _.find(dynamicFormFields, function(field){ return field.type == type; });

            dynamicField.getViewValue($element);


            //TODO if the values
            var properties = _.find(value.properties, function(property){ return property.publicId === publicId; });
            var values;

            if(properties){
                values = properties.values;
                if(type === 'checkbox' && !$element.prop('checked')) {}
                else {
                    values.push({value: $element.val()});
                    properties.values = values;
                }
            }
            else {
                values = [];
                if(type === 'checkbox' && !$element.prop('checked')) {}
                else values.push({ value : $element.val() });

                if(required || !_.isEmpty(values))
                    value.properties.push({
                        publicId: $element.attr('name'),
                        propertyType:  $element.attr('fieldType'),
                        required : $element.attr('required'),
                        values: values
                    });
            }
        });

        return value;

        //OR

        return _.map(forms.getFields(sideCode), function(field){
          field.getViewValue();
        });
    }


    var requiredFieldMissing = function(sideCode){
        return _.some(forms.getFields(sideCode), function(field){
            if(field.isRequired && !field.hasValue())
              return false;
        });
    };

    var isSavable = function(){
        return _.every(forms.getAllFields(), function(field){
          return field.isValid();
        });
    };

  };
})(this);