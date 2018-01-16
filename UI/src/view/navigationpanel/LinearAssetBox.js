(function(root) {
  root.LinearAssetBox = function(selectedLinearAsset, layerName, title, className, legendValues, showUnit, unit, allowComplementaryLinks, hasTrafficSignReadOnlyLayer) {
    var checkIcon = '<img src="images/check-icon.png" title="Kuntakäyttäjän Todentama"/>';

    var legendTemplate = _.map(legendValues, function(value, idx) {
      return value ? '<div class="legend-entry">' +
               '<div class="label">' + value + '</div>' +
               '<div class="symbol linear limit-' + idx + '" />' +
             '</div>' : '';
    }).join('');

      var trafficSignsCheckbox = hasTrafficSignReadOnlyLayer ? [
          '<div class="check-box-container">' +
          '<input id="signsCheckbox" type="checkbox" /> <lable>Näytä liikennemerkit</lable>' +
          '</div>'
      ].join('') : '';

      var complementaryLinkCheckBox = allowComplementaryLinks ? [
          '  <div  class="check-box-container">' +
          '<input id="complementaryLinkCheckBox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +

          '</div>'
      ].join('') : '';

      var header = ['<div id="left-panel">    ' + title + (showUnit ? ' ('+unit+')': '') + '</div>' +
      ' <div id="right-panel">' + checkIcon + '</div>'].join('');

    var expandedTemplate = [
      '<div class="panel ' + layerName +'">',
      '  <header class="panel-header expanded">',
            header,
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
      new ActionPanelBoxes.Tool('Select', ActionPanelBoxes.selectToolIcon, selectedLinearAsset),
      new ActionPanelBoxes.Tool('Cut', ActionPanelBoxes.cutToolIcon, selectedLinearAsset),
      new ActionPanelBoxes.Tool('Rectangle', ActionPanelBoxes.rectangleToolIcon, selectedLinearAsset),
      new ActionPanelBoxes.Tool('Polygon', ActionPanelBoxes.polygonToolIcon, selectedLinearAsset)
    ];

    var toolSelection = new ActionPanelBoxes.ToolSelection(actions);

    var editModeToggle = new EditModeToggleButton(toolSelection);
    var userRoles;

    var bindExternalEventHandlers = function() {
      eventbus.on('roles:fetched', function(roles) {
        userRoles = roles;
        if ((layerName != 'trafficVolume') && (_.contains(roles, 'operator') || (_.contains(roles, 'premium') && layerName != 'maintenanceRoad') ||
           (_.contains(roles, 'serviceRoadMaintainer') && layerName == 'maintenanceRoad'))) {
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
        eventbus.trigger(layerName + ':showReadOnlyTrafficSigns');
      } else {
        eventbus.trigger(layerName + ':hideReadOnlyTrafficSigns');
      }
    });

    eventbus.on('verificationInfo:fetched', function(visible) {
      var img = elements.expanded.find('#right-panel');
        if (visible)
           img.css('display','inline');
        else
           img.css('display','none');
    });

    var element = $('<div class="panel-group simple-limit ' + className + 's"/>').append(elements.expanded).hide();

    function show() {
      if ((layerName == 'trafficVolume') || (editModeToggle.hasNoRolesPermission(userRoles) || (_.contains(userRoles, 'premium') && (layerName == 'maintenanceRoad')))) {
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

