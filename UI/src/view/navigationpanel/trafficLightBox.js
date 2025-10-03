(function (root){
  root.TrafficLightBox = function (assetConfig) {
    PointAssetBox.call(this, assetConfig);
    var me = this;

    var element = $('<div class="panel-group point-asset"' + _.kebabCase(assetConfig.layerName) + '></div>');

    me.panel = function () {
      return ['<div class="panel">' +
      '  <header class="panel-header expanded">' +
      assetConfig.title +
      '  </header>'
      ].join('');
    };

    function putLabel(val){
      return '<div class="legend-entry">' +
        '  <div class="label">' +
        '    <span>' + val.label + '</span> ' +
        '    <img class="symbol" src="' + val.symbolUrl + '"/>' +
        '  </div>' +
        '</div>';
    }

    var oldValuesLabels = function(){
      return '<label>Vanhan tietomallin liikennevalo</label>' +
        _(assetConfig.legendValues.oldValues).map(putLabel).join('');
    };

    var newValuesLabels = function(){
      return '<label>Uuden tietomallin liikennevalo</label>' +
        _(assetConfig.legendValues.newValues).map(putLabel).join('');
    };

    me.labeling = function () {
      var labels = [
        oldValuesLabels(),
        newValuesLabels()
      ];

      return _.map(labels, function (label) {
        return '<div class="panel-section traffic-lights-legend">' + label + '</div>';
      }).join('');
    };

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
