(function (root) {
  root.WeightLimitForm = function(selectedWeightLimit, newWeightLimitTitle, className, eventCategory) {
    var template = function(selectedWeightLimit) {
      var modifiedBy = selectedWeightLimit.getModifiedBy() || '-';
      var modifiedDateTime = selectedWeightLimit.getModifiedDateTime() ? ' ' + selectedWeightLimit.getModifiedDateTime() : '';
      var createdBy = selectedWeightLimit.getCreatedBy() || '-';
      var createdDateTime = selectedWeightLimit.getCreatedDateTime() ? ' ' + selectedWeightLimit.getCreatedDateTime() : '';
      var disabled = selectedWeightLimit.isDirty() ? '' : 'disabled';
      var buttons = ['<button class="save btn btn-primary" ' + disabled + '>Tallenna</button>',
          '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');
      var expiredChecked = selectedWeightLimit.expired() ? 'checked' : '';
      var nonExpiredChecked = selectedWeightLimit.expired() ? '' : 'checked';
      var value = selectedWeightLimit.getValue() ? selectedWeightLimit.getValue() + ' kg' : '-';
      var title = selectedWeightLimit.isNew() ?
        '<span>' + newWeightLimitTitle + '</span>' :
        '<span>Segmentin ID: ' + selectedWeightLimit.getId() + '</span>';
      var header = '<header>' + title + '<div class="' + className + ' form-controls">' + buttons + '</div></header>';
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
        '<p class="form-control-static ' + className + '">' + value + '</p>' +
        '<div class="choice-group">' +
        '<div class="radio">' +
        '<label>Ei painorajoitusta<input type="radio" name="' + className + '" value="disabled" ' + expiredChecked + '/></label>' +
        '</div>' +
        '<div class="radio">' +
        '<label>Painorajoitus<input type="radio" name="' + className + '" value="enabled" ' + nonExpiredChecked + '/></label>' +
        '</div>' +
        '</div>' +
        '</div>' +
        '<div class="form-group editable">' +
        '<label class="control-label"></label>' +
        '<div class="input-group" style="display: none">' +
        '<input type="text" class="form-control ' + className + '">' +
        '<span class="input-group-addon">kg</span>' +
        '</div>' +
        '</div>' +
        '</div>' +
        '</div>' +
        '<footer class="' + className + ' form-controls" style="display: none">' +
        buttons +
        '</footer>';
    };

    var removeWhitespace = function(s) {
      return s.replace(/\s/g, '');
    };

    var setupWeightLimitInput = function(toggleElement, inputElement, selectedWeightLimit) {
      inputElement.val(selectedWeightLimit.getValue());
      inputElement.prop('disabled', selectedWeightLimit.expired());
      inputElement.on('input', function(event) {
        var value = parseInt(removeWhitespace($(event.currentTarget).val()), 10);
        selectedWeightLimit.setValue(value);
      });
      toggleElement.change(function(event) {
        var expired = $(event.currentTarget).val();
        var disabled = expired === 'disabled';
        selectedWeightLimit.setExpired(disabled);
        inputElement.prop('disabled', disabled);
      });
    };

    var bindEvents = function(selectedWeightLimit, className, eventCategory) {
      var rootElement = $('#feature-attributes');
      var toggleMode = function(readOnly) {
        rootElement.find('.editable .form-control-static').toggle(readOnly);
        rootElement.find('.editable .input-group').toggle(!readOnly);
        rootElement.find('.editable .choice-group').toggle(!readOnly);
        rootElement.find('.form-controls').toggle(!readOnly);
      };
      var events = function() {
        return _.map(arguments, function(argument) { return eventCategory + ':' + argument; }).join(' ');
      };
      eventbus.on(events('selected', 'cancelled', 'saved'), function() {
        rootElement.html(template(selectedWeightLimit));
        var toggleElement = rootElement.find(".radio input");
        var inputElement = rootElement.find('.' + className);
        setupWeightLimitInput(toggleElement, inputElement, selectedWeightLimit);
        toggleMode(applicationModel.isReadOnly());
      });
      eventbus.on(events('unselected'), function() {
        rootElement.empty();
      });
      eventbus.on('application:readOnly', toggleMode);
      eventbus.on(events('limitChanged', 'expirationChanged'), function() {
        rootElement.find('.form-controls button').attr('disabled', false);
      });
      rootElement.on('click', '.' + className + ' button.save', function() {
        selectedWeightLimit.save();
      });
      rootElement.on('click', '.' + className + ' button.cancel', function() {
        selectedWeightLimit.cancel();
      });
    };

    bindEvents(selectedWeightLimit, className, eventCategory);
  };
})(this);
