(function(root) {

    var DynamicField = function (fieldSettings, isDisabled) {
        var me = this;
        me.element = undefined;

        me.disabled = function() { return isDisabled ? 'disabled' : '';};
        me.required = function() { return _.isUndefined(fieldSettings.required) ? '' : 'required';};

        me.hasDefaultValue = function(){
            return !_.isUndefined(fieldSettings.defaultValue);
        };

        me.createPropertyValue = function(values){
            return {
                publicId: fieldSettings.publicId,
                propertyType: fieldSettings.type,
                required : fieldSettings.required,
                values: values
            };
        };

        me.getPropertyValue = function(){
            var value = me.getValue();
            return me.createPropertyValue([{ value: value }]);
        };

        me.getPropertyDefaultValue = function(){
            return me.createPropertyValue([{ value: fieldSettings.defaultValue}]);
        };

        me.emptyPropertyValue = function(){
            return me.createPropertyValue([]);
        };

        me.isValid = function(){
            return !me.isRequired() || me.isRequired() && me.hasValue();
        };

        me.compare = function(propertyValueA, propertyValueB){
            return _.isEqual(_.head(propertyValueA.values).value, _.head(propertyValueB.values).value);
        };

        me.hasValue = function(){
            return !_.isEmpty(me.getValue());
        };

        me.getValue = function() {
            return $(me.element).find('input').val();
        };

        me.setValue = function() {};

        me.hasValidValue = function() {};

        me.isRequired = function() {
            return fieldSettings.required ? fieldSettings.required : false;
        };

        me.viewModeRender = function (field, propertyValues) {
            var value = _.head(propertyValues, function(propertyValue) { return propertyValue.value ; });
            var _value = value ? value.value : '-';

            return $('' +
                '<div class="form-group">' +
                '   <label class="control-label">' + field.label + '</label>' +
                '   <p class="form-control-static">' + _value + '</p>' +
                '</div>'
            );
        };

        me.editModeRender = function (currentValue, sideCode, setValue, getValue){};

        me.setSelectedValue = function(setValue, getValue){

            var currentPropertyValue = me.hasValue() ?  me.getPropertyValue() : (me.hasDefaultValue() ? me.getPropertyDefaultValue() : me.emptyPropertyValue());

            var properties = _.filter(getValue() ? getValue().properties : getValue(), function(property){ return property.publicId !== currentPropertyValue.publicId; });
            var value = properties.concat(currentPropertyValue);

            setValue({ properties: value});
        };
    };

    var TextualField = function(assetTypeConfiguration, field, isDisabled){
        DynamicField.call(this, field, isDisabled);
        var me = this;
        var className =  assetTypeConfiguration.className;

        me.editModeRender = function (fieldValue, sideCode, setValue, getValue) {
            var value = _.head(fieldValue, function(values) { return values.value ; });
            var _value = value ? value.value : field.defaultValue ? field.defaultValue : '';

            me.element = $('' +
                '<div class="form-group">' +
                '   <label class="control-label">' + field.label + '</label>' +
                '   <input type="text" fieldType = "' + field.type + '" '+ me.required() +' name="' + field.publicId + '" class="form-control ' + className + '" ' + me.disabled()  + ' value="' + _value + '" >' +
                '</div>');

            if (!isDisabled && me.hasDefaultValue() && !value)
                me.setSelectedValue(setValue, getValue);

            me.element.find('input[type=text]').on('keyup', function(){
                me.setSelectedValue(setValue, getValue);
            });
            return me.element;
        };
    };

    var TextualLongField = function(assetTypeConfiguration, field, isDisabled){
        DynamicField.call(this, field, isDisabled);
        var me = this;
        var className = assetTypeConfiguration.className;

        me.editModeRender = function (fieldValue, sideCode, setValue, getValue) {
            var value = _.head(fieldValue, function(values) { return values.value ; });
            var _value = value ? value.value : field.defaultValue ? field.defaultValue : '';

            me.element = $('' +
                '<div class="form-group">' +
                '   <label class="control-label">' + field.label + '</label>' +
                '   <textarea fieldType = "' + field.type + '" '+ me.required() +' name="' + field.publicId + '" class="form-control ' + className + ' ' + me.disabled() + '" >' + _value  + '</textarea>' +
                '</div>');

            if (!isDisabled && me.hasDefaultValue() && !value)
                me.setSelectedValue(setValue, getValue);

            me.element.find('textarea').on('keyup', function () {
                me.setSelectedValue(setValue, getValue);
            });

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

        me.editModeRender = function (fieldValue, sideCode, setValue, getValue) {
            var value = _.head(fieldValue, function(values) { return values.value ; });
            var _value = value ? value.value : field.defaultValue ? field.defaultValue : '';

            var unit = _.isUndefined(field.unit) ? '' :  '<span class="input-group-addon ' + className + '">' + field.unit + '</span>';

      me.element = $('' +
          '<div class="form-group">' +
          '   <label class="control-label">' + field.label + '</label>' +
          '   <input type="text" name="' + field.publicId + '" fieldType = "' + field.type + '" ' + me.required() + ' class="form-control" value="' + _value + '"  id="' + className + '" ' + me.disabled() + '>' +
          unit +
          '</div>');

            if (!isDisabled && me.hasDefaultValue() && !value)
                me.setSelectedValue(setValue, getValue);

            me.element.find('input').on('keyup', function () {
                me.setSelectedValue(setValue, getValue);
            });

            return me.element;
        };
    };

    var ReadOnlyFields = function(assetTypeConfiguration, field, isDisabled){
        DynamicField.call(this, field, isDisabled);
        var me = this;

        me.editModeRender = function (fieldValue) {
            var value = _.head(fieldValue, function(values) { return values.value ; });
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
            return /^\d+$/.test(value) ? Number(value) === parseInt(value, 10) : false;
        };

        me.isValid = function(){
            return me.isRequired() && me.hasValue() &&  me.hasValidValue() || (!me.isRequired() && (!me.hasValue() ||  me.hasValue() && me.hasValidValue()));
        };

        me.editModeRender = function (fieldValue, sideCode, setValue, getValue) {
            var value = _.head(fieldValue, function(values) { return values.value ; });
            var _value = value ? value.value : field.defaultValue ? field.defaultValue : '';

            var unit = _.isUndefined(field.unit) ? '' :  '<span class="input-group-addon ' + className + '">' + field.unit + '</span>';
      var unitClass = _.isUndefined(unit) ? '' : ' unit';

            me.element =   $('' +
                '<div class="form-group">' +
                '   <label class="control-label">' + field.label + '</label>' +
                '   <input type="text" name="' + field.publicId + '" '+ me.required() +' class="form-control' + unitClass + '"  fieldType = "' + field.type + '" value="' + _value + '"  id="' + className + '" '+ me.disabled() + '>' +
                unit +
                '</div>');


      if (!isDisabled && me.hasDefaultValue()&& !value)
        me.setSelectedValue(setValue, getValue);

            me.element.find('input[type=text]').on('keyup', function () {
                me.setSelectedValue(setValue, getValue);
            });

            return me.element;
        };
    };

    var SingleChoiceField = function (assetTypeConfiguration, field, isDisabled){
        DynamicField.call(this, field, isDisabled);
        var me = this;
        var className = assetTypeConfiguration.className;

        me.editModeRender = function (fieldValue, sideCode, setValue, getValue) {
            var value = _.head(fieldValue, function(values) { return values.value ; });
            var selectedValue = value ? value.value : field.defaultValue ? field.defaultValue : '';

            var template =  _.template(
                '<div class="form-group">' +
                '<label class="control-label">'+ field.label +'</label>' +
                '  <select <%- disabled %> class="form-control <%- className %>" name ="<%- name %>" fieldType ="<%- fieldType %>" <%- required %>><option value="" selected disabled hidden></option><%= optionTags %> </select>' +
                '</div>');


            var optionTags = _.map(field.values, function(value) {
                var selected = value.id.toString() === selectedValue ? " selected" : "";
                return value.hidden ? '' : '<option value="' + value.id + '"' + selected + '>' + value.label + '</option>';
            }).join('');

            me.element = $(template({className: className, optionTags: optionTags, disabled: me.disabled(), name: field.publicId, fieldType: field.type, required: me.required()}));

            me.getValue = function() {
                return me.element.find(":selected").val();
            };

            if (!isDisabled && me.hasDefaultValue() && !value)
                me.setSelectedValue(setValue, getValue);

            me.element.find('select').on('change', function(){
                me.setSelectedValue(setValue, getValue);
            });

            return me.element;
        };

        me.viewModeRender = function (field, currentValue) {
            var value = _.head(currentValue, function(values) { return values.value ; });
            var _value = value ? value.value : '-';

            var someValue = _.find(field.values, function(value) { return value.id.toString() === _value.toString() ; });
            var printValue = someValue ? someValue.label: '-';

            return $('' +
                '<div class="form-group">' +
                '   <label class="control-label">' + field.label + '</label>' +
                '   <p class="form-control-static">' + printValue + '</p>' +
                '</div>'
            );
        };
    };

    var MultiSelectField = function (assetTypeConfiguration, field, isDisabled){
        DynamicField.call(this, field, isDisabled);
        var me = this;

        me.editModeRender = function (fieldValue, sideCode, setValue, getValue) {
            var value = _.head(fieldValue, function(values) { return values.value ; });
            var checkedValue = value ? value.value : field.defaultValue ? field.defaultValue : '';

            var template =  _.template(
                '<div class="form-group">' +
                '<label class="control-label">'+ field.label+'</label>' +
                '<div class="choice-group"> ' +
                ' <%= divCheckBox %>' +
                '</div>'+
                '</div>');

            var divCheckBox = _.map(field.values, function(value) {
                var checked =  _.find(checkedValue, function (checkedValue) {return checkedValue === String(value.id);}) ? " checked" : "";
                return '' +
                    '<div class = "checkbox">' +
                    ' <label>'+ value.label + '<input type = "checkbox" fieldType = "' + field.type + '" '+me.required() +' class="multiChoice-'+sideCode+'"  name = "'+fieldValue.publicId+'" value="'+value.id+'" '+ me.disabled() +' ' + checked + '></label>' +
                    '</div>';
            }).join('');

            me.element =  $(template({divCheckBox: divCheckBox}));

            if (!isDisabled && me.hasDefaultValue() && !value)
                me.setSelectedValue(setValue, getValue);

            me.getValue = function() {
                return _.map($('.multiChoice-'+sideCode+':checked'), function(fields) {
                    return fields.value; });
            };

            me.element.find('input').on('click', function(){
                me.setSelectedValue(setValue, getValue);
            });

            return me.element;
        };

        me.viewModeRender = function (field, currentValue) {
            var values =  _.map(currentValue, function (values) { return values.value ; });

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

    var DateField = function(assetTypeConfiguration, field, isDisabled){
        DynamicField.call(this, field, isDisabled);
        var me = this;

        me.editModeRender = function (fieldValue, sideCode, setValue, getValue) {

            var someValue = _.head(fieldValue, function(values) { return values.value ; });
            var value = _.isEmpty(someValue) ? (fieldValue.defaultValue ? fieldValue.defaultValue : '') : someValue.value;

            var addDatePickers = function (field, html) {
                var $dateElement = html.find('#' + field.publicId);
                dateutil.addDependentDatePicker($dateElement);
            };

            me.element = $('' +
                '<div class="form-group">' +
                '<label class="control-label">' + field.label + '</label>' +
                '</div>');

            var inputLabel = $('<input type="text" ' + me.disabled() + '/>').addClass('form-control')
                .attr('id', field.publicId)
                .attr('required', me.required())
                .attr('placeholder',"pp.kk.vvvv")
                .attr('fieldType', fieldValue.type)
                .attr('value', value )
                .attr('name', field.publicId).on('keyup datechange', _.debounce(function (target) {
                    // tab press
                    if (target.keyCode === 9) {
                        return;
                    }
                    me.setSelectedValue(setValue, getValue);
                }, 500));

            if (!isDisabled && me.hasDefaultValue() && !value)
                me.setSelectedValue(setValue, getValue);

            me.element.append(inputLabel);
            addDatePickers(field, me.element);
            return me.element;
        };
    };

    var CheckboxField = function(assetTypeConfiguration, field, isDisabled){
        DynamicField.call(this, field, isDisabled);
        var me = this;

        me.editModeRender = function (fieldValue, sideCode, setValue, getValue) {
            var value = _.head(fieldValue, function(values) { return values.value ; });
            var _value = value ? value.value : field.defaultValue ? field.defaultValue : "0";

            var checked = !!parseInt(_value) ? 'checked' : '';

            me.element = $('' +
                '<div class="form-group">' +
                '<label class="control-label">'+ field.label+'</label>' +
                '<div class="choice-group"> ' +
                '<input type = "checkbox" fieldType = "' + field.type + '" '+ me.required() +' class="multiChoice" name = "' + field.publicId + '" value=' + _value +' '+ me.disabled() +' '+  checked +'>' +
                '</div>'+
                '</div>');

            me.getValue = function() {
                return $(me.element).prop('checked') ? 1: 0;
            };

            me.hasValue = function(){ return true;};

            me.setValue = function(value) {
                $(me.element).prop('checked', ~~(value === 0));
            };

            if(!isDisabled && (me.hasDefaultValue() || me.isRequired()) && !value) {
                me.setValue(me.getValue());
                me.setSelectedValue(setValue, getValue);
            }

            me.element.find('input').on('click', function(){
                me.setValue(me.getValue());
                me.setSelectedValue(setValue, getValue);
            });

            return me.element;
        };

        me.viewModeRender = function (field, currentValue) {
            var curr = _.isEmpty(currentValue) ? '0' : currentValue[0].value;

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

    var TimePeriodField = function(assetTypeConfiguration, field, isDisabled){
        DynamicField.call(this, field, isDisabled);
        var me = this;
        var className = assetTypeConfiguration.className;

        me.editModeRender = function (fieldValue, sideCode, setValue, getValue) {

            var existingValidityPeriodElements =
                _(_.map(fieldValue, function(values) { return values.value ; }))
                    .sortBy('days', 'startHour', 'startMinute', 'endHour', 'endMinute')
                    .map(validityPeriodElement)
                    .join('');

            function newValidityPeriodElement() {
                return '' +
                    '<li><div class="form-group new-validity-period">' +
                    '  <select class="form-control select" ' + me.disabled() + '>' +
                    '    <option class="empty" disabled selected>Lisää voimassaoloaika</option>' +
                    '    <option value="0">Ma–Pe</option>' +
                    '    <option value="1">La</option>' +
                    '    <option value="2">Su</option>' +
                    '  </select>' +
                    '</div></li>';
            }

            function validityPeriodElement(period) {
                var dayLabels = {0: "Ma–Pe", 1: "La", 2: "Su"};

                return '' +
                    '<li><div class="form-group existing-validity-period" data-days="' + period.days + '">' +
                    '  <button class="delete btn-delete"' + me.disabled() + '>x</button>' +
                    '  <label class="control-label daysf">' +
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
                    '<select class="form-control sub-control select ' + className + '"' + me.disabled()+ '>' +
                    hourOptions(selectedHour, type) +
                    '</select>';
            }

            function minutesElement(selectedMinute, type) {
                var className = type + '-minute';
                return '' +
                    '<select class="form-control sub-control select ' + className + '"' + me.disabled()+ '>' +
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
                '<label>Voimassaoloaika (lisäkilvessä):</label>' +
                ' <ul>' +
                '   <%= existingValidityPeriodElements %>' +
                newValidityPeriodElement() +
                ' </ul>');

            me.element = $(template({existingValidityPeriodElements: existingValidityPeriodElements}));

            me.getPropertyValue = function(){
                var values = me.getValue();
                return me.createPropertyValue(values);
            };

            me.compare = function(propertyValueA, propertyValueB){
                var isEqual = function(valueA , valueB) {
                    return valueA.startHour === valueB.startHour && valueA.startMinute === valueB.startMinute && valueA.endHour === valueB.endHour && valueA.endMinute === valueB.endMinute && valueA.days === valueB.days;
                };

                var isRemoved = function(firstProperty, secondProperty) {
                    _.remove(firstProperty.values, function (valuesA) {
                        return !_.isUndefined(_.find(secondProperty.values, function (valuesB) {
                            return isEqual(valuesB.value, valuesA.value);
                        }));
                    });
                    return _.isEmpty(firstProperty.values);
                };
                return isRemoved(_.cloneDeep(propertyValueA), _.cloneDeep(propertyValueB)) && isRemoved(_.cloneDeep(propertyValueB), _.cloneDeep(propertyValueA));
            };

            me.getValue = function() {
                var periodElements = me.element.find('.existing-validity-period');
                return _.map(periodElements, function (element) {
                    return { value: {
                            startHour: parseInt($(element).find('.start-hour').val(), 10),
                            startMinute: parseInt($(element).find('.start-minute').val(), 10),
                            endHour: parseInt($(element).find('.end-hour').val(), 10),
                            endMinute: parseInt($(element).find('.end-minute').val(), 10),
                            days: parseInt($(element).data('days'), 10)
                        }};
                });
            };

            me.element.on('click', '.existing-validity-period .delete', function(event) {
                $(event.target).parent().parent().remove();
                me.setSelectedValue(setValue, getValue);
            });

            me.element.on('change', '.existing-validity-period .select', function(event) {
                me.setSelectedValue(setValue, getValue);
            });

            me.element.on('change', '.new-validity-period select', function(event) {
                $(event.target).closest('.validity-period-group ul').append(newValidityPeriodElement());
                $(event.target).parent().parent().replaceWith(validityPeriodElement({
                    days: $(event.target).val(),
                    startHour: 0,
                    startMinute: 0,
                    endHour: 24,
                    endMinute: 0
                }));
                me.setSelectedValue(setValue, getValue);
            });

            return me.element;
        };

        me.viewModeRender = function (field, currentValue) {
            var validityPeriodLabel = _.isEmpty(currentValue) ? '' : '<label>Voimassaoloaika (lisäkilvessä):</label>';

            var validityPeriodTable = _.map(currentValue, function(value) {
                var dayLabels = {0: "Ma–Pe", 1: "La", 2: "Su"};
                var period = value.value;
                return '' +
                    '<li>' + dayLabels[period.days] + " " + period.startHour + ":" + ("0" + period.startMinute).slice(-2)  + " - " + period.endHour + ":" + ("0" + period.endMinute).slice(-2) + '</li>';
            }).join('');

            return $('' +
                '<div class="form-group read-only">' +
                '<ul class="form-control-static validity-period-group">' +
                validityPeriodLabel +
                validityPeriodTable +
                '</ul>' +
                '</div>' );
        };
    };
    //hides field when in edit mode, show in view mode
    var HiddenReadOnlyFields = function(assetTypeConfiguration){
        DynamicField.call(this, assetTypeConfiguration);
        var me = this;

        me.viewModeRender = function (field, currentValue) {
            var value = _.head(currentValue, function(values) { return values.value ; });
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
        {name: 'read_only_text', fieldType: ReadOnlyFields},
        {name: 'time_period', fieldType: TimePeriodField},
        {name: 'hidden_read_only_number', fieldType: HiddenReadOnlyFields}
    ];

    root.DynamicAssetForm = function (formStructure) {
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

        me.initialize = function(assetTypeConfiguration){
            var rootElement = $('#feature-attributes');
            _assetTypeConfiguration = assetTypeConfiguration;

          var updateStatusForMassButton = function(element) {
            if(assetTypeConfiguration.selectedLinearAsset.isSplitOrSeparated()) {
              element.prop('disabled', !(me.isSaveable() && me.isSplitOrSeparatedAllowed()));
            } else
              element.prop('disabled', !(me.isSaveable()));
          };

            eventbus.on(events('selected', 'cancelled'), function () {
                var isDisabled = _.isNull(_assetTypeConfiguration.selectedLinearAsset.getId());
                rootElement.html(me.renderForm(_assetTypeConfiguration.selectedLinearAsset, isDisabled));

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
                    var isDisabled = _.isNull(_assetTypeConfiguration.selectedLinearAsset.getId());
                    rootElement.html(me.renderForm(_assetTypeConfiguration.selectedLinearAsset, isDisabled ));
                }
            });

             eventbus.on("massDialog:rendered", function(buttonElement){
               eventbus.on(multiEvents('valueChanged'), function() {
                 updateStatusForMassButton(buttonElement);
               });
             });

            function events() {
                return _.map(arguments, function(argument) { return _assetTypeConfiguration.singleElementEventCategory + ':' + argument; }).join(' ');
            }

            function multiEvents() {
                return _.map(arguments, function(argument) { return _assetTypeConfiguration.multiElementEventCategory + ':' + argument; }).join(' ');
            }
        };

        me.renderFormElements = function(asset, isReadOnly, sideCode, setAsset, getValue, isDisabled) {

            var fieldGroupElement = $('<div class = "input-unit-combination" >');
            _.each(_.sortBy(formStructure.fields, function(field){ return field.weight; }), function (field) {
                var fieldValues = [];
                if (asset.value) {
                    var existingProperty = _.find(asset.value.properties, function (property) { return property.publicId === field.publicId; });
                    if(!_.isUndefined(existingProperty))
                        fieldValues = existingProperty.values;
                }
                var dynamicField = _.find(dynamicFormFields, function (availableFieldType) { return availableFieldType.name === field.type; });
                var fieldType = new dynamicField.fieldType(_assetTypeConfiguration, field, isDisabled);
                forms.addField(fieldType, sideCode);
                var fieldElement = isReadOnly ? fieldType.viewModeRender(field, fieldValues) : fieldType.editModeRender(fieldValues, sideCode, setAsset, getValue);

                fieldGroupElement.append(fieldElement);

            });
            return fieldGroupElement;
        };

        function _isReadOnly(selectedAsset){
            return checkAuthorizationPolicy(selectedAsset) || applicationModel.isReadOnly();
        }

        me.renderForm = function (selectedAsset, isDisabled) {
            forms = new AvailableForms();
            var isReadOnly = _isReadOnly(selectedAsset);
            var asset = selectedAsset.get();

            var body = createBodyElement(selectedAsset);

            if(selectedAsset.isSplitOrSeparated()) {
                //Render form A
                renderFormElements(asset[0], isReadOnly, 'a', selectedAsset.setAValue, selectedAsset.getValue, selectedAsset.removeAValue, false, body);
                //Remder form B
                renderFormElements(asset[1], isReadOnly, 'b', selectedAsset.setBValue, selectedAsset.getBValue, selectedAsset.removeBValue, false, body);
            }
            else
            {
                renderFormElements(asset[0], isReadOnly, '', selectedAsset.setValue, selectedAsset.getValue, selectedAsset.removeValue, isDisabled, body);
            }

            //Render separate button if is separable asset type
            renderSeparateButtonElement(selectedAsset, body);

            //Hide or show elements depending on the readonly mode
            toggleBodyElements(body, isReadOnly);
            return body;
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

        function renderFormElements(asset, isReadOnly, sideCode, setValueFn, getValueFn, removeValueFn, isDisabled, body) {
            var sideCodeClass = generateClassName(sideCode);

            var unit = _assetTypeConfiguration.unit ? asset.value ? asset.value + ' ' + _assetTypeConfiguration.unit : '-' : asset.value ? 'on' : 'ei ole';

            var formGroup = $('' +
                '<div class="dynamic-form editable form-editable-'+ sideCodeClass +'">' +
                '  <label class="control-label">' + _assetTypeConfiguration.editControlLabels.title + '</label>' +
                '  <p class="form-control-static ' + _assetTypeConfiguration.className + '" style="display:none;">' + unit.replace(/[\n\r]+/g, '<br>') + '</p>' +
                '</div>');

            var disableChecked = isDisabled ? 'checked' : '';
            var enableChecked = isDisabled ? '' : 'checked';

            var toggleElement = $('' +
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
                '</div>');

      toggleElement.find('.radio input').on('change', function(event) {
        var disabled = $(this).val() === 'disabled';
        var input = formGroup.find('.form-control, .choice-group .multiChoice-'+sideCode).not('.edit-control-group.choice-group');
        input.prop('disabled', disabled);

                if(disabled){
                    removeValueFn();
                    _assetTypeConfiguration.selectedLinearAsset.setDirty(!isDisabled);
                }else{
                  if(asset.value)
                    setValueFn(asset.value);
                  else
                    setValueFn({ properties: [] });
                }

        formGroup.find('.input-unit-combination').replaceWith(me.renderFormElements(asset, isReadOnly, sideCode, setValueFn, getValueFn, disabled));

                eventbus.trigger(events('valueChanged'));
            });

            formGroup.append(toggleElement);
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
            var info = {
                modifiedBy :  selectedAsset.getModifiedBy() || '-',
                modifiedDate : selectedAsset.getModifiedDateTime() ? ' ' + selectedAsset.getModifiedDateTime(): '-',
                createdBy : selectedAsset.getCreatedBy() || '-',
                createdDate : selectedAsset.getCreatedDateTime() ? ' ' + selectedAsset.getCreatedDateTime(): '',
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
                    '<span>Segmentin ID: ' + selectedAsset.getId() + '</span>' : '<span>' + _assetTypeConfiguration.title + '</span>';

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

        function checkAuthorizationPolicy(selectedAsset){
            var auth = _assetTypeConfiguration.authorizationPolicy || function() { return false; };

            var selectedAssets = _.filter(selectedAsset.get(), function (asset) {
                return auth.formEditModeAccess(asset);
            });
            return _.isEmpty(selectedAssets);
        }

        me.isSplitOrSeparatedAllowed = function(){
            //When both are deleted
            if(_.isEmpty(forms.getAllFields()))
                return false;

            if(forms.getFields('a').length !== forms.getFields('b').length)
                return true;

      return _.some(forms.getFields('a'), function(fieldA){
        var propertyValueA = fieldA.getPropertyValue();
        var fieldB = _.head(_.filter(forms.getFields('b'), function (fieldB) {return propertyValueA.publicId === fieldB.getPropertyValue().publicId;}));

        return !fieldA.compare(propertyValueA, fieldB.getPropertyValue()) || !_.isEqual(fieldB.disabled(), fieldA.disabled());
      });
    };

    me.isSaveable = function(){
        var otherSaveCondition = function () {
            if (_.isUndefined(_assetTypeConfiguration.saveCondition)) {
                return true;
            } else {
                return _assetTypeConfiguration.saveCondition(forms.getAllFields());
            }
        };
        return _.every(forms.getAllFields(), function(field){
          return field.isValid();
        })&& otherSaveCondition();
    };

        function events() {
            return _.map(arguments, function(argument) { return _assetTypeConfiguration.singleElementEventCategory + ':' + argument; }).join(' ');
        }

        var SaveButton = function(assetTypeConfiguration) {

            var element = $('<button />').addClass('save btn btn-primary').prop('disabled', !assetTypeConfiguration.selectedLinearAsset.isDirty()).text('Tallenna').on('click', function() {
                assetTypeConfiguration.selectedLinearAsset.save();
            });

            var updateStatus = function(element) {
                if(assetTypeConfiguration.selectedLinearAsset.isSplitOrSeparated()) {
                    element.prop('disabled', !(me.isSaveable() && me.isSplitOrSeparatedAllowed()));
                } else
                    element.prop('disabled', !(me.isSaveable() && assetTypeConfiguration.selectedLinearAsset.isDirty()));
            };

            updateStatus(element);

            eventbus.on(events('valueChanged'), function() {
                updateStatus(element);
            });

            return {
                element: element
            };
        };

        var CancelButton = function(assetTypeConfiguration) {

            var element = $('<button />').prop('disabled', !assetTypeConfiguration.selectedLinearAsset.isDirty()).addClass('cancel btn btn-secondary').text('Peruuta').click(function() {
                assetTypeConfiguration.selectedLinearAsset.cancel();
            });

            eventbus.on(events('valueChanged'), function() {
                $('.cancel').prop('disabled', false);
            });

            return {
                element: element
            };
        };

        var VerificationButton = function(assetTypeConfiguration) {
            var visible = (assetTypeConfiguration.isVerifiable && !_.isNull(assetTypeConfiguration.selectedLinearAsset.getId()) && assetTypeConfiguration.selectedLinearAsset.count() === 1);

            var element = visible ? $('<button />').prop('disabled', me.isSaveable()).addClass('verify btn btn-primary').text('Merkitse tarkistetuksi').click(function() {
                assetTypeConfiguration.selectedLinearAsset.verify();
            }) : '';

            var updateStatus = function() {
                if(!_.isEmpty(element))
                    element.prop('disabled', me.isSaveable());
            };

            updateStatus();

            eventbus.on(events('valueChanged'), function() {
                updateStatus();
            });

            return {
                element: element
            };
        };
    };
})(this);