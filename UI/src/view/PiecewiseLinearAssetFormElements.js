(function(root) {
  function generateClassName(className, sideCode) {
    return sideCode ? className + '-' + sideCode : className;
  }

  function sideCodeMarker(sideCode) {
    if (_.isUndefined(sideCode)) {
      return '';
    } else {
      return '<span class="marker">' + sideCode + '</span>';
    }
  }

  function singleValueEditElement(currentValue, sideCode, editControlLabels, input, className) {
    var withoutValue = _.isUndefined(currentValue) ? 'checked' : '';
    var withValue = _.isUndefined(currentValue) ? '' : 'checked';
    return '' +
      sideCodeMarker(sideCode) +
      '<div class="choice-group">' +
      '<div class="radio">' +
      '<label>' + editControlLabels.disabled +
      '<input ' +
      'class="' + generateClassName(className, sideCode) + '" ' +
      'type="radio" name="' + generateClassName(className, sideCode) + '" ' +
      'value="disabled" ' + withoutValue + '/>' +
      '</label>' +
      '</div>' +
      '<div class="radio">' +
      '<label>' + editControlLabels.enabled +
      '<input ' +
      'class="' + generateClassName(className, sideCode) + '" ' +
      'type="radio" name="' + generateClassName(className, sideCode) + '" ' +
      'value="enabled" ' + withValue + '/>' +
      '</label>' +
      '</div>' +
      input +
      '</div>';
  }

  function bindEventsLol(rootElement, selectedLinearAsset, sideCode, inputElementValue, unit, defaultValue, className) {
    var inputElement = rootElement.find('.input-unit-combination .' + generateClassName(className, sideCode));
    var toggleElement = rootElement.find('.radio input.' + generateClassName(className, sideCode));
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

  function singleValueElementLol(currentValue, sideCode, editControlLabels, className, valueString, measureInput) {
    return '' +
      '<div class="form-group editable">' +
      '<label class="control-label">' + editControlLabels.title + '</label>' +
      '<p class="form-control-static ' + className + '" style="display:none;">' + valueString(currentValue) + '</p>' +
      singleValueEditElement(currentValue, sideCode, editControlLabels, measureInput(currentValue, sideCode), className) +
      '</div>';
  }

  root.PiecewiseLinearAssetFormElements = function(unit, editControlLabels, className, defaultValue) {
    return {
      singleValueElement: singleValueElement,
      bindEvents: bindEvents
    };

    function singleValueElement(currentValue, sideCode) {
      return singleValueElementLol(currentValue, sideCode, editControlLabels, className, valueString, measureInput);
    }

    function inputElementValue(input) {
      var removeWhitespace = function(s) {
        return s.replace(/\s/g, '');
      };
      var value = parseInt(removeWhitespace(input.val()), 10);
      return _.isFinite(value) ? value : 0;
    }

    function bindEvents(rootElement, selectedLinearAsset, sideCode) {
      bindEventsLol(rootElement, selectedLinearAsset, sideCode, inputElementValue, unit, defaultValue,className);
    }

    function valueString(currentValue) {
      if (unit) {
        return currentValue ? currentValue + ' ' + unit : '-';
      } else {
        return currentValue ? 'ei ole' : 'on';
      }
    }

    function measureInput(currentValue, sideCode) {
      if (unit) {
        var value = currentValue ? currentValue : '';
        var disabled = _.isUndefined(currentValue) ? 'disabled' : '';
        return '' +
          '<div class="input-unit-combination input-group">' +
            '<input ' +
              'type="text" ' +
              'class="form-control ' + generateClassName(className, sideCode) + '" ' +
              'value="' + value  + '" ' + disabled + ' >' +
            '<span class="input-group-addon ' + className + '">' + unit + '</span>' +
          '</div>';
      } else {
        return '';
      }
    }
  };



  root.DropDownFormElement = function DropDownFormElementConstructor(unit, editControlLabels, className, defaultValue) {

    return {
      singleValueElement: singleValueElement,
      bindEvents: bindEvents
    };

    function valueString(currentValue) {
      return currentValue ? currentValue + ' ' + unit : '-';
    }

    function singleValueElement(currentValue, sideCode) {
      return singleValueElementLol(currentValue, sideCode, editControlLabels, className, valueString, measureInput);
    }

    function measureInput(currentValue, sideCode) {
      var SPEED_LIMITS = [100, 80, 70, 60];
      var speedLimitOptionTags = _.map(SPEED_LIMITS, function(value) {
        var selected = value === currentValue ? " selected" : "";
        return '<option value="' + value + '"' + selected + '>' + value + '</option>';
      }).join('');
      var speedLimitClass = sideCode ? className + sideCode : className;

      var value = currentValue ? currentValue : '';
      var disabled = _.isUndefined(currentValue) ? 'disabled' : '';

      var template =  _.template(
        '<div class="input-unit-combination">' +
        '<% if(sideCode) { %> <span class="marker"><%- sideCode %></span> <% } %>' +
        '<select <%- disabled %> class="form-control <%- speedLimitClass %>" ><%= optionTags %></select>' +
        '</div>');
      return template({sideCode: sideCode, speedLimitClass: speedLimitClass, optionTags: speedLimitOptionTags, disabled: disabled});
    }

    function inputElementValue(input) {
      return parseInt(input.val(), 10);
    }

    function bindEvents(rootElement, selectedLinearAsset, sideCode) {
      bindEventsLol(rootElement, selectedLinearAsset, sideCode, inputElementValue, unit,defaultValue,className);
    }
  };
})(this);
