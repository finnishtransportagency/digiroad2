(function (root) {
  root.PointAssetForm = {
    initialize: bindEvents
  };

  function bindEvents(selectedAsset) {
    var rootElement = $('#feature-attributes');

    rootElement.on('change', 'input[type="checkbox"]', function(event) {
      var eventTarget = $(event.currentTarget);
      if (eventTarget.attr('checked') === 'checked') {
        selectedAsset.setExpired(true);
      } else {
        selectedAsset.setExpired(false);
      }
    });

    eventbus.on('pedestrianCrossing:opened', function() {
      renderForm(rootElement, selectedAsset);
      enableSaving(rootElement);
    });
    
    eventbus.on('pedestrianCrossing:selected pedestrianCrossing:cancelled', function() {
      renderForm(rootElement, selectedAsset);
    });

    eventbus.on('pedestrianCrossing:changed', function() {
      rootElement.find('.form-controls button').attr('disabled', false);
    });

    rootElement.on('click', '.pointasset button.save', function() {
      selectedAsset.save();
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
      '<div class="wrapper read-only">' +
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