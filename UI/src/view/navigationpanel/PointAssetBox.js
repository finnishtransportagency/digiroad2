(function (root) {
  root.PointAssetBox = function (selectedPointAsset, title, layerName, legendValues, allowComplementaryLinks) {
    var className = _.kebabCase(layerName);
    var element = $('<div class="panel-group point-asset ' + className + '"></div>').hide();

    var toolSelection = new ActionPanelBoxes.ToolSelection([
      new ActionPanelBoxes.Tool('Select', ActionPanelBoxes.selectToolIcon, selectedPointAsset),
      new ActionPanelBoxes.Tool('Add', ActionPanelBoxes.addToolIcon, selectedPointAsset)
    ]);

    var editModeToggle = new EditModeToggleButton(toolSelection);

    var trafficSignPanel = (layerName == 'trafficSigns') ?
            '<div class="panel-section">' +
              '<div class="traffic-sign-checkbox">' +
                '<lable><input name="speedLimits" type="checkbox" /> Nopeusrajoitukset</lable> <br>' +
              '</div>' +
              '<div class="traffic-sign-checkbox">' +
                '<lable><input name="pedestrianCrossings" type="checkbox" /> Suojatiet</lable> <br>' +
              '</div>' +
              '<div class="traffic-sign-checkbox">' +
                '<lable><input name="maximumLengths" type="checkbox" /> Suurin sallittu pituus</lable> <br>' +
              '</div>' +
              '<div class="traffic-sign-checkbox">' +
                '<lable><input name="generalWarnings" type="checkbox" /> Varoitukset</lable> <br>' +
              '</div>' +
              '<div class="traffic-sign-checkbox">' +
                '<lable><input name="turningRestrictions" type="checkbox" /> Kääntymiskiellot</lable>' +
              '</div>' +
            '</div>' : "";

    var complementaryCheckBox = allowComplementaryLinks ?
            '<div class="panel-section">' +
              '<div class="check-box-container">' +
                '<input id="complementaryCheckbox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
              '</div>' +
            '</div>' : '';

    var legendTemplate = _(legendValues).map(function (val) {
      return '<div class="legend-entry">' +
        '<div class="label"><span>' + val.label + '</span> <img class="symbol" src="' + val.symbolUrl + '"/></div>' +
        '</div>';
    }).join('');

    var legend = '<div class="panel-section panel-legend limit-legend">' + legendTemplate + '</div>';
    var panel = $('<div class="panel"><header class="panel-header expanded">' + title + '</header>' + legend + trafficSignPanel + complementaryCheckBox + '</div>');
    panel.append(toolSelection.element);

    element.append(panel);

    var userRoles;

    eventbus.on('roles:fetched', function(roles) {
      userRoles = roles;
      if (_.contains(roles, 'operator') || _.contains(roles, 'premium')) {
        panel.append(editModeToggle.element);
      }
    });

    element.find('#complementaryCheckbox').on('change', function (event) {
      if ($(event.currentTarget).prop('checked')) {
        eventbus.trigger('withComplementary:show');
      } else {
        if (applicationModel.isDirty()) {
          $(event.currentTarget).prop('checked', true);
          new Confirm();
        } else {
          eventbus.trigger('withComplementary:hide');
        }
      }
    });

    var trafficSignHandler = function(event) {
        var el = $(event.currentTarget);
        var trafficSignType = el.prop('name');
        if (el.prop('checked')) {
          eventbus.trigger('trafficSigns:changeSigns', [trafficSignType, true]);
        } else {
          if (applicationModel.isDirty()) {
            el.prop('checked', true);
            new Confirm();
          } else {
            eventbus.trigger('trafficSigns:changeSigns', [trafficSignType, false]);
          }
        }
    };

    element.find('.traffic-sign-checkbox').find('input[type=checkbox]').change(trafficSignHandler);

    return {
      title: title,
      layerName: layerName,
      element: element,
      allowComplementaryLinks: allowComplementaryLinks,
      show: show,
      hide: hide
    };

    function show() {
      if ((layerName != 'massTransitStop') && editModeToggle.hasNoRolesPermission(userRoles)) {
        editModeToggle.reset();
      } else {
        editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      }
      element.show();
    }

    function hide() {
      element.hide();
    }
  };
})(this);
