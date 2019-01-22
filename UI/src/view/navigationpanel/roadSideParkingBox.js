(function(root) {
    root.RoadSideParkingBox = function (assetConfig) {
      LinearAssetBox.call(this, assetConfig);
      var me = this;

      this.header = function () {
          return assetConfig.title;
      };

      this.legendName = function () {
        return 'linear-asset-legend road-side-parking';
      };

      this.labeling = function () {
        var roadSideParkingValues = [
            [1, 'Ei tietoa rajoituksesta'],
            [2, 'Pysähtyminen kielletty'],
            [3, 'Pysäköinti kielletty']
        ];

        return _.map(roadSideParkingValues, function (roadSideParkingValue) {
           return '<div class="legend-entry">' +
            '<div class="label">' + roadSideParkingValue[1] + '</div>' +
            '<div class="symbol linear road-side-parking-' + roadSideParkingValue[0] + '" />' +
            '</div>';
        }).join('') + '</div>';
      };

      var element = $('<div class="panel-group road-side-parking"/>');

      function show() {
          if(!assetConfig.authorizationPolicy.editModeAccess()) {
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