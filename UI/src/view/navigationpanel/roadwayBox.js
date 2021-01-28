(function(root) {
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
        {index: 0, text: 'Ei tiemerkintöjä'},
        {index: 1, text: 'Tiemerkintöjä'},
      ];

      var widthOfRoadAxisEntries = [
        {symbolUrl: 'images/roadway/roadwayWidth/L1_pysaytysviiva.jpg', label: 'L1 pysäytysviiva'},
        {symbolUrl: 'images/roadway/roadwayWidth/L2_vaistamisviiva.jpg', label: 'L2 Väistämisviiva'},
        {symbolUrl: 'images/roadway/roadwayWidth/L3_suojatie.jpg', label: 'L3 Suojatie'},
        {symbolUrl: 'images/roadway/roadwayWidth/L4_1_pyoratien_jatke.jpg', label: 'L4.1 Pyörätien jatke'},
        {symbolUrl: 'images/roadway/roadwayWidth/L4_2_pyaratien_jatke_ja_suojatie.jpg', label: 'L4.2 Pyörätien jatke ja suojatie'},
        {symbolUrl: 'images/roadway/roadwayWidth/L4_3_pyoratien_jatke_ja_suojatie_rinnakkain.jpg', label: 'L4.3 Pyörätien jatke ja suojatie rinnakkaine'},
        {symbolUrl: 'images/roadway/roadwayWidth/L5_1_toyssy.jpg', label: 'L5.1 Töyssy'},
        {symbolUrl: 'images/roadway/roadwayWidth/L5_2_toyssy.jpg', label: 'L5.2 Töyssy'},
        {symbolUrl: 'images/roadway/roadwayWidth/L6_herateraidat.jpg', label: 'L6 Heräteraidat'},
      ];
      var otherMarkingEntries = [
        {symbolUrl: 'images/roadway/roadwayOther/M1_ajokaistanuoli.jpg', label: 'M1 ajokaistanuoli'},
        {symbolUrl: 'images/roadway/roadwayOther/M2_ajokaistan_vaihtamisnuoli.jpg', label: 'M2 ajokaistan vaihtamisnuoli'},
        {symbolUrl: 'images/roadway/roadwayOther/M3_pysakointialue.jpg', label: 'M3 Pysäköintialue'},
        {symbolUrl: 'images/roadway/roadwayOther/M4_keltainen_reunamerkinta.jpg', label: 'M4 Keltanen reunamerkintä'},
        {symbolUrl: 'images/roadway/roadwayOther/M5_pysayttamisrajoitus.jpg', label: 'M5 Pysäyttämisrajoitus'},
        {symbolUrl: '', label: 'M6 ohjausviiva'},
        {symbolUrl: 'images/roadway/roadwayOther/M7_jalankulkija.jpg', label: 'M7 Jalankulkija'},
        {symbolUrl: 'images/roadway/roadwayOther/M8_pyorailija.jpg', label: 'M8 Pyöräilijä'},
        {symbolUrl: 'images/roadway/roadwayOther/M9_vaistamisvelvollisuutta_osoittava_ennakkomerkinta.jpg', label: 'M9 Väistämisvelvollisuutta osoittava ennakkomerkintä'},
        {symbolUrl: 'images/roadway/roadwayOther/M10_stop-ennakkomerkinta.jpg', label: 'M10 Stop-ennakkomerkintä'},
        {symbolUrl: 'images/roadway/roadwayOther/M11_P-merkinta.jpg', label: 'M11 P-Merkintä'},
        {symbolUrl: 'images/roadway/roadwayOther/M12_invalidin_ajoneuvo.jpg', label: 'M12 Invalidin ajoneuvo'},
        {symbolUrl: 'images/roadway/roadwayOther/M13_BUS-merkinta.jpg', label: 'M13 BUS-merkintä'},
        {symbolUrl: 'images/roadway/roadwayOther/M14_TAXI-merkinta.jpg', label: 'M14 Taxi-merkintä'},
        {symbolUrl: 'images/roadway/roadwayOther/M15_lataus.jpg', label: 'M15 Lataus'},
        {symbolUrl: 'images/roadway/roadwayOther/M16_nopeusrajoitus.jpg', label: 'M16 Nopeusrajoitus'},
        {symbolUrl: 'images/roadway/roadwayOther/M17_tienumero.jpg', label: 'M17 Tienumero'},
        {symbolUrl: '', label: 'M18 Risteysruudutus'},
        {symbolUrl: '', label: 'M19 Liikennemerkki'},
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
              $('.roadway .cut').show();
              $('.roadway .rectangle').show();
              $('.roadway .polygon').show();
              //event buss trigger layer where asset are
            me.showEditModeButton();
          } else if (this.value === 'width-of-road-axis') {
              $('.roadwayLength-legend').hide();
              $('.roadwayWidth-legend').show();
              $('.roadwayOther-legend').hide();
              $('.roadway .cut').hide();
              $('.roadway .rectangle').hide();
              $('.roadway .polygon').hide();
            //event buss trigger layer where asset are
          } else if (this.value === 'other-roadway') {
              $('.roadwayLength-legend').hide();
              $('.roadwayWidth-legend').hide();
              $('.roadwayOther-legend').show();
              $('.roadway .cut').hide();
              $('.roadway .rectangle').hide();
              $('.roadway .polygon').hide();
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

