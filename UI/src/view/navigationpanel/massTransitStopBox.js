(function(root) {
  root.MassTransitStopBox = function (selectedMassTransitStop) {
    ActionPanelBox.call(this);
    var me = this;

    this.header = function () {
      return 'Joukkoliikenteen pysäkki';
    };

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
        '  </div>'].join('');
    };

    this.title = function (){
      return 'Joukkoliikenteen pysäkki';
    };

    this.layerName = function () {
      return 'massTransitStop';
    };

    this.labeling = function () {
      var roadTypePanel =  [
        '  <div class="panel-section panel-legend road-link-legend">',
        '   <div class="legend-entry">',
        '      <div class="label">Valtion omistama</div>',
        '      <div class="symbol linear road"/>',
        '   </div>',
        '   <div class="legend-entry">',
        '     <div class="label">Kunnan omistama</div>',
        '     <div class="symbol linear street"/>',
        '   </div>',
        '   <div class="legend-entry">',
        '     <div class="label">Yksityisen omistama</div>',
        '     <div class="symbol linear private-road"/>',
        '   </div>',
        '   <div class="legend-entry">',
        '     <div class="label">Ei tiedossa tai kevyen liikenteen väylä</div>',
        '     <div class="symbol linear unknown"/>',
        '   </div>',
        '  </div>'
      ].join('');

      var constructionTypePanel = [
      '   <div class="panel-section panel-legend linear-asset-legend construction-type-legend">',
        '    <div class="legend-entry">',
        '      <div class="label">Rakenteilla</div>',
        '      <div class="symbol linear construction-type-1"/>',
        '   </div>',
        '   <div class="legend-entry">',
        '     <div class="label">Suunnitteilla</div>',
        '     <div class="symbol linear construction-type-3"/>',
        '   </div>',
        ' </div>'
      ].join('');

      return roadTypePanel.concat(constructionTypePanel);
    };

    this.checkboxPanel = function () {
      return [
        '  <div class="panel-section roadLink-complementary-checkbox">',
        '<div class="check-box-container">' +
        '<input id="complementaryCheckbox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
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
      new me.Tool('AddTerminal', setTitleTool(me.terminalToolIcon, 'Lisää terminaalipysäkki'), selectedMassTransitStop)
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

      eventbus.on('roles:fetched', function(roles) {
        if (!readOnly && _.contains(roles, 'operator') || _.contains(roles, 'premium') || _.isEmpty(roles) || _.contains(roles, 'busStopMaintainer')) {
          me.toolSelection.reset();
          $(me.expanded).append(me.toolSelection.element);
          $(me.expanded).append(me.editModeToggle.element);
        }
      });

      eventbus.on('road-type:selected', toggleRoadType);
    };

    var toggleRoadType = function(bool) {
      var expandedRoadTypeCheckboxSelector = $(me.expanded).find('.road-type-checkbox').find('input[type=checkbox]');

      $(me.expanded).find('.road-link-legend').toggle(bool);
      $(me.expanded).find('.construction-type-legend').toggle(bool);
      expandedRoadTypeCheckboxSelector.prop("checked", bool);
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
    };

    this.renderTemplate = function () {
      this.expanded = me.elements().expanded;
      me.eventHandler();
      bindDOMEventHandlers();
      me.bindExternalEventHandlers();
      toggleRoadType(true);
      return element
        .append(this.expanded)
        .hide();
    };

    return {
      title: me.title(),
      layerName: me.layerName(),
      element: me.renderTemplate(),
      show: show,
      hide: hide
    };
  };
})(this);
