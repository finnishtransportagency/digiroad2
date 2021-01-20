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

      var linearMarkingEntries = [
        {index: 0, text: 'Keskiviiva'},
        {index: 1, text: 'Keskiviiva ja reunaviiva'},
        {index: 2, text: 'Reunaviiva,ajokaistaviiva ja varoitusviiva'},
        {index: 3, text: 'Reunaviiva'},
        {index: 4, text: 'Ajokaistaviiva'},
      ];

      var widthOfRoadAxisEntries = [
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'test'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'test'},
      ];
      var otherMarkingEntries = [
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'test'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'test'},
      ];

      var roadwayLabel =
          '<div class="legend-entry">' +
          '<div class="fat-label"> test</div>' +
          '</div>';

      var linearLegend = legendDiv('roadwayLength', roadwayLinearLegend(linearMarkingEntries), roadwayLabel);
      var widthOfRoadAxisLegend = legendDiv('roadwayWidth', roadwayPointLikeLegend(widthOfRoadAxisEntries), roadwayLabel);
      var otherMarkingLegend = legendDiv('roadwayOther', roadwayPointLikeLegend(otherMarkingEntries), roadwayLabel);

      return '<div class="panel-section panel-legend '+ me.legendName() + '-legend">' +
          linearLegend +
          widthOfRoadAxisLegend +
          otherMarkingLegend + '</div>';
    };
      var legendDiv =function(className,entries,label){
          return '<div class="' + className + '-legend'+'">'+label+ entries+'</div>';
      };

    var roadwayPointLikeLegend = function(values){
      return _.map(values, function(value) {
            return '<div class="legend-entry">' +
                '  <div class="label">' +
                '    <span>' + value.label + '</span> ' +
                '    <img class="symbol-to-right" src="' + value.symbolUrl + '"/>' +
                '  </div>' +
                '</div>';
        }).join('');
    };

    var roadwayLinearLegend = function(values) {
      return _.map(values, function(value) {
        return '<div class="legend-entry">' +
          '<div class="label">' + value.text + '</div>' +
          '<div class="symbol linear roadway-' + value.index + '" />' +
          '</div>';
      }).join('');
    };

    this.template = function () {
      this.expanded = me.elements().expanded;
      $(me.expanded).find('input[type=radio][name=labelRadio]').change(function() {
        if(applicationModel.isDirty()){
          $(me.expanded).find('input[type=radio][name=labelRadio][value=length-of-road-axis]').prop("checked", true);
          $('.roadwayLength-legend').show();
          $('.roadwayWidth-legend').hide();
          $('.roadwayOther-legend').hide();
          new Confirm();
        } else {
          console.log(this.value);
          if (this.value === 'length-of-road-axis') {
            $('.roadwayLength-legend').show();
              $('.roadwayWidth-legend').hide();
              $('.roadwayOther-legend').hide();
              //event buss trigger layer where asset are
            me.showEditModeButton();
          } else if (this.value === 'width-of-road-axis') {
              $('.roadwayLength-legend').hide();
              $('.roadwayWidth-legend').show();
              $('.roadwayOther-legend').hide();
            //event buss trigger layer where asset are
            applicationModel.setReadOnly(true);
          } else if (this.value === 'other-roadway') {
              $('.roadwayLength-legend').hide();
              $('.roadwayWidth-legend').hide();
              $('.roadwayOther-legend').show();
              //event buss trigger layer where asset are
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

