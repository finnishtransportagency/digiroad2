(function(root) {
  //roadway
  root.RoadwayBox = function (assetConfig) {
    HybridAssetBox.call(this, assetConfig);
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
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'L1 pysäytysviiva'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'L2 Väistämisviiva'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'L3 Suojatie'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'L4.1 Pyörätien jatke'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'L4.2 Pyörätien jatke'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'L4.3 Pyörätien jatke'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'L5.1 Töyssy'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'L5.2 Töyssy'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'L6 Heräteraidat'},
      ];
      var otherMarkingEntries = [
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M1 ajokaistanuoli'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M2 ajokaistan vaihtamisnuoli'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M3 Pysäköintialue'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M4 Keltanen reunamerkintä'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M5 Pysäyttämisrajoitus'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M6 ohjausviiva'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M7 Jalankulkija'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M8 Pyöräilijä'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M9 Väistämisvelvollisuutta osoittava ennakkomerkintä'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M10 Stop-ennakkomerkintä'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M11 P-Merkintä'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M12 Invalidin ajoneuvo'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M13 BUS-merkintä'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M14 Taxi-merkintä'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M15 Lataus'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M16 Nopeusrajoitus'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M17 Tienumero'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M18 Risteysruudutus'},
        {symbolUrl: 'images/mass-transit-stops/1.png', label: 'M19 Liikennemerkki'},
      ];

      var linearLegend = legendDiv('roadwayLength', roadwayLinearLegend(linearMarkingEntries));
      var widthOfRoadAxisLegend = legendDiv('roadwayWidth', roadwayPointLikeLegend(widthOfRoadAxisEntries));
      var otherMarkingLegend = legendDiv('roadwayOther', roadwayPointLikeLegend(otherMarkingEntries));

      return '<div class="panel-section panel-legend '+ me.legendName() + '-legend">' +
          linearLegend +
          widthOfRoadAxisLegend +
          otherMarkingLegend + '</div>';
    };
      var legendDiv =function(className,entries){
          return '<div class="' + className + '-legend'+'">'+ entries+'</div>';
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
          } else if (this.value === 'other-roadway') {
              $('.roadwayLength-legend').hide();
              $('.roadwayWidth-legend').hide();
              $('.roadwayOther-legend').show();
              //event buss trigger layer where asset are
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
        '     <label>' + //length-of-road-axis
        '       <input name="labelRadio" value="length-of-road-axis" type="radio" checked>Pituussuuntaiset tiemerkinnät ' +
        '     </label>' +
        '     <label>' + //width-of-road-axis
        '       <input name="labelRadio" value="width-of-road-axis" type="radio">Poikittaissuuntaiset tiemerkinnät' +
        '     </label>' +
        '     <label>' +
        '       <input name="labelRadio" value="other-roadway" type="radio">Muut tiemerkinnät' +
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

