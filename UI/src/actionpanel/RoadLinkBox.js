(function(root) {
  root.RoadLinkBox = function() {
    var className = 'road-link';
    var title = 'Tielinkit';
    var layerName = 'linkProperties';

    var collapsedTemplate = _.template('' +
      '<div class="panel <%= className %>">' +
        '<header class="panel-header"><%- title %></header>' +
      '</div>');

    var expandedTemplate = _.template('' +
      '<div class="panel <%= className %>">' +
        '<header class="panel-header expanded"><%- title %></header>' +
        '<div class="panel-section panel-legend road-link-legend">' +
          '<div class="radio">' +
            '<label><input type="radio" name="visualization" value="administrative-class" checked>Hallinnollinen luokka</input></label>' +
          '</div>' +
          '<div class="radio">' +
            '<label><input type="radio" name="visualization" value="functional-class">Toiminnallinen luokka</input></label>' +
          '</div>' +
        '</div>' +
        '<div class="legend-container"></div>' +
      '</div>');

    var administrativeClassLegend = $('' +
      '<div class="panel-section panel-legend road-link-legend">' +
        '<div class="legend-entry">' +
          '<div class="label">Valtion omistama</div>' +
            '<div class="symbol linear road"/>' +
          '</div>' +
          '<div class="legend-entry">' +
            '<div class="label">Kunnan omistama</div>' +
            '<div class="symbol linear street"/>' +
          '</div>' +
          '<div class="legend-entry">' +
            '<div class="label">Yksityisen omistama</div>' +
            '<div class="symbol linear private-road"/>' +
          '</div>' +
          '<div class="legend-entry">' +
            '<div class="label">Ei tiedossa</div>' +
          '<div class="symbol linear unknown"/>' +
        '</div>' +
      '</div>');

    var functionalClassLegend = $('<div class="panel-section panel-legend linear-asset-legend functional-class-legend"></div>');

    var functionalClasses = [1, 2, 3, 4, 5, 6, 7, 8];
    var functionalClassLegendTemplate = _.map(functionalClasses, function(functionalClass) {
      return '<div class="legend-entry">' +
        '<div class="label">' + functionalClass + '</div>' +
        '<div class="symbol linear linear-asset-' + functionalClass + '" />' +
        '</div>';
    }).join('');

    functionalClassLegend.append(functionalClassLegendTemplate);

    var legends = {
      'administrative-class': administrativeClassLegend,
      'functional-class': functionalClassLegend
    };

    var editModeToggle = new EditModeToggleButton({
      hide: function() {},
      reset: function() {},
      show: function() {}
    });

    var templateAttributes = {
      className: className,
      title: title
    };

    var elements = {
      collapsed: $(collapsedTemplate(templateAttributes)),
      expanded: $(expandedTemplate(templateAttributes)).hide()
    };

    var bindDOMEventHandlers = function() {
      elements.collapsed.click(function() {
        executeOrShowConfirmDialog(function() {
          elements.collapsed.hide();
          elements.expanded.show();
          applicationModel.selectLayer(layerName);
        });
      });

      elements.expanded.find('input[name="visualization"]').change(function(event) {
        var datasetName = $(event.target).val();
        var legendContainer = $(elements.expanded.find('.legend-container'));
        legendContainer.empty();
        legendContainer.append(legends[datasetName]);
        eventbus.trigger('linkProperty:dataset:changed', datasetName);
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
          elements.expanded.append(editModeToggle.element);
        }
      });
    };

    bindDOMEventHandlers();

    bindExternalEventHandlers();

    elements.expanded.find('.legend-container').append(administrativeClassLegend);
    this.element = $('<div class="panel-group ' + className + 's"/>')
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
