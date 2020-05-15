(function(root) {
  root.PiecewiseLinearAssetFormElements = {
    WinterSpeedLimitsFormElements: WinterSpeedLimitsFormElements,
    EuropeanRoadsFormElements: TextualValueFormElements,
    ExitNumbersFormElements: TextualValueFormElements,
    DefaultFormElements: DefaultFormElements
  };

  function DefaultFormElements(unit, editControlLabels, className, authorizationPolicy, selectedLinearAsset, defaultValue, possibleValues) {
    var formElem = inputFormElement(unit);
    return formElementFunctions(unit, editControlLabels, className, defaultValue, possibleValues, formElem);
  }

  function WinterSpeedLimitsFormElements(unit, editControlLabels, className, authorizationPolicy, selectedLinearAsset, defaultValue, possibleValues) {
    var formElem = dropDownFormElement(unit);
    return formElementFunctions(unit, editControlLabels, className, defaultValue, possibleValues, formElem);
  }

  function TextualValueFormElements(unit, editControlLabels, className, authorizationPolicy, selectedLinearAsset, defaultValue, possibleValues) {
    var formElem = textAreaFormElement();
    return formElementFunctions(unit, editControlLabels, className, defaultValue, possibleValues, formElem);
  }

  function hasValue(value, id) {
    var nullOrUndefined = function(id){return _.isUndefined(id) || _.isNull(id);};
    return (nullOrUndefined(id) && !_.isUndefined(value)) || (!nullOrUndefined(id) && !_.isUndefined(value)) || !nullOrUndefined(id);
  }

  function formElementFunctions(unit, editControlLabels, className, defaultValue, possibleValues, formElem) {
    return {
      singleValueElement:  _.partial(singleValueElement, formElem.measureInput, formElem.valueString),
      bindEvents: _.partial(bindEvents, formElem.inputElementValue)
    };

    function generateClassName(sideCode) {
      return sideCode ? className + '-' + sideCode : className;
    }

    function sideCodeMarker(sideCode) {
      if (_.isEmpty(sideCode)) {
        return '';
      } else {
        return '<span class="marker">' + sideCode + '</span>';
      }
    }

    function singleValueEditElement(currentValue, sideCode, input, id) {
      var withoutValue = !hasValue(currentValue, id)  ? 'checked' : '';
      var withValue = !hasValue(currentValue, id) ? '' : 'checked';
      return '' +
        sideCodeMarker(sideCode) +
        '<div class="edit-control-group choice-group">' +
        '  <div class="radio">' +
        '    <label>' + editControlLabels.disabled +
        '      <input ' +
        '      class="' + generateClassName(sideCode) + '" ' +
        '      type="radio" name="' + generateClassName(sideCode) + '" ' +
        '      value="disabled" ' + withoutValue + '/>' +
        '    </label>' +
        '  </div>' +
        '  <div class="radio">' +
        '    <label>' + editControlLabels.enabled +
        '      <input ' +
        '      class="' + generateClassName(sideCode) + '" ' +
        '      type="radio" name="' + generateClassName(sideCode) + '" ' +
        '      value="enabled" ' + withValue + '/>' +
        '    </label>' +
        '  </div>' +
        input +
        '</div>';
    }

    function bindEvents(inputElementValue, rootElement, selectedLinearAsset, sideCode) {
      var inputElement = rootElement.find('.input-unit-combination .' + generateClassName(sideCode));
      var toggleElement = rootElement.find('.radio input.' + generateClassName(sideCode));
      var valueSetters = {
        a: selectedLinearAsset.setAValue,
        b: selectedLinearAsset.setBValue
      };
      var setValue = valueSetters[sideCode] || selectedLinearAsset.setValue;
      var valueRemovers = {
        a: selectedLinearAsset.removeAValue,
        b: selectedLinearAsset.removeBValue
      };
      var removeValue = valueRemovers[sideCode] || selectedLinearAsset.removeValue;

      inputElement.on('input change', function() {
        setValue(inputElementValue(inputElement));
      });

      toggleElement.on('change', function(event) {
        var disabled = $(event.currentTarget).val() === 'disabled';
        inputElement.prop('disabled', disabled);
        if (disabled) {
          removeValue();
        } else {
          var value = _.isUndefined(unit) ? defaultValue : inputElementValue(inputElement);
          setValue(value);
        }
      });
    }

    function obtainFormControl(className, valueString, currentValue, possibleValues){
        var newCurrentValue = _.map(possibleValues, function (property) {
            var arrayValue =  _.find(currentValue, function (value) {
                return (property.id == value.publicId);
            });
            return {propertyName: property.name, propertyValue: arrayValue, propertyType: property.propType };
        });

        return _.map(newCurrentValue, function(current){
            if(current.propertyType == "header")
              return ' <h2 class="form-control-static">' + current.propertyName + '</h2>';

            var value = singleChoiceValuesConversion(current, possibleValues);
            return ' <label class="control-label">' + current.propertyName + ': </label>' +
                  '  <p class="form-control-static">' + valueString(value).replace(/[\n\r]+/g, '<br>') + '</p>';
        }).join('');
    }

    function singleChoiceValuesConversion(current, propertyValues){
      var value = current.propertyValue ? current.propertyValue.value : "";
      var property = _.find(propertyValues, function(property){
        return property.name == current.propertyName;
      });

      var propertyValue = _.find(property.value, function(value){
        if(!current.propertyValue)
          return false;
        return value.typeId == current.propertyValue.value;
      });

      return propertyValue ? propertyValue.title : value;
    }

    function singleValueElement(measureInput, valueString, currentValue, sideCode, id) {
      if(Array.isArray(currentValue)){
          return '' +
              '<div class="form-group editable form-editable-'+ className +'">' +
              ' <label class="control-label">' + editControlLabels.title + '</label>' +
              ' <div class="form-control-static ' + className + '" style="display:none;">' +
                obtainFormControl(className, valueString, currentValue, possibleValues)  +
              ' </div>' +
                singleValueEditElement(currentValue, sideCode, measureInput(currentValue, generateClassName(sideCode), possibleValues, id), id) +
             '</div>';

      }else {
          return '' +
              '<div class="form-group editable form-editable-'+ className +'">' +
              '  <label class="control-label">' + editControlLabels.title + '</label>' +
              '  <p class="form-control-static ' + className + '" style="display:none;">' + valueString(currentValue).replace(/[\n\r]+/g, '<br>') + '</p>' +
                singleValueEditElement(currentValue, sideCode, measureInput(currentValue, generateClassName(sideCode), possibleValues, id), id) +
              '</div>';
      }
    }
  }

  function inputFormElement(unit) {
    return {
      inputElementValue: inputElementValue,
      valueString: valueString,
      measureInput: measureInput
    };

    function inputElementValue(input) {
      var removeWhitespace = function(s) {
        return s.replace(/\s/g, '');
      };
      var value = parseInt(removeWhitespace(input.val()), 10);
      return _.isFinite(value) ? value : 0;
    }

    function valueString(currentValue) {
      if (unit) {
        return currentValue ? currentValue + ' ' + unit : '-';
      } else {
        return currentValue ? 'on' : 'ei ole';
      }
    }

    function measureInput(currentValue, className) {
      if (unit) {
        var value = currentValue ? currentValue : '';
        var disabled = _.isUndefined(currentValue) ? 'disabled' : '';
        return '' +
          '<div class="input-unit-combination input-group">' +
          '  <input ' +
          '    type="text" ' +
          '    class="form-control ' + className + '" ' +
          '    value="' + value  + '" ' + disabled + ' >' +
          '  <span class="input-group-addon ' + className + '">' + unit + '</span>' +
          '</div>';
      } else {
        return '';
      }
    }
  }

  function textAreaFormElement() {
    return {
      inputElementValue: inputElementValue,
      valueString: valueString,
      measureInput: measureInput
    };

    function inputElementValue(input) {
      return _.trim(input.val());
    }

    function valueString(currentValue) {
      return currentValue || '';
    }

    function measureInput(currentValue, className, possibleValues, id) {
      var value = currentValue ? currentValue : '';
      var disabled = !hasValue(currentValue, id) ? 'disabled' : '';
      return '' +
        '<div class="input-unit-combination input-group">' +
        '  <textarea class="form-control large-input ' + className + '" ' +
          disabled + '>' +
        value  + '</textarea>' +
          '</div>';
    }
  }

  function dropDownFormElement(unit) {
    var template =  _.template(
     '<div class="input-unit-combination">' +
      '  <select <%- disabled %> class="form-control <%- className %>" ><%= optionTags %></select>' +
      '</div>');

    return {
      inputElementValue: inputElementValue,
      valueString: valueString,
      measureInput: measureInput
    };

    function valueString(currentValue) {
      return currentValue ? currentValue + ' ' + unit : '-';
    }

    function measureInput(currentValue, className, possibleValues) {
      var optionTags = _.map(possibleValues, function(value) {
        var selected = value === currentValue ? " selected" : "";
        return '<option value="' + value + '"' + selected + '>' + value + ' ' + unit + '</option>';
      }).join('');
      var value = currentValue ? currentValue : '';
      var disabled = _.isUndefined(currentValue) ? 'disabled' : '';


      return template({className: className, optionTags: optionTags, disabled: disabled});
    }

    function inputElementValue(input) {
      return parseInt(input.val(), 10);
    }
  }
})(this);
