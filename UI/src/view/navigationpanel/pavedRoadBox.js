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
        [99, 'Päällysteen tyyppi tuntematon'],
        [1, 'Betoni'],
        [2, 'Kivi '],
        [10, 'Kovat asfalttibetonit'],
        [20, 'Pehmeät asfalttibetonit'],
        [30, 'Soratien pintaus'],
        [40, 'Sorakulutuskerros '],
        [50, 'Muut pinnoitteet']
      ];

      return _.map(pavementClassValues, function (pavementClassValue) {
        return '<div class="legend-entry">' +
          '<div class="label">' + pavementClassValue[1] + '</div>' +
          '<div class="symbol linear paved-road-' + pavementClassValue[0] + '" />' +
          '</div>';
      }).join('') + '</div>';
    };

    this.predicate = function () {
      return assetConfig.authorizationPolicy.editModeAccess();
    };

    var element = $('<div class="panel-group paved-roads"/>');

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

    return {
      title: me.title(),
      layerName: me.layerName(),
      element: me.renderTemplate(),
      show: show,
      hide: hide
    };
  };
})(this);

