(function (root) {
  root.LinearAssetForm2 = {
    initialize: bindEvents
  };

  function bindEvents(selectedLinearAsset, eventCategory, formElements) {
    var rootElement = $('#feature-attributes');

    eventbus.on(events('selected', 'cancelled'), function() {
      rootElement.html(template(selectedLinearAsset, formElements));
      formElements.bindEvents(rootElement, selectedLinearAsset);

      rootElement.find('#separate-limit').on('click', function() { selectedLinearAsset.separate(); });
      rootElement.find('.form-controls.speed-limit button.save').on('click', function() { selectedLinearAsset.save(); });
      rootElement.find('.form-controls.speed-limit button.cancel').on('click', function() { selectedLinearAsset.cancel(); });
      toggleMode(applicationModel.isReadOnly());
    });
    eventbus.on(events('unselect'), function() {
      rootElement.empty();
    });
    eventbus.on('application:readOnly', toggleMode);
    eventbus.on(events('valueChanged'), function(selectedLinearAsset) {
      rootElement.find('.form-controls.speed-limit button.save').attr('disabled', !selectedLinearAsset.isSaveable());
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

    function toggleMode(readOnly) {
      rootElement.find('.read-only.form-group').toggle(readOnly);
      rootElement.find('.editable.form-group').toggle(!readOnly);
      rootElement.find('.form-controls').toggle(!readOnly);
    }

    function events() {
      return _.map(arguments, function(argument) { return eventCategory + ':' + argument; }).join(' ');
    }
  }

  function template(selectedLinearAsset, formElements) {
    var modifiedBy = selectedLinearAsset.getModifiedBy() || '-';
    var modifiedDateTime = selectedLinearAsset.getModifiedDateTime() ? ' ' + selectedLinearAsset.getModifiedDateTime() : '';
    var createdBy = selectedLinearAsset.getCreatedBy() || '-';
    var createdDateTime = selectedLinearAsset.getCreatedDateTime() ? ' ' + selectedLinearAsset.getCreatedDateTime() : '';
    var disabled = selectedLinearAsset.isDirty() ? '' : 'disabled';
    var buttons = ['<button class="save btn btn-primary" disabled>Tallenna</button>',
                   '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');
    var title = function() {
      if (selectedLinearAsset.isUnknown() || selectedLinearAsset.isSplit()) {
        return '<span>Uusi nopeusrajoitus</span>';
      } else if (selectedLinearAsset.count() == 1) {
        return '<span>Segmentin ID: ' + selectedLinearAsset.getId() + '</span>';
      } else {
        return '<span>Nopeusrajoitus</span>';
      }
    };

    var separatorButton = function() {
      if (selectedLinearAsset.isSeparable()) {
        return '<div class="form-group editable">' +
        '<label class="control-label"></label>' +
        '<button class="cancel btn btn-secondary" id="separate-limit">Jaa nopeusrajoitus kaksisuuntaiseksi</button>' +
        '</div>';
      } else {
        return '';
      }
    };

    var limitValueButtons = function() {
      var separateValueElement = formElements.singleValueElement(selectedLinearAsset, "a") + formElements.singleValueElement(selectedLinearAsset, "b");
      return selectedLinearAsset.isSplitOrSeparated() ? separateValueElement : formElements.singleValueElement(selectedLinearAsset);
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
                 '<p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + selectedLinearAsset.count() + '</p>' +
               '</div>' +
               limitValueButtons() +
               separatorButton() +
             '</div>' +
           '</div>' +
           '<footer class="speed-limit form-controls" style="display: none">' +
             buttons +
           '</footer>';
  }

  function renderLinktoWorkList() {
    var notRendered = !$('#work-list-link').length;
    if(notRendered) {
      $('#information-content').append('' +
        '<div class="form form-horizontal">' +
          '<a id="work-list-link" class="unknown-speed-limits" href="#work-list/speedLimit">Tuntemattomien nopeusrajoitusten lista</a>' +
        '</div>');
    }
  }
})(this);
