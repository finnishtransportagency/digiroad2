(function(root) {

  var DynamicField = function () {
    var me = this;

    me.viewModeRender = function (field, currentValue) {
      var value = _.first(currentValue, function(values) { return values.value ; });
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
    me.editModeRender = function (field, currentValue, sideCode, setValue, asset){};
    me.inputElementHandler = function(assetTypeConfiguration, inputValue, field, setValue, asset){

      if(!asset)
        asset = assetTypeConfiguration.selectedLinearAsset.get()[0];

      var value = _.cloneDeep(asset.value);

      if(!value || !value.properties)
        value = { properties: [] };

      var properties = _.find(value.properties, function(property){ return property.publicId === field.publicId; });

      var values = [];
      var exists;

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
  };

  var TextualLongField = function(assetTypeConfiguration){
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

  var TextualField = function(assetTypeConfiguration){
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

  var NumericalField = function(assetTypeConfiguration){
    DynamicField.call(this, assetTypeConfiguration);
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
        '   <input type="text" name="' + field.publicId + '" fieldType = "' + field.type + '" ' +required+ ' class="form-control" value=' + _value + '  id="' + className + '" '+ disabled+'>' +
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

  var ReadOnlyFields = function(assetTypeConfiguration){
    DynamicField.call(this, assetTypeConfiguration);
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

  var IntegerField = function(assetTypeConfiguration){
    DynamicField.call(this, assetTypeConfiguration);
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

  var SingleChoiceField = function (assetTypeConfiguration) {
    DynamicField.call(this, assetTypeConfiguration);
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

      // var defaultValue = field.defaultValue;
      // if(defaultValue && _.isEmpty(fieldValue))
      //   fieldValue = String(defaultValue);
      //
      // // _.forEach(fieldValue, function(current){
      // //   element.find('option[value="'+current+'"]').attr('selected', true);
      // // });

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

  var MultiSelectField = function (assetTypeConfiguration) {
    DynamicField.call(this, assetTypeConfiguration);
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

      //
      //
      // _.forEach(fieldValue, function(current){
      //   element.find(':input[value="'+current+'"]').attr('checked', true);
      // });

      element.find('input').on('click', function(){
        var val = [];
        $('.multiChoice-'+sideCode+':checked').each(function(i){
          val[i] = $(this).val();
        });
        me.inputElementHandler(assetTypeConfiguration, val, field, setValue, asset);
      });


      // element.find('input').on('click', function(){
      //   var val = {
      //     checked : $(this).prop('checked'),
      //     value : $(this).val()
      //   };
      //   me.inputElementHandler(assetTypeConfiguration, val, field, setValue, asset);
      // });

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

  var DateField = function(assetTypeConfiguration){
    DynamicField.call(this, assetTypeConfiguration);
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

  var CheckboxField = function(assetTypeConfiguration) {
    DynamicField.call(this, assetTypeConfiguration);
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

      // var checked = value === defaultValue ;
      // element.find("input[type=checkbox]").attr('checked', !!parseInt(value));
      // element.find('input').attr('value', value);
      // if(assetValue != value)
      //  me.inputElementHandler(assetTypeConfiguration, value, field, setValue, asset);
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

  var TimePeriodField = function(assetTypeConfiguration){
    DynamicField.call(this, assetTypeConfiguration);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.editModeRender = function (field, fieldValue, assetValue, sideCode, setValue, asset) {
      function dayOrder(period) {
        var days = {
          Weekday: 0,
          Saturday: 1,
          Sunday: 2
        };
        return days[period.days];
      }
      var existingValidityPeriodElements =
        _(assetValue ? assetValue.validityPeriods : '')
          .sortByAll(dayOrder, 'startHour', 'startMinute', 'endHour', 'endMinute')
          .map(validityPeriodElement)
          .join('');

      function newValidityPeriodElement() {
        return '' +
          '<li><div class="form-group new-validity-period">' +
          '  <select class="form-control select">' +
          '    <option class="empty" disabled selected>Lisää voimassaoloaika</option>' +
          '    <option value="Weekday">Ma–Pe</option>' +
          '    <option value="Saturday">La</option>' +
          '    <option value="Sunday">Su</option>' +
          '  </select>' +
          '</div></li>';
      }

      var dayLabels = {
        Weekday: "Ma–Pe",
        Saturday: "La",
        Sunday: "Su"
      };

      function validityPeriodElement(period) {
        return '' +
          '<li><div class="form-group existing-validity-period" data-days="' + period.days + '">' +
          '  <button class="delete btn-delete">x</button>' +
          '  <label class="control-label">' +
          dayLabels[period.days] +
          '  </label>' +
          hourElement(period.startHour, 'start') +
          '  <span class="minute-separator"></span>' +
          minutesElement(period.startMinute, 'start') +
          '  <span class="hour-separator"> - </span>' +
          hourElement(period.endHour, 'end') +
          '  <span class="minute-separator"></span>' +
          minutesElement(period.endMinute, 'end') +
          '</div></li>';
      }

      function hourElement(selectedHour, type) {
        var className = type + '-hour';
        return '' +
          '<select class="form-control sub-control select ' + className + '">' +
          hourOptions(selectedHour, type) +
          '</select>';
      }

      function minutesElement(selectedMinute, type) {
        var className = type + '-minute';
        return '' +
          '<select class="form-control sub-control select ' + className + '">' +
          minutesOptions(selectedMinute) +
          '</select>';
      }

      function hourOptions(selectedOption, type) {
        var range = type === 'start' ? _.range(0, 24) : _.range(1, 25);
        return _.map(range, function (hour) {
          var selected = hour === selectedOption ? 'selected' : '';
          return '<option value="' + hour + '" ' + selected + '>' + hour + '</option>';
        }).join('');
      }

      function minutesOptions(selectedOption) {
        var range = _.range(0, 60, 5);
        return _.map(range, function (minute) {
          var selected = minute === selectedOption ? 'selected' : '';
          return '<option value="' + minute + '" ' + selected + '>' + (minute<10 ? '0' + minute : minute) + '</option>';
        }).join('');
      }


      var template = _.template('' +
       '<div class="validity-period-group">' +
      ' <ul>' +
      '   <%= existingValidityPeriodElements %>' +
      newValidityPeriodElement() +
      ' </ul>');

      var element = $(template({existingValidityPeriodElements: existingValidityPeriodElements}));

      var manoeuvreData = function(formGroupElement) {
        return {

          validityPeriods: extractValidityPeriods(formGroupElement)

        };
      };

      function extractValidityPeriods(element) {
        var periodElements = element.find('.existing-validity-period');
        return _.map(periodElements, function (element) {
          return {
            startHour: parseInt($(element).find('.start-hour').val(), 10),
            startMinute: parseInt($(element).find('.start-minute').val(), 10),
            endHour: parseInt($(element).find('.end-hour').val(), 10),
            endMinute: parseInt($(element).find('.end-minute').val(), 10),
            days: parseInt( dayOrder({days: $(element).data('days')}), 10)
          };
        });
      }

      function updateValidityPeriods(element) {
        var validityPeriods = extractValidityPeriods(element);

        // var manoeuvre = manoeuvreData(element);
        // selectedManoeuvreSource.setValidityPeriods(manoeuvreId, manoeuvre.validityPeriods);
        // element.find('input').on('click', function(){
        //   var val  = $(this).prop('checked') ? 1: 0;
        //   element.find('input').attr('value', val);
          me.inputElementHandler(assetTypeConfiguration, validityPeriods, field, setValue, asset);
      }

      element.on('click', '.existing-validity-period .delete', function(event) {
        $(event.target).parent().parent().remove();
        updateValidityPeriods($(event.delegateTarget));
      });

      element.on('change', '.existing-validity-period .select', function(event) {
        updateValidityPeriods($(event.delegateTarget));
      });

      element.on('change', '.new-validity-period select', function(event) {
        $(event.target).closest('.validity-period-group ul').append(newValidityPeriodElement());
        $(event.target).parent().parent().replaceWith(validityPeriodElement({
          days: $(event.target).val(),
          startHour: 0,
          startMinute: 0,
          endHour: 24,
          endMinute: 0
        }));
        updateValidityPeriods($(event.delegateTarget));
      });

      return element;
    };
  };

  var SaveButton = function(assetTypeConfiguration) {

    var element = $('<button />').addClass('save btn btn-primary').prop('disabled', !assetTypeConfiguration.selectedLinearAsset.isDirty()).text('Tallenna').on('click', function() {
      assetTypeConfiguration.selectedLinearAsset.save();
    });

    var updateStatus = function(element) {
     if(!assetTypeConfiguration.selectedLinearAsset.requiredPropertiesMissing() && assetTypeConfiguration.selectedLinearAsset.hasValidValues())
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

  root.AssetFormFactory = function (formStructure) {
    var me = this;
    var _assetTypeConfiguration;

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
          renderLinktoWorkList(layer);
        }
        else {
          $('#information-content .form[data-layer-name="' + assetTypeConfiguration.layerName +'"]').remove();
        }
      });

      eventbus.on('application:readOnly', function(readOnly){
        if(assetTypeConfiguration.layerName ===  applicationModel.getSelectedLayer() && assetTypeConfiguration.selectedLinearAsset.count() !== 0) {
          rootElement.html(me.renderForm(assetTypeConfiguration.selectedLinearAsset));
          rootElement.find('.read-only-title').toggle(readOnly);
          rootElement.find('.edit-mode-title').toggle(!readOnly);
          me.bindEvents(rootElement, assetTypeConfiguration, '');
        }
      });

      function events() {
        return _.map(arguments, function(argument) { return _assetTypeConfiguration.singleElementEventCategory + ':' + argument; }).join(' ');
      }
    };

    me.renderElements = function(selectedAsset, isReadOnly, sideCode, setAsset, asset) {
      var assetTypeConfiguration = _assetTypeConfiguration;
      var availableFieldTypes = [
        {name: 'long_text', field: new TextualLongField(assetTypeConfiguration)},
        {name: 'single_choice', field: new SingleChoiceField(assetTypeConfiguration)},
        {name: 'date', field: new DateField(assetTypeConfiguration)},
        {name: 'multiple_choice', field: new MultiSelectField(assetTypeConfiguration)},
        {name: 'integer', field: new IntegerField(assetTypeConfiguration)},
        {name: 'number', field: new NumericalField(assetTypeConfiguration)},
        {name: 'text', field: new TextualField(assetTypeConfiguration)},
        {name: 'checkbox', field: new CheckboxField(assetTypeConfiguration)},
        {name: 'read_only_number', field: new ReadOnlyFields(assetTypeConfiguration)},
        {name: 'read_only_text', field: new ReadOnlyFields(assetTypeConfiguration)},
        {name: 'time_period', field: new TimePeriodField(assetTypeConfiguration)}
      ];

      var fieldGroupElement = $('<div class = "input-unit-combination" >');
      _.each(_.sortBy(formStructure.fields, function(field){ return field.weight; }), function (field) {
        var fieldValues = [];
        var assetValues = selectedAsset.get()[0].value;
        if (assetValues) {
          var existingProperty = _.find(selectedAsset.get()[0].value.properties, function (property) { return property.publicId === field.publicId; });
          if(!_.isUndefined(existingProperty))
            fieldValues = existingProperty.values;
        }
        var fieldType = _.find(availableFieldTypes, function (availableFieldType) { return availableFieldType.name === field.type; }).field;
        var fieldElement = isReadOnly ? fieldType.viewModeRender(field, fieldValues) : fieldType.editModeRender(field, fieldValues, assetValues, sideCode, setAsset, asset);
        fieldGroupElement.append(fieldElement);

      });
      return fieldGroupElement;
    };

    me.renderForm = function (selectedAsset) {
      var assetTypeConfiguration = _assetTypeConfiguration;
      var isReadOnly =  validateAdministrativeClass(selectedAsset, assetTypeConfiguration.editConstrains) || applicationModel.isReadOnly();
      var asset = selectedAsset.get();

      var created = createBody(selectedAsset);
      var body  = created.body;

      if(selectedAsset.isSplitOrSeparated()) {

        var sideCodeClassA = generateClassName('a');
        var radioA = singleValueEditElement(asset[0].value,  assetTypeConfiguration, 'a');
        body.find('.form').append(radioA);
        body.find('.form-editable-' + sideCodeClassA ).append(me.renderElements(selectedAsset, isReadOnly, 'a', selectedAsset.setAValue, asset[0]));

        var sideCodeClassB = generateClassName('b');
        var radioB = singleValueEditElement(asset[1].value,  assetTypeConfiguration, 'b');
        body.find('.form').append(radioB);
        body.find('.form-editable-' + sideCodeClassB).append(me.renderElements(selectedAsset, isReadOnly, 'b', selectedAsset.setBValue, asset[1]));
      }
      else
      {
        var sideCodeClass = generateClassName('');
        var radio = singleValueEditElement(asset[0].value, assetTypeConfiguration);
        body.find('.form').append(radio);
        body.find('.form-editable-' + sideCodeClass).append(me.renderElements(selectedAsset, isReadOnly, '', selectedAsset.setValue));
      }
      body.find('.form').append(created.separateButton);
      addBodyEvents(body, assetTypeConfiguration, isReadOnly);
      return body;
    };

    function singleValueEditElement(currentValue, assetTypeConfiguration, sideCode) {
      var withoutValue = _.isUndefined(currentValue) ? 'checked' : '';
      var withValue = _.isUndefined(currentValue) ? '' : 'checked';

      var unit = assetTypeConfiguration.unit ? currentValue ? currentValue + ' ' + assetTypeConfiguration.unit : '-' : currentValue ? 'on' : 'ei ole';

      var formGroup = $('' +
        '<div class="form-group editable form-editable-'+ generateClassName(sideCode) +'">' +
        '  <label class="control-label">' + assetTypeConfiguration.editControlLabels.title + '</label>' +
        '  <p class="form-control-static ' + assetTypeConfiguration.className + '" style="display:none;">' + unit.replace(/[\n\r]+/g, '<br>') + '</p>' +
        '</div>');

      formGroup.append($('' +
        sideCodeMarker(sideCode) +
        '<div class="edit-control-group choice-group">' +
        '  <div class="radio">' +
        '    <label>' + assetTypeConfiguration.editControlLabels.disabled +
        '      <input ' +
        '      class="' + generateClassName(sideCode) + '" ' +
        '      type="radio" name="' + generateClassName(sideCode) + '" ' +
        '      value="disabled" ' + withoutValue + '/>' +
        '    </label>' +
        '  </div>' +
        '  <div class="radio">' +
        '    <label>' + assetTypeConfiguration.editControlLabels.enabled +
        '      <input ' +
        '      class="' + generateClassName(sideCode) + '" ' +
        '      type="radio" name="' + generateClassName(sideCode) + '" ' +
        '      value="enabled" ' + withValue + '/>' +
        '    </label>' +
        '  </div>' +
        '</div>'));

      return formGroup;
    }

    function sideCodeMarker(sideCode) {
      if (_.isUndefined(sideCode)) {
        return '';
      } else {
        return '<span class="marker">' + sideCode + '</span>';
      }
    }

    function createBody(asset) {
      var assetTypeConfiguration = _assetTypeConfiguration;
      var info = {
        modifiedBy :  asset.getModifiedBy() || '-',
        modifiedDate : asset.getModifiedDateTime() ? ' ' + asset.getModifiedDateTime() : '',
        createdBy : asset.getCreatedBy() || '-',
        createdDate : asset.getCreatedDateTime() ? ' ' + asset.getCreatedDateTime() : '',
        verifiedBy : asset.getVerifiedBy(),
        verifiedDateTime : asset.getVerifiedDateTime()
      };

      var verifiedFields = function() {
        return (asset.isVerifiable && info.verifiedBy && info.verifiedDateTime) ?
          '<div class="form-group">' +
          '   <p class="form-control-static asset-log-info">Tarkistettu: ' + info.verifiedBy + ' ' + info.verifiedDateTime + '</p>' +
          '</div>' : '';
      };

      var toSeparateButton =  function() {
        return asset.isSeparable() ?
          '<div class="form-group editable">' +
          '  <label class="control-label"></label>' +
          '  <button class="cancel btn btn-secondary" id="separate-limit">Jaa kaksisuuntaiseksi</button>' +
          '</div>' : '';
      };

      var title = function () {
        if(asset.isUnknown() || asset.isSplit()) {
          return '<span class="read-only-title" style="display: block">' +assetTypeConfiguration.title + '</span>' +
            '<span class="edit-mode-title" style="display: block">' + assetTypeConfiguration.newTitle + '</span>';
        }
        return asset.count() === 1 ?
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
          '       <p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + asset.count() + '</p>' +
          '     </div>' +
          '   </div>' +
          '</div>' +
          '<footer >' +
          '   <div class="linear-asset-footer form-controls" style="display: none">' +
          '  </div>' +
          '</footer>'
      );

      body.find('.linear-asset-header').append( new SaveButton(assetTypeConfiguration).element).append(new CancelButton(assetTypeConfiguration).element);
      body.find('.linear-asset-footer').append( new VerificationButton(assetTypeConfiguration).element).append( new SaveButton(assetTypeConfiguration).element).append(new CancelButton(assetTypeConfiguration).element);
      return { body : body, separateButton: toSeparateButton};
    }

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
          setValue(extractInputValues(input));
        }
      });
    };

    function extractInputValues(input) {
      var value = {};

      _.forEach(input, function(element){
        var $element = $(element);
        var publicId = $element.attr('name');
        var type = $element.attr('type');
        var required = $element.attr('required');

        if(!value || !value.properties)
          value = { properties: [] };

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
    }



    function addBodyEvents(rootElement, assetTypeConfiguration, isReadOnly) {
      rootElement.find('.form-controls').toggle(!isReadOnly);
      rootElement.find('.editable .form-control-static').toggle(isReadOnly);
      rootElement.find('.editable .edit-control-group').toggle(!isReadOnly);
      rootElement.find('#separate-limit').toggle(!isReadOnly);
      rootElement.find('#separate-limit').on('click', function() { assetTypeConfiguration.selectedLinearAsset.separate(); });
      rootElement.find('.read-only-title').toggle(isReadOnly);
      rootElement.find('.edit-mode-title').toggle(!isReadOnly);
    }

    function generateClassName(sideCode) {
      return sideCode ? _assetTypeConfiguration.className + '-' + sideCode : _assetTypeConfiguration.className;
    }

    function validateAdministrativeClass(selectedLinearAsset, editConstrains){
      editConstrains = editConstrains || function() { return false; };

      var selectedAssets = _.filter(selectedLinearAsset.get(), function (selected) {
        return editConstrains(selected);
      });
      return !_.isEmpty(selectedAssets);
    }

    var renderLinktoWorkList = function renderLinktoWorkList(layerName) {
      var textName;
      switch(layerName) {
        case "maintenanceRoad":
          textName = "Tarkistamattomien huoltoteiden lista";
          break;
        default:
          textName = "Vanhentuneiden kohteiden lista";
      }

      $('#information-content').append('' +
        '<div class="form form-horizontal" data-layer-name="' + layerName + '">' +
        '<a id="unchecked-links" class="unchecked-linear-assets" href="#work-list/' + layerName + '">' + textName + '</a>' +
        '</div>');
    };
  };
})(this);