(function(ActionPanelBoxes) {
  ActionPanelBoxes.LinearAssetBox = function() {
    var collapsedTemplate = [
      '<div class="actionPanel">',
      '  <div class="layerGroup">',
      '    <div class="layerGroupImg_linearAsset layerGroupImg_unselected_linearAsset"></div>',
      '    <div class="layerGroupLabel">Nopeusrajoitukset</div>',
      '  </div>',
      '</div>'].join('');

    var expandedTemplate = [
      '<div class="actionPanel">',
      '  <div class="layerGroup layerGroupSelectedMode">',
      '    <div class="layerGroupImg_linearAsset layerGroupImg_selected_linearAsset"></div>',
      '    <div class="layerGroupLabel">Nopeusrajoitukset</div>',
      '  </div>',
      '</div>'].join('');

    var elements = {
      collapsed: $(collapsedTemplate),
      expanded: $(expandedTemplate).hide()
    };

    var bindDOMEventHandlers = function() {
      elements.collapsed.click(function() {
        if (isDirty()) {
          return;
        }
        elements.collapsed.hide();
        elements.expanded.show();
        eventbus.trigger('layer:selected', 'linearAsset');
      });
    };

    var bindExternalEventHandlers = function() {
      eventbus.on('layer:selected', function(selectedLayer) {
        if (selectedLayer !== 'linearAsset') {
          elements.expanded.hide();
          elements.collapsed.show();
        }
      }, this);
    };

    bindDOMEventHandlers();

    bindExternalEventHandlers();

    this.element = $('<div/>')
      .append(elements.collapsed)
      .append(elements.expanded);
  };

  var isDirty = function() {
    if (window.selectedAssetController && selectedAssetController.isDirty()) {
      new Confirm();
      return true;
    }
  };

  ActionPanelBoxes.AssetBox = function() {
    var expandedTemplate = [
      '<div class="actionPanel">',
      '  <div class="layerGroup layerGroupSelectedMode">',
      '    <div class="layerGroupImg_asset layerGroupImg_selected_asset"></div>',
      '    <div class="layerGroupLabel">Joukkoliikenteen pysäkit</div>',
      '  </div>',
      '  <div class="layerGroupLayers" style="display: block;">',
      '    <div class="busStopLayer">',
      '      <div class="busStopLayerCheckbox"><input name="current" type="checkbox" checked=""></div>',
      '      Voimassaolevat',
      '    </div>',
      '    <div class="busStopLayer">',
      '      <div class="busStopLayerCheckbox"><input name="future" type="checkbox"></div>',
      '      Tulevat',
      '    </div>',
      '    <div class="busStopLayer">',
      '      <div class="busStopLayerCheckbox"><input name="past" type="checkbox"></div>',
      '      Käytöstä poistuneet',
      '    </div>',
      '  </div>',
      '  <button class="actionModeButton editMode" style="display: none;">Siirry muokkaustilaan</button>',
      '</div>'].join('');

    var editModeTemplate = [
      '<div class="actionPanel">',
      '  <div class="layerGroup layerGroupSelectedMode layerGroupEditMode">',
      '    <div class="layerGroupImg_asset layerGroupImg_selected_asset"></div>',
      '    <div class="layerGroupLabel">Joukkoliikenteen pysäkit</div>',
      '  </div>',
      '  <div class="layerGroupLayers" style="display: block;">',
      '    <div class="busStopLayer">',
      '      <div class="busStopLayerCheckbox"><input name="current" type="checkbox" checked=""></div>',
      '      Voimassaolevat',
      '    </div>',
      '    <div class="busStopLayer">',
      '      <div class="busStopLayerCheckbox"><input name="future" type="checkbox"></div>',
      '      Tulevat',
      '    </div>',
      '    <div class="busStopLayer">',
      '      <div class="busStopLayerCheckbox"><input name="past" type="checkbox"></div>',
      '      Käytöstä poistuneet',
      '    </div>',
      '  </div>',
      '  <div class="actionButtons" style="">',
      '    <div data-action="Select" class="actionButton actionPanelButtonSelect actionButtonActive">',
      '      <div class="actionPanelButtonSelectImage"></div>',
      '    </div>',
      '    <div data-action="Add" class="actionButton actionPanelButtonAdd">',
      '      <div class="actionPanelButtonAddImage"></div>',
      '    </div>',
      '  </div>',
      '  <button class="actionModeButton readOnlyMode" style="display: none;">Siirry katselutilaan</button>',
      '</div>'].join('');

    var collapsedTemplate = [
      '<div class="actionPanel">',
      '  <div class="layerGroup">',
      '    <div class="layerGroupImg_asset layerGroupImg_unselected_asset"></div>',
      '    <div class="layerGroupLabel">Joukkoliikenteen pysäkit</div>',
      '  </div>',
      '</div>'].join('');

    var elements = {
      collapsed: $(collapsedTemplate).hide(),
      expanded: $(expandedTemplate),
      editMode: $(editModeTemplate).hide()
    };
    var actionButtons = elements.editMode.find('.actionButtons .actionButton');

    var validityPeriods = {
      current: true,
      future: false,
      past: false
    };

    var selectedValidityPeriods = function(validityPeriods) {
      return _.keys(_.pick(validityPeriods, function(selected) {
        return selected;
      }));
    };

    var bindDOMEventHandlers = function() {
      elements.expanded.find('button.editMode').click(function() {
        elements.expanded.hide();
        elements.editMode.show();
        eventbus.trigger('application:readOnly', false);
      });

      elements.editMode.find('button.readOnlyMode').click(function() {
        if (isDirty()) {
          return;
        }
        elements.editMode.hide();
        elements.expanded.show();
        eventbus.trigger('application:readOnly', true);
      });

      actionButtons.on('click', function() {
        if (isDirty()) {
          return;
        }
        var el = $(this);
        var action = el.attr('data-action');

        actionButtons.removeClass('actionButtonActive');
        el.addClass('actionButtonActive');

        eventbus.trigger('tool:changed', action);
      });

      var validityPeriodChangeHandler = function(evt) {
        if (isDirty()) {
          var target = evt.currentTarget;
          $(target).prop('checked', !target.checked);
          evt.preventDefault();
          return;
        }
        var el = $(this);
        var validityPeriod = el.prop('name');
        var checked = el.prop('checked');
        validityPeriods[validityPeriod] = checked;
        eventbus.trigger('validityPeriod:changed', selectedValidityPeriods(validityPeriods));
      };

      elements.expanded.find('input[type=checkbox]').change(validityPeriodChangeHandler);

      elements.editMode.find('input[type=checkbox]').change(validityPeriodChangeHandler);

      elements.collapsed.click(function() {
        elements.collapsed.hide();
        elements.expanded.show();
        eventbus.trigger('layer:selected', 'asset');
      });
    };

    var bindExternalEventHandlers = function() {
      eventbus.on('validityPeriod:changed', function() {
        var toggleValidityPeriodCheckbox = function(validityPeriods, el) {
          $(el).prop('checked', validityPeriods[el.name]);
        };

        var checkboxes = $.makeArray(elements.expanded.find('input[type=checkbox]'))
                           .concat($.makeArray(elements.editMode.find('input[type=checkbox]')));
        _.forEach(checkboxes, _.partial(toggleValidityPeriodCheckbox, validityPeriods));
      });

      eventbus.on('asset:saved asset:created', function(asset) {
        validityPeriods[asset.validityPeriod] = true;
        eventbus.trigger('validityPeriod:changed', selectedValidityPeriods(validityPeriods));
      }, this);

      eventbus.on('layer:selected', function(selectedLayer) {
        if (selectedLayer !== 'asset') {
          elements.expanded.hide();
          elements.editMode.hide();
          elements.collapsed.show();
        }
        actionButtons.removeClass('actionButtonActive');
        actionButtons.filter('.actionPanelButtonSelect').addClass('actionButtonActive');
      }, this);

      eventbus.on('roles:fetched', function(roles) {
        if (!_.contains(roles, 'viewer')) {
          elements.expanded.find('.actionModeButton').show();
          elements.editMode.find('.actionModeButton').show();
        }
      });
    };

    bindDOMEventHandlers();

    bindExternalEventHandlers();

    this.element = $('<div/>')
      .append(elements.collapsed)
      .append(elements.expanded)
      .append(elements.editMode);
  };
})(window.ActionPanelBoxes = window.ActionPanelBoxes || {});
