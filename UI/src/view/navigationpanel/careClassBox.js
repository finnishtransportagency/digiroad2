(function(root) {
  root.CareClassBox = function (assetConfig) {
    LinearAssetBox.call(this, assetConfig);
    var me = this;

    this.header = function () {
      return assetConfig.title;
    };

    this.legendName = function () {
      return 'linear-asset-legend care-class';
    };

    this.labeling = function () {

      var walkwayLabel =
        '<div class="legend-entry">' +
        '<div class="fat-label"> Kävelyn ja pyöräilyn väylät </div>' +
        '</div>';

      var specialAreasLabel =
        '<div class="legend-entry">' +
        '<div class="fat-label"> Erityisalueet </div>' +
        '</div>';

      var suburbanAreasLabel =
        '<div class="legend-entry">' +
        '<div class="fat-label"> Taajamien hoitoluokat </div>' +
        '</div>';

      var winterCareValues= [
        [0, '(IsE) Liukkaudentorjunta ilman toimenpideaikaa'],
        [1, '(Is) Normaalisti aina paljaana'],
        [2, '(I) Normaalisti paljaana'],
        [3, '(Ib) Pääosin suolattava, ajoittain hieman liukas'],
        [4, '(Ic) Pääosin hiekoitettava, ohut lumipolanne sallittu'],
        [5, '(II) Pääosin lumipintainen'],
        [6, '(III) Pääosin lumipintainen, pisin toimenpideaika'],
        [24, '(L) Kävelyn ja pyöräilyn laatukäytävät'],
        [7, '(K1) Melko vilkkaat kävelyn ja pyöräilyn väylät'],
        [8, '(K2) Kävelyn ja pyöräilyn väylien perus talvihoitotaso'],
        [9, 'Kävelyn ja pyöräilyn väylät, joilla ei talvihoitoa'],
        [10, 'Pääkadut ja vilkkaat väylät'],
        [11, 'Kokoojakadut'],
        [12, 'Tonttikadut']
      ];

      var winterWalkwayValues = [
        [13, 'A-luokan väylät'],
        [14, 'B-luokan väylät'],
        [15, 'C-luokan väylät']
      ];

      var greenCareValues = [
        [16, '(N1) 2-ajorataiset tiet'],
        [17, '(N2) Valta- ja kantatiet sekä vilkkaat seututiet'],
        [18, '(N3) Muut tiet']
      ];

      var suburbanAreaValues = [
        [19, '(T1) Puistomainen taajamassa'],
        [20, '(T2) Luonnonmukainen taajamassa']
      ];

      var specialAreaValues = [
        [21, '(E1) Puistomainen erityisalue'],
        [22, '(E2) Luonnonmukainen erityisalue'],
        [23, '(Y) Ympäristötekijä']
      ];

      var badWinterCareValues = [
        [25, 'Vain viherhoitoluokka'],
        [26, 'Ei hoitoluokkaa']
      ];

      var badGreenCareValues = [
        [25, 'Vain talvihoitoluokka'],
        [26, 'Ei hoitoluokkaa']
      ];

      var missing = 'missing-value-legend';
      var green = 'green-care';
      var winter = 'winter-care';

      var winterCareLegend = careLegend('', winter, winterCareValues) + careLegend(walkwayLabel, winter, winterWalkwayValues) + careLegend('', winter, badWinterCareValues, missing);
      var greenCareLegend = careLegend('', green, greenCareValues) + careLegend(suburbanAreasLabel, green, suburbanAreaValues) + careLegend(specialAreasLabel, green, specialAreaValues) + careLegend('', green, badGreenCareValues, missing);

      return '<div class="panel-section panel-legend '+ me.legendName() + '-legend">' + greenCareLegend + winterCareLegend + '</div>';
    };

    var careLegend = function(label, className, values, additionalClass) {
      var _additionalClass = additionalClass ? additionalClass : '';
      return '<div class="' + className + '-legend ' + _additionalClass + '">' + label + _.map(values, function(value) {
        return '<div class="legend-entry">' +
          '<div class="label">' + value[1] + '</div>' +
          '<div class="symbol linear care-class-' + value[0] + '" ></div>' +
          '</div>';
      }).join('') + '</div>';
    };


    this.template = function () {
      this.expanded = me.elements().expanded;
      $(me.expanded).find('input[type=radio][name=labelRadio]').change(function() {
        if(applicationModel.isDirty()){
          //if the asset is dirty reselect winterCare and trigger the confirm since switching to green care should be impossible
          $(me.expanded).find('input[type=radio][name=labelRadio][value=winterCare]').prop("checked", true);
          $('.green-care-legend').hide();
          $('.winter-care-legend').show();
          eventbus.trigger('careClass:winterCare', true);
          new Confirm();
        } else {
          if (this.value == 'winterCare') {
            $('.green-care-legend').hide();
            $('.winter-care-legend').show();
            eventbus.trigger('careClass:winterCare', true);
            me.showEditModeButton();
          } else {
            $('.green-care-legend').show();
            $('.winter-care-legend').hide();
            eventbus.trigger('careClass:winterCare', false);
            applicationModel.setReadOnly(true);
            me.hideEditModeButton();
          }
        }
      });
      me.eventHandler();
      return me.getElement()
          .append(this.expanded)
          .hide();
    };

    this.checkboxPanel = function () {
      return assetConfig.allowComplementaryLinks ? [
        '   <div class="panel-section">' +
        '     <div class="check-box-container">' +
        '       <input id="complementaryLinkCheckBox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
        '     </div>' +
        '   </div>'
      ].join('') : '';
    };

    this.radioButton = function () {
      return [
        '  <div class="panel-section">' +
        '    <div class="radio">' +
        '     <label>' +
        '       <input name="labelRadio" value="winterCare" type="radio" checked> Talvihoitoluokka' +
        '     </label>' +
        '     <label>' +
        '       <input name="labelRadio" value="greenCare" type="radio"> Viherhoitoluokka' +
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

    var element = $('<div class="panel-group care-class"></div>');

    this.hideEditModeButton = function() {
      me.toolSelection.reset();
      $(me.toolSelection.element).hide();
      $(me.editModeToggle.element).hide();
    };

    this.showEditModeButton = function() {
      me.editModeToggle.reset();
      $(me.editModeToggle.element).show();
    };

    this.getElement = function () {
      return element;
    };
  };
})(this);

