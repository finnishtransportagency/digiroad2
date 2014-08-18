(function (root) {
  var template = function(speedLimit) {
    var startPoint = _.first(speedLimit.points);
    var endPoint = _.last(speedLimit.points);
    var fields = [
      {key: "Rajoitus", value: speedLimit.limit},
      {key: "Alkupiste X", value: startPoint.x},
      {key: "Y", value: startPoint.y},
      {key: "Loppupiste X", value: endPoint.x},
      {key: "Y", value: endPoint.y}
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