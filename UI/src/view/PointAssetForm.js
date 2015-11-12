(function (root) {
  root.PointAssetForm = {
    initialize: bindEvents
  };

  var buttons = '' +
    '<div class="pointasset form-controls">' +
    '  <button class="save btn btn-primary" disabled>Tallenna</button>' +
    '  <button class="cancel btn btn-secondary" disabled>Peruuta</button>' +
    '</div>';

  function bindEvents(selectedAsset) {
    var rootElement = $('#feature-attributes');

    eventbus.on('pedestrianCrossing:opened', function() {
      renderForm(rootElement, selectedAsset);
      enableSaving(rootElement);
    });

    eventbus.on('pedestrianCrossing:selected pedestrianCrossing:cancelled', function() {
      renderForm(rootElement, selectedAsset);
    });
  }

  function enableSaving(rootElement) {
    rootElement.find('.save.btn').prop('disabled', false);
  }

  function renderForm(rootElement, selectedAsset) {
    var header = '<header><span>ID: ' + selectedAsset.getId() + '</span>' + buttons + '</header>';
    var form = renderMeta(selectedAsset.asset());
    var footer = '<footer>' + buttons + '</footer>';

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
})(this);