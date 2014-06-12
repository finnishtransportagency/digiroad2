(function(ActionPanelBoxes) {
  var panelControl = ['<div class="action-panels"></div>'].join('');

  $('#map-tools').append(panelControl);

  var assetBox = new ActionPanelBoxes.AssetBox();
  $('.action-panels').append(assetBox.element);

  var linearAssetBox = new ActionPanelBoxes.LinearAssetBox();
  $('.action-panels').append(linearAssetBox.element);

  Backend.getUserRoles();

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
    eventbus.trigger('tool:changed', 'Select');
  });

  eventbus.on('application:readOnly', handleEditMessage);
}(window.ActionPanelBoxes));
