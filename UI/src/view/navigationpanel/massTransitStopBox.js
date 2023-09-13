(function(root) {
  root.MassTransitStopBox = function (selectedMassTransitStop) {
    ActionPanelBox.call(this);
    var me = this;
    var authorizationPolicy = new MassTransitStopAuthorizationPolicy();
    var enumerations = new Enumerations();

    this.header = function () {
      return 'Joukkoliikenteen pysäkki';
    };

    var pointAssetLegend = [
      {symbolUrl: 'images/service_points/airport.png', label: 'Lentokenttä'},
      {symbolUrl: 'images/service_points/ferry.png', label: 'Laivaterminaali'},
      {symbolUrl: 'images/service_points/railwayStation2.png', label: 'Merkittävä rautatieasema'},
      {symbolUrl: 'images/service_points/railwayStation.png', label: 'Vähäisempi rautatieasema'},
      {symbolUrl: 'images/service_points/subwayStation.png', label: 'Metroasema'}
    ];

    var massTransitStopLegend = [
      {symbolUrl: 'images/mass-transit-stops/1.png', label: 'Raitiovaunu'},
      {symbolUrl: 'images/mass-transit-stops/2.png', label: 'Linja-autopysäkki'},
      {symbolUrl: 'images/mass-transit-stops/5.png', label: 'Virtuaalipysäkki'},
      {symbolUrl: 'images/mass-transit-stops/6.png', label: 'Terminaalipysäkki'}
    ];

    function putLabel(val){
      return '<div class="legend-entry">' +
        '  <div class="label">' +
        '    <span>' + val.label + '</span> ' +
        '    <img class="symbol-to-right" src="' + val.symbolUrl + '"/>' +
        '  </div>' +
        '</div>';
    }

    this.panel = function () {
      return ['<div class="panel">',
        '  <header class="panel-header expanded">',
        me.header(),
        '  </header>',
        '  <div class="panel-section">',
        '    <div class="checkbox">',
        '      <label>',
        '        <input name="current" type="checkbox" checked> Voimassaolevat',
        '      </label>',
        '    </div>',
        '    <div class="checkbox">',
        '      <label>',
        '        <input name="future" type="checkbox"> Tulevat',
        '      </label>',
        '    </div>',
        '    <div class="checkbox">',
        '      <label>',
        '        <input name="past" type="checkbox"> K&auml;yt&ouml;st&auml; poistuneet',
        '      </label>',
        '    </div>',
        '    <div class="checkbox road-type-checkbox">',
        '      <label>',
        '        <input name="road-types" type="checkbox"> Hallinnollinen luokka',
        '      </label>',
        '    </div>',
        '    <div class="checkbox point-asset-checkbox">',
        '      <label>',
        '        <input name="point-asset" type="checkbox"> Palvelupiste',
        '      </label>',
        '    </div>',
        '  </div>'].join('');
    };

    this.title = 'Joukkoliikenteen pysäkki';

    this.layerName = 'massTransitStop';

    this.labeling = function () {
      var administrativeClassLegend =  [
        '  <div class="panel-section panel-legend road-link-legend">'];
      var administrativeClassLegendEntries = _.map(enumerations.administrativeClasses, function(administrativeClass) {
        return '<div class="legend-entry">' +
          '<div class="label">'+ administrativeClass.text +'</div>' +
          '<div class="symbol linear administrative-class-' + administrativeClass.value + '" />' +
          '</div>';
      });

      var admistrativeClassPanel = administrativeClassLegend.concat(administrativeClassLegendEntries).join('') + '</div>';

      var constructionTypeLegend = '<div class="panel-section panel-legend linear-asset-legend construction-type-legend">';
      var constructionTypeLegendEntries = _.map(enumerations.constructionTypes, function(constructionType) {
        return !constructionType.visibleInLegend ? '' :
          '<div class="legend-entry">' +
          '<div class="label">' + constructionType.legendText + '</div>' +
          '<div class="symbol linear construction-type-' + constructionType.value + '" />' +
          '</div>';
      }).join('')+ '</div>';

      var constructionTypePanel = constructionTypeLegend.concat(constructionTypeLegendEntries);

      var massTransitStopPanel = '<div class="panel-section panel-legend limit-legend point-asset">';
      var pointAssetTypePanel = '<div class="panel-section panel-legend limit-legend point-asset service-points point-asset-legend">';

      massTransitStopPanel = massTransitStopPanel.concat(massTransitStopLegend.map(putLabel).join('')).concat('</div>');
      pointAssetTypePanel = pointAssetTypePanel.concat(pointAssetLegend.map(putLabel).join('')).concat('</div>');

      return admistrativeClassPanel.concat(constructionTypePanel).concat(massTransitStopPanel).concat(pointAssetTypePanel);
    };

    this.constructionTypeLabeling = function () {};

    this.checkboxPanel = function () {
      return [
        '  <div class="panel-section roadLink-complementary-checkbox">',
        '<div class="check-box-container">' +
        '<input id="complementaryLinkCheckBox" type="checkbox" checked/> <lable>Näytä täydentävä geometria</lable>' +
        '</div>' +
        '</div>'
      ].join('');
    };

    this.assetTools = function () {
      me.bindExternalEventHandlers(false);
    };

    this.toolSelection = new me.ToolSelection([
      new me.Tool('Select', me.selectToolIcon, selectedMassTransitStop),
      new me.Tool('Add', setTitleTool(me.addToolIcon, 'Lisää pysäkki'), selectedMassTransitStop),
      new me.Tool('AddTerminal', setTitleTool(me.terminalToolIcon, 'Lisää terminaalipysäkki'), selectedMassTransitStop),
      new me.Tool('AddPointAsset', setTitleTool(me.pointAssetToolIcon, 'Lisää palvelupiste'), selectedMassTransitStop)
    ]);

    function setTitleTool(icon, title) {
      return icon.replace('/>', ' title="'+title+'"/>');
    }

    this.editModeToggle = new EditModeToggleButton(me.toolSelection);

    var element = $('<div class="panel-group mass-transit-stops"/>');

    function show() {
      me.editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      element.show();
    }

    function hide() {
      element.hide();
    }

    this.bindExternalEventHandlers = function(readOnly) {
      eventbus.on('validityPeriod:changed', function() {
        var toggleValidityPeriodCheckbox = function(validityPeriods, el) {
          $(el).prop('checked', validityPeriods[el.name]);
        };

        var checkboxes = $.makeArray($(me.expanded).find('input[type=checkbox]'));
        _.forEach(checkboxes, _.partial(toggleValidityPeriodCheckbox, massTransitStopsCollection.getValidityPeriods()));
      });

      eventbus.on('asset:saved asset:created', function(asset) {
        massTransitStopsCollection.selectValidityPeriod(asset.validityPeriod, true);
      }, this);

      eventbus.on('roles:fetched', function() {
        if (authorizationPolicy.editModeAccess()) {
          me.toolSelection.reset();
          $(me.expanded).append(me.toolSelection.element);
          $(me.expanded).append(me.editModeToggle.element);
        }
      });
      me.addVerificationIcon();
      eventbus.on('road-type:selected', toggleRoadType);

      eventbus.on('verificationInfo:fetched', function(visible) {
        var img = me.expanded.find('#right-panel');
        if (visible)
          img.css('display','inline');
        else
          img.css('display','none');
      });
    };

    var toggleRoadType = function(bool) {
      var expandedRoadTypeCheckboxSelector = $(me.expanded).find('.road-type-checkbox').find('input[type=checkbox]');

      $(me.expanded).find('.road-link-legend').toggle(bool);
      $(me.expanded).find('.construction-type-legend').toggle(bool);
      expandedRoadTypeCheckboxSelector.prop("checked", bool);
    };

    var togglePointAsset = function(bool) {
      var expandedPointAssetCheckboxSelector = $(me.expanded).find('.point-asset-checkbox').find('input[type=checkbox]');

      $(me.expanded).find('.point-asset-legend').toggle(bool);
      expandedPointAssetCheckboxSelector.prop("checked", bool);
    };

    var bindDOMEventHandlers = function() {
      var validityPeriodChangeHandler = function(event) {
        me.executeOrShowConfirmDialog(function() {
          var el = $(event.currentTarget);
          var validityPeriod = el.prop('name');
          massTransitStopsCollection.selectValidityPeriod(validityPeriod, el.prop('checked'));
        });
      };

      $(me.expanded).find('.checkbox').find('input[type=checkbox]').change(validityPeriodChangeHandler);
      $(me.expanded).find('.checkbox').find('input[type=checkbox]').click(function(event) {
        if (applicationModel.isDirty()) {
          event.preventDefault();
        }
      });

      var expandedRoadTypeCheckboxSelector = $(me.expanded).find('.road-type-checkbox').find('input[type=checkbox]');

      var roadTypeSelected = function(e) {
        var checked = e.currentTarget.checked;
        applicationModel.setRoadTypeShown(checked);
      };

      expandedRoadTypeCheckboxSelector.change(roadTypeSelected);

      var expandedPointAssetCheckboxSelector = $(me.expanded).find('.point-asset-checkbox').find('input[type=checkbox]');
      expandedPointAssetCheckboxSelector.change( function (paCheckbox){
        var checked = paCheckbox.currentTarget.checked;
        togglePointAsset(checked);
        massTransitStopsCollection.showHideServicePoints(checked);
          }
      );
    };

    this.template = function () {
      this.expanded = me.elements().expanded;
      me.eventHandler();
      bindDOMEventHandlers();
      me.bindExternalEventHandlers();
      toggleRoadType(true);
      togglePointAsset(true);
      return element
        .append(this.expanded)
        .hide();
    };

    this.show = show;
    this.hide = hide;
  };
})(this);
