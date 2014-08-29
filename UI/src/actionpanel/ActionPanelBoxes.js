(function(ActionPanelBoxes) {
  ActionPanelBoxes.SpeedLimitBox = function() {
    var collapsedTemplate = [
      '<div class="panel speed-limits">',
      '  <header class="panel-header">',
      '    Nopeusrajoitukset',
      '  </header>',
      '</div>'].join('');

    var speedLimits = [120, 100, 80, 70, 60, 50, 40, 30, 20];
    var speedLimitLegendTemplate = _.map(speedLimits, function(speedLimit) {
      return '<div class="legend-entry">' +
               '<div class="label">' + speedLimit + '</div>' +
               '<div class="symbol linear speed-limit-' + speedLimit + '" />' +
             '</div>';
    }).join('');

    var expandedTemplate = [
      '<div class="panel">',
      '  <header class="panel-header expanded">',
      '    Nopeusrajoitukset',
      '  </header>',
      '  <div class="panel-section panel-legend speed-limit-legend">',
            speedLimitLegendTemplate,
      '  </div>',
      '</div>'].join('');

    var buttonTemplate = function(readOnly) {
      var editButton = '<button class="action-mode-btn edit-mode-btn btn btn-primary btn-block">Siirry muokkaustilaan</button>';
      var viewButton = '<button class="action-mode-btn read-only-btn btn btn-secondary btn-block">Siirry katselutilaan</button>';
      return '<div class="panel-section panel-toggle-edit-mode">' + (readOnly ? editButton : viewButton) + '</div>';
    };

    var elements = {
      collapsed: $(collapsedTemplate),
      expanded: $(expandedTemplate).hide()
    };

    var bindDOMEventHandlers = function() {
      elements.collapsed.click(function() {
        executeOrShowConfirmDialog(function() {
          elements.collapsed.hide();
          elements.expanded.show();
          applicationModel.selectLayer('speedLimit');
        });
      });
      elements.expanded.on('click', '.edit-mode-btn', function() {
        executeOrShowConfirmDialog(function() {
          applicationModel.setReadOnly(false);
        });
      });
      elements.expanded.on('click', '.read-only-btn', function() {
        executeOrShowConfirmDialog(function() {
          applicationModel.setReadOnly(true);
        });
      });
    };

    var bindExternalEventHandlers = function() {
      eventbus.on('layer:selected', function(selectedLayer) {
        if (selectedLayer !== 'speedLimit') {
          elements.expanded.hide();
          elements.collapsed.show();
        }
      }, this);
      eventbus.on('roles:fetched', function(roles) {
        if (_.contains(roles, 'operator')) {
          elements.expanded.append(buttonTemplate(true));
        }
      });
      eventbus.on('application:readOnly', function(readOnly) {
        elements.expanded.find('.panel-toggle-edit-mode').replaceWith(buttonTemplate(readOnly));
        elements.expanded.find('.panel-header').toggleClass('edit', !readOnly);
      });
    };

    bindDOMEventHandlers();

    bindExternalEventHandlers();

    this.element = $('<div class="panel-group"/>')
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

  ActionPanelBoxes.AssetBox = function() {

    var roadTypeLegend = [
        '  <div class="panel-section panel-legend road-link-legend">',
        '    <div class="legend-entry">',
        '      <div class="label">Maantie</div>',
        '      <div class="symbol linear road"/>',
        '   </div>',
        '   <div class="legend-entry">',
        '     <div class="label">Katu</div>',
        '     <div class="symbol linear street"/>',
        '   </div>',
        '   <div class="legend-entry">',
        '     <div class="label">Yksityistie</div>',
        '     <div class="symbol linear private-road"/>',
        '   </div>',
        '   <div class="legend-entry">',
        '     <div class="label">Ei tiedossa</div>',
        '     <div class="symbol linear unknown"/>',
        '   </div>',
        '  </div>'
    ].join('');

    var expandedTemplate = [
      '<div class="panel">',
      '  <header class="panel-header expanded">',
      '    Joukkoliikenteen pys&auml;kit',
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
      '        <input name="road-types" type="checkbox"> V&auml;yl&auml;tyyppi',
      '      </label>',
      '    </div>',
      '  </div>',
      roadTypeLegend,
      '  <div class="panel-section">',
      '    <button class="action-mode-btn edit-mode-btn btn btn-primary btn-block" style="display: none;">Siirry muokkaustilaan</button>',
      '  </div>',
      '</div>'].join('');

    var editModeTemplate = [
      '<div class="panel">',
      '  <header class="panel-header edit">',
      '    Joukkoliikenteen pys&auml;kit',
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
      '        <input name="road-types" type="checkbox"> V&auml;yl&auml;tyyppi',
      '      </label>',
      '    </div>',
      '  </div>',
      roadTypeLegend,
      '  <div class="panel-section panel-actions">',
      '    <div data-action="Select" class="action select active">',
      '      <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" version="1.1" class="icon-select" x="0px" y="0px" viewBox="0 0 26 26" enable-background="new 0 0 26 26" xml:space="preserve"><path class="shape" fill-rule="evenodd" clip-rule="evenodd" fill="#171717" d="M6 7l7 13v-6h6L6 7z"/></svg>',
      '    </div>',
      '    <div data-action="Add" class="action add">',
      '      <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" version="1.1" class="icon-add" x="0px" y="0px" viewBox="0 0 26 26" enable-background="new 0 0 26 26" xml:space="preserve"><polygon class="shape" points="19,12 14,12 14,7 12,7 12,12 7,12 7,14 12,14 12,19 14,19 14,14 19,14 "/></svg>',
      '    </div>',
      '  </div>',
      '  <div class="panel-section">',
      '    <button class="action-mode-btn read-only-btn btn btn-secondary btn-block" style="display: none;">Siirry katselutilaan</button>',
      '  </div>',
      '</div>'].join('');

    var collapsedTemplate = [
      '<div class="panel">',
      '  <header class="panel-header">',
      '    Joukkoliikenteen pys&auml;kit',
      '  </header>',
      '</div>'].join('');

    var elements = {
      collapsed: $(collapsedTemplate).hide(),
      expanded: $(expandedTemplate),
      editMode: $(editModeTemplate).hide()
    };
    var actionButtons = elements.editMode.find('.panel-actions .action');

    var bindDOMEventHandlers = function() {
      elements.expanded.find('button.edit-mode-btn').click(function() {
        elements.expanded.hide();
        elements.editMode.show();
        applicationModel.setReadOnly(false);
      });

      elements.editMode.find('button.read-only-btn').click(function() {
        executeOrShowConfirmDialog(function() {
          elements.editMode.hide();
          elements.expanded.show();
          actionButtons.removeClass('active');
          actionButtons.filter('.select').addClass('active');
          applicationModel.setReadOnly(true);
        });
      });

      actionButtons.on('click', function(event) {
        executeOrShowConfirmDialog(function() {
          var el = $(event.currentTarget);
          var action = el.attr('data-action');

          actionButtons.removeClass('active');
          el.addClass('active');

          eventbus.trigger('tool:changed', action);
        });
      });

      var validityPeriodChangeHandler = function(event) {
        executeOrShowConfirmDialog(function() {
          var el = $(event.currentTarget);
          var validityPeriod = el.prop('name');
          assetsModel.selectValidityPeriod(validityPeriod, el.prop('checked'));
        });
      };

      elements.expanded.find('.checkbox').find('input[type=checkbox]').change(validityPeriodChangeHandler);
      elements.editMode.find('.checkbox').find('input[type=checkbox]').change(validityPeriodChangeHandler);
      elements.expanded.find('.checkbox').find('input[type=checkbox]').click(function(event) {
        if (applicationModel.isDirty()) {
          event.preventDefault();
        }
      });
      elements.editMode.find('.checkbox').find('input[type=checkbox]').click(function(event) {
        if (applicationModel.isDirty()) {
          event.preventDefault();
        }
      });

      elements.collapsed.click(function() {
        executeOrShowConfirmDialog(function() {
          elements.collapsed.hide();
          elements.expanded.show();
          applicationModel.selectLayer('asset');
        });
      });

      var expandedRoadTypeCheckboxSelector = elements.expanded.find('.road-type-checkbox').find('input[type=checkbox]');
      var editModeRoadTypeCheckboxSelector = elements.editMode.find('.road-type-checkbox').find('input[type=checkbox]');

      var roadTypeSelected = function(e) {
        var checked = e.currentTarget.checked;
        elements.expanded.find('.road-link-legend').toggle(checked);
        elements.editMode.find('.road-link-legend').toggle(checked);
        expandedRoadTypeCheckboxSelector.prop("checked", checked);
        editModeRoadTypeCheckboxSelector.prop("checked", checked);
        eventbus.trigger('road-type:selected', checked);
      };

      expandedRoadTypeCheckboxSelector.change(roadTypeSelected);
      editModeRoadTypeCheckboxSelector.change(roadTypeSelected);
    };

    var bindExternalEventHandlers = function() {
      eventbus.on('validityPeriod:changed', function() {
        var toggleValidityPeriodCheckbox = function(validityPeriods, el) {
          $(el).prop('checked', validityPeriods[el.name]);
        };

        var checkboxes = $.makeArray(elements.expanded.find('input[type=checkbox]'))
                           .concat($.makeArray(elements.editMode.find('input[type=checkbox]')));
        _.forEach(checkboxes, _.partial(toggleValidityPeriodCheckbox, assetsModel.getValidityPeriods()));
      });

      eventbus.on('asset:saved asset:created', function(asset) {
        assetsModel.selectValidityPeriod(asset.validityPeriod, true);
      }, this);

      eventbus.on('layer:selected', function(selectedLayer) {
        if (selectedLayer !== 'asset') {
          elements.expanded.hide();
          elements.editMode.hide();
          elements.collapsed.show();
        }
        actionButtons.removeClass('active');
        actionButtons.filter('.select').addClass('active');
      }, this);

      eventbus.on('roles:fetched', function(roles) {
        if (!_.contains(roles, 'viewer')) {
          elements.expanded.find('.action-mode-btn').show();
          elements.editMode.find('.action-mode-btn').show();
        }
      });
    };

    bindDOMEventHandlers();

    bindExternalEventHandlers();

    this.element = $('<div class="panel-group"/>')
      .append(elements.collapsed)
      .append(elements.expanded)
      .append(elements.editMode);
  };
})(window.ActionPanelBoxes = window.ActionPanelBoxes || {});
