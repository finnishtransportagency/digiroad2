(function(ActionPanelBoxes) {
  var panelControl = [
    '<div class="panelControl">',
    '  <div class="panelControlLine"></div>',
    '  <div class="panelControlLine"></div>',
    '  <div class="panelControlLine"></div>',
    '</div>',
    '<div class="panelLayerGroup"></div>'].join('');

  $('#maptools').append(panelControl);

  var assetBox = new ActionPanelBoxes.AssetBox();
  $('.panelLayerGroup').append(assetBox.element);

  var linearAssetBox = new ActionPanelBoxes.LinearAssetBox();
  $('.panelLayerGroup').append(linearAssetBox.element);

  Backend.getUserRoles();

  // FIXME: This definitely doesn't belong here (top bar or somewhere else)
  var editMessage = $('<div class="editMessage">Olet muokkaustilassa</div>');
  $('.container').append(editMessage.hide());

  var handleEditMessage = function(readOnly) {
    if (readOnly) {
      editMessage.hide();
    } else {
      editMessage.show();
    }
  };

  eventbus.on('layer:selected', function() {
    eventbus.trigger('application:readOnly', true);
  });

  eventbus.on('application:readOnly', function() {
    eventbus.trigger('tool:changed', 'Select');
  });

  eventbus.on('application:readOnly', handleEditMessage);
}(window.ActionPanelBoxes));
