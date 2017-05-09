(function(root) {
  root.LinearAssetBox = function(selectedLinearAsset, layerName, title, className, legendValues) {
    var legendTemplate = _.map(legendValues, function(value, idx) {
      return '<div class="legend-entry">' +
               '<div class="label">' + value + '</div>' +
               '<div class="symbol linear limit-' + idx + '" />' +
             '</div>';
    }).join('');

    var expandedTemplate = [
      '<div class="panel ' + layerName +'">',
      '  <header class="panel-header expanded">',
      '    ' + title,
      '  </header>',
      '  <div class="panel-section panel-legend limit-legend">',
            legendTemplate,
      '  </div>',
      '</div>'].join('');

    var elements = {
      expanded: $(expandedTemplate)
    };

    var actions = [
      new ActionPanelBoxes.Tool('Select', ActionPanelBoxes.selectToolIcon, selectedLinearAsset),
      new ActionPanelBoxes.Tool('Cut', ActionPanelBoxes.cutToolIcon, selectedLinearAsset)
    ];

    actions = layerName == 'maintenanceRoad' ? actions.concat(
      new ActionPanelBoxes.Tool('Rectangle', ActionPanelBoxes.rectangleToolIcon, selectedLinearAsset),
      new ActionPanelBoxes.Tool('Polygon', ActionPanelBoxes.polygonToolIcon, selectedLinearAsset)) : actions;

    var toolSelection = new ActionPanelBoxes.ToolSelection(actions);

    var editModeToggle = new EditModeToggleButton(toolSelection);
    var userRoles;

    var bindExternalEventHandlers = function() {
      eventbus.on('roles:fetched', function(roles) {
        userRoles = roles;
        if (_.contains(roles, 'operator') || (_.contains(roles, 'premium') && layerName != 'maintenanceRoad') ||
           (_.contains(roles, 'serviceRoadMaintainer') && layerName == 'maintenanceRoad')) {
          toolSelection.reset();
          elements.expanded.append(toolSelection.element);
          elements.expanded.append(editModeToggle.element);
        }
      });
      eventbus.on('application:readOnly', function(readOnly) {
        elements.expanded.find('.panel-header').toggleClass('edit', !readOnly);
      });
    };

    bindExternalEventHandlers();

    var element = $('<div class="panel-group simple-limit ' + className + 's"/>').append(elements.expanded).hide();

    function show() {
      if (editModeToggle.hasNoRolesPermission(userRoles) || (_.contains(userRoles, 'premium') && (layerName == 'maintenanceRoad'))) {
        editModeToggle.reset();
      } else {
        editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      }
      element.show();
    }

    function hide() {
      element.hide();
    }

    return {
      title: title,
      layerName: layerName,
      element: element,
      show: show,
      hide: hide
    };
  };
})(this);

