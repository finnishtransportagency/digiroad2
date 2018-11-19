(function (root) {
  root.LinearAssetForm = {
    initialize: bindEvents
  };

  var infoContent = $('ul[class=information-content]');

  function bindEvents(linearAsset, formElements, feedbackModel) {

    var selectedLinearAsset = linearAsset.selectedLinearAsset,
      eventCategory = linearAsset.singleElementEventCategory,
      newTitle = linearAsset.newTitle,
      title = linearAsset.title,
      authorizationPolicy = linearAsset.authorizationPolicy,
      layerName = linearAsset.layerName,
      isVerifiable = linearAsset.isVerifiable,
      hasInaccurate = linearAsset.hasInaccurate;
      new FeedbackDataTool(feedbackModel, linearAsset.layerName, authorizationPolicy, eventCategory);

    var rootElement = $('#feature-attributes');

    eventbus.on(events('selected', 'cancelled'), function() {
      rootElement.find('#feature-attributes-header').html(header(selectedLinearAsset, title, newTitle));
      rootElement.find('#feature-attributes-form').html(template(selectedLinearAsset, formElements));
      rootElement.find('#feature-attributes-footer').html(footer(selectedLinearAsset, isVerifiable));

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
      rootElement.find('#feature-attributes-header').empty();
      rootElement.find('#feature-attributes-form').empty();
      rootElement.find('#feature-attributes-footer').empty();
    });

    eventbus.on('closeForm', function() {
      rootElement.find('#feature-attributes-header').empty();
      rootElement.find('#feature-attributes-form').empty();
      rootElement.find('#feature-attributes-footer').empty();
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
      if(layerName === layer){
        if(isVerifiable){
          renderLinktoWorkList(layer);
        }
        if(hasInaccurate){
          renderInaccurateWorkList(layer);
        }
      }
       else {
        // $('ul[class=information-content]').empty();
        }
    });
  }

  function header(selectedLinearAsset, title, newTitle) {
    var disabled = selectedLinearAsset.isDirty() ? '' : 'disabled';
    var topButtons = ['<button class="save btn btn-primary" disabled>Tallenna</button>',
      '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');

    var generateTitle = function () {
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

    return generateTitle() + '<div class="linear-asset form-controls">' + topButtons + '</div>';
  }

  var buttons = function(selectedLinearAsset, isVerifiable) {
    var disabled = selectedLinearAsset.isDirty() ? '' : 'disabled';
    return [(isVerifiable && !_.isNull(selectedLinearAsset.getId()) && selectedLinearAsset.count() === 1) ? '<button class="verify btn btn-primary">Merkitse tarkistetuksi</button>' : '',
      '<button class="save btn btn-primary" disabled> Tallenna</button>',
      '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');
  };


  function template(selectedLinearAsset, formElements) {
    var modifiedBy = selectedLinearAsset.getModifiedBy() || '-';
    var modifiedDateTime = selectedLinearAsset.getModifiedDateTime() ? ' ' + selectedLinearAsset.getModifiedDateTime() : '';
    var createdBy = selectedLinearAsset.getCreatedBy() || '-';
    var createdDateTime = selectedLinearAsset.getCreatedDateTime() ? ' ' + selectedLinearAsset.getCreatedDateTime() : '';
    var verifiedBy = selectedLinearAsset.getVerifiedBy();
    var verifiedDateTime = selectedLinearAsset.getVerifiedDateTime();

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

    var verifiedFields = function(isVerifiable) {
      return (isVerifiable && verifiedBy && verifiedDateTime) ? '<div class="form-group">' +
      '<p class="form-control-static asset-log-info">Tarkistettu: ' + verifiedBy + ' ' + verifiedDateTime + '</p>' +
      '</div>' : '';
    };

    return '<div class="wrapper read-only">' +
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
           '</div>';
  }

  function footer(selectedLinearAsset, isVerifiable) {
    return '<div class="linear-asset form-controls" style="display: none">' +
    buttons(selectedLinearAsset, isVerifiable) +
    '</div>';
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
    // $('ul[class=information-content]').empty();
    $('ul[class=information-content]').append('<li><a id="unchecked-links" class="unchecked-linear-assets" href="#work-list/' + layerName + '">' + textName + '</a></li>');
  };

  var renderInaccurateWorkList= function renderInaccurateWorkList(layerName) {
    // $('ul[class=information-content]').empty();
    $('ul[class=information-content]').append('<li><a id="work-list-link-errors" class="wrong-linear-assets" href="#work-list/' + layerName + 'Errors">Laatuvirheet Lista</a></li>');
  };

  function validateAdministrativeClass(selectedLinearAsset, authorizationPolicy){
    var selectedAssets = _.filter(selectedLinearAsset.get(), function (selected) {
      return !authorizationPolicy.formEditModeAccess(selected);
    });
    return !_.isEmpty(selectedAssets);
  }

})(this);
