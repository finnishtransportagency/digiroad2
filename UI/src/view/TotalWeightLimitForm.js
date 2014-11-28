(function (root) {
  var template = function(selectedTotalWeightLimit) {
    var formFieldTemplate = function(key, value) {
      return '<div class="form-group">' +
               '<label class="control-label">' + key + '</label>' +
               '<p class="form-control-static">' + value + '</p>' +
             '</div>';
    };
    var firstPoint = _.first(selectedTotalWeightLimit.getEndpoints());
    var lastPoint = _.last(selectedTotalWeightLimit.getEndpoints());
    var modifiedBy = selectedTotalWeightLimit.getModifiedBy() || '-';
    var modifiedDateTime = selectedTotalWeightLimit.getModifiedDateTime() ? ' ' + selectedTotalWeightLimit.getModifiedDateTime() : '';
    var createdBy = selectedTotalWeightLimit.getCreatedBy() || '-';
    var createdDateTime = selectedTotalWeightLimit.getCreatedDateTime() ? ' ' + selectedTotalWeightLimit.getCreatedDateTime() : '';
    var header = selectedTotalWeightLimit.getId() ?
                   '<header>Segmentin ID: ' + selectedTotalWeightLimit.getId() + '</header>' :
                   '<header>Uusi kokonaispainorajoitus</header>';
    var disabled = selectedTotalWeightLimit.isDirty() ? '' : 'disabled';
    var buttons = ['<button class="save btn btn-primary" ' + disabled + '>Tallenna</button>',
                   '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');
    return header +
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
                 '<p class="form-control-static">' + selectedTotalWeightLimit.getLimit() + ' kg</p>' +
                 '<input type="text" class="form-control total-weight-limit" style="display: none" />' +
               '</div>' +
               formFieldTemplate("Päätepiste 1 X", firstPoint ? firstPoint.x : '') +
               formFieldTemplate("Y", firstPoint ? firstPoint.y : '') +
               formFieldTemplate("Päätepiste 2 X", lastPoint ? lastPoint.x : '') +
               formFieldTemplate("Y", lastPoint ? lastPoint.y : '') +
             '</div>' +
           '</div>' +
           '<footer class="total-weight-limit form-controls" style="display: none">' +
             buttons +
           '</footer>';
  };

  var setupTotalWeightLimitInput = function(inputElement, selectedTotalWeightLimit) {
    inputElement.val(selectedTotalWeightLimit.getLimit());
    inputElement.on('input', function(event) {
      selectedTotalWeightLimit.setLimit(parseInt($(event.currentTarget).val(), 10));
    });
  };

  var bindEvents = function(selectedTotalWeightLimit) {
    var rootElement = $('#feature-attributes');
    var toggleMode = function(readOnly) {
      rootElement.find('.editable .form-control-static').toggle(readOnly);
      rootElement.find('.editable .form-control').toggle(!readOnly);
      rootElement.find('.form-controls').toggle(!readOnly);
    };
    eventbus.on('totalWeightLimit:selected totalWeightLimit:cancelled totalWeightLimit:saved', function(totalWeightLimit) {
      rootElement.html(template(selectedTotalWeightLimit));
      setupTotalWeightLimitInput(rootElement.find('.total-weight-limit'), selectedTotalWeightLimit);
      toggleMode(applicationModel.isReadOnly());
    });
    eventbus.on('totalWeightLimit:unselected', function() {
      rootElement.empty();
    });
    eventbus.on('application:readOnly', toggleMode);
    eventbus.on('totalWeightLimit:limitChanged', function(selectedTotalWeightLimit) {
      rootElement.find('.form-controls button').attr('disabled', false);
    });
    rootElement.on('click', '.total-weight-limit button.save', function() {
      if (selectedTotalWeightLimit.isNew()) {
        selectedTotalWeightLimit.saveSplit();
      } else {
        selectedTotalWeightLimit.save();
      }
    });
    rootElement.on('click', '.total-weight-limit button.cancel', function() {
      if (selectedTotalWeightLimit.isNew()) {
        selectedTotalWeightLimit.cancelSplit();
      } else {
        selectedTotalWeightLimit.cancel();
      }
    });
  };

  root.TotalWeightLimitForm = {
    initialize: function(selectedTotalWeightLimit) {
      bindEvents(selectedTotalWeightLimit);
    }
  };
})(this);
