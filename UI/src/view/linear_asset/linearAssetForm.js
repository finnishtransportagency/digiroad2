(function (root) {
  root.LinearAssetForm = {
    initialize: bindEvents
  };

  function bindEvents(linearAsset, formElements, feedbackModel) {

    var selectedLinearAsset = linearAsset.selectedLinearAsset,
      eventCategory = linearAsset.singleElementEventCategory,
      newTitle = linearAsset.newTitle,
      title = linearAsset.title,
      authorizationPolicy = linearAsset.authorizationPolicy,
      layerName = linearAsset.layerName,
      isVerifiable = linearAsset.isVerifiable;
      new FeedbackDataTool(feedbackModel, linearAsset.layerName, authorizationPolicy, eventCategory);

    var rootElement = $('#feature-attributes');

    eventbus.on(events('selected', 'cancelled'), function() {
      rootElement.html(template(selectedLinearAsset, formElements, newTitle, title, isVerifiable));

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
      toggleMode( validateAdministrativeClass(selectedLinearAsset, authorizationPolicy) || applicationModel.isReadOnly());
    });

    eventbus.on(events('unselect'), function() {
      rootElement.empty();
    });

    eventbus.on('closeForm', function() {
      rootElement.empty();
    });

    eventbus.on('application:readOnly', function(readOnly){
      if(layerName ===  applicationModel.getSelectedLayer()) {
        toggleMode(validateAdministrativeClass(selectedLinearAsset, authorizationPolicy) || readOnly);
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
      if(isVerifiable && layerName === layer){
        renderLinktoWorkList(layer);
      }
       else {
        $('#information-content .form[data-layer-name="' + layerName +'"]').remove();
       }
    });
  }

  function template(selectedLinearAsset, formElements, newTitle, title, isVerifiable) {
    var modifiedBy = selectedLinearAsset.getModifiedBy() || '-';
    var modifiedDateTime = selectedLinearAsset.getModifiedDateTime() ? ' ' + selectedLinearAsset.getModifiedDateTime() : '';
    var createdBy = selectedLinearAsset.getCreatedBy() || '-';
    var createdDateTime = selectedLinearAsset.getCreatedDateTime() ? ' ' + selectedLinearAsset.getCreatedDateTime() : '';
    var verifiedBy = selectedLinearAsset.getVerifiedBy();
    var verifiedDateTime = selectedLinearAsset.getVerifiedDateTime();
    var disabled = selectedLinearAsset.isDirty() ? '' : 'disabled';
    var buttons = [(isVerifiable && !_.isNull(selectedLinearAsset.getId()) && selectedLinearAsset.count() === 1) ? '<button class="verify btn btn-primary">Merkitse tarkistetuksi</button>' : '',
                   '<button class="save btn btn-primary" disabled> Tallenna</button>',
                   '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');

    var generateTitle = function() {
      if (selectedLinearAsset.isUnknown() || selectedLinearAsset.isSplit()) {
        return '<span class="read-only-title">' + title + '</span>' +
            '<span class="edit-mode-title">' + newTitle + '</span>';
      } else {
        if (selectedLinearAsset.count() === 1) {
          return '<span>Kohteen ID: ' + selectedLinearAsset.getId() + '</span>';
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
        formElements.singleValueElement(selectedLinearAsset.getValue(), "a", selectedLinearAsset.getId()) +
        formElements.singleValueElement(selectedLinearAsset.getValue(), "b", selectedLinearAsset.getId());
      var valueElements = selectedLinearAsset.isSplitOrSeparated() ?
        separateValueElement :
        formElements.singleValueElement(selectedLinearAsset.getValue(), "", selectedLinearAsset.getId());
      return '' +
        '<div class="form-elements-container">' +
        valueElements +
        '</div>';
    };

    var verifiedFields = function() {
      return (isVerifiable && verifiedBy && verifiedDateTime) ? '<div class="form-group">' +
      '<p class="form-control-static asset-log-info">Tarkistettu: ' + informationLog(verifiedDateTime, verifiedBy) + '</p>' +
      '</div>' : '';
    };

    var informationLog = function (date, username) {
      return date ? (date + ' / ' + username) : '-';
    };

    var header = '<header>' + generateTitle() + '</header>';
    return header +
           '<div class="wrapper read-only">' +
             '<div class="form form-horizontal form-dark linear-asset">' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + informationLog(createdDateTime, createdBy) + '</p>' +
               '</div>' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Muokattu viimeksi: ' + informationLog(modifiedDateTime, modifiedBy)  + '</p>' +
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
        textName = "Vanhentuneiden kohteiden lista";
    }

      $('#information-content').append('' +
          '<div class="form form-horizontal" data-layer-name="' + layerName + '">' +
          '<a id="unchecked-links" class="unchecked-linear-assets" href="#work-list/' + layerName + '">' + textName + '</a>' +
          '</div>');
};

  function validateAdministrativeClass(selectedLinearAsset, authorizationPolicy){
    var selectedAssets = _.filter(selectedLinearAsset.get(), function (selected) {
      return !authorizationPolicy.formEditModeAccess(selected);
    });
    return !_.isEmpty(selectedAssets);
  }

})(this);
