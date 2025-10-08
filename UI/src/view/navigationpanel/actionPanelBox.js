(function(root) {
  root.ActionPanelBox = function() {
    var me = this;
    var authorizationPolicy = new AuthorizationPolicy();

    this.selectToolIcon = '<img src="images/select-tool.svg"/>';
    this.cutToolIcon = '<img src="images/cut-tool.svg"/>';
    this.addToolIcon = '<img src="images/add-tool.svg"/>';
    this.rectangleToolIcon = '<img src="images/rectangle-tool.svg"/>';
    this.polygonToolIcon = '<img src="images/polygon-tool.svg"/>';
    this.terminalToolIcon = '<img src="images/add-terminal-tool.svg"/>';
    this.checkIcon = '<img src="images/check-icon.png" title="Kuntakäyttäjän todentama"/>';
    this.pointAssetToolIcon = '<img src="images/add-point-asset-tool.svg"/>';

    this.Tool = function (toolName, icon) {
      var className = toolName.toLowerCase();
      var element = $('<div class="action"></div>').addClass(className).attr('action', toolName).append(icon).click(function () {
        me.executeOrShowConfirmDialog(function () {
          applicationModel.setSelectedTool(toolName);
        });
      });
      var deactivate = function () {
        element.removeClass('active');
      };
      var activate = function () {
        element.addClass('active');
      };

      return {
        element: element,
        deactivate: deactivate,
        activate: activate,
        name: toolName
      };
    };
    this.ToolSelection = function (tools) {
      var element = $('<div class="panel-section panel-actions"></div>');
      _.each(tools, function (tool) {
        element.append(tool.element);
      });
      var hide = function () {
        element.hide();
      };
      var show = function () {
        element.show();
      };
      var deactivateAll = function () {
        _.each(tools, function (tool) {
          tool.deactivate();
        });
      };
      var reset = function () {
        deactivateAll();
        tools[0].activate();
      };
      eventbus.on('tool:changed', function (name) {
        _.each(tools, function (tool) {
          if (tool.name != name) {
            tool.deactivate();
          } else {
            tool.activate();
          }
        });
      });

      hide();

      return {
        element: element,
        reset: reset,
        show: show,
        hide: hide
      };
    };

    this.executeOrShowConfirmDialog = function(f) {
      if (applicationModel.isDirty()) {
        new Confirm();
      } else {
        f();
      }
    };

    this.template = function () {};
    this.header = function () {};
    this.title = undefined;
    this.layerName = undefined;
    this.labeling = function () {};
    this.checkboxPanel = function () {};
    this.walkingCyclingPanel = function () {};
    this.predicate = function () {};
    this.radioButton = function () {};
    this.legendName = function () {};
    this.municipalityVerified = function () {};

    this.constructionTypeLabeling = function() {

      return [
        '  <div class="panel-section panel-legend linear-asset-legend construction-type-legend">',
        '    <div class="legend-entry">',
        '      <div class="label">Väliaikaisesti poissa käytöstä (haalennettu linkki)</div>',
        '      <div class="symbol linear construction-type-4"></div>',
        '    </div>',
        '  </div>'
      ].join('');
    };

    this.predicate = function () {
      return authorizationPolicy.editModeAccess();
    };

    this.elements = function (){
      return { expanded: $([
        me.panel(),
        me.radioButton(),
        me.labeling(),
        me.constructionTypeLabeling(),
        me.checkboxPanel(),
        me.walkingCyclingPanel(),
        me.bindExternalEventHandlers(),
        '  </div>',
        '</div>'].join(''))  };
    };

    this.panel = function () {
      return [ '<div class="panel ' + me.layerName +'">',
               '  <header class="panel-header expanded">',
                me.header() ,
               '  </header>',
               '   <div class="panel-section panel-legend '+ me.legendName() + '-legend">'].join('');
    };

    this.verificationIcon = function() {
      return '<div id="right-panel">' + me.checkIcon + '</div>';
    };

    this.addVerificationIcon = function(){
      $(me.expanded).find('.panel-header').css('display', 'flex');
      var panel = $(me.expanded).find('.panel-header');
      if(!panel.find('#right-panel').length)
        panel.append(me.verificationIcon());
    };

    this.bindExternalEventHandlers = function() {

      eventbus.on('roles:fetched', function() {
        if (me.predicate()) {
          me.toolSelection.reset();
          $(me.expanded).append(me.toolSelection.element);
          $(me.expanded).append(me.editModeToggle.element);
        }
        if(me.municipalityVerified()){
          me.addVerificationIcon();
        }
      });

      eventbus.on('verificationInfo:fetched', function(visible) {
        var img = me.expanded.find('#right-panel');
        if (visible)
          img.css('display','inline');
        else
          img.css('display','none');
      });

      eventbus.on('application:readOnly', function(readOnly) {
        $(me.expanded).find('.panel-header').toggleClass('edit', !readOnly);
      });
    };

    var handlerWithConfirmation = function(id, layer) {
      $(me.expanded).find('#' + id).on('change', function (event) {
        if ($(event.currentTarget).prop('checked')) {
          eventbus.trigger(me.layerName + '-' + layer + ':show');
        } else {
          if (applicationModel.isDirty()) {
            $(event.currentTarget).prop('checked', true);
            new Confirm();
          } else {
            eventbus.trigger(me.layerName + '-' + layer + ':hide');
          }
        }
      });
    };

    var handlerWithoutConfirmation = function(id, layer) {
      $(me.expanded).find('#' + id).on('change', function (event) {
        if ($(event.currentTarget).prop('checked')) {
          eventbus.trigger(me.layerName + '-' + layer + ':show');
        } else {
          eventbus.trigger(me.layerName + '-' + layer + ':hide');
        }
      });
    };

    this.eventHandler = function(){
      handlerWithConfirmation('mapViewOnlyCheckbox', 'mapViewOnly');
      handlerWithConfirmation('complementaryLinkCheckBox', 'complementaryLinks');
      handlerWithConfirmation('walkingCyclingCheckbox', 'walkingCyclingLinks');
      handlerWithoutConfirmation('trafficSignsCheckbox', 'readOnlyTrafficSigns');
    };
  };
})(this);
