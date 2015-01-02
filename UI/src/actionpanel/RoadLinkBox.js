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
            '<div class="label">Maantie</div>' +
            '<div class="symbol linear road"/>' +
          '</div>' +
          '<div class="legend-entry">' +
            '<div class="label">Katu</div>' +
            '<div class="symbol linear street"/>' +
          '</div>' +
          '<div class="legend-entry">' +
            '<div class="label">Yksityistie</div>' +
            '<div class="symbol linear private-road"/>' +
          '</div>' +
          '<div class="legend-entry">' +
            '<div class="label">Ei tiedossa</div>' +
            '<div class="symbol linear unknown"/>' +
          '</div>' +
        '</div>' +
      '</div>');

    // FIXME: horrible copy-paste
    var EditModeToggleButton = function() {
      var button = $('<button class="action-mode-btn btn btn-block edit-mode-btn btn-primary">').text('Siirry muokkaustilaan');
      var element = $('<div class="panel-section panel-toggle-edit-mode">').append(button);
      var toggleReadOnlyMode = function(mode) {
        applicationModel.setReadOnly(mode);
        if (mode) {
          button.removeClass('read-only-btn').addClass('edit-mode-btn');
          button.removeClass('btn-secondary').addClass('btn-primary');
        } else {
          button.removeClass('edit-mode-btn').addClass('read-only-btn');
          button.removeClass('btn-primary').addClass('btn-secondary');
        }
        button.text(mode ? 'Siirry muokkaustilaan' : 'Siirry katselutilaan');
      };
      button.click(function() {
        executeOrShowConfirmDialog(function() {
          toggleReadOnlyMode(!applicationModel.isReadOnly());
        });
      });
      var reset = function() {
        toggleReadOnlyMode(true);
      };

      return {
        element: element,
        reset: reset
      };
    };

    var editModeToggle = new EditModeToggleButton();

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
          elements.expanded.hide();
          elements.collapsed.show();
        }
      }, this);
      eventbus.on('roles:fetched', function(roles) {
        if (_.contains(roles, 'operator')) {
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