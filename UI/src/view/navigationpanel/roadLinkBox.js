(function(root) {
  root.RoadLinkBox = function(linkPropertiesModel) {
    var className = 'road-link';
    var title = 'Tielinkki';
    var authorizationPolicy = new AuthorizationPolicy();
    var enumerations = new Enumerations();

    var roadLinkCheckBoxs = '<div class="panel-section checkbox-box">' +
          '<div class="check-box-container">' +
            '<div><input id="historyCheckbox" type="checkbox" /><span>Näytä poistuneet tielinkit</span></div>' +
            '<div><input id="complementaryCheckbox" type="checkbox" /><span>Näytä täydentävä geometria</span></div>' +
        '</div>' +
  '</div>';

    var roadLinkComplementaryCheckBox = '<div class="panel-section checkbox-box">' +
          '<div class="check-box-container">' +
            '<input id="complementaryCheckbox" type="checkbox" /> <span>Näytä täydentävä geometria</span>' +
          '</div>' +
        '</div>';

    var expandedTemplate = _.template('' +
      '<div class="panel <%= className %>">' +
        '<header class="panel-header expanded"><%- title %></header>' +
        '<div class="panel-section road-link-legend">' +
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

    var administrativeClassLegend = $('<div class="panel-section panel-legend road-link-legend"></div>');

    var administrativeClassLegendEntries = _.map(enumerations.administrativeClasses, function(administrativeClass) {
      return '<div class="legend-entry">' +
        '<div class="label">'+ administrativeClass.text +'</div>' +
        '<div class="symbol linear administrative-class-' + administrativeClass.value + '" />' +
        '</div>';
    }).join('');
    administrativeClassLegend.append(administrativeClassLegendEntries);

    var functionalClassLegend = $('<div class="panel-section panel-legend linear-asset-legend functional-class-legend"></div>');
    var functionalClassLegendEntries = _.map(enumerations.functionalClasses, function(functionalClass) {
      return '<div class="legend-entry">' +
        '<div class="label">' + functionalClass.text + '</div>' +
        '<div class="symbol linear linear-asset-' + functionalClass.value + '" />' +
        '</div>';
    }).join('');
    functionalClassLegend.append(functionalClassLegendEntries);

    var linkTypeLegend = $('<div class="panel-section panel-legend linear-asset-legend link-type-legend"></div>');

    var linkTypeLegendEntries = _.map(enumerations.linkTypes, function(linkType) {
      return linkType.specialLegendRendering ? '':
        '<div class="legend-entry">' +
        '<div class="label">' + linkType.text + '</div>' +
        '<div class="symbol linear linear-asset-' + linkType.value + '" />' +
        '</div>';
      }).concat([
        '<div class="legend-entry">' +
        '<div class="label-2lined">Huolto- tai pelastustie, liitännäisliikennealue tai levähdysalue</div>' +
        '<div class="symbol linear linear-asset-11" />'
      ]).join('');
    linkTypeLegend.append(linkTypeLegendEntries);

    var verticalLevelLegend = $('<div class="panel-section panel-legend linear-asset-legend vertical-level-legend"></div>');
    var verticalLevels = [
      [4, 'Silta, Taso 4'],
      [3, 'Silta, Taso 3'],
      [2, 'Silta, Taso 2'],
      [1, 'Silta, Taso 1'],
      [0, 'Maan pinnalla'],
      [-1, 'Alikulku, taso 1'],
      [-2 , 'Alikulku, taso 2'],
      [-3 , 'Alikulku, taso 3'],
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
    var constructionTypeLegendEntries = _.map(enumerations.constructionTypes, function(constructionType) {
      return !constructionType.visibleInLegend ? '' :
        '<div class="legend-entry">' +
          '<div class="label">' + constructionType.legendText + '</div>' +
          '<div class="symbol linear construction-type-' + constructionType.value + '" />' +
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

        var complementaryCheckboxChecked = elements.expanded.find('#complementaryCheckbox').prop('checked');

        legendContainer.find('#historyCheckbox').prop('checked', false);
        eventbus.trigger('roadLinkHistory:hide');

        legendContainer.empty();
        legendContainer.append(legends[datasetName]);
        legendContainer.append(constructionTypeLegend);

        var allCheckBoxs = datasetAllCheckboxs[datasetName];
        if (allCheckBoxs) {
          elements.expanded.find('.panel-section.checkbox-box').replaceWith(allCheckBoxs);
          if (complementaryCheckboxChecked) {
            elements.expanded.find('#complementaryCheckbox').prop('checked', true);
          } else {
            elements.expanded.find('#complementaryCheckbox').prop('checked', false);
          }
        }

        linkPropertiesModel.setDataset(datasetName);

        bindEventHandlers(elements.expanded);
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

    var bindExternalEventHandlers = function() {
      eventbus.on('roles:fetched', function() {
        if (authorizationPolicy.editModeAccess())
          elements.expanded.append(editModeToggle.element);
      });
    };

    bindDOMEventHandlers();

    bindExternalEventHandlers();

    var initialLegendContainer = elements.expanded.find('.legend-container');
    initialLegendContainer.append(functionalClassLegend);
    initialLegendContainer.append(constructionTypeLegend);
    elements.expanded.append(roadLinkCheckBoxs);
    var element = $('<div class="panel-group ' + className + 's"/>').append(elements.expanded).hide();

    bindEventHandlers(elements.expanded);

    function show() {
      if (!authorizationPolicy.editModeAccess()) {
        editModeToggle.reset();
      } else {
        editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      }
      element.show();
    }

    function hide() {
      element.hide();
    }

    this.template = function() { return element;};
    this.title = title;
    this.layerName = 'linkProperty';
    this.show = show;
    this.hide = hide;
  };
})(this);
