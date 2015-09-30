(function(root) {
  root.PiecewiseLinearAssetFormElements = function(unit, editControlLabels, className, defaultValue) {
    return {
      singleValueElement: singleValueElement,
      bindEvents: bindEvents,
      bindMassUpdateDialog: bindMassUpdateDialog
    };

    function generateClassName(sideCode) {
      return sideCode ? className + '-' + sideCode : className;
    }

    function singleValueElement(currentValue, isUnknown, sideCode) {
      var withoutValue = isUnknown ? 'checked' : '';
      var withValue = isUnknown ? '' : 'checked';

      var readOnlyFormGroup = '' +
        '<div class="form-group read-only">' +
          '<label class="control-label">' + editControlLabels.title + '</label>' +
          '<p class="form-control-static ' + className + '">' + valueString(currentValue, isUnknown) + '</p>' +
        '</div>';

      var editableFormGroup = '' +
        '<div class="form-group editable">' +
          sideCodeMarker(sideCode) +
          '<label class="control-label">' + editControlLabels.title + '</label>' +
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
          '</div>' +
        '</div>';

      return readOnlyFormGroup + editableFormGroup;
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
      return _.isFinite(value) ? value : undefined;
    }

    function bindMassUpdateDialog(rootElement, valueElement) {
      var inputElement = rootElement.find('.input-unit-combination input.' + className);
      var toggleElement = rootElement.find('.radio input.' + className);
      function setValue(value){
        valueElement.text(value);
      }
      inputElement.on('input', function() {
        setValue(inputElementValue(inputElement));
      });

      toggleElement.on('change', function(event) {
        var disabled = $(event.currentTarget).val() === 'disabled';
        inputElement.prop('disabled', disabled);
        if (disabled) {
          setValue('');
        } else {
          var value = unit ? inputElementValue(inputElement) : defaultValue;
          setValue(value);
        }
      });
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
            '<span class="input-group-addon">' + unit + '</span>' +
          '</div>';
      } else {
        return '';
      }
    }
  };
})(this);
