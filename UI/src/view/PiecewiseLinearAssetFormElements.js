(function(root) {
  root.PiecewiseLinearAssetFormElements = function(unit, editControlLabels, className) {
    function singleValueElement(selectedLinearAsset, sideCode) {
      var expiredChecked = selectedLinearAsset.isUnknown() ? 'checked' : '';
      var nonExpiredChecked = selectedLinearAsset.isUnknown() ? '' : 'checked';
      return '<div class="form-group editable">' +
        '<label class="control-label">' + editControlLabels.title + '</label>' +
        '<p class="form-control-static ' + className + '">' + valueString(selectedLinearAsset) + '</p>' +
        '<div class="choice-group form-control">' +
          '<div class="radio">' +
            '<label>' + editControlLabels.disabled + '<input type="radio" name="' + className + '" value="disabled" ' + expiredChecked + '/></label>' +
          '</div>' +
          '<div class="radio">' +
            '<label>' + editControlLabels.enabled + '<input type="radio" name="' + className + '" value="enabled" ' + nonExpiredChecked + '/></label>' +
          '</div>' +
        '</div>' +
      '</div>';
    }

    function valueString(selectedLinearAsset) {
      if (unit) {
        return selectedLinearAsset.getValue() ? selectedLinearAsset.getValue() + ' ' + unit : '-';
      } else {
        return selectedLinearAsset.isUnknown() ? 'ei ole' : 'on';
      }
    }

    return {
      singleValueElement: singleValueElement
    };
  };
})(this);
