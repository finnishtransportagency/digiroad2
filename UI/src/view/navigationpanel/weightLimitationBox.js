(function (root){
  root.WeightLimitationBox = function (assetConfig) {
    PointAssetBox.call(this, assetConfig);
    var me = this;

    this.labeling = function () {
      var weightLimitValues = [
        [0, 'Suurin sallittu massa'],
        [1, 'Yhdistelmän suurin sallittu massa'],
        [2, 'Suurin sallittu akselimassa'],
        [3, 'Suurin sallittu telimassa']
      ];

      return _(assetConfig.legendValues).map(function (val) {
        return '<div class="legend-entry">' +
          '  <div class="label">' +
          '    <span>' + val.label + '</span> ' +
          '    <img class="symbol" src="' + val.symbolUrl + '"/>' +
          '  </div>' +
          '</div>';
      }).join('').concat(_.map(weightLimitValues, function(weightLimit) {
        return '<div class="panel-legend limitation-label-legend">' +
          '  <div class="labeling-entry">' +
          '   <div class="limitation-' + weightLimit[0] + '">' + weightLimit[1] +
          '   </div>' +
          '  </div>' +
          '</div>';
      }).join(''));
    };

    var element = $('<div class="panel-group point-asset ' +  _.kebabCase(assetConfig.layerName) + '"/>');

    function show() {
      me.getShow();
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
