(function(root) {
  root.RoadLinkBox = function(linkPropertiesModel) {
    var className = 'road-link';
    var title = 'Tietyypit';

    var expandedTemplate = _.template('' +
      '<div class="panel <%= className %>">' +
        '<header class="panel-header expanded"><%- title %></header>' +
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
            '<div class="label">Ei tiedossa tai kevyen liikenteen väylä</div>' +
          '<div class="symbol linear unknown"/>' +
        '</div>' +
      '</div>');

    var roadClassLegend = $('<div class="panel-section panel-legend linear-asset-legend road-class-legend"></div>');
    var roadClasses = [
      [1, 'Valtatie (1-39)'],
      [2, 'Kantatie (40-99)'],
      [3, 'Seututie (100-999)'],
      [4, 'Yhdystie (1000-9999)'],
      [5, 'Yhdystie (10001-19999)'],
      [6, 'Numeroitu katu (40000-49999)'],
      [7, 'Ramppi tai kiertoliittymä (20001 - 39999)'],
      [8, 'Jalka- tai pyörätie (70001 - 89999, 90001 - 99999)'],
      [9, 'Talvitie (60001 - 61999)'],
      [10,'Polku (62001 - 62999)'],
      [11,'Muu tieverkko'],
      [99,'Tuntematon']
    ];
    var roadClassLegendEntries = _.map(roadClasses, function(roadClass) {
      return '<div class="legend-entry">' +
        '<div class="label">' + roadClass[1] + '</div>' +
        '<div class="symbol linear linear-asset-' + roadClass[0] + '" />' +
        '</div>';
    }).join('');
    roadClassLegend.append(roadClassLegendEntries);

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
      expanded: $(expandedTemplate(templateAttributes))
    };

    var bindExternalEventHandlers = function() {
      eventbus.on('roles:fetched', function(roles) {
        if (_.contains(roles, 'operator') || _.contains(roles, 'premium')) {
          elements.expanded.append(editModeToggle.element);
        }
      });
    };

    bindExternalEventHandlers();

    elements.expanded.find('.legend-container').append(roadClassLegend);
    var element = $('<div class="panel-group ' + className + 's"/>').append(elements.expanded).hide();

    function show() {
      editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      element.show();
    }

    function hide() {
      element.hide();
    }

    return {
      title: title,
      layerName: 'linkProperty',
      element: element,
      show: show,
      hide: hide
    };
  };
})(this);
