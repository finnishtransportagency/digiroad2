(function(root) {
  root.PiecewiseLinearAssetFormElements = function(unit, editControlLabels, className) {
    return {
      singleValueElement: singleValueElement,
      bindEvents: bindEvents
    };

    function generateClassName(sideCode) {
      return sideCode ? className + '-' + sideCode : className;
    }

    function singleValueElement(selectedLinearAsset, sideCode) {
      var expiredChecked = selectedLinearAsset.isUnknown() ? 'checked' : '';
      var nonExpiredChecked = selectedLinearAsset.isUnknown() ? '' : 'checked';

      var readOnlyFormGroup = '' +
        '<div class="form-group read-only">' +
          '<label class="control-label">' + editControlLabels.title + '</label>' +
          '<p class="form-control-static ' + className + '">' + valueString(selectedLinearAsset) + '</p>' +
        '</div>';

      var editableFormGroup = '' +
        '<div class="form-group editable">' +
          '<label class="control-label">' + editControlLabels.title + '</label>' +
          '<div class="choice-group">' +
            '<div class="radio">' +
              '<label>' + editControlLabels.disabled +
                '<input ' + 
                  'class="' + generateClassName(sideCode) + '" ' +
                  'type="radio" name="' + generateClassName(sideCode) + '" ' +
                  'value="disabled" ' + expiredChecked + '/>' +
              '</label>' +
            '</div>' +
            '<div class="radio">' +
              '<label>' + editControlLabels.enabled +
                '<input ' +
                  'class="' + generateClassName(sideCode) + '" ' +
                  'type="radio" name="' + generateClassName(sideCode) + '" ' +
                  'value="enabled" ' + nonExpiredChecked + '/>' +
              '</label>' +
            '</div>' +
          '</div>' +
          measureInput(selectedLinearAsset, sideCode) +
        '</div>';

      return readOnlyFormGroup + editableFormGroup;
    }

    function bindEvents(rootElement, selectedLinearAsset) {
      var inputElement = rootElement.find('.input-unit-combination input.' + generateClassName());
      var inputElementA = rootElement.find('.input-unit-combination input.' + generateClassName('a'));
      var inputElementB = rootElement.find('.input-unit-combination input.' + generateClassName('b'));
      var toggleElement = rootElement.find('.radio input.' + generateClassName());
      var toggleElementA = rootElement.find('.radio input.' + generateClassName('a'));
      var toggleElementB = rootElement.find('.radio input.' + generateClassName('b'));

      var inputElementValue = function(inputElement) {
        var removeWhitespace = function(s) {
          return s.replace(/\s/g, '');
        };
        var value = parseInt(removeWhitespace(inputElement.val()), 10);
        return _.isFinite(value) ? value : undefined;
      };

      inputElement.on('input', function(event) {
        selectedLinearAsset.setValue(inputElementValue(inputElement));
      });

      inputElementA.on('input', function(event) {
        selectedLinearAsset.setAValue(inputElementValue(inputElementA));
      });

      inputElementB.on('input', function(event) {
        selectedLinearAsset.setBValue(inputElementValue(inputElementB));
      });

      toggleElement.on('change', function(event) {
        var disabled = $(event.currentTarget).val() === 'disabled';
        inputElement.prop('disabled', disabled);
        if (disabled) {
          selectedLinearAsset.removeValue();
        } else {
          selectedLinearAsset.setValue(inputElementValue(inputElement));
        }
      });

      toggleElementA.on('change', function(event) {
        var disabled = $(event.currentTarget).val() === 'disabled';
        inputElementA.prop('disabled', disabled);
        if (disabled) {
          selectedLinearAsset.removeAValue();
        } else {
          selectedLinearAsset.setAValue(inputElementValue(inputElementA));
        }
      });

      toggleElementB.on('change', function(event) {
        var disabled = $(event.currentTarget).val() === 'disabled';
        inputElementB.prop('disabled', disabled);
        if (disabled) {
          selectedLinearAsset.removeBValue();
        } else {
          selectedLinearAsset.setBValue(inputElementValue(inputElementB));
        }
      });
    }

    function valueString(selectedLinearAsset) {
      if (unit) {
        return selectedLinearAsset.getValue() ? selectedLinearAsset.getValue() + ' ' + unit : '-';
      } else {
        return selectedLinearAsset.isUnknown() ? 'ei ole' : 'on';
      }
    }

    function measureInput(selectedLinearAsset, sideCode) {
      if (unit) {
        var value = selectedLinearAsset.getValue() ? selectedLinearAsset.getValue() : '';
        return '' +
          '<div class="labelless input-unit-combination input-group">' +
            '<input ' +
              'type="text" ' +
              'class="form-control ' + generateClassName(sideCode) + '" ' +
              'value="' + value  + '">' +
            '<span class="input-group-addon">' + unit + '</span>' +
          '</div>';
      } else {
        return '';
      }
    }
  };
})(this);
