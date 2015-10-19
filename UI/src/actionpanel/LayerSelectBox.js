(function(root) {
  root.LayerSelectBox = function(assetSelection) {
    var groupDiv = $('<div class="panel-group select-layer"/>');
    var layerSelectDiv = $('<div class="panel"/>');
    var selectLayerButton = $('<button class="btn btn-sm action-mode-btn btn btn-block btn-primary">Valitse tietolaji</button>');
    var panelHeader = $('<div class="panel-header"></div>').append(selectLayerButton);

    var bindEvents = function() {
      function selectLayerOrShowConfirmDialog() {
        if (applicationModel.isDirty()) {
          new Confirm();
        } else {
          assetSelection.toggle();
        }
      }

      selectLayerButton.on('click', selectLayerOrShowConfirmDialog);

      $(document).on('click', function(evt) {
        var clickOutside = !$(evt.target).closest('.navigation-panel').length;
        if (clickOutside) { assetSelection.hide(); }
      });

      $(document).keyup(function(evt) {
        if (evt.keyCode === 27) { assetSelection.hide(); }
      });
    };

    bindEvents();
    this.element = groupDiv.append(layerSelectDiv.append(panelHeader).append(assetSelection.element));
  };
})(this);
