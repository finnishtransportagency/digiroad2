(function(root) {
  root.TRSpeedLimitBox = function (assetConfig) {
    LinearAssetBox.call(this, assetConfig);
    var me = this;

    this.legendName = function () {
      return 'linear-asset-legend speed-limit';
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

    this.predicate = function () {
      return (!assetConfig.readOnly && _.contains(me.roles, 'operator') || _.contains(me.roles, 'premium'));
    };

    this.toolSelection = new me.ToolSelection([
      new me.Tool('Select', me.selectToolIcon, assetConfig.selectedLinearAsset),
      new me.Tool('Cut',  me.cutToolIcon, assetConfig.selectedLinearAsset)
    ]);

    this.editModeToggle = new EditModeToggleButton(me.toolSelection);

    var element = $('<div class="panel-group tr-speed-limits"/>');

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
