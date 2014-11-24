(function(root) {
  root.TotalWeightLimitBox = function() {
    var collapsedTemplate = [
      '<div class="panel total-weight-limit">',
      '  <header class="panel-header">',
      '    Kokonaispainorajoitukset',
      '  </header>',
      '</div>'].join('');

    var values = ['Ei rajoitusta', 'Rajoitus'];
    var totalWeightLimitLegendTemplate = _.map(values, function(value, idx) {
      return '<div class="legend-entry">' +
               '<div class="label">' + value + '</div>' +
               '<div class="symbol linear total-weight-limit-' + idx + '" />' +
             '</div>';
    }).join('');

    var expandedTemplate = [
      '<div class="panel">',
      '  <header class="panel-header expanded">',
      '    Kokonaispainorajoitukset',
      '  </header>',
      '  <div class="panel-section panel-legend total-weight-limit-legend">',
            totalWeightLimitLegendTemplate,
      '  </div>',
      '</div>'].join('');

    var elements = {
      collapsed: $(collapsedTemplate),
      expanded: $(expandedTemplate).hide()
    };

    var toolSelection = new ActionPanelBoxes.ToolSelection(
      null,
      [new ActionPanelBoxes.Tool('Select', ActionPanelBoxes.selectToolIcon, null),
       new ActionPanelBoxes.Tool('Cut', ActionPanelBoxes.cutToolIcon, null)]);

    var bindDOMEventHandlers = function() {
      elements.collapsed.click(function() {
        executeOrShowConfirmDialog(function() {
          elements.collapsed.hide();
          elements.expanded.show();
          applicationModel.selectLayer('totalWeightLimit');
        });
      });
    };

    var bindExternalEventHandlers = function() {
      eventbus.on('layer:selected', function(selectedLayer) {
        if (selectedLayer !== 'totalWeightLimit') {
          elements.expanded.hide();
          elements.collapsed.show();
        }
      }, this);
      eventbus.on('roles:fetched', function(roles) {
        if (_.contains(roles, 'operator')) {
          toolSelection.reset();
          elements.expanded.append(toolSelection.element);
        }
      });
      eventbus.on('application:readOnly', function(readOnly) {
        elements.expanded.find('.panel-header').toggleClass('edit', !readOnly);
      });
    };

    bindDOMEventHandlers();

    bindExternalEventHandlers();

    this.element = $('<div class="panel-group total-weight-limits"/>')
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

