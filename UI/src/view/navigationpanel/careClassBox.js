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
        '<div class="fat-label"> Kevyen liikenteen väylät </div>' +
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
        [24, '(L) Kevyen liikenteen laatukäytävät'],
        [7, '(K1) Melko vilkkaat kevyen liikenteen väylät'],
        [8, '(K2) Kevyen liikenteen väylien perus talvihoitotaso'],
        [9, 'Kevyen liikenteen väylät, joilla ei talvihoitoa'],
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
        [18, '(N3) Muut tiet'],
        [19, '(T1) Puistomainen taajamassa'],
        [20, '(T2) Luonnonmukainen taajamassa']

      ];

      var suburbanAreaValues = [
        [20, '(T1) Puistomainen taajamassa'],
        [21, '(T2) Luonnonmukainen taajamassa']
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



      var missingGreenCareLegend = '<div class="green-care-legend missing-value-legend">' + _.map(badGreenCareValues, function(value) {
        return '<div class="legend-entry">' +
          '<div class="label">' + value[1] + '</div>' +
          '<div class="symbol linear care-class-' + value[0] + '" />' +
          '</div>';
      }).join('') + '</div>';

      var missingWinterCareLegend = '<div class="winter-care-legend missing-value-legend">' + _.map(badWinterCareValues, function(value) {
        return '<div class="legend-entry">' +
          '<div class="label">' + value[1] + '</div>' +
          '<div class="symbol linear care-class-' + value[0] + '" />' +
          '</div>';
      }).join('') + '</div>';

      var winterWalkwayLegend = walkwayLabel + '<div class="winter-care-legend">' + _.map(winterWalkwayValues, function(winterWalkwayValue) {
        return '<div class="legend-entry">' +
            '<div class="label">' + winterWalkwayValue[1] + '</div>' +
            '<div class="symbol linear care-class-' + winterWalkwayValue[0] + '" />' +
            '</div>';
      }).join('') + '</div>';

      var winterCareLegend = '<div class="winter-care-legend">' + _.map(winterCareValues, function(winterCareValue) {
        return '<div class="legend-entry">' +
            '<div class="label">' + winterCareValue[1] + '</div>' +
            '<div class="symbol linear care-class-' + winterCareValue[0] + '" />' +
            '</div>';
      }).join('') + winterWalkwayLegend + missingWinterCareLegend + '</div>';

      var specialAreaLegend = specialAreasLabel + '<div class="green-care-legend">' + _.map(specialAreaValues, function(specialAreaValue) {
        return '<div class="legend-entry">' +
            '<div class="label">' + specialAreaValue[1] + '</div>' +
            '<div class="symbol linear care-class-' + specialAreaValue[0] + '" />' +
            '</div>';
      }).join('') + '</div>';

      var suburbanAreaLegend = suburbanAreasLabel + '<div class="green-care-legend">' + _.map(suburbanAreaValues, function(suburbanAreaValue) {
          return '<div class="legend-entry">' +
              '<div class="label">' + suburbanAreaValue[1] + '</div>' +
              '<div class="symbol linear care-class-' + suburbanAreaValue[0] + '" />' +
              '</div>';
      }).join('') + '</div>';

      var greenCareLegend = '<div class="green-care-legend">' + _.map(greenCareValues, function(greenCareValue) {
        return '<div class="legend-entry green-care-legend">' +
          '<div class="label">' + greenCareValue[1] + '</div>' +
          '<div class="symbol linear care-class-' + greenCareValue[0] + '" />' +
          '</div>';
      }).join('') + suburbanAreaLegend + specialAreaLegend + missingGreenCareLegend + '</div>';

      return '<div class="panel-section panel-legend '+ me.legendName() + '-legend">' + winterCareLegend + greenCareLegend + '</div>';
    };

    this.renderTemplate = function () {
      this.expanded = me.elements().expanded;
      $(me.expanded).find('input[type=radio][name=labelRadio]').change(event, function() {
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
        '   <div class="panel-section panel-legend '+ me.legendName() + '-legend">' +
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
      return [ '<div class="panel ' + me.layerName() +'">',
        '  <header class="panel-header expanded">',
        me.header() ,
        '  </header>'
      ].join('');
    };

    this.predicate = function () {
      return assetConfig.authorizationPolicy.editModeAccess();
    };

    var element = $('<div class="panel-group care-class"/>');

    this.hideEditModeButton = function() {
      me.toolSelection.reset();
      $(me.toolSelection.element).hide();
      $(me.editModeToggle.element).hide();
    };

    this.showEditModeButton = function() {
      me.editModeToggle.reset();
      $(me.editModeToggle.element).show();
    };

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

