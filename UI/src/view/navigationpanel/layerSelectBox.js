(function(root) {
  root.LayerSelectBox = function(assetSelection) {
    var groupDiv = $('<div class="panel-group select-layer"></div>');
    var layerSelectDiv = $('<div class="panel"></div>');
    var selectLayerButton = $('<button class="action-mode-btn btn btn-block btn-primary">Valitse tietolaji</button>');
    var panelHeader = $('<div class="panel-header"></div>').append(selectLayerButton);

    var bindEvents = function() {
      function selectLayerOrShowConfirmDialog() {
        if (applicationModel.isDirty()) {
          new Confirm();
        }
        else if(applicationModel.getApplicationState() === applicationState.Feedback){
            new GenericConfirmPopup("Palautetta voi antaa kerralla vain yhdelle tietolajille. Sulje palauteikkuna, ja yritä uudelleen.", {type: 'alert'});
        }
      }
      selectLayerButton.on('click', selectLayerOrShowConfirmDialog);
    };

    bindEvents();

    return {
      hide: assetSelection.hide,
      toggle: assetSelection.toggle,
      button: selectLayerButton,
      element: groupDiv.append(layerSelectDiv.append(panelHeader).append(assetSelection.element))
    };
  };
})(this);
