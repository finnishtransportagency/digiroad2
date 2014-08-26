(function (root) {
  var template = function(selectedSpeedLimit) {
    var SPEED_LIMITS = [120, 100, 80, 70, 60, 50, 40, 30, 20];
    var speedLimitOptionTags = _.map(SPEED_LIMITS, function(limit) {
      var selected = limit === selectedSpeedLimit.getLimit() ? " selected" : "";
      return '<option value="' + limit + '"' + selected + '>' + limit + '</option>';
    });
    var formFieldTemplate = function(key, value) {
      return '<div class="form-group">' +
               '<label class="control-label">' + key + '</label>' +
               '<p class="form-control-static">' + value + '</p>' +
             '</div>';
    };
    var firstPoint = _.first(selectedSpeedLimit.getEndpoints());
    var lastPoint = _.last(selectedSpeedLimit.getEndpoints());
    return '<header>Segmentin ID: ' + selectedSpeedLimit.getId() + '</header>' +
           '<div class="wrapper read-only">' +
             '<div class="form form-horizontal form-dark">' +
               '<div class="form-group editable">' +
                 '<label class="control-label">Rajoitus</label>' +
                 '<p class="form-control-static">' + selectedSpeedLimit.getLimit() + '</p>' +
                 '<select class="form-control speed-limit" style="display: none">' + speedLimitOptionTags.join('') + '</select>' +
               '</div>' +
               formFieldTemplate("Alkupiste X", firstPoint.x) +
               formFieldTemplate("Y", firstPoint.y) +
               formFieldTemplate("Loppupiste X", lastPoint.x) +
               formFieldTemplate("Y", lastPoint.y) +
               formFieldTemplate("Vaikutussuunta", convertSideCodeToValidityDirection(selectedSpeedLimit.getSideCode(), firstPoint, lastPoint)) +
             '</div>' +
           '</div>' +
           '<footer class="form-controls" style="display: none">' +
             '<button class="save btn btn-primary" disabled>Tallenna</button>' +
             '<button class="cancel btn btn-secondary" disabled>Peruuta</button>' +
           '</footer>';
  };

  var convertSideCodeToValidityDirection = function(sideCode, firstPoint, lastPoint) {
    if (sideCode === validitydirections.bothDirections) return "Molempiin suuntiin";

    var angleThreshold = 20;
    var angle = geometrycalculator.getLineDirectionDegAngle({start: firstPoint, end: lastPoint});
    if (sideCode === validitydirections.oppositeDirection) angle = geometrycalculator.oppositeAngle(angle);

    if (angle >= 90 - angleThreshold / 2 && angle < 90 + angleThreshold / 2) return "Lännestä itään";
    if (angle >= 90 + angleThreshold / 2 && angle < 270 - angleThreshold / 2) return "Pohjoisesta etelään";
    if (angle >= 270 - angleThreshold / 2 && angle < 270 + angleThreshold / 2) return "Idästä länteen";
    return "Etelästä pohjoiseen";
  };

  var bindEvents = function () {
    var toggleMode = function(readOnly) {
      $('#feature-attributes .editable').find('.form-control-static').toggle(readOnly);
      $('#feature-attributes .editable').find('.form-control').toggle(!readOnly);
      $('#feature-attributes').find('.form-controls').toggle(!readOnly);
    };
    eventbus.on('speedLimit:selected', function(selectedSpeedLimit) {
      $('#feature-attributes').html(template(selectedSpeedLimit));
      $('#feature-attributes .speed-limit').change(function(event) { selectedSpeedLimit.setLimit(parseInt($(event.currentTarget).find(':selected').attr('value'), 10)); });
      toggleMode(applicationModel.isReadOnly());
    });
    eventbus.on('speedLimit:unselected', function() {
      $('#feature-attributes').empty();
    });
    eventbus.on('application:readOnly', toggleMode);
    eventbus.on('speedLimit:limitChanged', function() {
      $('#feature-attributes .form-controls button').attr('disabled', false);
    });
  };

  root.SpeedLimitForm = {
    initialize: function () {
      bindEvents();
    }
  };
})(this);
