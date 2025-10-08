(function(root) {
  root.PavedRoadBox = function (assetConfig) {
    LinearAssetBox.call(this, assetConfig);
    var me = this;

    this.header = function () {
      return assetConfig.title;
    };

    this.legendName = function () {
      return 'linear-asset-legend paved-road';
    };

    this.labeling = function () {
      var pavementClassValues = [
        [1, 'Asfaltti'],
        [2, 'Kivi '],
        [3, 'Sitomaton kulutuskerros'],
        [4, 'Muut päällysteluokat'],
        [99, 'Päällystetty, tyyppi tuntematon']
      ];

      return _.map(pavementClassValues, function (pavementClassValue) {
        return '<div class="legend-entry">' +
          '<div class="label">' + pavementClassValue[1] + '</div>' +
          '<div class="symbol linear paved-road-' + pavementClassValue[0] + '" ></div>' +
          '</div>';
      }).join('') + '</div>';
    };

    var element = $('<div class="panel-group paved-roads"></div>');

    this.getElement = function () {
      return element;
    };
  };
})(this);

