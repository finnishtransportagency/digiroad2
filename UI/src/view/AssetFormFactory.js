(function(root) {

  var DynamicField = function (fieldSettings, isDisabled) {
    var me = this;
    me.element = undefined;

    me.disabled = function() { return isDisabled ? 'disabled' : '';};
    me.required = function() { return _.isUndefined(fieldSettings.required) ? '' : 'required';};

    me.hasValueToSet = function(value){
      return !_.isUndefined(value) || !_.isUndefined(fieldSettings.defaultValue);
    };

    var createPropertyValue = function(value){
      return {
            publicId: fieldSettings.publicId,
            propertyType: fieldSettings.type,
            required : fieldSettings.required,
            values: [ { value: value } ]
        };
    };

    me.getPropertyValue = function(){
      var value = $(me.element).find('input').val();
      return createPropertyValue(value);
    };

    me.getPropertyDefaultValue = function(){
      return createPropertyValue(fieldSettings.defaultValue);
    };

    me.isValid = function(){
      return !fieldSettings.required || fieldSettings.required && me.hasValue();
    };

    me.compare = function(fieldA, fieldB){
      if(_.isUndefined(fieldA) && _.isUndefined(fieldB))
        return true;
      else
       if (!_.isUndefined(fieldA) && !_.isUndefined(fieldB))
         return _.isEqual(_.head(fieldA.getPropertyValue().values).value, _.head(fieldB.getPropertyValue().values).value);
       else
         return false;

    };

    me.hasValue = function(){
      return !_.isEmpty($(me.element).find('input').val());
    };

    me.getValue = function() {
      return $(me.element).find('input').val();
    };

    me.hasValidValue = function() {};

    me.isRequired = function() {
      return fieldSettings.required ? fieldSettings.required : false;
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

    me.editModeRender = function (currentValue, sideCode, setValue, getValue){};

    me.setSelectedValue = function(setValue, getValue){

      var currentPropertyValue = me.hasValue() ?  me.getPropertyValue() : me.getPropertyDefaultValue();

      var properties = _.filter(getValue() ? getValue().properties : getValue(), function(property){ return property.publicId !== currentPropertyValue.publicId; });
      var value = properties.concat(currentPropertyValue);

      setValue({ properties: value});
    };
  };

  var TextualField = function(assetTypeConfiguration, field){
    DynamicField.call(this, field);
    var me = this;
    var className =  assetTypeConfiguration.className;

    me.editModeRender = function (fieldValue, assetValue, sideCode, setValue, getValue) {
      var value = _.first(fieldValue, function(values) { return values.value ; });
      var _value = value ? value.value : field.defaultValue ? field.defaultValue : '';

      me.element = $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <input type="text" fieldType = "' + field.type + '" '+ me.required+' name="' + field.publicId + '" class="form-control ' + className + '" ' + me.disabled()  + ' value="' + _value + '" >' +
        '</div>');

      if (me.hasValueToSet(value))
        me.setSelectedValue(setValue, getValue);

      me.element.find('input[type=text]').on('keyup', function(){
        me.setSelectedValue(setValue, getValue);
        });
      return me.element;
        };
    };

  var TextualLongField = function(assetTypeConfiguration, field){
    DynamicField.call(this, field);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.editModeRender = function (fieldValue, assetValue, sideCode, setValue, getValue) {
      var value = _.first(fieldValue, function(values) { return values.value ; });
      var _value = value ? value.value : field.defaultValue ? field.defaultValue : '';

      me.element = $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <textarea fieldType = "' + field.type + '" '+ me.required+' name="' + field.publicId + '" class="form-control ' + className + '" >' + _value  + '</textarea>' +
        '</div>');

      if(!me.isDisabled()) {
        if (me.hasValueToSet(value))
          me.setSelectedValue(setValue, getValue);

        me.element.find('textarea').on('keyup', function () {
          me.setSelectedValue(setValue, getValue);
        });
      }

      return me.element;
    };
  };

  var NumericalField = function(assetTypeConfiguration, field, isDisabled){
    DynamicField.call(this, field, isDisabled);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.hasValidValue = function() {
      return /^\d+$/.test(me.element.find('input').val());
    };

    me.isValid = function(){
      return me.isRequired() && me.hasValue() &&  me.hasValidValue() || (!me.isRequired() && (!me.hasValue() ||  me.hasValue() && me.hasValidValue()));
    };

    me.editModeRender = function (fieldValue, assetValue, sideCode, setValue, getValue) {
      var value = _.first(fieldValue, function(values) { return values.value ; });
      var _value = value ? value.value : field.defaultValue ? field.defaultValue : '';

      var unit = _.isUndefined(field.unit) ? '' :  '<span class="input-group-addon ' + className + '">' + field.unit + '</span>';

      me.element = $('' +
          '<div class="form-group">' +
          '   <label class="control-label">' + field.label + '</label>' +
          '   <input type="text" name="' + field.publicId + '" fieldType = "' + field.type + '" ' + me.required + ' class="form-control" value="' + _value + '"  id="' + className + '" ' + me.disabled() + '>' +
          unit +
          '</div>');

      if(!isDisabled) {
        if (me.hasValueToSet(value))
          me.setSelectedValue(setValue, getValue);
      }

        me.element.find('input').on('keyup', function () {
          me.setSelectedValue(setValue, getValue);
        });

      return me.element;
    };
  };

  var ReadOnlyFields = function(){
    DynamicField.call(this);
    var me = this;

    me.editModeRender = function (fieldValue) {
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

  var IntegerField = function(assetTypeConfiguration, field, isDisabled){
    DynamicField.call(this, field, isDisabled);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.hasValidValue = function() {
      var value = me.element.find('input').val();
      // return /^\d+$/.test(value) ? _.isInteger(Number(value)) : false;
      return /^\d+$/.test(value) ? Number(value) === parseInt(value, 10) : false;
    };

    me.isValid = function(){
      return me.isRequired() && me.hasValue() &&  me.hasValidValue() || (!me.isRequired() && (!me.hasValue() ||  me.hasValue() && me.hasValidValue()));
    };

    me.editModeRender = function (fieldValue, assetValue, sideCode, setValue, getValue) {
      var value = _.first(fieldValue, function(values) { return values.value ; });
      var _value = value ? value.value : field.defaultValue ? field.defaultValue : '';

      var unit = _.isUndefined(field.unit) ? '' :  '<span class="input-group-addon ' + className + '">' + field.unit + '</span>';

      me.element =   $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <input type="text" name="' + field.publicId + '" '+ me.required +' class="form-control"  fieldType = "' + field.type + '" value="' + _value + '"  id="' + className + '" '+ me.disabled() +'>' +
        unit +
        '</div>');

      if(!isDisabled) {
        if (me.hasValueToSet(value))
          me.setSelectedValue(setValue, getValue);
      }

        me.element.find('input[type=text]').on('keyup', function () {
          me.setSelectedValue(setValue, getValue);
        });

      return me.element;
    };
  };

  var SingleChoiceField = function (assetTypeConfiguration, field) {
    DynamicField.call(this, field);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.editModeRender = function (fieldValue, assetValue, sideCode, setValue, getValue) {
      fieldValue = _.first(fieldValue, function(curr) { return curr.value; });
      var template =  _.template(
        '<div class="form-group">' +
        '<label class="control-label">'+ field.label +'</label>' +
        '  <select <%- disabled %> class="form-control <%- className %>" name ="<%- name %>" fieldType ="<%- fieldType %>" <%- required %>><option value="" selected disabled hidden></option><%= optionTags %> </select>' +
        '</div>');

      var selectedValue = _.isEmpty(fieldValue) ? (field.defaultValue ? field.defaultValue : '') : fieldValue.value;

      var optionTags = _.map(field.values, function(value) {
        var selected = value.id.toString() === selectedValue ? " selected" : "";
        return '<option value="' + value.id + '"' + selected + '>' + value.label + '</option>';
      }).join('');

      me.element = $(template({className: className, optionTags: optionTags, disabled: me.disabled, name: field.publicId, fieldType: field.type, required: me.required}));

      me.setSelectedValue(setValue, getValue);

      me.element.find('select').on('change', function(){
        me.setSelectedValue(setValue, getValue);
      });

      return me.element;
    };

    me.viewModeRender = function (field, currentValue) {
      var value = _.first(currentValue, function(values) { return values.value ; });
      var _value = value ? value.value : field.defaultValue ? field.defaultValue : '-';

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

    me.editModeRender = function (field, fieldValue, assetValue, sideCode, setValue, getValue) {
      var disabled = _.isEmpty(assetValue) ? 'disabled' : '';
      fieldValue = _.map(fieldValue, function(curr) { return curr.value; });
      var template =  _.template(
        '<div class="form-group">' +
        '<label class="control-label">'+ field.label+'</label>' +
        '<div class="choice-group"> ' +
        ' <%= divCheckBox %>' +
        '</div>'+
        '</div>');

        var defaultValue = field.defaultValue;
        if(defaultValue && _.isEmpty(fieldValue))
            fieldValue = defaultValue;

      var checkedValue = _.isEmpty(fieldValue) ? (field.defaultValue ? String(field.defaultValue) : '') : fieldValue;

      var divCheckBox = _.map(field.values, function(value) {
        var checked =  _.find(checkedValue, function (checkedValue) {return checkedValue === String(value.id);}) ? " checked" : "";
        return '' +
          '<div class = "checkbox">' +
          ' <label>'+ value.label + '<input type = "checkbox" fieldType = "' + field.type + '" '+me.required+' class="multiChoice-'+sideCode+'"  name = "'+field.publicId+'" value="'+value.id+'" '+ disabled+' ' + checked + '></label>' +
          '</div>';
      }).join('');

      me.element =  $(template({divCheckBox: divCheckBox}));

      me.setSelectedValue(setValue, getValue);

      me.element.find('input').on('click', function(){
        var val = [];
        $('.multiChoice-'+sideCode+':checked').each(function(i){
          val[i] = $(this).val();
        });
        me.setSelectedValue(setValue, getValue);
      });

      return me.element;
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

    me.editModeRender = function (field, fieldValue, assetValue, sideCode, setValue, getValue) {
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

      me.elements = $('<input type="text"/>').addClass('form-control')
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
        me.setSelectedValue(setValue, getValue);
      }, 500));

      datePicker.append(me.elements);
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

    me.editModeRender = function (field, fieldValue, assetValue, sideCode, setValue, getValue) {

      var disabled = _.isEmpty(assetValue) ? 'disabled' : '';
      var defaultValue = _.isUndefined(field.defaultValue) ? 0 : field.defaultValue;

      var value = _.isEmpty(fieldValue) ? defaultValue : parseInt(fieldValue[0].value);
      var required = _.isUndefined(field.required) ? '' : 'required';
      var checked = !!parseInt(value) ? 'checked' : '';

      me.element = $('' +
        '<div class="form-group">' +
        '<label class="control-label">'+ field.label+'</label>' +
        '<div class="choice-group"> ' +
        '<input type = "checkbox" fieldType = "' + field.type + '" '+required+' class="multiChoice" name = "' + field.publicId + '" value=' +String(value)+' '+disabled+' '+checked+'>' +
        '</div>'+
        '</div>');

      me.element.find('input').on('click', function(){
        var val  = $(this).prop('checked') ? 1: 0;
        me. element.find('input').attr('value', val);
        me.setSelectedValue(setValue, getValue);
      });

      return me.element;
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

  //TODO change the name to DynamicAssetForm
  root.AssetFormFactory = function (formStructure) {
    var me = this;
    var _assetTypeConfiguration;
    var AvailableForms = function(){
      var formFields = {};

      this.isDisabled = function(sideCode){
        if(!formFields['sidecode_'+sideCode])
          Error("The form of the sidecode " + sideCode + " doesn't exist");
        return formFields['sidecode_'+sideCode].isDisabled;
      };

      this.setDisabled = function(disabled, sideCode){
        if(!formFields['sidecode_'+sideCode])
          Error("The form of the sidecode " + sideCode + " doesn't exist");

        formFields['sidecode_'+sideCode].isDisabled = disabled;
      };

      this.addField = function(field, sideCode){
        if(!formFields['sidecode_'+sideCode])
            formFields['sidecode_'+sideCode] = { isDisabled: false, fields : [] };
        formFields['sidecode_'+sideCode].fields.push(field);
      };

      this.getFields = function(sideCode){
          if(!formFields['sidecode_'+sideCode])
            Error("The form of the sidecode " + sideCode + " doesn't exist");
          var form = formFields['sidecode_'+sideCode];
          return form ? form.fields : undefined;
      };

      this.getAllFields = function(){
        return _.flatten(_.map(formFields, function(form) {
          return form.fields;
        }));
      };
    };

    var forms = new AvailableForms();

    me.initialize = function(assetTypeConfiguration){
      var rootElement = $('#feature-attributes');
      _assetTypeConfiguration = assetTypeConfiguration;

      eventbus.on(events('selected', 'cancelled'), function () {
        rootElement.html(me.renderForm(assetTypeConfiguration.selectedLinearAsset, true));

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
        if(_assetTypeConfiguration.isVerifiable && _assetTypeConfiguration.layerName === layer){
            renderLinkToWorkList(layer);
        }
        else {
          $('#information-content .form[data-layer-name="' + _assetTypeConfiguration.layerName +'"]').remove();
        }
      });

      eventbus.on('application:readOnly', function(){
        if(_assetTypeConfiguration.layerName ===  applicationModel.getSelectedLayer() && _assetTypeConfiguration.selectedLinearAsset.count() !== 0) {
          rootElement.html(me.renderForm(_assetTypeConfiguration.selectedLinearAsset, applicationModel.isReadOnly()));
          me.bindEvents(rootElement, _assetTypeConfiguration, '');
        }
      });

      function events() {
        return _.map(arguments, function(argument) { return _assetTypeConfiguration.singleElementEventCategory + ':' + argument; }).join(' ');
      }
    };

    me.renderFormElements = function(asset, isReadOnly, sideCode, setAsset, getValue, isDisabled) {

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
        var fieldType = new dynamicField.fieldType(_assetTypeConfiguration, field, isDisabled);
        var fieldElement = isReadOnly ? fieldType.viewModeRender(field, fieldValues) : fieldType.editModeRender(fieldValues, assetValues, sideCode, setAsset, getValue);
        //TODO We can add the new instance of the field here
        forms.addField(fieldType, sideCode);

        fieldGroupElement.append(fieldElement);

      });
      return fieldGroupElement;
    };

    function _isReadOnly(selectedAsset){
      return checkEditConstrains(selectedAsset) || applicationModel.isReadOnly();
    }

    me.renderForm = function (selectedAsset, isDisabled) {
      forms = new AvailableForms();
      var isReadOnly = _isReadOnly(selectedAsset);
      var asset = selectedAsset.get();

      var body = createBodyElement(selectedAsset);

      if(selectedAsset.isSplitOrSeparated()) {
        //Render form A
        renderFormElements(asset[0], isReadOnly, 'a', selectedAsset.setAValue, selectedAsset.getValue, isDisabled, body);
        //Remder form B
        renderFormElements(asset[1], isReadOnly, 'b', selectedAsset.setBValue, selectedAsset.getBValue, isDisabled, body);
      }
      else
      {
        renderFormElements(asset[0], isReadOnly, '', selectedAsset.setValue, selectedAsset.getValue, isDisabled, body);
      }

      //Render separate button if is separable asset type
      renderSeparateButtonElement(selectedAsset, body);

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

                // $('#feature-attributes').html(me.renderForm(assetTypeConfiguration.selectedLinearAsset, disabled));
                //
                // if (assetTypeConfiguration.selectedLinearAsset.isSplitOrSeparated()) {
                //   me.bindEvents($('#feature-attributes'), assetTypeConfiguration, 'a');
                //   me.bindEvents($('#feature-attributes'), assetTypeConfiguration, 'b');
                // } else {
                //   me.bindEvents($('#feature-attributes'), assetTypeConfiguration, '');
                // }

          });
      };

    function renderSeparateButtonElement(selectedAsset, body){
        if(selectedAsset.isSeparable() && !_isReadOnly(selectedAsset)){
          var separateElement = $(''+
              '<div class="form-group editable">' +
              '  <label class="control-label"></label>' +
              '  <button class="cancel btn btn-secondary" id="separate-limit">Jaa kaksisuuntaiseksi</button>' +
              '</div>');

          separateElement.find('#separate-limit').on('click', function() { _assetTypeConfiguration.selectedLinearAsset.separate(); });

          body.find('.form').append(separateElement);
        }
    }

    function renderFormElements(asset, isReadOnly, sideCode, setValueFn, getValueFn, isDisabled, body) {
      var sideCodeClass = generateClassName(sideCode);

      var unit = _assetTypeConfiguration.unit ? asset.value ? asset.value + ' ' + _assetTypeConfiguration.unit : '-' : asset.value ? 'on' : 'ei ole';

      var formGroup = $('' +
        '<div class="form-group editable form-editable-'+ sideCodeClass +'">' +
        '  <label class="control-label">' + _assetTypeConfiguration.editControlLabels.title + '</label>' +
        '  <p class="form-control-static ' + _assetTypeConfiguration.className + '" style="display:none;">' + unit.replace(/[\n\r]+/g, '<br>') + '</p>' +
        '</div>');

      var disableChecked = isDisabled ? 'checked' : '';
      var enableChecked = isDisabled ? '' : 'checked';

      formGroup.append($('' +
        createSideCodeMarker(sideCode) +
        '<div class="edit-control-group choice-group">' +
        '  <div class="radio">' +
        '    <label>' + _assetTypeConfiguration.editControlLabels.disabled +
        '      <input ' +
        '      class= "' + sideCodeClass + '"' +
        '      type="radio" name="' + sideCodeClass + '" ' +
        '      value="disabled" ' + disableChecked + '/>' +
        '    </label>' +
        '  </div>' +
        '  <div class="radio">' +
        '    <label>' + _assetTypeConfiguration.editControlLabels.enabled +
        '      <input ' +
        '      class= "' + sideCodeClass + '"' +
        '      type="radio" name="' + sideCodeClass + '" ' +
        '      value="enabled" ' + enableChecked + ' />' +
        '    </label>' +
        '  </div>' +
        '</div>'));

      body.find('.form').append(formGroup);
      body.find('.form-editable-' + sideCodeClass).append(me.renderFormElements(asset, isReadOnly, sideCode, setValueFn, getValueFn, isDisabled));

      return body;
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
      // var assetTypeConfiguration = _assetTypeConfiguration;
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
          return '<span class="read-only-title" style="display: block">' +_assetTypeConfiguration.title + '</span>' +
            '<span class="edit-mode-title" style="display: block">' + _assetTypeConfiguration.newTitle + '</span>';
        }
        return selectedAsset.count() === 1 ?
          '<span>Segmentin ID: ' + asset.getId() + '</span>' : '<span>' + _assetTypeConfiguration.title + '</span>';

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

      body.find('.linear-asset-header').append( new SaveButton(_assetTypeConfiguration, formStructure).element).append(new CancelButton(_assetTypeConfiguration).element);
      body.find('.linear-asset-footer').append( new VerificationButton(_assetTypeConfiguration).element).append( new SaveButton(_assetTypeConfiguration, formStructure).element).append(new CancelButton(_assetTypeConfiguration).element);
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
      // var assetTypeConfiguration = _assetTypeConfiguration;

      var editConstrains = _assetTypeConfiguration.editConstrains || function() { return false; };

      var selectedAssets = _.filter(selectedAsset.get(), function (asset) {
        return editConstrains(asset);
      });
      return !_.isEmpty(selectedAssets);
    }

    me.isSplitOrSeparatedAllowed = function(){
      var uniqProperties = _.unique(forms.getAllFields(), function(field) {
          return field.getPropertyValue().publicId;
      });

        return _.some(uniqProperties, function(field){
          var fieldA = _.filter(forms.getFields('a'), function (fieldA) {
            return field.getPropertyValue().publicId === fieldA.getPropertyValue().publicId;
          });

          var fieldB = _.filter(forms.getFields('b'), function (fieldB) {
            return field.getPropertyValue().publicId === fieldB.getPropertyValue().publicId;
          });

          if(!field.compare(_.head(fieldA), _.head(fieldB)))
            return true;
        });
    };

    me.isSaveable = function(){
        return _.every(forms.getAllFields(), function(field){
          return field.isValid();
        });
    };

    var SaveButton = function(assetTypeConfiguration, formStructure) {

      var element = $('<button />').addClass('save btn btn-primary').prop('disabled', !assetTypeConfiguration.selectedLinearAsset.isDirty()).text('Tallenna').on('click', function() {
        assetTypeConfiguration.selectedLinearAsset.save();
      });

      var updateStatus = function(element) {
        if(!assetTypeConfiguration.selectedLinearAsset.isSplitOrSeparated()) {
          element.prop('disabled', !me.isSaveable());
        } else{
          element.prop('disabled', !(me.isSaveable() && me.isSplitOrSeparatedAllowed()));
        }
      };

      // updateStatus(element);

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

      var element = visible ? $('<button />').prop('disabled', isSaveable()).addClass('verify btn btn-primary').text('Merkitse tarkistetuksi').click(function() {
        assetTypeConfiguration.selectedLinearAsset.verify();
      }) : '';

      var updateStatus = function() {
        if(!_.isEmpty(element))
          element.prop('disabled', isSaveable());
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
  };
})(this);