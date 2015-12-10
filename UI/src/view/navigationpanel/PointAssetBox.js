(function(root) {
  root.PointAssetBox = function(selectedPointAsset, title, layerName) {
    var className = _.kebabCase(layerName);
    var element = $('<div class="panel-group simple-limit ' + className + 's"></div>').hide();

    var toolSelection = new ActionPanelBoxes.ToolSelection([
      new ActionPanelBoxes.Tool('Select', ActionPanelBoxes.selectToolIcon, selectedPointAsset),
      new ActionPanelBoxes.Tool('Add', ActionPanelBoxes.addToolIcon, selectedPointAsset)
    ]);

    var editModeToggle = new EditModeToggleButton(toolSelection);
    var panel = $('<div class="panel"><header class="panel-header expanded">Suojatie</header></div>');
    panel.append(toolSelection.element);
    panel.append(editModeToggle.element);

    element.append(panel);

    return {
      title: title,
      layerName: layerName,
      element: element,
      show: show,
      hide: hide
    };

    function show() {
      element.show();
    }

    function hide() {
      element.hide();
      editModeToggle.reset();
    }
  };
})(this);
