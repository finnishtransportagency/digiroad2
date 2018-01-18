(function(root) {
  root.ActionPanelBox = function() {
    var me = this;
    this.userRoles = {};

    this.selectToolIcon = '<img src="images/select-tool.svg"/>';
    this.cutToolIcon = '<img src="images/cut-tool.svg"/>';
    this.addToolIcon = '<img src="images/add-tool.svg"/>';
    this.rectangleToolIcon = '<img src="images/rectangle-tool.svg"/>';
    this.polygonToolIcon = '<img src="images/polygon-tool.svg"/>';
    this.terminalToolIcon = '<img src="images/add-terminal-tool.svg"/>';

    this.Tool = function (toolName, icon) {
      var className = toolName.toLowerCase();
      var element = $('<div class="action"/>').addClass(className).attr('action', toolName).append(icon).click(function () {
        executeOrShowConfirmDialog(function () {
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

    var executeOrShowConfirmDialog = function(f) {
      if (applicationModel.isDirty()) {
        new Confirm();
      } else {
        f();
      }
    };

    this.elements = function (){
      return { expanded: $([ '<div class="panel ' + me.layerName() +'">',
              '  <header class="panel-header expanded">',
              me.header() ,
              '  </header>',
              '   <div class="panel-section panel-legend linear-asset-legend '+ me.legendName() + '-legend">',
              me.labeling(),
              me.checkboxPanel(),
              me.assetTools(),
              '  </div>',
              '</div>'].join(''))  };
    };

    var renderTemplate = function () {};
    var header = function () {};
    var title = function () {};
    var layerName = function () {};
    var legendName = function () {};
    var labeling = function () {};
    var checkboxPanel = function () {};
    var assetTools = function () {};


    this.bindExternalEventHandlers = function(readOnly) {
      eventbus.on('roles:fetched', function(roles) {
        me.userRoles = roles;
        if (!readOnly && _.contains(roles, 'operator') || _.contains(roles, 'premium') ) {
          me.toolSelection.reset();
          $(me.expanded).append(me.toolSelection.element);
          $(me.expanded).append(me.editModeToggle.element);
        }
      });
      eventbus.on('application:readOnly', function(readOnly) {
        $(expanded).find('.panel-header').toggleClass('edit', !readOnly);
      });
    };


    return {
      renderTemplate: renderTemplate,
      header: header,
      title: title,
      layerName: layerName,
      legendName: legendName,
      labeling: labeling,
      checkboxPanel: checkboxPanel,
      assetTools: assetTools
    };
  };
})(this);
