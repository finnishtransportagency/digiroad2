(function(root) {
  root.LinearAssetBox = function(assetConfig, legendValues) {
    ActionPanelBox.call(this);
    var me = this;

    this.header = function () {
      return assetConfig.title + (assetConfig.editControlLabels.showUnit ? ' ('+assetConfig.unit+')': '');
    };

    this.title = assetConfig.title;

    this.layerName = assetConfig.layerName;

    this.legendName = function () {
      return 'limit';
    };

    this.labeling = function () {
      return _.map(legendValues, function(value, idx) {
        return value ? '<div class="legend-entry">' +
          '<div class="label">' + value + '</div>' +
          '<div class="symbol linear limit-' + idx + '" ></div>' +
          '</div>' : '';
      }).join('') + '</div>';
    };

    this.checkboxPanel = function () {
      var mapViewOnlyCheckbox = assetConfig.allowMapViewOnly ? [
          '     <div class="check-box-container">' +
          '       <input id="mapViewOnlyCheckbox" type="checkbox" checked/> ' +
          '       <lable>Näytä geometria</lable>' +
          '     </div>'
      ].join('') : '';

      var trafficSignsCheckbox = assetConfig.readOnlyLayer ? [
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

      return mapViewOnlyCheckbox.concat(trafficSignsCheckbox.concat(complementaryLinkCheckBox));
    };

    this.walkingCyclingPanel = function () {
      return assetConfig.allowWalkingCyclingLinks ? [
        '     <div class="check-box-container">' +
        '       <input id="walkingCyclingCheckbox" type="checkbox" />' +
        '       <lable>Näytä käpy-väylät</lable>' +
        '     </div>'
      ].join('') : '';
    };

    this.predicate = function () {
      return assetConfig.authorizationPolicy.editModeAccess();
    };

    this.municipalityVerified = function () {
      return assetConfig.hasMunicipalityValidation;
    };

    this.toolSelection = new me.ToolSelection([
      new me.Tool('Select', me.selectToolIcon, assetConfig.selectedLinearAsset),
      new me.Tool('Cut',  me.cutToolIcon, assetConfig.selectedLinearAsset),
      new me.Tool('Rectangle',  me.rectangleToolIcon, assetConfig.selectedLinearAsset),
      new me.Tool('Polygon',  me.polygonToolIcon, assetConfig.selectedLinearAsset)
    ]);

    this.editModeToggle = new EditModeToggleButton(me.toolSelection);

    var element = $('<div class="panel-group simple-limit ' + assetConfig.className + 's"></div>');

    this.template = function () {
      this.expanded = me.elements().expanded;
      me.eventHandler();
      return me.getElement()
        .append(this.expanded)
        .hide();
    };

    function show() {
      if (!assetConfig.authorizationPolicy.editModeAccess()) {
        me.editModeToggle.reset();
      } else {
        me.editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      }
      me.getElement().show();
    }

    function hide() {
      me.getElement().hide();
    }

    this.getElement = function () {
      return element;
    };

      this.show = show;
      this.hide = hide;
  };
})(this);
