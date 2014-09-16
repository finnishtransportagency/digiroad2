(function(ActionPanelBoxes) {
  ActionPanelBoxes.SpeedLimitBox = function(selectedSpeedLimit) {
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

    var buttonTemplate = function() {
      if (applicationModel.isReadOnly()) {
        return '<div class="panel-section panel-toggle-edit-mode"><button class="action-mode-btn edit-mode-btn btn btn-primary btn-block">Siirry muokkaustilaan</button></div>';
      } else {
        return '<div class="panel-section panel-toggle-edit-mode"><button class="action-mode-btn read-only-btn btn btn-secondary btn-block">Siirry katselutilaan</button></div>';
      }
    };

    var speedLimitToolSelection = function() {
      return [
        '  <div class="panel-section panel-actions">',
        '    <div data-action="Select" class="action select active">',
        '      <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" version="1.1" class="icon-select" x="0px" y="0px" viewBox="0 0 26 26" enable-background="new 0 0 26 26" xml:space="preserve"><path class="shape" fill-rule="evenodd" clip-rule="evenodd" fill="#171717" d="M6 7l7 13v-6h6L6 7z"/></svg>',
        '    </div>',
        '    <div data-action="Cut" class="action cut">',
        '      <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" version="1.1" class="icon-cut" x="0px" y="0px" viewBox="0 0 26 26" enable-background="new 0 0 26 26" xml:space="preserve">',
        '        <path class="shape" d="M12.5 17c1.1 0 2 0.9 2 2 0 1.1-0.9 2-2 2s-2-0.9-2-2C10.5 17.9 11.4 17 12.5 17M12.5 16c-1.7 0-3 1.3-3 3s1.3 3 3 3 3-1.3 3-3S14.2 16 12.5 16L12.5 16z"/>',
        '        <path class="shape" d="M18.1 13.8c0.3 0 0.7 0.1 1 0.3 1 0.6 1.3 1.8 0.7 2.7 -0.4 0.6-1 1-1.7 1 -0.3 0-0.7-0.1-1-0.3 -1-0.6-1.3-1.8-0.7-2.7C16.8 14.1 17.4 13.8 18.1 13.8M18.1 12.8c-1.1 0-2.1 0.6-2.6 1.5 -0.8 1.4-0.3 3.3 1.1 4.1 0.5 0.3 1 0.4 1.5 0.4 1.1 0 2.1-0.6 2.6-1.5 0.4-0.7 0.5-1.5 0.3-2.3 -0.2-0.8-0.7-1.4-1.4-1.8C19.2 12.9 18.7 12.8 18.1 12.8L18.1 12.8z"/>',
        '        <path class="shape" d="M14 7c0-1.6-1.3-3-3-3h0v4.7l3 1.7V7zM17.1 13.5L15.6 16 7 11C5.5 10.2 5.1 8.4 5.9 7l0 0L17.1 13.5zM13 11.6c-0.5-0.3-1.1-0.1-1.4 0.4s-0.1 1.1 0.4 1.4 1.1 0.1 1.4-0.4S13.5 11.9 13 11.6zM11 9.9l3 1.7V17h-3V9.9zM13 11.6c-0.5-0.3-1.1-0.1-1.4 0.4s-0.1 1.1 0.4 1.4 1.1 0.1 1.4-0.4S13.5 11.9 13 11.6z"/>',
        '      </svg>',
        '    </div>',
        '  </div>'].join('');
    };

    var elements = {
      collapsed: $(collapsedTemplate),
      expanded: $(expandedTemplate).hide()
    };

    var resetTools = function() {
      elements.expanded.find('.action').removeClass('active');
      elements.expanded.find('.action.select').addClass('active');
      selectedSpeedLimit.close();
      eventbus.trigger('tool:changed', 'Select');
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
          resetTools();
          applicationModel.setReadOnly(true);
        });
      });

      elements.expanded.on('click', '.cut', function(evt) {
        executeOrShowConfirmDialog(function() {
          elements.expanded.find('.action').removeClass('active');
          $(evt.currentTarget).addClass('active');
          selectedSpeedLimit.close();
          eventbus.trigger('tool:changed', 'Cut');
        });
      });

      elements.expanded.on('click', '.select', function(evt) {
        executeOrShowConfirmDialog(function() {
          elements.expanded.find('.action').removeClass('active');
          elements.expanded.find('.action.select').addClass('active');
          resetTools();
          selectedSpeedLimit.close();
        });
      });

    };

    var bindExternalEventHandlers = function() {
      eventbus.on('layer:selected', function(selectedLayer) {
        if (selectedLayer !== 'speedLimit') {
          resetTools();
          elements.expanded.hide();
          elements.collapsed.show();
        }
      }, this);
      eventbus.on('roles:fetched', function(roles) {
        if (_.contains(roles, 'operator')) {
          elements.expanded.append($(speedLimitToolSelection()).hide());
          elements.expanded.append(buttonTemplate());
        }
      });
      eventbus.on('application:readOnly', function(readOnly) {
        var actionButtons = elements.expanded.find('.panel-section.panel-actions');
        if (applicationModel.isReadOnly()) {
          actionButtons.hide();
        } else {
          actionButtons.show();
        }
        elements.expanded.find('.panel-toggle-edit-mode').replaceWith(buttonTemplate());
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
