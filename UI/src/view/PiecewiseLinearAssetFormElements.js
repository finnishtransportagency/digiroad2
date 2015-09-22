(function(root) {
  root.PiecewiseLinearAssetFormElements = {
    singleValueElement: singleValueElement
  };

  function singleValueElement(selectedLinearAssetModel, sideCode) {
    var SPEED_LIMITS = [120, 100, 90, 80, 70, 60, 50, 40, 30, 20];
    var defaultUnknownOptionTag = ['<option value="" style="display:none;"></option>'];
    var speedLimitOptionTags = defaultUnknownOptionTag.concat(_.map(SPEED_LIMITS, function(value) {
      var selected = value === selectedLinearAssetModel.getValue() ? " selected" : "";
      return '<option value="' + value + '"' + selected + '>' + value + '</option>';
    }));
    var speedLimitClass = sideCode ? "speed-limit-" + sideCode : "speed-limit";
    var template =  _.template(
      '<div class="form-group editable">' +
        '<% if(sideCode) { %> <span class="marker"><%- sideCode %></span> <% } %>' +
        '<label class="control-label">Rajoitus</label>' +
        '<p class="form-control-static">' + (selectedLinearAssetModel.getValue() || 'Tuntematon') + '</p>' +
        '<select class="form-control <%- speedLimitClass %>" style="display: none">' + speedLimitOptionTags.join('') + '</select>' +
      '</div>');
    return template({sideCode: sideCode, speedLimitClass: speedLimitClass});
  }
})(this);
