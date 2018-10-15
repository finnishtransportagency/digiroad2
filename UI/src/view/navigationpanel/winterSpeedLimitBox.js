(function(root) {
  root.WinterSpeedLimitBox = function (assetConfig) {
    LinearAssetBox.call(this, assetConfig);
    var me = this;

    this.legendName = function () {
      return 'linear-asset-legend speed-limit';
    };

    this.labeling = function () {
      var speedLimits = [100, 80, 70, 60];
      return  _.map(speedLimits, function(speedLimit) {
        return '<div class="legend-entry">' +
          '<div class="label">' + speedLimit + '</div>' +
          '<div class="symbol linear speed-limit-' + speedLimit + '" />' +
          '</div>';
      }).join('') + '</div>';
    };

    this.predicate = function () {
      return assetConfig.authorizationPolicy.editModeAccess();
    };

    var element = $('<div class="panel-group winter-speed-limits"/>');

    function show() {
      if (!assetConfig.authorizationPolicy.editModeAccess()) {
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

    this.show = show;
    this.hide = hide;
  };
})(this);