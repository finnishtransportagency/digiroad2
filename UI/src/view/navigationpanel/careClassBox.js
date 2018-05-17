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

      var winterCareValues= [
        [0, '(IsE) Liukkaudentorjunta ilman toimenpideaikaa'],
        [1, '(Is) Normaalisti aina paljaana'],
        [2, '(I) Normaalisti paljaana'],
        [3, '(Ib) Osan talvea lumipintaisena'],
        [4, '(TIb) Ib-luokka taajamassa'],
        [5, '(II) Pääosin lumipintainen'],
        [6, '(III) Hiekoitus vain pahimmissa tilanteissa'],
        [7, '(K1) Hyvin hoidettu kevyen liikenteen väylä'],
        [8, '(K2) Merkitykseltään vähäisempi kevyen liikenteen väylä'],
        [9, 'Kevyen liikenteen väylällä ei talvihoitoa'],
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
        [20, '(T2) Luonnonmukainen taajamassa'],
        [21, '(E1) Puistomainen erityisalue'],
        [22, '(E2) Luonnonmukainen erityisalue'],
        [23, '(Y) Ympäristötekijä']
      ];

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
      }).join('') + winterWalkwayLegend + '</div>';

      var greenCareLegend = '<div class="green-care-legend">' + _.map(greenCareValues, function(greenCareValue) {
        return '<div class="legend-entry green-care-legend">' +
            '<div class="label">' + greenCareValue[1] + '</div>' +
            '<div class="symbol linear care-class-' + greenCareValue[0] + '" />' +
            '</div>';
      }).join('') + '</div>';

      return '<div class="panel-section panel-legend '+ me.legendName() + '-legend">' + winterCareLegend + greenCareLegend + '</div>';
    };

    this.renderTemplate = function () {
      this.expanded = me.elements().expanded;
      $(me.expanded).find('input[type=radio][name=labelRadio]').change(labelingHandler);
      me.eventHandler();
      return me.getElement()
          .append(this.expanded)
          .hide();
    };

    var labelingHandler = function() {
      if (this.value == 'winterCare') {
        $('.green-care-legend').hide();
        $('.winter-care-legend').show();
        eventbus.trigger('careClass:winterCare', true);

      } else {
        $('.green-care-legend').show();
        $('.winter-care-legend').hide();
        eventbus.trigger('careClass:winterCare', false);
      }
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

