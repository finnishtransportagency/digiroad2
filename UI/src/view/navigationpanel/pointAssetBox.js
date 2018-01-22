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
      }).join('').concat (

      (assetConfig.layerName == 'trafficSigns') ? [
        '<div class="panel-section">' +
        '   <div class="checkbox">' +
        '     <label><input name="speedLimits" type="checkbox" /> Nopeusrajoitukset</label> <br>' +
        '   </div>' +
        '   <div class="checkbox">' +
        '     <label><input name="pedestrianCrossings" type="checkbox" /> Suojatiet</label> <br>' +
        '   </div>' +
        '   <div class="checkbox">' +
        '     <label><input name="maximumRestrictions" type="checkbox" /> Suurin sallittu - rajoitukset</label> <br>' +
        '   </div>' +
        '   <div class="checkbox">' +
        '     <label><input name="generalWarningSigns" type="checkbox" /> Varoitukset</label> <br>' +
        '   </div>' +
        '   <div class="checkbox">' +
        '     <label><input name="prohibitionsAndRestrictions" type="checkbox" /> Kiellot ja rajoitukset</label>' +
        '   </div>' +
        '</div>'].join('') : "");
    };

    this.checkboxPanel = function () {
      return assetConfig.allowComplementaryLinks ? [
        '  <div class="check-box-container">' +
        '     <input id="complementaryLinkCheckBox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
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

    var trafficSignHandler = function(event) {
        var el = $(event.currentTarget);
        var trafficSignType = el.prop('name');
        if (el.prop('checked')) {
          eventbus.trigger('trafficSigns:changeSigns', [trafficSignType, true]);
        } else {
          if (applicationModel.isDirty()) {
            el.prop('checked', true);
            new Confirm();
          } else {
            eventbus.trigger('trafficSigns:changeSigns', [trafficSignType, false]);
          }
        }
    };

    function show() {
      if (me.editModeToggle.hasNoRolesPermission(me.roles)) {
        me.editModeToggle.reset();
      } else {
        me.editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      }
      element.show();
    }

    function hide() {
      element.hide();
    }

    this.renderTemplate = function () {
      this.expanded = me.elements().expanded;
     $(me.expanded).find('.checkbox').find('input[type=checkbox]').change(trafficSignHandler);
      me.eventHandler();
      return element
        .append(this.expanded)
        .hide();
    };

    return {
      title: me.title(),
      layerName: me.layerName(),
      element: me.renderTemplate(),
      // allowComplementaryLinks: assetConfig.allowComplementaryLinks,
      show: show,
      hide: hide
    };
  };
})(this);
