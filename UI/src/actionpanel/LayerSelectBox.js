(function(root) {
  root.LayerSelectBox = function(assetSelection) {
    var groupDiv = $('<div class="panel-group select-layer"/>');
    var layerSelectDiv = $('<div class="panel"/>');
    var selectLayerButton = $('<button class="btn btn-sm action-mode-btn btn btn-block btn-primary">Valitse tietolaji</button>');
    var panelHeader = $('<div class="panel-header"></div>').append(selectLayerButton);

    var bindEvents = function() {
      var showAssetSelection = function() {
        assetSelection.show();
      };
      selectLayerButton.on('click', function() {
        showAssetSelection();
      });
    };

    bindEvents();
    this.element = groupDiv.append(layerSelectDiv.append(panelHeader));
  };
})(this);
