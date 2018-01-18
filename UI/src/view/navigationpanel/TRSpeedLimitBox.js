(function(root) {
  root.TRSpeedLimitBox = function (assetConfig) {
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
      return 'speed-limit';
    };

    this.labeling = function () {
      var speedLimits = [120, 100, 90, 80, 70, 60, 50, 40, 30, 20];
      return  _.map(speedLimits, function(speedLimit) {
        return '<div class="legend-entry">' +
          '<div class="label">' + speedLimit + '</div>' +
          '<div class="symbol linear speed-limit-' + speedLimit + '" />' +
          '</div>';
      }).join('');
    };

    this.checkboxPanel = function () {
      return [
        '<div class="check-box-container">' +
        '<input id="signsCheckbox" type="checkbox" /> <lable>Näytä liikennemerkit</lable>' +
        '</div>' +
        '</div>'
      ].join('');
    };

    this.assetTools = function () {
      me.bindExternalEventHandlers(assetConfig.readOnly);
    };

    this.toolSelection = new me.ToolSelection([
      new me.Tool('Select', me.selectToolIcon, assetConfig.selectedLinearAsset),
      new me.Tool('Cut',  me.cutToolIcon, assetConfig.selectedLinearAsset)
    ]);

    this.editModeToggle = new EditModeToggleButton(me.toolSelection);

    var element = $('<div class="panel-group tr-speed-limits"/>');
    this.expanded = {};

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
      $(me.expanded).find('#signsCheckbox').on('change', function (event) {
        if ($(event.currentTarget).prop('checked')) {
          eventbus.trigger(assetConfig.layerName + ':showReadOnlyTrafficSigns');
        } else {
          eventbus.trigger(assetConfig.layerName + ':hideReadOnlyTrafficSigns');
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
