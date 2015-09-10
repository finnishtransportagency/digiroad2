(function(root) {
  root.NumericalLimitBox = function(selectedNumericalLimit, layerName, numericalLimitTitle, className, legendValues) {
    var collapsedTemplate = [
      '<div class="panel ' + className + '">',
      '  <header class="panel-header">',
      '    ' + numericalLimitTitle,
      '  </header>',
      '</div>'].join('');

    var numericalLimitLegendTemplate = _.map(legendValues, function(value, idx) {
      return '<div class="legend-entry">' +
               '<div class="label">' + value + '</div>' +
               '<div class="symbol linear limit-' + idx + '" />' +
             '</div>';
    }).join('');

    var expandedTemplate = [
      '<div class="panel">',
      '  <header class="panel-header expanded">',
      '    ' + numericalLimitTitle,
      '  </header>',
      '  <div class="panel-section panel-legend limit-legend">',
            numericalLimitLegendTemplate,
      '  </div>',
      '</div>'].join('');

    var elements = {
      collapsed: $(collapsedTemplate),
      expanded: $(expandedTemplate).hide()
    };

    var toolSelection = new ActionPanelBoxes.ToolSelection(
      null,
      [new ActionPanelBoxes.Tool('Select', ActionPanelBoxes.selectToolIcon, selectedNumericalLimit),
       new ActionPanelBoxes.Tool('Cut', ActionPanelBoxes.cutToolIcon, selectedNumericalLimit)]);
    var editModeToggle = new EditModeToggleButton(toolSelection);

    var bindDOMEventHandlers = function() {
      elements.collapsed.click(function() {
        executeOrShowConfirmDialog(function() {
          elements.collapsed.hide();
          elements.expanded.show();
          applicationModel.selectLayer(layerName);
        });
      });
    };

    var bindExternalEventHandlers = function() {
      eventbus.on('layer:selected', function(selectedLayer) {
        if (selectedLayer !== layerName) {
          editModeToggle.reset();
          elements.expanded.hide();
          elements.collapsed.show();
        } else {
          elements.collapsed.hide();
          elements.expanded.show();
        }
      }, this);
      eventbus.on('roles:fetched', function(roles) {
        if (_.contains(roles, 'operator') || _.contains(roles, 'premium')) {
          toolSelection.reset();
          elements.expanded.append(toolSelection.element);
          elements.expanded.append(editModeToggle.element);
        }
      });
      eventbus.on('application:readOnly', function(readOnly) {
        elements.expanded.find('.panel-header').toggleClass('edit', !readOnly);
      });
    };

    bindDOMEventHandlers();

    bindExternalEventHandlers();

    this.element = $('<div class="panel-group simple-limit ' + className + 's"/>')
      .append(elements.collapsed)
      .append(elements.expanded);
  };

  var executeOrShowConfirmDialog = function(f) {
    if (applicationModel.isDirty()) {
      new Confirm();
    } else {
      f();
    }
  };
})(this);

