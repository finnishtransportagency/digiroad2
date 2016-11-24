(function(root) {
  root.RoadLinkBox = function(linkPropertiesModel) {
    var className = 'road-link';
    var title = 'Tielinkki';

    var historyRoadLinkCheckBox = '<div class="check-box-container">' +
        '<input type="checkbox" /> <lable>Näytä poistuneet tielinkit</lable>' +
        '</div>';

    var expandedTemplate = _.template('' +
      '<div class="panel <%= className %>">' +
        '<header class="panel-header expanded"><%- title %></header>' +
        '<div class="panel-section panel-legend road-link-legend">' +
          '<div class="radio">' +
            '<label><input type="radio" name="dataset" value="functional-class" checked>Toiminnallinen luokka</input></label>' +
          '</div>' +
          '<div class="radio">' +
            '<label><input type="radio" name="dataset" value="link-type">Tielinkin tyyppi</input></label>' +
          '</div>' +
          '<div class="radio">' +
            '<label><input type="radio" name="dataset" value="administrative-class">Hallinnollinen luokka</input></label>' +
          '</div>' +
          '<div class="radio">' +
            '<label><input type="radio" name="dataset" value="vertical-level">Silta, alikulku tai tunneli</input></label>' +
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
            '<div class="label">Ei tiedossa tai kevyen liikenteen väylä</div>' +
          '<div class="symbol linear unknown"/>' +
        '</div>' +
      '</div>');

    var functionalClassLegend = $('<div class="panel-section panel-legend linear-asset-legend functional-class-legend"></div>');
    var functionalClasses = [
      [1, '1'],
      [2, '2'],
      [3, '3'],
      [4, '4'],
      [5, '5'],
      [6, '6: Muu yksityistie'],
      [7, '7: Ajopolku'],
      [8, '8: Kevyen liikenteen väylä']
    ];
    var functionalClassLegendEntries = _.map(functionalClasses, function(functionalClass) {
      return '<div class="legend-entry">' +
        '<div class="label">Luokka ' + functionalClass[1] + '</div>' +
        '<div class="symbol linear linear-asset-' + functionalClass[0] + '" />' +
        '</div>';
    }).join('');
    functionalClassLegend.append(functionalClassLegendEntries).append(historyRoadLinkCheckBox);

    var linkTypeLegend = $('<div class="panel-section panel-legend linear-asset-legend link-type-legend"></div>');
    var linkTypes = [
      [1, 'Moottoritie'],
      [4, 'Moottoriliikennetie'],
      [3, 'Yksiajoratainen tie'],
      [2, 'Moniajoratainen tie'],
      [6, 'Kiertoliittymä'],
      [5, 'Ramppi'],
      [9, 'Jalankulkualue'],
      [8, 'Kevyen liikenteen väylä'],
      [11, '<div class="label-2lined">Huolto- tai pelastustie, liitännäisliikennealue tai levähdysalue</div>'],
      [12, 'Ajopolku'],
      [21, 'Huoltoaukko moottoritiellä'],
      [13, 'Lautta tai lossi']
    ];
    var linkTypeLegendEntries = _.map(linkTypes, function(linkType) {
      return '<div class="legend-entry">' +
        '<div class="label">' + linkType[1] + '</div>' +
        '<div class="symbol linear linear-asset-' + linkType[0] + '" />' +
        '</div>';
    }).join('');
    linkTypeLegend.append(linkTypeLegendEntries).append(historyRoadLinkCheckBox);

    var verticalLevelLegend = $('<div class="panel-section panel-legend linear-asset-legend vertical-level-legend"></div>');
    var verticalLevels = [
      [4, 'Silta, Taso 4'],
      [3, 'Silta, Taso 3'],
      [2, 'Silta, Taso 2'],
      [1, 'Silta, Taso 1'],
      [0, 'Maan pinnalla'],
      [-1, 'Alikulku'],
      [-11, 'Tunneli']
    ];
    var verticalLevelLegendEntries = _.map(verticalLevels, function(verticalLevel) {
      return '<div class="legend-entry">' +
        '<div class="label">' + verticalLevel[1] + '</div>' +
        '<div class="symbol linear linear-asset-' + verticalLevel[0] + '" />' +
        '</div>';
    }).join('');
    verticalLevelLegend.append(verticalLevelLegendEntries);

    var legends = {
      'administrative-class': administrativeClassLegend,
      'functional-class': functionalClassLegend,
      'link-type': linkTypeLegend,
      'vertical-level': verticalLevelLegend
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
      expanded: $(expandedTemplate(templateAttributes))
    };

    var bindDOMEventHandlers = function() {
      elements.expanded.find('input[name="dataset"]').change(function(event) {
        var datasetName = $(event.target).val();
        var legendContainer = $(elements.expanded.find('.legend-container'));

        legendContainer.find('input[type="checkbox"]').prop('checked', false);
        eventbus.trigger('roadLinkHistory:hide');

        legendContainer.empty();
        legendContainer.append(legends[datasetName]);
        linkPropertiesModel.setDataset(datasetName);

        bindHistoryEventHandlers(legendContainer);
      });
    };

    var bindHistoryEventHandlers = function(checkboxContainer){
      checkboxContainer.find('input[type="checkbox"]').on('change', function(event) {
        var eventTarget = $(event.currentTarget);
        if(eventTarget.prop('checked')){
          eventbus.trigger('roadLinkHistory:show');
        } else {
          eventbus.trigger('roadLinkHistory:hide');
        }
      });
    };

    var bindExternalEventHandlers = function() {
      eventbus.on('roles:fetched', function(roles) {
        if (_.contains(roles, 'operator') || _.contains(roles, 'premium')) {
          elements.expanded.append(editModeToggle.element);
        }
      });
    };

    bindDOMEventHandlers();

    bindExternalEventHandlers();

    elements.expanded.find('.legend-container').append(functionalClassLegend);
    var element = $('<div class="panel-group ' + className + 's"/>').append(elements.expanded).hide();

    bindHistoryEventHandlers(elements.expanded);

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
