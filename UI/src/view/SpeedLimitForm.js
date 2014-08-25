(function (root) {
  var template = function(speedLimit) {
    var SPEED_LIMITS = [120, 100, 80, 70, 60, 50, 40, 30, 20];
    var speedLimitOptionTags = _.map(SPEED_LIMITS, function(limit) {
      var selected = limit === speedLimit.limit ? " selected" : "";
      return '<option value="' + limit + '"' + selected + '>' + limit + '</option>';
    });
    var formFieldTemplate = function(key, value) {
      return '<div class="form-group">' +
               '<label class="control-label">' + key + '</label>' +
               '<p class="form-control-static">' + value + '</p>' +
             '</div>';
    };
    var firstPoint = _.first(speedLimit.endpoints);
    var lastPoint = _.last(speedLimit.endpoints);
    return '<header>Segmentin ID: ' + speedLimit.id + '</header>' +
           '<div class="wrapper read-only">' +
             '<div class="form form-horizontal form-dark">' +
               '<div class="form-group editable">' +
                 '<label class="control-label">Rajoitus</label>' +
                 '<p class="form-control-static">' + speedLimit.limit + '</p>' +
                 '<select class="form-control" style="display: none">' + speedLimitOptionTags.join('') + '</select>' +
               '</div>' +
               formFieldTemplate("Alkupiste X", firstPoint.x) +
               formFieldTemplate("Y", firstPoint.y) +
               formFieldTemplate("Loppupiste X", lastPoint.x) +
               formFieldTemplate("Y", lastPoint.y) +
             '</div>' +
           '</div>' +
           '<footer class="form-controls" style="display: none">' +
             '<button class="save btn btn-primary" disabled>Tallenna</button>' +
             '<button class="cancel btn btn-secondary" disabled>Peruuta</button>' +
           '</footer>';
  };


  var bindEvents = function () {
    var toggleMode = function(readOnly) {
      $('#feature-attributes .editable').find('.form-control-static').toggle(readOnly);
      $('#feature-attributes .editable').find('.form-control').toggle(!readOnly);
      $('#feature-attributes').find('.form-controls').toggle(!readOnly);
    };
    eventbus.on('speedLimit:selected', function(speedLimit) {
      $('#feature-attributes').html(template(speedLimit));
      toggleMode(applicationModel.isReadOnly());
    });
    eventbus.on('speedLimit:unselected', function() {
      $('#feature-attributes').empty();
    });
    eventbus.on('application:readOnly', toggleMode);
  };

  root.SpeedLimitForm = {
    initialize: function () {
      bindEvents();
    }
  };
})(this);
