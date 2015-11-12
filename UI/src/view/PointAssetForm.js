(function (root) {
  root.PointAssetForm = {
    initialize: bindEvents
  };

  function bindEvents(selectedAsset) {
    var rootElement = $('#feature-attributes');

    eventbus.on('pedestrianCrossing:selected pedestrianCrossing:cancelled', function() {
      var header = '<header><span>ID: ' + selectedAsset.getId() + '</span><div class="linear-asset form-controls"></div></header>';
      var form = renderMeta(selectedAsset.asset());

      rootElement.html(header + form);
    });
  }

  function renderMeta(asset) {
    return '' +
      '<div class="wrapper read-only">' +
        '<div class="form form-horizontal form-dark linear-asset">' +
          '<div class="form-group">' +
            '<p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + (asset.createdBy || '-') + ' ' + (asset.createdAt || '') + '</p>' +
          '</div>' +
          '<div class="form-group">' +
            '<p class="form-control-static asset-log-info">Muokattu viimeksi: ' + (asset.modifiedBy || '-') + ' ' + (asset.modifiedAt || '') + '</p>' +
          '</div>' +
        '</div>' +
      '</div>';
  }
})(this);