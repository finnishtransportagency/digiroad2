(function (root) {
  root.PointAssetForm = {
    initialize: bindEvents
  };

  function bindEvents(selectedAsset) {
    var rootElement = $('#feature-attributes');
    eventbus.on('pedestrianCrossing:selected pedestrianCrossing:cancelled', function() {
      var header = '<header><span>ID: ' + selectedAsset.getId() + '</span><div class="linear-asset form-controls"></div></header>';

      rootElement.html(header);
    });
  }
})(this);