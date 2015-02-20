(function(root) {
  root.ManoeuvreBox = function() {
    var layerName = 'manoeuvre';
    var collapsedTemplate = [
      '<div class="panel manoeuvre">',
      '  <header class="panel-header">',
      '    Kääntymisrajoitus',
      '  </header>',
      '</div>'].join('');

    var values = ['Ei kääntymisrajoitusta', 'Kääntymisrajoituksen lähde', 'Kääntymisrajoituksen kohde', 'Kääntymisrajoituksen lähde ja kohde'];
    var manoeuvreLegendTemplate = _.map(values, function(value, idx) {
      return '<div class="legend-entry">' +
        '<div class="label">' + value + '</div>' +
        '<div class="symbol linear limit-' + idx + '" />' +
        '</div>';
    }).join('');

    var expandedTemplate = [
      '<div class="panel">',
      '  <header class="panel-header expanded">',
      '    Kääntymisrajoitus',
      '  </header>',
      '  <div class="panel-section panel-legend limit-legend">',
      manoeuvreLegendTemplate,
      '  </div>',
      '</div>'].join('');

    var elements = {
      collapsed: $(collapsedTemplate),
      expanded: $(expandedTemplate).hide()
    };

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
          elements.expanded.hide();
          elements.collapsed.show();
        } else {
          elements.collapsed.hide();
          elements.expanded.show();
        }
      }, this);
    };

    bindDOMEventHandlers();

    bindExternalEventHandlers();

    this.element = $('<div class="panel-group manoeuvres-limit manoeuvres"/>')
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

