(function (root) {
  var template = function(selectedTotalWeightLimit) {
    var modifiedBy = selectedTotalWeightLimit.getModifiedBy() || '-';
    var modifiedDateTime = selectedTotalWeightLimit.getModifiedDateTime() ? ' ' + selectedTotalWeightLimit.getModifiedDateTime() : '';
    var createdBy = selectedTotalWeightLimit.getCreatedBy() || '-';
    var createdDateTime = selectedTotalWeightLimit.getCreatedDateTime() ? ' ' + selectedTotalWeightLimit.getCreatedDateTime() : '';
    var header = selectedTotalWeightLimit.isNew() ?
      '<header>Uusi kokonaispainorajoitus</header>' :
      '<header>Segmentin ID: ' + selectedTotalWeightLimit.getId() + '</header>';
    var disabled = selectedTotalWeightLimit.isDirty() ? '' : 'disabled';
    var buttons = ['<button class="save btn btn-primary" ' + disabled + '>Tallenna</button>',
                   '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');
    var expiredChecked = (selectedTotalWeightLimit.expired() || selectedTotalWeightLimit.isNew()) ? 'checked' : '';
    var nonExpiredChecked = (selectedTotalWeightLimit.expired() || selectedTotalWeightLimit.isNew()) ? '' : 'checked';
    var limit = selectedTotalWeightLimit.getLimit() ? selectedTotalWeightLimit.getLimit() + 'kg' : '-';
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
                 '<div class="choice-group">' +
                   '<div class="radio">' +
                     '<label>Ei painorajoitusta<input type="radio" name="total-weight-limit" value="disabled" ' + expiredChecked + '/></label>' +
                     '<label>Painorajoitus:<input type="radio" name="total-weight-limit" value="enabled" ' + nonExpiredChecked + '/></label>' +
                   '</div>' +
                   '<input type="text" class="form-control total-weight-limit" style="display: none" />' +
                   '<span class="unit-of-measure total-weight-limit">kg</span>' +
                 '</div>' +
                 '<p class="form-control-static total-weight-limit">' + limit + '</p>' +
               '</div>' +
             '</div>' +
           '</div>' +
           '<footer class="total-weight-limit form-controls" style="display: none">' +
             buttons +
           '</footer>';
  };

  var removeWhitespace = function(s) {
    return s.replace(/\s/g, '');
  };

  var setupTotalWeightLimitInput = function(toggleElement, inputElement, selectedTotalWeightLimit) {
    inputElement.val(selectedTotalWeightLimit.getLimit());
    inputElement.prop('disabled', selectedTotalWeightLimit.expired());
    inputElement.on('input', function(event) {
      var value = parseInt(removeWhitespace($(event.currentTarget).val()), 10);
      selectedTotalWeightLimit.setLimit(value);
    });
    toggleElement.change(function(event) {
      var expired = $(event.currentTarget).val();
      var disabled = expired === 'disabled';
      selectedTotalWeightLimit.setExpired(disabled);
      inputElement.prop('disabled', disabled);
    });
  };

  var bindEvents = function(selectedTotalWeightLimit) {
    var rootElement = $('#feature-attributes');
    var toggleMode = function(readOnly) {
      rootElement.find('.editable .form-control-static').toggle(readOnly);
      rootElement.find('.editable .form-control').toggle(!readOnly);
      rootElement.find('.editable .choice-group').toggle(!readOnly);
      rootElement.find('.form-controls').toggle(!readOnly);
    };
    eventbus.on('totalWeightLimit:selected totalWeightLimit:cancelled totalWeightLimit:saved', function() {
      rootElement.html(template(selectedTotalWeightLimit));
      var toggleElement = rootElement.find(".radio input");
      var inputElement = rootElement.find('.total-weight-limit');
      setupTotalWeightLimitInput(toggleElement, inputElement, selectedTotalWeightLimit);
      toggleMode(applicationModel.isReadOnly());
    });
    eventbus.on('totalWeightLimit:unselected', function() {
      rootElement.empty();
    });
    eventbus.on('application:readOnly', toggleMode);
    eventbus.on('totalWeightLimit:limitChanged totalWeightLimit:expirationChanged', function() {
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
