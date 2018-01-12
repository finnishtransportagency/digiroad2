(function(root) {
  root.LinearAssetBox = function(assetConfig, legendValues) {
    var legendTemplate = _.map(legendValues, function(value, idx) {
      return value ? '<div class="legend-entry">' +
               '<div class="label">' + value + '</div>' +
               '<div class="symbol linear limit-' + idx + '" />' +
             '</div>' : '';
    }).join('');

      var trafficSignsCheckbox = assetConfig.hasTrafficSignReadOnlyLayer ? [
          '<div class="check-box-container">' +
          '<input id="signsCheckbox" type="checkbox" /> <lable>Näytä liikennemerkit</lable>' +
          '</div>'
      ].join('') : '';

      var complementaryLinkCheckBox = assetConfig.allowComplementaryLinks ? [
          '  <div  class="check-box-container">' +
          '<input id="complementaryLinkCheckBox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +

          '</div>'
      ].join('') : '';

    var expandedTemplate = [
      '<div class="panel ' + assetConfig.layerName +'">',
      '  <header class="panel-header expanded">',
      '    ' + assetConfig.title + (assetConfig.editControlLabels.showUnit ? ' ('+assetConfig.unit+')': ''),
      '  </header>',
      '  <div class="panel-section panel-legend limit-legend">',
            legendTemplate,
            complementaryLinkCheckBox,
            trafficSignsCheckbox,
      '  </div>',
      '</div>'].join('');

    var elements = {
      expanded: $(expandedTemplate)
    };

    var actions = [
      new ActionPanelBoxes.Tool('Select', ActionPanelBoxes.selectToolIcon, assetConfig),
      new ActionPanelBoxes.Tool('Cut', ActionPanelBoxes.cutToolIcon, assetConfig),
      new ActionPanelBoxes.Tool('Rectangle', ActionPanelBoxes.rectangleToolIcon, assetConfig),
      new ActionPanelBoxes.Tool('Polygon', ActionPanelBoxes.polygonToolIcon, assetConfig)
    ];

    var toolSelection = new ActionPanelBoxes.ToolSelection(actions);

    var editModeToggle = new EditModeToggleButton(toolSelection);
    var userRoles;

    var bindExternalEventHandlers = function() {
      eventbus.on('roles:fetched', function(roles) {
        userRoles = roles;
        if (!assetConfig.readOnly && (_.contains(roles, 'operator') || (_.contains(roles, 'premium') && assetConfig.layerName != 'maintenanceRoad') || (_.contains(roles, 'serviceRoadMaintainer') && assetConfig.layerName == 'maintenanceRoad'))) {
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

    elements.expanded.find('#complementaryLinkCheckBox').on('change', function (event) {
        if ($(event.currentTarget).prop('checked')) {
            eventbus.trigger('complementaryLinks:show');
        } else {
            if (applicationModel.isDirty()) {
                $(event.currentTarget).prop('checked', true);
                new Confirm();
            } else {
                eventbus.trigger('complementaryLinks:hide');
            }
        }
    });

    elements.expanded.find('#signsCheckbox').on('change', function (event) {
      if ($(event.currentTarget).prop('checked')) {
        eventbus.trigger(assetConfig.layerName + ':showReadOnlyTrafficSigns');
      } else {
        eventbus.trigger(assetConfig.layerName + ':hideReadOnlyTrafficSigns');
      }
    });

    var element = $('<div class="panel-group simple-limit ' + assetConfig.className + 's"/>').append(elements.expanded).hide();

    function show() {
      if (assetConfig.readOnly || (editModeToggle.hasNoRolesPermission(userRoles) || (_.contains(userRoles, 'premium') && (assetConfig.layerName == 'maintenanceRoad')))) {
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
      title: assetConfig.title,
      layerName: assetConfig.layerName,
      element: element,
      show: show,
      hide: hide
    };
  };
})(this);

