(function (root) {
  root.LinearAssetForm = {
    initialize: bindEvents
  };

  function bindEvents(selectedLinearAsset, eventCategory, formElements, newTitle, title, editConstrains, layerName, hasVerificationButton) {
    var rootElement = $('#feature-attributes');

    eventbus.on(events('selected', 'cancelled'), function() {
      rootElement.html(template(selectedLinearAsset, formElements, newTitle, title, hasVerificationButton));

      if (selectedLinearAsset.isSplitOrSeparated()) {
        formElements.bindEvents(rootElement.find('.form-elements-container'), selectedLinearAsset, 'a');
        formElements.bindEvents(rootElement.find('.form-elements-container'), selectedLinearAsset, 'b');
      } else {
        formElements.bindEvents(rootElement.find('.form-elements-container'), selectedLinearAsset);
      }

      rootElement.find('#separate-limit').on('click', function() { selectedLinearAsset.separate(); });
      rootElement.find('.form-controls.linear-asset button.save').on('click', function() { selectedLinearAsset.save(); });
      rootElement.find('.form-controls.linear-asset button.cancel').on('click', function() { selectedLinearAsset.cancel(); });
      rootElement.find('.form-controls.linear-asset button.verify').on('click', function() { selectedLinearAsset.verify(); });
      toggleMode( validateAdministrativeClass(selectedLinearAsset, editConstrains) || applicationModel.isReadOnly());
    });
    eventbus.on(events('unselect'), function() {
      rootElement.empty();
    });
    eventbus.on('application:readOnly', function(readOnly){
      if(layerName ===  applicationModel.getSelectedLayer()) {
        toggleMode(validateAdministrativeClass(selectedLinearAsset, editConstrains) || readOnly);
      }
    });
    eventbus.on(events('valueChanged'), function(selectedLinearAsset) {
      rootElement.find('.form-controls.linear-asset button.save').attr('disabled', !selectedLinearAsset.isSaveable());
      rootElement.find('.form-controls.linear-asset button.cancel').attr('disabled', false);
      rootElement.find('.form-controls.linear-asset button.verify').attr('disabled', selectedLinearAsset.isSaveable());
    });

    function toggleMode(readOnly) {
      rootElement.find('.editable .form-control-static').toggle(readOnly);
      rootElement.find('.editable .edit-control-group').toggle(!readOnly);
      rootElement.find('.form-controls').toggle(!readOnly);
      rootElement.find('#separate-limit').toggle(!readOnly);
      rootElement.find('.read-only-title').toggle(readOnly);
      rootElement.find('.edit-mode-title').toggle(!readOnly);
    }

    function events() {
      return _.map(arguments, function(argument) { return eventCategory + ':' + argument; }).join(' ');
    }

    eventbus.on('layer:selected', function(layer) {
      if(layerName === 'maintenanceRoad' && layerName === layer) {
        renderLinktoWorkList(layer);
      }
       else {
        $('#information-content .form[data-layer-name="' + layerName +'"]').remove();
       }
    });
  }

  function template(selectedLinearAsset, formElements, newTitle, title, hasVerificationButton) {
    var modifiedBy = selectedLinearAsset.getModifiedBy() || '-';
    var modifiedDateTime = selectedLinearAsset.getModifiedDateTime() ? ' ' + selectedLinearAsset.getModifiedDateTime() : '';
    var createdBy = selectedLinearAsset.getCreatedBy() || '-';
    var createdDateTime = selectedLinearAsset.getCreatedDateTime() ? ' ' + selectedLinearAsset.getCreatedDateTime() : '';
    var verifiedBy = selectedLinearAsset.getVerifiedBy();
    var verifiedDateTime = selectedLinearAsset.getVerifiedDateTime();
    var disabled = selectedLinearAsset.isDirty() ? '' : 'disabled';
    var buttons = [(hasVerificationButton && !_.isNull(selectedLinearAsset.getId())) ? '<button class="verify btn btn-primary">Merkitse tarkistetuksi</button>' : '',
                   '<button class="save btn btn-primary" disabled> Tallenna</button>',
                   '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');
    var topButtons = ['<button class="save btn btn-primary" disabled>Tallenna</button>',
                      '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');
    var generateTitle = function() {
      if (selectedLinearAsset.isUnknown() || selectedLinearAsset.isSplit()) {
        return '<span class="read-only-title">' + title + '</span>' +
            '<span class="edit-mode-title">' + newTitle + '</span>';
      } else {
        if (selectedLinearAsset.count() === 1) {
          return '<span>Segmentin ID: ' + selectedLinearAsset.getId() + '</span>';
        } else {
          return '<span>' + title + '</span>';
        }
      }
    };

    var separatorButton = function() {
      if (selectedLinearAsset.isSeparable()) {
        return '<div class="form-group editable">' +
        '<label class="control-label"></label>' +
        '<button class="cancel btn btn-secondary" id="separate-limit">Jaa kaksisuuntaiseksi</button>' +
        '</div>';
      } else {
        return '';
      }
    };

    var limitValueButtons = function() {
      var separateValueElement =
        formElements.singleValueElement(selectedLinearAsset.getValue(), "a") +
        formElements.singleValueElement(selectedLinearAsset.getValue(), "b");
      var valueElements = selectedLinearAsset.isSplitOrSeparated() ?
        separateValueElement :
        formElements.singleValueElement(selectedLinearAsset.getValue());
      return '' +
        '<div class="form-elements-container">' +
        valueElements +
        '</div>';
    };

    var verifiedFields = function() {
      return (hasVerificationButton && verifiedBy && verifiedDateTime) ? '<div class="form-group">' +
      '<p class="form-control-static asset-log-info">Tarkistettu: ' + verifiedBy + verifiedDateTime + '</p>' +
      '</div>' : '';
    };

    var header = '<header>' + generateTitle() + '<div class="linear-asset form-controls">' + topButtons + '</div></header>';
    return header +
           '<div class="wrapper read-only">' +
             '<div class="form form-horizontal form-dark linear-asset">' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + createdBy + createdDateTime + '</p>' +
               '</div>' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Muokattu viimeksi: ' + modifiedBy + modifiedDateTime + '</p>' +
               '</div>' +
               verifiedFields() +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + selectedLinearAsset.count() + '</p>' +
               '</div>' +
               limitValueButtons() +
               separatorButton() +
             '</div>' +
           '</div>' +
           '<footer class="linear-asset form-controls" style="display: none">' +
             buttons +
           '</footer>';
  }

  var renderLinktoWorkList = function renderLinktoWorkList(layerName) {
    var textName;
    switch(layerName) {
      case "maintenanceRoad":
        textName = "Tarkistamattomien huoltoteiden lista";
            break;
      default:
        textName = "";
    }

      $('#information-content').append('' +
          '<div class="form form-horizontal" data-layer-name="' + layerName + '">' +
          '<a id="unchecked-links" class="unchecked-linear-assets" href="#work-list/' + layerName + '">' + textName + '</a>' +
          '</div>');
  };

  function validateAdministrativeClass(selectedLinearAsset, editConstrains){
    var selectedAssets = _.filter(selectedLinearAsset.get(), function (selected) {
      return editConstrains(selected);
    });
    return !_.isEmpty(selectedAssets);
  }

})(this);
