(function(root) {
  root.ActionPanel = {
    initialize: function(backend, instructionsPopup, selectedSpeedLimit, numericalLimits, linkPropertiesModel, locationSearch) {
      var panelControl = ['<div class="action-panels"></div>'].join('');

      $('#map-tools').append(panelControl);

      var searchBox = new SearchBox(instructionsPopup, locationSearch);
      $('.action-panels').append(searchBox.element);

      var roadLinkBox = new RoadLinkBox(linkPropertiesModel);
      $('.action-panels').append(roadLinkBox.element);

      var assetBox = new ActionPanelBoxes.AssetBox();
      $('.action-panels').append(assetBox.element);

      var speedLimitBox = new ActionPanelBoxes.SpeedLimitBox(selectedSpeedLimit);
      $('.action-panels').append(speedLimitBox.element);

      var manoeuvreBox = new ManoeuvreBox();
      $('.action-panels').append(manoeuvreBox.element);

      _.forEach(numericalLimits, function(numericalLimit) {
        var numericalLimitBox = new NumericalLimitBox(numericalLimit.selectedNumericalLimit, numericalLimit.layerName, numericalLimit.numericalLimitTitle, numericalLimit.className);
        $('.action-panels').append(numericalLimitBox.element);
      });

      backend.getUserRoles();

      // FIXME: Message now appended to top bar, but should this code live somewhere else?
      var editMessage = $('<div class="action-state">Olet muokkaustilassa. Kuntakäyttäjien tulee kohdistaa muutokset katuverkolle, ELY-käyttäjien maantieverkolle.</div>');
      $('#header').append(editMessage.hide());

      var handleEditMessage = function(readOnly) {
        if (readOnly) {
          editMessage.hide();
        } else {
          editMessage.show();
        }
      };

      var showEditInstructionsPopup = function(readOnly) {
        if(!readOnly) {
          instructionsPopup.show('Kuntakäyttäjien tulee kohdistaa muutokset katuverkolle, ELY-käyttäjien maantieverkolle.', 4000);
        }
      };

      eventbus.on('application:readOnly', function() {
        applicationModel.setSelectedTool('Select');
      });

      eventbus.on('application:readOnly', handleEditMessage);

      eventbus.on('application:readOnly', showEditInstructionsPopup);
    }
  };
}(this));
