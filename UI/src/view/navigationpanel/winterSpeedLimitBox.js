(function(root) {
  root.WinterSpeedLimitBox = function (assetConfig) {
    LinearAssetBox.call(this, assetConfig);
    var me = this;

    this.legendName = function () {
      return 'speed-limit';
    };

    this.labeling = function () {
      var speedLimits = [100, 80, 70, 60];
      return  _.map(speedLimits, function(speedLimit) {
        return '<div class="legend-entry">' +
          '<div class="label">' + speedLimit + '</div>' +
          '<div class="symbol linear speed-limit-' + speedLimit + '" />' +
          '</div>';
      }).join('');
    };

    this.predicate = function () {
      return _.contains(me.roles, 'operator') || _.contains(me.roles, 'premium');
    };

    var element = $('<div class="panel-group winter-speed-limits"/>');

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