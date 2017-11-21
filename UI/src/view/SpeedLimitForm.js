(function (root) {
  var unit = 'km/h';
  var template = function(selectedSpeedLimit) {
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
        return '<div class="form-group editable">' +
        '<label class="control-label"></label>' +
        '<button class="cancel btn btn-secondary" id="separate-limit">Jaa nopeusrajoitus kaksisuuntaiseksi</button>' +
        '</div>';
      } else {
        return '';
      }
    };

    var limitValueButtons = function() {
      var singleValueElement = function(sideCode) {
        var SPEED_LIMITS = [120, 100, 90, 80, 70, 60, 50, 40, 30, 20];
        var defaultUnknownOptionTag = ['<option value="" style="display:none;"></option>'];
        var speedLimitOptionTags = defaultUnknownOptionTag.concat(_.map(SPEED_LIMITS, function(value) {
          var selected = value === selectedSpeedLimit.getValue() ? " selected" : "";
          return '<option value="' + value + '"' + selected + '>' + value + ' ' + unit + '</option>';
        }));
        var speedLimitClass = sideCode ? "speed-limit-" + sideCode : "speed-limit";
        var template =  _.template(
          '<div class="form-group editable">' +
            '<% if(sideCode) { %> <span class="marker"><%- sideCode %></span> <% } %>' +
            '<label class="control-label">Rajoitus</label>' +
            '<p class="form-control-static">' + ((selectedSpeedLimit.getValue() + ' ' + unit) || 'Tuntematon') + '</p>' +
            '<select class="form-control <%- speedLimitClass %>" style="display: none">' + speedLimitOptionTags.join('') + '</select>' +
          '</div>');
        return template({sideCode: sideCode, speedLimitClass: speedLimitClass});
      };
      var separateValueElement = singleValueElement("a") + singleValueElement("b");
      return selectedSpeedLimit.isSplitOrSeparated() ? separateValueElement : singleValueElement();
    };

    var header = '<header>' + title() + '<div class="speed-limit form-controls">' + buttons + '</div></header>';
    return header +
           '<div class="wrapper read-only">' +
             '<div class="form form-horizontal form-dark linear-asset">' +
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

  var renderLinktoWorkList = function renderLinktoWorkList() {
    var notRendered = !$('#work-list-link').length;
    if(notRendered) {
      $('#information-content').append('' +
        '<div class="form form-horizontal">' +
          '<a id="work-list-link" class="unknown-speed-limits" href="#work-list/speedLimit">Tuntemattomien nopeusrajoitusten lista</a>' +
        '</div>');
    }
  };

  var bindEvents = function(selectedSpeedLimit) {
    var rootElement = $('#feature-attributes');
    var toggleMode = function(readOnly) {
      rootElement.find('.editable .form-control-static').toggle(readOnly);
      rootElement.find('.editable .form-control').toggle(!readOnly);
      rootElement.find('.form-controls').toggle(!readOnly);
      rootElement.find('#separate-limit').toggle(!readOnly);
      rootElement.find('.editable .form-control').prop('disabled', readOnly);
    };
    eventbus.on('speedLimit:selected speedLimit:cancelled', function() {
      rootElement.html(template(selectedSpeedLimit));
      var extractValue = function(event) {
        return parseInt($(event.currentTarget).find(':selected').attr('value'), 10);
      };

      rootElement.find('select.speed-limit').change(function(event) {
        selectedSpeedLimit.setValue(extractValue(event));
      });
      rootElement.find('select.speed-limit-a').change(function(event) {
        selectedSpeedLimit.setAValue(extractValue(event));
      });
      rootElement.find('select.speed-limit-b').change(function(event) {
        selectedSpeedLimit.setBValue(extractValue(event));
      });
      rootElement.find('#separate-limit').on('click', function() { selectedSpeedLimit.separate(); });
      rootElement.find('.form-controls.speed-limit button.save').on('click', function() { selectedSpeedLimit.save(); });
      rootElement.find('.form-controls.speed-limit button.cancel').on('click', function() { selectedSpeedLimit.cancel(); });
      toggleMode(validateAdministrativeClass(selectedSpeedLimit) || applicationModel.isReadOnly());
    });
    eventbus.on('speedLimit:unselect', function() {
      rootElement.empty();
    });
    eventbus.on('application:readOnly', function(readOnly){
      toggleMode(validateAdministrativeClass(selectedSpeedLimit) || readOnly);
    });
    eventbus.on('speedLimit:valueChanged', function(selectedSpeedLimit) {
      rootElement.find('.form-controls.speed-limit button.save').attr('disabled', !selectedSpeedLimit.isSaveable());
      rootElement.find('.form-controls.speed-limit button.cancel').attr('disabled', false);
    });
    eventbus.on('layer:selected', function(layer) {
      if(layer === 'speedLimit') {
        renderLinktoWorkList();
      }
      else {
        $('#work-list-link').parent().remove();
      }
    });
  };

  function validateAdministrativeClass(selectedSpeedLimit){
    var selectedSpeedLimits = _.filter(selectedSpeedLimit.get(), function (selected) {
      return editConstrains(selected);
    });
    return !_.isEmpty(selectedSpeedLimits);
  }

  var editConstrains = function(selectedAsset) {
    return false;
    //TODO revert this when DROTH-909
    //return selectedAsset.administrativeClass === 'State';
  };

  root.SpeedLimitForm = {
    initialize: function(selectedSpeedLimit) {
      bindEvents(selectedSpeedLimit);
    }
  };
})(this);
