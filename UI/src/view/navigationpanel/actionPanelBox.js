(function(root) {
  root.ActionPanelBox = function() {
    var me = this;
    this.roles = {};

    this.selectToolIcon = '<img src="images/select-tool.svg"/>';
    this.cutToolIcon = '<img src="images/cut-tool.svg"/>';
    this.addToolIcon = '<img src="images/add-tool.svg"/>';
    this.rectangleToolIcon = '<img src="images/rectangle-tool.svg"/>';
    this.polygonToolIcon = '<img src="images/polygon-tool.svg"/>';
    this.terminalToolIcon = '<img src="images/add-terminal-tool.svg"/>';

    this.Tool = function (toolName, icon) {
      var className = toolName.toLowerCase();
      var element = $('<div class="action"/>').addClass(className).attr('action', toolName).append(icon).click(function () {
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
      var element = $('<div class="panel-section panel-actions" />');
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

    this.renderTemplate = function () {};
    this.header = function () {};
    this.title = function () {};
    this.layerName = function () {};
    this.labeling = function () {};
    this.checkboxPanel = function () {};
    this.predicate = function () {};
    this.legendName = function () {};

    this.elements = function (){
      return { expanded: $([
        me.panel(),
        me.labeling(),
        me.checkboxPanel(),
        me.bindExternalEventHandlers(),
        '  </div>',
        '</div>'].join(''))  };
    };

    this.panel = function () {
      return [ '<div class="panel ' + me.layerName() +'">',
               '  <header class="panel-header expanded">',
                me.header() ,
               '  </header>',
               '   <div class="panel-section panel-legend linear-asset-legend '+ me.legendName() + '-legend">'].join('');
    };


    this.bindExternalEventHandlers = function() {
      eventbus.on('roles:fetched', function(roles) {
        me.roles = roles;
        if (me.predicate()) {
          me.toolSelection.reset();
          $(me.expanded).append(me.toolSelection.element);
          $(me.expanded).append(me.editModeToggle.element);
        }
      });
      eventbus.on('application:readOnly', function(readOnly) {
        $(me.expanded).find('.panel-header').toggleClass('edit', !readOnly);
      });
    };

    this.eventHandler = function(){
      $(me.expanded).find('#complementaryLinkCheckBox').on('change', function (event) {
        if ($(event.currentTarget).prop('checked')) {
          eventbus.trigger(me.layerName() + '-complementaryLinks:show');
        } else {
          if (applicationModel.isDirty()) {
            $(event.currentTarget).prop('checked', true);
            new Confirm();
          } else {
            eventbus.trigger(me.layerName() +'-complementaryLinks:hide');
          }
        }
      });

      $(me.expanded).find('#trafficSignsCheckbox').on('change', function (event) {
        if ($(event.currentTarget).prop('checked')) {
          eventbus.trigger(me.layerName() + '-readOnlyTrafficSigns:show');
        } else {
          eventbus.trigger(me.layerName() + '-readOnlyTrafficSigns:hide');
        }
      });
    };
  };
})(this);
