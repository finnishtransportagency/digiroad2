(function (root) {
  root.NumericalLimitForm = function(selectedNumericalLimit, newNumericalLimitTitle, className, eventCategory, unit) {
    var template = function(selectedNumericalLimit) {
      var modifiedBy = selectedNumericalLimit.getModifiedBy() || '-';
      var modifiedDateTime = selectedNumericalLimit.getModifiedDateTime() ? ' ' + selectedNumericalLimit.getModifiedDateTime() : '';
      var createdBy = selectedNumericalLimit.getCreatedBy() || '-';
      var createdDateTime = selectedNumericalLimit.getCreatedDateTime() ? ' ' + selectedNumericalLimit.getCreatedDateTime() : '';
      var disabled = selectedNumericalLimit.isDirty() ? '' : 'disabled';
      var buttons = ['<button class="save btn btn-primary" ' + disabled + '>Tallenna</button>',
          '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');
      var expiredChecked = selectedNumericalLimit.expired() ? 'checked' : '';
      var nonExpiredChecked = selectedNumericalLimit.expired() ? '' : 'checked';
      var value = selectedNumericalLimit.getValue() ? selectedNumericalLimit.getValue() + ' ' + unit : '-';
      var title = selectedNumericalLimit.isNew() ?
        '<span>' + newNumericalLimitTitle + '</span>' :
        '<span>Segmentin ID: ' + selectedNumericalLimit.getId() + '</span>';
      var header = '<header>' + title + '<div class="' + className + ' form-controls">' + buttons + '</div></header>';
      var measureInput = function() {
        if(unit) {
          return '' +
          '<div class="form-group editable">' +
            '<label class="control-label"></label>' +
            '<div class="input-group" style="display: none">' +
              '<input type="text" class="form-control ' + className + '">' +
              '<span class="input-group-addon">' + unit + '</span>' +
            '</div>' +
          '</div>';
        }
        else {
          return '';
        }
      };

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
        '<label>Ei rajoitusta<input type="radio" name="' + className + '" value="disabled" ' + expiredChecked + '/></label>' +
        '</div>' +
        '<div class="radio">' +
        '<label>Rajoitus<input type="radio" name="' + className + '" value="enabled" ' + nonExpiredChecked + '/></label>' +
        '</div>' +
        '</div>' +
        '</div>' +
         measureInput() +
        '</div>' +
        '</div>' +
        '<footer class="' + className + ' form-controls" style="display: none">' +
        buttons +
        '</footer>';
    };

    var removeWhitespace = function(s) {
      return s.replace(/\s/g, '');
    };

    var setupNumericalLimitInput = function(toggleElement, inputElement, selectedNumericalLimit) {
      inputElement.val(selectedNumericalLimit.getValue());
      inputElement.prop('disabled', selectedNumericalLimit.expired());
      inputElement.on('input', function(event) {
        var value = parseInt(removeWhitespace($(event.currentTarget).val()), 10);
        selectedNumericalLimit.setValue(value);
      });
      toggleElement.change(function(event) {
        var expired = $(event.currentTarget).val();
        var disabled = expired === 'disabled';
        selectedNumericalLimit.setExpired(disabled);
        inputElement.prop('disabled', disabled);
      });
    };

    var bindEvents = function(selectedNumericalLimit, className, eventCategory) {
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
        rootElement.html(template(selectedNumericalLimit));
        var toggleElement = rootElement.find(".radio input");
        var inputElement = rootElement.find('.' + className);
        setupNumericalLimitInput(toggleElement, inputElement, selectedNumericalLimit);
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
        selectedNumericalLimit.save();
      });
      rootElement.on('click', '.' + className + ' button.cancel', function() {
        selectedNumericalLimit.cancel();
      });
    };

    bindEvents(selectedNumericalLimit, className, eventCategory);
  };
})(this);
