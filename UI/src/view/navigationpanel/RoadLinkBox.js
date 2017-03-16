(function(root) {
  root.RoadLinkBox = function(linkPropertiesModel) {
    var className = 'road-link';
    var title = 'Tielinkki';

    var roadLinkCheckBoxs = '<div class="panel-section">' +
          '<div class="check-box-container">' +
            '<input id="historyCheckbox" type="checkbox" /> <span>Näytä poistuneet tielinkit</span>' +
          '</div>' +
          '<div class="check-box-container">' +
            '<input id="complementaryCheckbox" type="checkbox" /> <span>Näytä täydentävä geometria</span>' +
          '</div>' +
        '</div>';

    var roadLinkComplementaryCheckBox = '<div class="panel-section">' +
          '<div class="check-box-container">' +
            '<input id="complementaryCheckbox" type="checkbox" /> <span>Näytä täydentävä geometria</span>' +
          '</div>' +
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
    functionalClassLegend.append(functionalClassLegendEntries);

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
    linkTypeLegend.append(linkTypeLegendEntries);

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

    var datasetAllCheckboxs = {
      'administrative-class': roadLinkComplementaryCheckBox,
      'functional-class': roadLinkCheckBoxs,
      'link-type': roadLinkCheckBoxs,
      'vertical-level': roadLinkComplementaryCheckBox
    };

    var constructionTypeLegend = $('<div class="panel-section panel-legend linear-asset-legend construction-type-legend"></div>');
    var constructionTypes = [
      [1, 'Rakenteilla'], //Under construction
      [3, 'Suunnitteilla'] //Planned
    ];
    var constructionTypeLegendEntries = _.map(constructionTypes, function(constructionType) {
      return '<div class="legend-entry">' +
          '<div class="label">' + constructionType[1] + '</div>' +
          '<div class="symbol linear construction-type-' + constructionType[0] + '" />' +
          '</div>';
    }).join('');
    constructionTypeLegend.append(constructionTypeLegendEntries);

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

        var complementaryCheckboxChecked = legendContainer.find('#complementaryCheckbox').prop('checked');

        legendContainer.find('#historyCheckbox').prop('checked', false);
        eventbus.trigger('roadLinkHistory:hide');

        legendContainer.empty();
        legendContainer.append(legends[datasetName]);
        legendContainer.append(constructionTypeLegend);

        var allCheckBoxs = datasetAllCheckboxs[datasetName];
        if (allCheckBoxs) {
          legendContainer.append(allCheckBoxs);
          if (complementaryCheckboxChecked) {
            legendContainer.find('#complementaryCheckbox').prop('checked', true);
          } else {
            legendContainer.find('#complementaryCheckbox').prop('checked', false);
          }
        }

        linkPropertiesModel.setDataset(datasetName);

        bindEventHandlers(legendContainer);
      });
    };

    var bindEventHandlers = function(checkboxContainer){
      checkboxContainer.find('#historyCheckbox').on('change', function(event) {
        if($(event.currentTarget).prop('checked')){
          eventbus.trigger('roadLinkHistory:show');
        } else {
          eventbus.trigger('roadLinkHistory:hide');
        }
      });

      checkboxContainer.find('#complementaryCheckbox').on('change', function (event) {
        if ($(event.currentTarget).prop('checked')) {
          eventbus.trigger('roadLinkComplementary:show');
        } else {
          if (applicationModel.isDirty()) {
            $(event.currentTarget).prop('checked', true);
            new Confirm();
          } else {
            eventbus.trigger('roadLinkComplementary:hide');
          }
        }
      });

      eventbus.on('roadLinkComplementaryCheckBox:check', function() {
        checkboxContainer.find('#complementaryCheckbox').prop('checked', true);
      });
    };

    var userRoles;

    var bindExternalEventHandlers = function() {
      eventbus.on('roles:fetched', function(roles) {
        userRoles = roles;
        if (_.contains(roles, 'operator') || _.contains(roles, 'premium')) {
          elements.expanded.append(editModeToggle.element);
        }
      });
    };

    bindDOMEventHandlers();

    bindExternalEventHandlers();

    var initialLegendContainer = elements.expanded.find('.legend-container');
    initialLegendContainer.append(functionalClassLegend);
    initialLegendContainer.append(constructionTypeLegend);
    initialLegendContainer.append(roadLinkCheckBoxs);
    var element = $('<div class="panel-group ' + className + 's"/>').append(elements.expanded).hide();

    bindEventHandlers(elements.expanded);

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
      title: title,
      layerName: 'linkProperty',
      element: element,
      show: show,
      hide: hide
    };
  };
})(this);
