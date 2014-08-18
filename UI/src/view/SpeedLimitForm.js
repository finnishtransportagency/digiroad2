(function (root) {
  var template = function(speedLimit) {
    return '<div class="wrapper read-only">' +
             '<div class="form form-horizontal form-dark">' +
                '<div class="form-group">' +
                  '<label class="control-label">Rajoitus</label>' +
                  '<p class="form-control-static">' + speedLimit.limit + '</p>' +
                '</div>' +
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