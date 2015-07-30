(function (root) {
  var template = function(selectedSpeedLimit) {
    var SPEED_LIMITS = [120, 100, 80, 70, 60, 50, 40, 30, 20];
    var defaultUnknownOptionTag = ['<option value="" style="display:none;"></option>'];
    var speedLimitOptionTags = defaultUnknownOptionTag.concat(_.map(SPEED_LIMITS, function(value) {
      var selected = value === selectedSpeedLimit.getValue() ? " selected" : "";
      return '<option value="' + value + '"' + selected + '>' + value + '</option>';
    }));
    var modifiedBy = selectedSpeedLimit.getModifiedBy() || '-';
    var modifiedDateTime = selectedSpeedLimit.getModifiedDateTime() ? ' ' + selectedSpeedLimit.getModifiedDateTime() : '';
    var createdBy = selectedSpeedLimit.getCreatedBy() || '-';
    var createdDateTime = selectedSpeedLimit.getCreatedDateTime() ? ' ' + selectedSpeedLimit.getCreatedDateTime() : '';
    var disabled = selectedSpeedLimit.isDirty() ? '' : 'disabled';
    var buttons = ['<button class="save btn btn-primary" ' + disabled + '>Tallenna</button>',
                   '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');
    var title = function() {
      if (selectedSpeedLimit.isUnknown() || selectedSpeedLimit.isSplit()) {
        return '<span>Uusi nopeusrajoitus</span>';
      } else if (selectedSpeedLimit.count() == 1) {
        return '<span>Segmentin ID: ' + selectedSpeedLimit.getId() + '</span>';
      } else {
        return '<span>Nopeusrajoitus</span>';
      }
    };

    var header = '<header>' + title() + '<div class="speed-limit form-controls">' + buttons + '</div></header>';
    return header +
           '<div class="wrapper read-only">' +
             '<div class="form form-horizontal form-dark">' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + createdBy + createdDateTime + '</p>' +
               '</div>' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Muokattu viimeksi: ' + modifiedBy + modifiedDateTime + '</p>' +
               '</div>' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + selectedSpeedLimit.count() + '</p>' +
               '</div>' +
               '<div class="form-group editable">' +
                 '<label class="control-label">Rajoitus</label>' +
                 '<p class="form-control-static">' + (selectedSpeedLimit.getValue() || 'Tuntematon') + '</p>' +
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
    eventbus.on('speedLimit:selected speedLimit:cancelled speedLimit:saved', function() {
      rootElement.html(template(selectedSpeedLimit));
      rootElement.find('.speed-limit').change(function(event) {
        selectedSpeedLimit.setValue(parseInt($(event.currentTarget).find(':selected').attr('value'), 10));
      });
      toggleMode(applicationModel.isReadOnly());
    });
    eventbus.on('speedLimit:unselect', function() {
      rootElement.empty();
    });
    eventbus.on('application:readOnly', toggleMode);
    eventbus.on('speedLimit:valueChanged', function(selectedSpeedLimit) {
      rootElement.find('.speed-limit button').attr('disabled', false);
    });
    rootElement.on('click', '.speed-limit button.save', function() { selectedSpeedLimit.save(); });
    rootElement.on('click', '.speed-limit button.cancel', function() { selectedSpeedLimit.cancel(); });
  };

  root.SpeedLimitForm = {
    initialize: function(selectedSpeedLimit) {
      bindEvents(selectedSpeedLimit);
    }
  };
})(this);
