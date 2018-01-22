(function (root) {
  root.PointAssetBox = function (assetConfig) {
    ActionPanelBox.call(this);
    var me = this;

    this.header = function(){
      return assetConfig.title;
    };

    this.title = function () {
      return assetConfig.title;
    };

    this.layerName = function () {
      return assetConfig.layerName;
    };

    this.elements = function (){
      return { expanded: $([
        me.panel(),
        me.labeling(),
        '  </div>',
        me.checkboxPanel(),
        me.bindExternalEventHandlers(),
        '</div>'].join(''))  };
    };

    this.panel = function () {
      var legend = !_.isEmpty(assetConfig.legendValues) ? '<div class="panel-section panel-legend limit-legend">' : "";
      return ['<div class="panel">' +
              '  <header class="panel-header expanded">' +
                    assetConfig.title +
              '  </header>' +
              legend
      ].join('');
    };

    this.labeling = function () {
      return _(assetConfig.legendValues).map(function (val) {
        return '<div class="legend-entry">' +
               '  <div class="label">' +
               '    <span>' + val.label + '</span> ' +
               '    <img class="symbol" src="' + val.symbolUrl + '"/>' +
               '  </div>' +
               '</div>';
      }).join('');
    };

    this.checkboxPanel = function () {
      return assetConfig.allowComplementaryLinks ? [
         '<div class="panel-section">' +
        '  <div class="check-box-container">' +
        '     <input id="complementaryLinkCheckBox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
        '   </div>'+
        '</div>'].join('') : '';
    };

    this.predicate = function () {
      return _.contains(me.roles, 'operator') || _.contains(me.roles, 'premium');
    };

    this.assetTools = function () {
      me.bindExternalEventHandlers(false);
    };

    this.toolSelection = new me.ToolSelection([
      new me.Tool('Select', me.selectToolIcon, assetConfig.selectedPointAsset),
      new me.Tool('Add',  me.addToolIcon, assetConfig.selectedPointAsset)
    ]);

    this.editModeToggle = new EditModeToggleButton(me.toolSelection);
    var element = $('<div class="panel-group point-asset ' +  _.kebabCase(assetConfig.layerName) + '"/>');

    function show() {
      if (me.editModeToggle.hasNoRolesPermission(me.roles)) {
        me.editModeToggle.reset();
      } else {
        me.editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      }
      me.getElement().show();
    }

    function hide() {
      me.getElement().hide();
    }

    this.renderTemplate = function () {
      this.expanded = me.elements().expanded;
      me.eventHandler();
      return me.getElement()
        .append(this.expanded)
        .hide();
    };

    this.getElement = function () {
      return element;
    };

    this.getShow = function () {
      return show();
    };

    this.getHide = function () {
      return hide();
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
