(function (root) {
  root.PointAssetBox = function (assetConfig) {
    ActionPanelBox.call(this);
    var me = this;

    this.header = function(){
      return assetConfig.title;
    };

    this.title = assetConfig.title;

    this.layerName = assetConfig.layerName;

    this.elements = function (){
      return { expanded: $([
        me.panel(),
        me.labeling(),
        me.roadTypeLabeling(),
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
      var labelingTypePanel = _(assetConfig.legendValues).map(function (val) {
        return '<div class="legend-entry">' +
          '    <div class="label ' + (val.cssClass ? val.cssClass : '') + '">' +
          '    <span>' + val.label + '</span> ' +
          '    <img class="symbol" src="' + val.symbolUrl + '"/>' +
          '  </div>' +
          '</div>';
      }).join('');

      return labelingTypePanel + '</div>';
    };

    this.roadTypeLabeling = function() {
      var roadTypePanel = [
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
      return assetConfig.allowComplementaryLinks ? [
         '<div class="panel-section">' +
        '  <div class="check-box-container">' +
        '     <input id="complementaryLinkCheckBox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
        '   </div>'+
        '</div>'].join('') : '';
    };

    this.predicate = function () {
      return assetConfig.authorizationPolicy.editModeAccess();
    };

    this.municipalityVerified = function () {
      return assetConfig.hasMunicipalityValidation;
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

    this.template = function () {
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

    this.show = show;
    this.hide = hide;
  };
})(this);
