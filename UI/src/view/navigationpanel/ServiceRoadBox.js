(function(root) {
  root.ServiceRoadBox = function (assetConfig) {
    ActionPanelBox.call(this);
    var me = this;

    this.header = function () {
      return assetConfig.title + (assetConfig.editControlLabels.showUnit ? ' ('+assetConfig.unit+')': '');
    };

    this.title = function (){
      return assetConfig.title;
    };

    this.layerName = function () {
      return assetConfig.layerName;
    };

    this.legendName = function () {
      return 'service-road';
    };

    this.labeling = function () {
      var serviceRoadValues = [
        [ 0, 'Tieoikeus'],
        [ 1, 'Tiekunnan osakkuus'],
        [ 2, 'LiVin hallinnoimalla maa-alueella'],
        [ 3, 'Kevyen liikenteen väylä'],
        [ 4, 'Tuntematon']
      ];

      return   _.map(serviceRoadValues, function(serviceRoadValue) {
        return '<div class="legend-entry">' +
          '<div class="label">' + serviceRoadValue[1] + '</div>' +
          '<div class="symbol linear service-road-' + serviceRoadValue[0] + '" />' +
          '</div>';
      }).join('');
    };

    this.checkboxPanel = function () {
      return assetConfig.allowComplementaryLinks ? [
          '<div class="check-box-container">' +
          '<input id="complementaryLinkCheckBox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
          '</div>' +
          '</div>'
        ].join('') : '';
    };

    this.assetTools = function () {
      me.bindExternalEventHandlers(assetConfig.readOnly);
    };

    this.toolSelection = new me.ToolSelection([
      new me.Tool('Select', me.selectToolIcon, assetConfig.selectedLinearAsset),
      new me.Tool('Cut',  me.cutToolIcon, assetConfig.selectedLinearAsset),
      new me.Tool('Rectangle',  me.rectangleToolIcon, assetConfig.selectedLinearAsset),
      new me.Tool('Polygon',  me.polygonToolIcon, assetConfig.selectedLinearAsset)
    ]);

    this.editModeToggle = new EditModeToggleButton(me.toolSelection);

    var element = $('<div class="panel-group service-road"/>');
    var expanded = {};

    this.renderTemplate = function () {
      this.expanded = me.elements().expanded;
      myEvents();
      return element
        .append(this.expanded)
        .hide();
    };

    function show() {
      if (me.editModeToggle.hasNoRolesPermission(me.userRoles)) {
        me.editModeToggle.reset();
      } else {
        me.editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      }
      element.show();
    }

    function hide() {
      element.hide();
    }

    var myEvents = function() {

      $(me.expanded).find('#complementaryLinkCheckBox').on('change', function (event) {
        if ($(event.currentTarget).prop('checked')) {
          eventbus.trigger('complementaryLinks:show');
        } else {
          if (applicationModel.isDirty()) {
            $(event.currentTarget).prop('checked', true);
            new Confirm();
          } else {
            eventbus.trigger('complementaryLinks:hide');
          }
        }
      });
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

