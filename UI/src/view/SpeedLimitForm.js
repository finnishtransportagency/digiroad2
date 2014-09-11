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
    var modifiedBy = selectedSpeedLimit.getModifiedBy() || '-';
    var modifiedDateTime = selectedSpeedLimit.getModifiedDateTime() ? ' ' + selectedSpeedLimit.getModifiedDateTime() : '';
    var createdBy = selectedSpeedLimit.getCreatedBy() || '-';
    var createdDateTime = selectedSpeedLimit.getCreatedDateTime() ? ' ' + selectedSpeedLimit.getCreatedDateTime() : '';
    return '<header>Segmentin ID: ' + selectedSpeedLimit.getId() + '</header>' +
           '<div class="wrapper read-only">' +
             '<div class="form form-horizontal form-dark">' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Lisätty järjestelmään: ' + createdBy + createdDateTime + '</p>' +
               '</div>' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Muokattu viimeksi: ' + modifiedBy + modifiedDateTime + '</p>' +
               '</div>' +
               '<div class="form-group editable">' +
                 '<label class="control-label">Rajoitus</label>' +
                 '<p class="form-control-static">' + selectedSpeedLimit.getLimit() + '</p>' +
                 '<select class="form-control speed-limit" style="display: none">' + speedLimitOptionTags.join('') + '</select>' +
               '</div>' +
               formFieldTemplate("Päätepiste 1 X", firstPoint.x) +
               formFieldTemplate("Y", firstPoint.y) +
               formFieldTemplate("Päätepiste 2 X", lastPoint.x) +
               formFieldTemplate("Y", lastPoint.y) +
             '</div>' +
           '</div>' +
           '<footer class="form-controls" style="display: none">' +
             '<button class="save btn btn-primary" disabled>Tallenna</button>' +
             '<button class="cancel btn btn-secondary" disabled>Peruuta</button>' +
           '</footer>';
  };

  var bindEvents = function () {
    var rootElement = $('#feature-attributes');
    var toggleMode = function(readOnly) {
      rootElement.find('.editable .form-control-static').toggle(readOnly);
      rootElement.find('.editable .form-control').toggle(!readOnly);
      rootElement.find('.form-controls').toggle(!readOnly);
    };
    eventbus.on('speedLimit:selected speedLimit:cancelled speedLimit:saved', function(selectedSpeedLimit) {
      rootElement.html(template(selectedSpeedLimit));
      rootElement.find('.speed-limit').change(function(event) { selectedSpeedLimit.setLimit(parseInt($(event.currentTarget).find(':selected').attr('value'), 10)); });
      toggleMode(applicationModel.isReadOnly());
    });
    eventbus.on('speedLimit:unselected', function() {
      rootElement.empty();
    });
    eventbus.on('application:readOnly', toggleMode);
    eventbus.on('speedLimit:limitChanged', function(selectedSpeedLimit) {
      rootElement.find('.form-controls button').attr('disabled', false);
      rootElement.find('button.save').click(function() { selectedSpeedLimit.save(); });
      rootElement.find('button.cancel').click(function() { selectedSpeedLimit.cancel(); });
    });
  };

  root.SpeedLimitForm = {
    initialize: function () {
      bindEvents();
    }
  };
})(this);
