(function(root) {
  root.LaneModellingToolBox = function (assetConfig) {
    LinearAssetBox.call(this, assetConfig);
    var me = this;

    this.header = function () {
      return assetConfig.title;
    };

    this.title = assetConfig.title;

    this.layerName = assetConfig.layerName;

    this.legendName = function () {
      return 'linear-asset-legend ' + assetConfig.className;
    };

    this.labeling = function () {
      var laneModellingToolValues = [assetConfig.editControlLabels.mainLane, assetConfig.editControlLabels.additionalLane];

      return _.map(laneModellingToolValues, function (parkingProhibitionValue, idx) {
        return '<div class="legend-entry">' +
          '<div class="label">' + parkingProhibitionValue + '</div>' +
          '<div class="symbol linear ' + assetConfig.className + '-' + idx + '" />' +
          '</div>';
      }).join('') + '</div>';
    };

    this.predicate = function () {
      return assetConfig.authorizationPolicy.editModeAccess();
    };

    this.municipalityVerified = function () {
      return assetConfig.hasMunicipalityValidation;
    };

    this.toolSelection = new me.ToolSelection([
      new me.Tool('Select', me.selectToolIcon, assetConfig.selectedLinearAsset),
      new me.Tool('Cut',  me.cutToolIcon, assetConfig.selectedLinearAsset)
    ]);

    var element = $('<div class="panel-group ' + assetConfig.className + 's"/>');

    this.editModeToggle = new EditModeToggleButton(me.toolSelection);

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