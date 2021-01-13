(function(root) {
  //roadway
  root.RoadwayBox = function (assetConfig) {
    LinearAssetBox.call(this, assetConfig);
    var me = this;

    this.header = function () {
      return assetConfig.title;
    };

    this.legendName = function () {
      return 'linear-asset-legend roadway';
    };

    this.labeling = function () {

      var roadwayLabel =
        '<div class="legend-entry">' +
        '<div class="fat-label"> test</div>' +
        '</div>';

      var roadwayLines = [
        [
          0, 'Keskiviiva'
        ],
        [
          1, 'Keskiviiva ja reunaviiva'
        ],
        [
          2, 'Reunaviiva,ajokaistaviiva ja varoitusviiva'
        ], [
          3, 'Reunaviiva'
        ], [
          4, 'Ajokaistaviiva'
        ]
      ];
      var roadwayLengthLegend = roadwayLegend(roadwayLabel, 'roadwayLength', roadwayLines);

      return '<div class="panel-section panel-legend '+ me.legendName() + '-legend">' + roadwayLengthLegend + '</div>';
    };

    var roadwayLegend = function(label, className, values, additionalClass) {
      var _additionalClass = additionalClass ? additionalClass : '';
      return '<div class="' + className + '-legend ' + _additionalClass + '">' + label + _.map(values, function(value) {
        return '<div class="legend-entry">' +
          '<div class="label">' + value[1] + '</div>' +
          '<div class="symbol linear roadway-' + value[0] + '" />' +
          '</div>';
      }).join('') + '</div>';
    };


    this.template = function () {
      this.expanded = me.elements().expanded;
      $(me.expanded).find('input[type=radio][name=labelRadio]').change(function() {
        if(applicationModel.isDirty()){
          $(me.expanded).find('input[type=radio][name=labelRadio][value=length-of-road-axis]').prop("checked", true);
          $('.roadwayLength-legend').show();
         // eventbus.trigger('careClass:winterCare', true);
          new Confirm();
        } else {
          console.log(this.value);
          if (this.value === 'length-of-road-axis') {
            $('.roadwayLength-legend').show();
            //eventbus.trigger('careClass:winterCare', true);
            me.showEditModeButton();
          } else {
            $('.roadwayLength-legend').hide();
           // eventbus.trigger('careClass:winterCare', false);
            applicationModel.setReadOnly(true);
          }
        }
      });
      me.eventHandler();
      return me.getElement()
          .append(this.expanded)
          .hide();
    };

    this.radioButton = function () {
      return [
        '  <div class="panel-section">' +
        '    <div class="radio">' +
        '     <label>' +
        '       <input name="labelRadio" value="length-of-road-axis" type="radio" checked>Pituussuuntaiset tiemerkinnät ' + //length-of-road-axis
        '     </label>' +
        '     <label>' +
        '       <input name="labelRadio" value="width-of-road-axis" type="radio">Poikittaissuuntaiset tiemerkinnät' + //
        '     </label>' +
        '     <label>' +
        '       <input name="labelRadio" value="other-roadway" type="radio">Muut tiemerkinnät' + //width-of-road-axis
        '     </label>' +
        '    </div>' +
        '  </div>'
      ].join('');
    };

    this.panel = function () {
      return [ '<div class="panel ' + me.layerName +'">',
        '  <header class="panel-header expanded">',
        me.header() ,
        '  </header>'
      ].join('');
    };

    this.predicate = function () {
      return assetConfig.authorizationPolicy.editModeAccess();
    };

    var element = $('<div class="panel-group roadway-panel"/>');

    this.showEditModeButton = function() {
      me.editModeToggle.reset();
      $(me.editModeToggle.element).show();
    };

    this.getElement = function () {
      return element;
    };
  };
})(this);

