(function (root) {
  var template = function(speedLimit) {
    var fields = [
      {key: "Rajoitus", value: speedLimit.limit}
    ];

    var formElementTemplate = _.template(
      '<div class="form-group">' +
        '<label class="control-label">{{ key }}</label>' +
        '<p class="form-control-static">{{ value }}</p>' +
      '</div>'
    );

    return '<header>Segmentin ID: ' + speedLimit.id + '</header>' +
           '<div class="wrapper read-only">' +
             '<div class="form form-horizontal form-dark">' +
               _.map(fields, formElementTemplate).join('') +
             '</div>' +
           '</div>';
  };

  var bindEvents = function () {
    eventbus.on('speedLimit:selected', function(speedLimit) {
      $('#feature-attributes').html(template(speedLimit));
    });
    eventbus.on('speedLimit:unselected', function() {
      $("#feature-attributes").empty();
    });
  };

  root.SpeedLimitForm = {
    initialize: function () {
      bindEvents();
    }
  };
})(this);
