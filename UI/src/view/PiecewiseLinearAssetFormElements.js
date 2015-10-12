(function(root) {
  root.PiecewiseLinearAssetFormElements = function(unit, editControlLabels, className, defaultValue) {
    return {
      singleValueElement: singleValueElement,
      bindEvents: bindEvents
    };

    function generateClassName(sideCode) {
      return sideCode ? className + '-' + sideCode : className;
    }

    function singleValueEditElement(currentValue, isUnknown, sideCode) {
      var withoutValue = currentValue ? '' : 'checked';
      var withValue = currentValue ? 'checked' : '';
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
          measureInput(currentValue, isUnknown, sideCode) +
        '</div>';
    }

    function singleValueElement(currentValue, isUnknown, sideCode) {
      return '' +
        '<div class="form-group editable">' +
          '<label class="control-label">' + editControlLabels.title + '</label>' +
          '<p class="form-control-static ' + className + '" style="display:none;">' + valueString(currentValue, isUnknown) + '</p>' +
          singleValueEditElement(currentValue, isUnknown, sideCode) +
        '</div>';
    }

    function sideCodeMarker(sideCode) {
      if (_.isUndefined(sideCode)) {
        return '';
      } else {
        return '<span class="marker">' + sideCode + '</span>';
      }
    }

    function inputElementValue(input) {
      var removeWhitespace = function(s) {
        return s.replace(/\s/g, '');
      };
      var value = parseInt(removeWhitespace(input.val()), 10);
      return _.isFinite(value) ? value : 0;
    }

    function bindEvents(rootElement, selectedLinearAsset, sideCode) {
      var inputElement = rootElement.find('.input-unit-combination input.' + generateClassName(sideCode));
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

    function valueString(currentValue, isUnknown) {
      if (unit) {
        return currentValue ? currentValue + ' ' + unit : '-';
      } else {
        return isUnknown ? 'ei ole' : 'on';
      }
    }

    function measureInput(currentValue, isUnknown, sideCode) {
      if (unit) {
        var value = currentValue ? currentValue : '';
        var disabled = isUnknown ? 'disabled' : '';
        return '' +
          '<div class="input-unit-combination input-group">' +
            '<input ' +
              'type="text" ' +
              'class="form-control ' + generateClassName(sideCode) + '" ' +
              'value="' + value  + '" ' + disabled + ' >' +
            '<span class="input-group-addon ' + className + '">' + unit + '</span>' +
          '</div>';
      } else {
        return '';
      }
    }
  };
})(this);
