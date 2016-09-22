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

    var roadClassLegend = $('<div class="panel-section panel-legend linear-asset-legend functional-class-legend"></div>');
    var roadClasses = [
      [1, 'Valtatie'],
      [2, 'Kantatie'],
      [3, 'Seututie'],
      [4, 'Yhdystie (4)'],
      [5, 'Yhdystie (5)'],
      [6, 'Katu'],
      [7, 'Rampit ja kiertoliittymät'],
      [8, 'Kevyen liikenteen väylä'],
      [9, 'Talvitie'],
      [10, 'Polku'],
      [11, 'Väliaikainen'],
      [99, 'Tuntematon']
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
