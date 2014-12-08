(function (root) {
  var template = function(selectedSpeedLimit) {
    var SPEED_LIMITS = [120, 100, 80, 70, 60, 50, 40, 30, 20];
    var speedLimitOptionTags = _.map(SPEED_LIMITS, function(limit) {
      var selected = limit === selectedSpeedLimit.getLimit() ? " selected" : "";
      return '<option value="' + limit + '"' + selected + '>' + limit + '</option>';
    });
    var modifiedBy = selectedSpeedLimit.getModifiedBy() || '-';
    var modifiedDateTime = selectedSpeedLimit.getModifiedDateTime() ? ' ' + selectedSpeedLimit.getModifiedDateTime() : '';
    var createdBy = selectedSpeedLimit.getCreatedBy() || '-';
    var createdDateTime = selectedSpeedLimit.getCreatedDateTime() ? ' ' + selectedSpeedLimit.getCreatedDateTime() : '';
    var header = selectedSpeedLimit.getId() ?
                   '<header>Segmentin ID: ' + selectedSpeedLimit.getId() + '</header>' :
                   '<header>Uusi nopeusrajoitus</header>';
    var disabled = selectedSpeedLimit.isDirty() ? '' : 'disabled';
    var buttons = ['<button class="save btn btn-primary" ' + disabled + '>Tallenna</button>',
                   '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');
    return header +
           '<div class="wrapper read-only">' +
             '<div class="form form-horizontal form-dark">' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + createdBy + createdDateTime + '</p>' +
               '</div>' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Muokattu viimeksi: ' + modifiedBy + modifiedDateTime + '</p>' +
               '</div>' +
               '<div class="form-group editable">' +
                 '<label class="control-label">Rajoitus</label>' +
                 '<p class="form-control-static">' + selectedSpeedLimit.getLimit() + '</p>' +
                 '<select class="form-control speed-limit" style="display: none">' + speedLimitOptionTags.join('') + '</select>' +
               '</div>' +
             '</div>' +
           '</div>' +
           '<footer class="speed-limit form-controls" style="display: none">' +
             buttons +
           '</footer>';
  };

  var bindEvents = function(selectedSpeedLimit) {
    var rootElement = $('#feature-attributes');
    var toggleMode = function(readOnly) {
      rootElement.find('.editable .form-control-static').toggle(readOnly);
      rootElement.find('.editable .form-control').toggle(!readOnly);
      rootElement.find('.form-controls').toggle(!readOnly);
    };
    eventbus.on('speedLimit:selected speedLimit:cancelled speedLimit:saved', function(speedLimit) {
      rootElement.html(template(selectedSpeedLimit));
      rootElement.find('.speed-limit').change(function(event) {
        selectedSpeedLimit.setLimit(parseInt($(event.currentTarget).find(':selected').attr('value'), 10));
      });
      toggleMode(applicationModel.isReadOnly());
    });
    eventbus.on('speedLimit:unselected', function() {
      rootElement.empty();
    });
    eventbus.on('application:readOnly', toggleMode);
    eventbus.on('speedLimit:limitChanged', function(selectedSpeedLimit) {
      rootElement.find('.form-controls button').attr('disabled', false);
    });
    rootElement.on('click', '.speed-limit button.save', function() {
      if (selectedSpeedLimit.isNew()) {
        selectedSpeedLimit.saveSplit();
      } else {
        selectedSpeedLimit.save();
      }
    });
    rootElement.on('click', '.speed-limit button.cancel', function() {
      if (selectedSpeedLimit.isNew()) {
        selectedSpeedLimit.cancelSplit();
      } else {
        selectedSpeedLimit.cancel();
      }
    });
  };

  root.SpeedLimitForm = {
    initialize: function(selectedSpeedLimit) {
      bindEvents(selectedSpeedLimit);
    }
  };
})(this);
