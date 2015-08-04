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
    var buttons = ['<button class="save btn btn-primary" disabled>Tallenna</button>',
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

    var separatorButton = function() {
      if (selectedSpeedLimit.isSeparable()) {
        return '<div class="form-group">' +
        '<label class="control-label"></label>' +
        '<button class="cancel btn btn-secondary" id="separate-limit">Jaa nopeusrajoitus kaksisuuntaiseksi</button>' +
        '</div>';
      } else {
        return '';
      }
    };

    var limitValueButtons = function() {
      var singleValueElement = function(sideCode) {
        var speedLimitClass = sideCode ? "speed-limit-" + sideCode : "speed-limit";
        var template =  _.template(
          '<div class="form-group editable">' +
            '<% if(sideCode) { %> <span class="marker"><%- sideCode %></span> <% } %>' +
            '<label class="control-label">Rajoitus</label>' +
            '<p class="form-control-static">' + (selectedSpeedLimit.getValue() || 'Tuntematon') + '</p>' +
            '<select class="form-control <%- speedLimitClass %>" style="display: none">' + speedLimitOptionTags.join('') + '</select>' +
          '</div>');
        return template({sideCode: sideCode, speedLimitClass: speedLimitClass});
      };
      var separateValueElement = singleValueElement("a") + singleValueElement("b");
      return selectedSpeedLimit.isSeparated() ? separateValueElement : singleValueElement();
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
               limitValueButtons() +
               separatorButton() +
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
      rootElement.find('#separate-limit').toggle(!readOnly);
    };
    eventbus.on('speedLimit:selected speedLimit:cancelled speedLimit:saved', function() {
      rootElement.html(template(selectedSpeedLimit));
      var extractValue = function(event) {
        return parseInt($(event.currentTarget).find(':selected').attr('value'), 10);
      };

      rootElement.find('.speed-limit').change(function(event) {
        selectedSpeedLimit.setValue(extractValue(event));
      });
      rootElement.find('.speed-limit-a').change(function(event) {
        selectedSpeedLimit.setAValue(extractValue(event));
      });
      rootElement.find('.speed-limit-b').change(function(event) {
        selectedSpeedLimit.setBValue(extractValue(event));
      });
      toggleMode(applicationModel.isReadOnly());
    });
    eventbus.on('speedLimit:unselect', function() {
      rootElement.empty();
    });
    eventbus.on('application:readOnly', toggleMode);
    eventbus.on('speedLimit:valueChanged', function(selectedSpeedLimit) {
      if(selectedSpeedLimit.isSaveable()) {
        rootElement.find('.speed-limit button.save').attr('disabled', false);
      }
      rootElement.find('.speed-limit button.cancel').attr('disabled', false);
    });
    rootElement.on('click', '#separate-limit', function() { selectedSpeedLimit.separate(); });
    rootElement.on('click', '.speed-limit button.save', function() { selectedSpeedLimit.save(); });
    rootElement.on('click', '.speed-limit button.cancel', function() { selectedSpeedLimit.cancel(); });
  };

  root.SpeedLimitForm = {
    initialize: function(selectedSpeedLimit) {
      bindEvents(selectedSpeedLimit);
    }
  };
})(this);
