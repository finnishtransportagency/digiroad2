(function(root) {
  root.RoadLinkBox = function(linkPropertiesModel) {
    var className = 'road-link';
    var title = 'Selite';

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

    var floatingLegend = $('' +
      '<div class="legend-entry">' +
      '<div class="label">Irti geometriasta</div>' +
      '</div>' +
      '<div class="floating-flag-with-stick-image"></div>'+
      '<div class="legend-entry">' +
      '<div class="symbol linear linear-asset-12" /></div>' +
      '</div>');

    var calibrationPointPicture = $('' +
      '<div class="legend-entry">' +
      '<div class="label">Etäisyyslukema</div>' +
      '</div>' +
      '<div class="calibration-point-image"></div>');

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
    var constructionTypes = [
      [0, 'Muu tieverkko, rakenteilla'],
      [1, 'Tuntematon, rakenteilla']
    ];
    var constructionTypeLegendEntries = _.map(constructionTypes, function(constructionType) {
      return '<div class="legend-entry">' +
          '<div class="label">' + constructionType[1] + '</div>' +
          '<div class="symbol linear construction-type-' + constructionType[0] + '" />' +
          '</div>';
    }).join('');
    var roadClassLegendEntries = _.map(roadClasses, function(roadClass) {
      return '<div class="legend-entry">' +
        '<div class="label">' + roadClass[1] + '</div>' +
        '<div class="symbol linear linear-asset-' + roadClass[0] + '" />' +
        '</div>';
    }).join('');
    roadClassLegend.append(roadClassLegendEntries);
    roadClassLegend.append(constructionTypeLegendEntries);
    roadClassLegend.append(floatingLegend);
    roadClassLegend.append(calibrationPointPicture);

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
        if (_.contains(roles, 'viite')) {
          elements.expanded.append(editModeToggle.element);
          $('#projectListButton').removeAttr('style');
        }
      });
    };

    eventbus.on('editMode:setReadOnly', function(mode) {
      editModeToggle.toggleEditMode(mode);
    });

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
