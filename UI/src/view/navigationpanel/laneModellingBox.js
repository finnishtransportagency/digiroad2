(function(root) {
  root.LaneModellingBox = function (assetConfig) {
    LinearAssetBox.call(this, assetConfig);
    var me = this;

    this.legendName = function () {
      return 'linear-asset-legend ' + assetConfig.className;
    };

    this.labeling = function () {
      var laneModellingToolValues = ['Pääkaista', 'Lisäkaista'];

      return _.map(laneModellingToolValues, function (laneModellingToolValue, idx) {
        return '<div class="legend-entry">' +
          '<div class="label">' + laneModellingToolValue + '</div>' +
          '<div class="symbol linear ' + assetConfig.className + '-' + idx + '" />' +
          '</div>';
      }).join('') + '</div>';
    };

    this.toolSelection = new me.ToolSelection([
      new me.Tool('Select', me.selectToolIcon, assetConfig.selectedLinearAsset),
      new me.Tool('Cut',  me.cutToolIcon, assetConfig.selectedLinearAsset)
    ]);

    this.editModeToggle = new EditModeToggleButton(me.toolSelection);

  };
})(this);