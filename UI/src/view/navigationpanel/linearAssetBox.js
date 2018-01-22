(function(root) {
  root.LinearAssetBox = function(assetConfig, legendValues) {
    ActionPanelBox.call(this);
    var me = this;

    this.header = function () {
      return  assetConfig.title + (assetConfig.editControlLabels.showUnit ? ' ('+assetConfig.unit+')': '');
    };

    this.title = function () {
      return assetConfig.title;
    };

    this.layerName = function () {
      return assetConfig.layerName;
    };

    this.legendName = function () {
      return assetConfig.layerName;
    };

    this.labeling = function () {
      return _.map(legendValues, function(value, idx) {
        return value ? '<div class="legend-entry">' +
          '<div class="label">' + value + '</div>' +
          '<div class="symbol linear limit-' + idx + '" />' +
          '</div>' : '';
      }).join('');
    };

    this.checkboxPanel = function () {
      var trafficSignsCheckbox = assetConfig.hasTrafficSignReadOnlyLayer ? [
          '<div class="check-box-container">' +
          '   <input id="trafficSignsCheckbox" type="checkbox" /> ' +
          '   <lable>Näytä liikennemerkit</lable>' +
          '</div>'
        ].join('') : '';

      var complementaryLinkCheckBox = assetConfig.allowComplementaryLinks ? [
          '  <div  class="check-box-container">' +
          '     <input id="complementaryLinkCheckBox" type="checkbox" /> ' +
          '     <lable>Näytä täydentävä geometria</lable>' +
          '   </div>'
        ].join('') : '';

      return trafficSignsCheckbox.concat(complementaryLinkCheckBox);
    };

    this.predicate = function () {
      return (!assetConfig.readOnly && (_.contains(me.roles, 'operator') || (_.contains(me.roles, 'premium') && assetConfig.layerName != 'maintenanceRoad') || (_.contains(me.roles, 'serviceRoadMaintainer') && assetConfig.layerName == 'maintenanceRoad')));
    };

    this.toolSelection = new me.ToolSelection([
      new me.Tool('Select', me.selectToolIcon, assetConfig.selectedLinearAsset),
      new me.Tool('Cut',  me.cutToolIcon, assetConfig.selectedLinearAsset),
      new me.Tool('Rectangle',  me.rectangleToolIcon, assetConfig.selectedLinearAsset),
      new me.Tool('Polygon',  me.polygonToolIcon, assetConfig.selectedLinearAsset)
    ]);

    this.editModeToggle = new EditModeToggleButton(me.toolSelection);

    var element = $('<div class="panel-group simple-limit ' + assetConfig.className + 's"/>');

    this.renderTemplate = function () {
      this.expanded = me.elements().expanded;
      me.eventHandler();
      return me.getElement()
        .append(this.expanded)
        .hide();
    };

    function show() {
      if (assetConfig.readOnly || (me.editModeToggle.hasNoRolesPermission(me.roles) || (_.contains(me.roles, 'premium') && (assetConfig.layerName == 'maintenanceRoad')))) {
        me.editModeToggle.reset();
      } else {
        me.editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      }
      element.show();
    }

    function hide() {
      element.hide();
    }

    this.getElement = function () {
      return element;
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

