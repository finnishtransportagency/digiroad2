(function(root) {
  root.CarryingCapacityBox = function (assetConfig) {
    LinearAssetBox.call(this, assetConfig);
    var me = this;

    this.header = function () {
      return assetConfig.title;
    };

    this.legendName = function () {
      return 'linear-asset-legend ' + assetConfig.className;
    };

    this.className = function() {
      return assetConfig.className;
    };

    this.labeling = function () {
      var frostHeavingFactorValues = [
        [1, '40 Erittäin routiva'],
        [2, '50 Väliarvo 50...60'],
        [3, '60 Routiva'],
        [4, '70 Väliarvo 60...80'],
        [5, '80 Routimaton']
      ];

      var springCarryingCapacityValues = [
        [10, '0 - 161 MN/m<sup>2</sup>'],
        [11, '162 - 286 MN/m<sup>2</sup>'],
        [12, '287 - 433 MN/m<sup>2</sup>'],
        [13, '434 – 670 MN/m<sup>2</sup>'],
        [14, '671 – 2050 MN/m<sup>2</sup>'],
        [15, '2050 -  MN/m<sup>2</sup>']
      ];

      var withoutValue = [[0, 'Ei tietoa']];

      var legend = function(legendName, values) {
        return'<div class="' + legendName + '-legend">' + _.map(values, function(value) {
          return '<div class="legend-entry">' +
            '<div class="label">' + value[1] + '</div>' +
            '<div class="symbol linear ' + me.className() + '-' + value[0] + '" ></div>' +
            '</div>';
        }).join('')+ '</div>';
      };

      return '<div class="panel-section panel-legend '+ me.legendName() + '-legend">' +
        legend('frost-heaving-factor', frostHeavingFactorValues.concat(withoutValue)) +
        legend('spring-carrying-capacity', springCarryingCapacityValues.concat(withoutValue)) + '</div>';
    };

      this.template = function () {
          this.expanded = me.elements().expanded;
          $(me.expanded).find('input[type=radio][name=labelingRadioButton-'+ me.className() + ']').change(labelingHandler);
          me.eventHandler();
          return me.getElement()
              .append(this.expanded)
              .hide();
      };

    var labelingHandler = function() {
      if (this.value === 'spring-carrying-capacity') {
          $('.frost-heaving-factor-legend').hide();
          $('.spring-carrying-capacity-legend').show();
          eventbus.trigger(assetConfig.singleElementEventCategory+':spring-carrying-capacity', true);
        } else {
          $('.frost-heaving-factor-legend').show();
          $('.spring-carrying-capacity-legend').hide();
          eventbus.trigger(assetConfig.singleElementEventCategory + ':spring-carrying-capacity', false);
        }
    };

    this.radioButton = function () {
      return [
        '  <div class="panel-section">' +
        '    <div class="radio">' +
        '     <label>' +
        '       <input name="labelingRadioButton-'+ me.className() + '" value="spring-carrying-capacity" type="radio" checked /> Kevätkantavuus' +
        '     </label>' +
        '     <label>' +
        '       <input name="labelingRadioButton-'+ me.className() + '" value="frost-heaving-factor" type="radio" /> Routivuuskerroin' +
        '     </label>' +
        '    </div>' +
        '  </div>'
            ].join('');
    };

    this.panel = function () {
      return [ '<div class="panel ' + me.className() +'">',
        '  <header class="panel-header expanded">',
        me.header() ,
        '  </header>'
        ].join('');
    };

    this.predicate = function () {
      return assetConfig.authorizationPolicy.editModeAccess();
    };

    var element = $('<div class="panel-group ' + me.className()+ '"></div>');

    this.getElement = function () {
      return element;
    };
  };
})(this);

