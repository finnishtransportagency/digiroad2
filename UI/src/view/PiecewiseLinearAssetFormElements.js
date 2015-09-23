(function(root) {
  root.PiecewiseLinearAssetFormElements = function(unit, editControlLabels, className) {
    return {
      singleValueElement: singleValueElement,
      bindEvents: bindEvents
    };

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
              '<label>' + editControlLabels.disabled + '<input type="radio" name="' + className + '" value="disabled" ' + expiredChecked + '/></label>' +
            '</div>' +
            '<div class="radio">' +
              '<label>' + editControlLabels.enabled + '<input type="radio" name="' + className + '" value="enabled" ' + nonExpiredChecked + '/></label>' +
            '</div>' +
          '</div>' +
          measureInput() +
        '</div>';

      return readOnlyFormGroup + editableFormGroup;
    }

    function bindEvents(rootElement, selectedLinearAsset) {
      var inputElement = rootElement.find('.' + className);
      var toggleElement = rootElement.find('.radio input');

      inputElement.on('input', function(event) {
        selectedLinearAsset.setValue(parseInt($(event.currentTarget).val(), 10));
      });

      toggleElement.on('change', function(event) {
        var disabled = $(event.currentTarget).val() === 'disabled';
        selectedLinearAsset.removeValue();
        inputElement.prop('disabled', disabled);
      });
    }

    function valueString(selectedLinearAsset) {
      if (unit) {
        return selectedLinearAsset.getValue() ? selectedLinearAsset.getValue() + ' ' + unit : '-';
      } else {
        return selectedLinearAsset.isUnknown() ? 'ei ole' : 'on';
      }
    }

    function measureInput() {
      if (unit) {
        return '' +
          '<div class="labelless input-unit-combination input-group">' +
            '<input type="text" class="form-control ' + className + '">' +
            '<span class="input-group-addon">' + unit + '</span>' +
          '</div>';
      } else {
        return '';
      }
    }
  };
})(this);
