(function (root){
  root.WidthLimitationBox = function (assetConfig) {
    PointAssetBox.call(this, assetConfig);
    var me = this;

    this.labeling = function () {
      var heightLimitValues = [
        [1, 'Suurin sallittu leveys']
      ];

      return _(assetConfig.legendValues).map(function (val) {
        return '<div class="legend-entry">' +
          '  <div class="label">' +
          '    <span>' + val.label + '</span> ' +
          '    <img class="symbol" src="' + val.symbolUrl + '"/>' +
          '  </div>' +
          '</div>';
      }).join('').concat(_.map(heightLimitValues, function(heightLimit) {
        return '<div class="panel-legend limitation-label-legend">' +
          '  <div class="labeling-entry">' +
          '   <div class="limitation-' + heightLimit[0] + '">' + heightLimit[1] +
          '   </div>' +
          '  </div>' +
          '</div>';
      }).join(''));
    };

    var element = $('<div class="panel-group point-asset ' +  _.kebabCase(assetConfig.layerName) + '"/>');

    this.predicate = function () {
      return assetConfig.readOnly;
    };

    function show() {
      me.getShow();
    }

    // function show() {
    //   if (!assetConfig.authorizationPolicy.editModeAccess()) {
    //     me.editModeToggle.reset();
    //   } else {
    //     me.editModeToggle.toggleEditMode(applicationModel.isReadOnly());
    //   }
    //   element.show();
    // }

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
