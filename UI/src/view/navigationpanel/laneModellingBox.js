(function(root) {
  root.LaneModellingBox = function (assetConfig) {
    LinearAssetBox.call(this, assetConfig);
    var me = this;

    this.Tool = function (toolName, icon) {
      var className = toolName.toLowerCase();
      var element = $('<div class="action"></div>').addClass(className).attr('action', toolName).append(icon).click(function () {
        applicationModel.setSelectedTool(toolName);
      });
      var deactivate = function () {
        element.removeClass('active');
      };
      var activate = function () {
        element.addClass('active');
      };

      return {
        element: element,
        deactivate: deactivate,
        activate: activate,
        name: toolName
      };
    };

    this.legendName = function () {
      return 'linear-asset-legend ' + assetConfig.className;
    };

    this.labeling = function () {
      var laneModellingToolValues = ['Pääkaista', 'Lisäkaista', "Tielinkillä 1 lisäkaista",
        "Tielinkillä 2 lisäkaistaa", "Tielinkillä 3 lisäkaistaa", "Tielinkillä 4 lisäkaistaa",
        "Tielinkillä 5 lisäkaistaa", "Tielinkillä 6 lisäkaistaa tai enemmän"];

      return _.map(laneModellingToolValues, function (laneModellingToolValue, idx) {
        return '<div class="legend-entry">' +
          '<div class="label">' + laneModellingToolValue + '</div>' +
          '<div class="symbol linear ' + assetConfig.className + '-' + idx + '" ></div>' +
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
