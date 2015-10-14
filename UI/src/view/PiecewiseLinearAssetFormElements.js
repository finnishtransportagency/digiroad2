(function(root) {

  root.getFormElement = function getFormElement(unit, editControlLabels, className, defaultValue, elementType) {
    var formElem = (elementType === 'dropdown') ? dropDownFormElement(unit) : inputFormElement(unit);
    return {
      singleValueElement:  _.partial(singleValueElement, formElem.measureInput, formElem.valueString),
      bindEvents: _.partial(bindEvents, formElem.inputElementValue)
    };

    function generateClassName(sideCode) {
      return sideCode ? className + '-' + sideCode : className;
    }

    function sideCodeMarker(sideCode) {
      if (_.isUndefined(sideCode)) {
        return '';
      } else {
        return '<span class="marker">' + sideCode + '</span>';
      }
    }

    function singleValueEditElement(currentValue, sideCode, input) {
      var withoutValue = _.isUndefined(currentValue) ? 'checked' : '';
      var withValue = _.isUndefined(currentValue) ? '' : 'checked';
      return '' +
        sideCodeMarker(sideCode) +
        '<div class="choice-group">' +
        '<div class="radio">' +
        '<label>' + editControlLabels.disabled +
        '<input ' +
        'class="' + generateClassName(sideCode) + '" ' +
        'type="radio" name="' + generateClassName(sideCode) + '" ' +
        'value="disabled" ' + withoutValue + '/>' +
        '</label>' +
        '</div>' +
        '<div class="radio">' +
        '<label>' + editControlLabels.enabled +
        '<input ' +
        'class="' + generateClassName(sideCode) + '" ' +
        'type="radio" name="' + generateClassName(sideCode) + '" ' +
        'value="enabled" ' + withValue + '/>' +
        '</label>' +
        '</div>' +
        input +
        '</div>';
    }

    function bindEvents(inputElementValue, rootElement, selectedLinearAsset, sideCode, unit) {
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

      inputElement.on('input', function() {
        setValue(inputElementValue(inputElement));
      });

      toggleElement.on('change', function(event) {
        var disabled = $(event.currentTarget).val() === 'disabled';
        inputElement.prop('disabled', disabled);
        if (disabled) {
          removeValue();
        } else {
          var value = unit ? inputElementValue(inputElement) : defaultValue;
          setValue(value);
        }
      });
    }

    function singleValueElement(measureInput, valueString, currentValue, sideCode) {
      return '' +
        '<div class="form-group editable">' +
        '<label class="control-label">' + editControlLabels.title + '</label>' +
        '<p class="form-control-static ' + className + '" style="display:none;">' + valueString(currentValue) + '</p>' +
        singleValueEditElement(currentValue, sideCode, measureInput(currentValue, generateClassName(sideCode))) +
        '</div>';
    }
  };

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
        return currentValue ? 'ei ole' : 'on';
      }
    }

    function measureInput(currentValue, className) {
      if (unit) {
        var value = currentValue ? currentValue : '';
        var disabled = _.isUndefined(currentValue) ? 'disabled' : '';
        return '' +
          '<div class="input-unit-combination input-group">' +
            '<input ' +
              'type="text" ' +
              'class="form-control ' + className + '" ' +
              'value="' + value  + '" ' + disabled + ' >' +
            '<span class="input-group-addon ' + className + '">' + unit + '</span>' +
          '</div>';
      } else {
        return '';
      }
    }
  }

  function dropDownFormElement(unit) {
    return {
      inputElementValue: inputElementValue,
      valueString: valueString,
      measureInput: measureInput
    };

    function valueString(currentValue) {
      return currentValue ? currentValue + ' ' + unit : '-';
    }

    function measureInput(currentValue, className) {
      var SPEED_LIMITS = [100, 80, 70, 60];
      var speedLimitOptionTags = _.map(SPEED_LIMITS, function(value) {
        var selected = value === currentValue ? " selected" : "";
        return '<option value="' + value + '"' + selected + '>' + value + '</option>';
      }).join('');
      var value = currentValue ? currentValue : '';
      var disabled = _.isUndefined(currentValue) ? 'disabled' : '';

      var template =  _.template(
        '<div class="input-unit-combination">' +
        '<select <%- disabled %> class="form-control <%- speedLimitClass %>" ><%= optionTags %></select>' +
        '</div>');
      return template({speedLimitClass: className, optionTags: speedLimitOptionTags, disabled: disabled});
    }

    function inputElementValue(input) {
      return parseInt(input.val(), 10);
    }

  }
})(this);
