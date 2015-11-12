(function (root) {
  root.PointAssetForm = {
    initialize: bindEvents
  };

  function bindEvents(selectedAsset) {
    var rootElement = $('#feature-attributes');

    function toggleMode(readOnly) {
      rootElement.find('.delete').toggle(!readOnly);
      rootElement.find('.form-controls').toggle(!readOnly);
    }
    eventbus.on('application:readOnly', toggleMode);

    rootElement.on('change', 'input[type="checkbox"]', function(event) {
      var eventTarget = $(event.currentTarget);
      if (eventTarget.attr('checked') === 'checked') {
        selectedAsset.setToBeDeleted(true);
      } else {
        selectedAsset.setToBeDeleted(false);
      }
    });

    eventbus.on('pedestrianCrossing:opened', function() {
      renderForm(rootElement, selectedAsset);
      enableSaving(rootElement);
    });
    
    eventbus.on('pedestrianCrossing:selected pedestrianCrossing:cancelled', function() {
      renderForm(rootElement, selectedAsset);
      toggleMode(applicationModel.isReadOnly());
    });

    eventbus.on('pedestrianCrossing:changed', function() {
      rootElement.find('.form-controls button').attr('disabled', !selectedAsset.isDirty());
    });

    eventbus.on('pedestrianCrossing:unselected', function() {
      rootElement.empty();
    });

    rootElement.on('click', '.pointasset button.save', function() {
      selectedAsset.save();
    });

    rootElement.on('click', '.pointasset button.cancel', function() {
      selectedAsset.cancel();
    });
  }

  function enableSaving(rootElement) {
    rootElement.find('.save.btn').prop('disabled', false);
  }

  function renderForm(rootElement, selectedAsset) {
    var header = '<header><span>ID: ' + selectedAsset.getId() + '</span>' + renderButtons() + '</header>';
    var form = renderMeta(selectedAsset.asset());
    var footer = '<footer>' + renderButtons() + '</footer>';

    rootElement.html(header + form + footer);
  }

  function renderMeta(asset) {
    return '' +
      '<div class="wrapper">' +
      '  <div class="form form-horizontal form-dark form-pointasset">' +
      '    <div class="form-group">' +
      '      <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + (asset.createdBy || '-') + ' ' + (asset.createdAt || '') + '</p>' +
      '    </div>' +
      '    <div class="form-group">' +
      '      <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + (asset.modifiedBy || '-') + ' ' + (asset.modifiedAt || '') + '</p>' +
      '    </div>' +
      '    <div class="form-group form-group delete">' +
      '      <div class="checkbox" >' +
      '        <input type="checkbox">' +
      '      </div>' +
      '      <p class="form-control-static">Poista</p>' +
      '    </div>' +
      '  </div>' +
      '</div>';
  }

  function renderButtons() {
    return '' +
      '<div class="pointasset form-controls">' +
      '  <button class="save btn btn-primary" disabled>Tallenna</button>' +
      '  <button class="cancel btn btn-secondary" disabled>Peruuta</button>' +
      '</div>';
  }
})(this);