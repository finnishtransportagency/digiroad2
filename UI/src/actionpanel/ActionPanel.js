(function(root) {
  root.ActionPanel = {
    initialize: function(backend, selectedSpeedLimit, weightLimits) {
      var panelControl = ['<div class="action-panels"></div>'].join('');

      $('#map-tools').append(panelControl);

      var assetBox = new ActionPanelBoxes.AssetBox();
      $('.action-panels').append(assetBox.element);

      var speedLimitBox = new ActionPanelBoxes.SpeedLimitBox(selectedSpeedLimit);
      $('.action-panels').append(speedLimitBox.element);

      _.forEach(weightLimits, function(weightLimit) {
        var weightLimitBox = new WeightLimitBox(weightLimit.selectedWeightLimit, weightLimit.layerName, weightLimit.weightLimitTitle, weightLimit.className);
        $('.action-panels').append(weightLimitBox.element);
      });

      backend.getUserRoles();

      // FIXME: Message now appended to top bar, but should this code live somewhere else?
      var editMessage = $('<div class="action-state">Olet muokkaustilassa</div>');
      $('#header').append(editMessage.hide());

      var handleEditMessage = function(readOnly) {
        if (readOnly) {
          editMessage.hide();
        } else {
          editMessage.show();
        }
      };

      eventbus.on('application:readOnly', function() {
        applicationModel.setSelectedTool('Select');
      });

      eventbus.on('application:readOnly', handleEditMessage);
    }
  };
}(this));
