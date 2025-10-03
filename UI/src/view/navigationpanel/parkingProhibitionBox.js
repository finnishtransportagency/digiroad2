(function(root) {
    root.ParkingProhibitionBox = function (assetConfig) {
      LinearAssetBox.call(this, assetConfig);
      var me = this;

      this.header = function () {
          return assetConfig.title;
      };

      this.legendName = function () {
        return 'linear-asset-legend parking-prohibition';
      };

      this.labeling = function () {
        var parkingProhibitionValues = [
            [1, 'Ei tietoa rajoituksesta'],
            [2, 'Pysähtyminen kielletty'],
            [3, 'Pysäköinti kielletty']
        ];

        return _.map(parkingProhibitionValues, function (parkingProhibitionValue) {
           return '<div class="legend-entry">' +
            '<div class="label">' + parkingProhibitionValue[1] + '</div>' +
            '<div class="symbol linear parking-prohibition-' + parkingProhibitionValue[0] + '" ></div>' +
            '</div>';
        }).join('') + '</div>';
      };

      var element = $('<div class="panel-group parking-prohibition"></div>');

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
