(function (root) {
  root.LinearAssetForm2 = {
    initialize: bindEvents
  };

  var template = function(selectedSpeedLimit, formElements) {
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
      var separateValueElement = formElements.singleValueElement(selectedSpeedLimit, "a") + formElements.singleValueElement(selectedSpeedLimit, "b");
      return selectedSpeedLimit.isSplitOrSeparated() ? separateValueElement : formElements.singleValueElement(selectedSpeedLimit);
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

  function bindEvents(selectedSpeedLimit, eventCategory, formElements) {
    var rootElement = $('#feature-attributes');
    var toggleMode = function(readOnly) {
      rootElement.find('.editable .form-control-static').toggle(readOnly);
      rootElement.find('.editable .form-control').toggle(!readOnly);
      rootElement.find('.form-controls').toggle(!readOnly);
      rootElement.find('#separate-limit').toggle(!readOnly);
    };
    var events = function() {
      return _.map(arguments, function(argument) { return eventCategory + ':' + argument; }).join(' ');
    };
    eventbus.on(events('selected', 'cancelled'), function() {
      rootElement.html(template(selectedSpeedLimit, formElements));
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
      rootElement.on('click', '#separate-limit', function() { selectedSpeedLimit.separate(); });
      rootElement.on('click', '.form-controls.speed-limit button.save', function() { selectedSpeedLimit.save(); });
      rootElement.on('click', '.form-controls.speed-limit button.cancel', function() { selectedSpeedLimit.cancel(); });
      toggleMode(applicationModel.isReadOnly());
    });
    eventbus.on(events('unselect'), function() {
      rootElement.empty();
    });
    eventbus.on('application:readOnly', toggleMode);
    eventbus.on(events('valueChanged'), function(selectedSpeedLimit) {
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
  }
})(this);
