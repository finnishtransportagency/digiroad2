(function (root) {
  root.LinearAssetForm = function(selectedLinearAsset, newTitle, className, eventCategory, unit, editControlLabels) {
    var template = function(selectedLinearAsset) {
      var modifiedBy = selectedLinearAsset.getModifiedBy() || '-';
      var modifiedDateTime = selectedLinearAsset.getModifiedDateTime() ? ' ' + selectedLinearAsset.getModifiedDateTime() : '';
      var createdBy = selectedLinearAsset.getCreatedBy() || '-';
      var createdDateTime = selectedLinearAsset.getCreatedDateTime() ? ' ' + selectedLinearAsset.getCreatedDateTime() : '';
      var disabled = selectedLinearAsset.isDirty() ? '' : 'disabled';
      var buttons = ['<button class="save btn btn-primary" ' + disabled + '>Tallenna</button>',
          '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');
      var expiredChecked = selectedLinearAsset.isUnknown() ? 'checked' : '';
      var nonExpiredChecked = selectedLinearAsset.isUnknown() ? '' : 'checked';
      var title = selectedLinearAsset.isNew() ?
        '<span>' + newTitle + '</span>' :
        '<span>Segmentin ID: ' + selectedLinearAsset.getId() + '</span>';
      var header = '<header>' + title + '<div class="' + className + ' form-controls">' + buttons + '</div></header>';
      var valueString = function() {
        if (unit) {
          return selectedLinearAsset.getValue() ? selectedLinearAsset.getValue() + ' ' + unit : '-';
        } else {
          return selectedLinearAsset.isUnknown() ? 'ei ole' : 'on';
        }
      };
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
        '<label class="control-label">' + editControlLabels.title + '</label>' +
        '<p class="form-control-static ' + className + '">' + valueString() + '</p>' +
        '<div class="choice-group">' +
        '<div class="radio">' +
        '<label>' + editControlLabels.disabled + '<input type="radio" name="' + className + '" value="disabled" ' + expiredChecked + '/></label>' +
        '</div>' +
        '<div class="radio">' +
        '<label>' + editControlLabels.enabled + '<input type="radio" name="' + className + '" value="enabled" ' + nonExpiredChecked + '/></label>' +
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

    var setupNumericalLimitInput = function(toggleElement, inputElement, selectedLinearAsset) {
      inputElement.val(selectedLinearAsset.getValue());
      inputElement.prop('disabled', selectedLinearAsset.isUnknown());
      inputElement.on('input', function(event) {
        var value = parseInt(removeWhitespace($(event.currentTarget).val()), 10);
        selectedLinearAsset.setValue(value);
      });
      toggleElement.change(function(event) {
        var expired = $(event.currentTarget).val();
        var disabled = expired === 'disabled';
        selectedLinearAsset.setExpired(disabled);
        inputElement.prop('disabled', disabled);
      });
    };

    var bindEvents = function(selectedLinearAsset, className, eventCategory) {
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
        rootElement.html(template(selectedLinearAsset));
        var toggleElement = rootElement.find(".radio input");
        var inputElement = rootElement.find('.' + className);
        setupNumericalLimitInput(toggleElement, inputElement, selectedLinearAsset);
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
        selectedLinearAsset.save();
      });
      rootElement.on('click', '.' + className + ' button.cancel', function() {
        selectedLinearAsset.cancel();
      });
    };

    bindEvents(selectedLinearAsset, className, eventCategory);
  };
})(this);
