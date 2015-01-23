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
        '</div>' +
        '<div class="panel-section panel-legend functional-class-legend">' +
          '<div class="legend-entry">' +
            '<div class="label">Luokka 1</div>' +
            '<div class="symbol linear class-1"/>' +
          '</div>' +        
          '<div class="legend-entry">' +
            '<div class="label">Luokka 2</div>' +
            '<div class="symbol linear class-2"/>' +
          '</div>' +        
          '<div class="legend-entry">' +
            '<div class="label">Luokka 3</div>' +
            '<div class="symbol linear class-3"/>' +
          '</div>' +        
          '<div class="legend-entry">' +
            '<div class="label">Luokka 4</div>' +
            '<div class="symbol linear class-4"/>' +
          '</div>' +        
          '<div class="legend-entry">' +
            '<div class="label">Luokka 5</div>' +
            '<div class="symbol linear class-5"/>' +
          '</div>' +        
          '<div class="legend-entry">' +
            '<div class="label">Luokka 6</div>' +
            '<div class="symbol linear class-6"/>' +
          '</div>' +        
          '<div class="legend-entry">' +
            '<div class="label">Luokka 7</div>' +
            '<div class="symbol linear class-7"/>' +
          '</div>' +        
          '<div class="legend-entry">' +
            '<div class="label">Luokka 8</div>' +
            '<div class="symbol linear class-8"/>' +
          '</div>' +        
        '</div>' +
      '</div>');

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
    };

    var bindExternalEventHandlers = function() {
      eventbus.on('layer:selected', function(selectedLayer) {
        if (selectedLayer !== layerName) {
          editModeToggle.reset();
          elements.expanded.hide();
          elements.collapsed.show();
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