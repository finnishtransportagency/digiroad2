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
        [1, 'Betoni'],
        [2, 'Kivi '],
        [10, 'Kovat asfalttibetonit'],
        [20, 'Pehmeät asfalttibetonit'],
        [30, 'Soratien pintaus'],
        [40, 'Sorakulutuskerros '],
        [50, 'Muut pinnoitteet'],
        [99, 'Päällystetty, tyyppi tuntematon']
      ];

      return _.map(pavementClassValues, function (pavementClassValue) {
        return '<div class="legend-entry">' +
          '<div class="label">' + pavementClassValue[1] + '</div>' +
          '<div class="symbol linear paved-road-' + pavementClassValue[0] + '" />' +
          '</div>';
      }).join('') + '</div>';
    };

    var element = $('<div class="panel-group paved-roads"/>');

    this.getElement = function () {
      return element;
    };
  };
})(this);

