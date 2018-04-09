(function(root) {
  root.ManoeuvreBox = function() {
    var layerName = 'manoeuvre';
    var values = ['Ei kääntymisrajoitusta', 'Kääntymisrajoituksen lähde', 'Kääntymisrajoituksen lähde, useampi', 'Kääntymisrajoituksen välilinkki', 'Kääntymisrajoituksen välilinkki, useampi', 'Kääntymisrajoituksen kohde', 'Kääntymisrajoituksen kohde, useampi', 'Kääntymisrajoituksen lähde ja kohde'];
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
      expanded: $(expandedTemplate)
    };

    var editModeToggle = new EditModeToggleButton({
      hide: function() {},
      reset: function() {},
      show: function() {}
    });

    var userRoles;

    var bindExternalEventHandlers = function() {
      eventbus.on('roles:fetched', function(roles) {
        userRoles = roles;
        if (_.contains(roles, 'operator') || _.contains(roles, 'premium')) {
          elements.expanded.append(editModeToggle.element);
        }
      });
    };

    bindExternalEventHandlers();

    var element = $('<div class="panel-group manoeuvres-limit manoeuvres"/>')
      .append(elements.expanded)
      .hide();

    function show() {
      if (editModeToggle.hasNoRolesPermission(userRoles)) {
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
      title: 'Kääntymisrajoitus',
      layerName: layerName,
      element: element,
      show: show,
      hide: hide
    };
  };
})(this);

