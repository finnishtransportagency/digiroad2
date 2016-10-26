(function (root) {
  root.LinkPropertyForm = function(selectedLinkProperty) {
    var functionalClasses = [1, 2, 3, 4, 5, 6, 7, 8];

    var localizedAdministrativeClasses = {
      Private: 'Yksityisen omistama',
      Municipality: 'Kunnan omistama',
      State: 'Valtion omistama'
    };

    var localizedTrafficDirections = {
      BothDirections: 'Molempiin suuntiin',
      AgainstDigitizing: 'Digitointisuuntaa vastaan',
      TowardsDigitizing: 'Digitointisuuntaan'
    };

    var linkTypes = [
      [1, 'Moottoritie'],
      [2, 'Moniajoratainen tie'],
      [3, 'Yksiajoratainen tie'],
      [4, 'Moottoriliikennetie'],
      [5, 'Kiertoliittymä'],
      [6, 'Ramppi'],
      [7, 'Levähdysalue'],
      [8, 'Kevyen liikenteen väylä'],
      [9, 'Jalankulkualue'],
      [10, 'Huolto- tai pelastustie'],
      [11, 'Liitännäisliikennealue'],
      [12, 'Ajopolku'],
      [13, 'Huoltoaukko moottoritiellä'],
      [21, 'Lautta/lossi']
    ];

    var verticalLevelTypes= [
      [-11, 'Tunneli'],
      [-1, 'Alikulku'],
      [0, 'Maan pinnalla'],
      [1, 'Silta, Taso 1'],
      [2, 'Silta, Taso 2'],
      [3, 'Silta, Taso 3'],
      [4, 'Silta, Taso 4']
    ];

    var allRoadTypes = [
      [1, 'Yleinen tie'],
      [2, 'Lauttaväylä yleisellä tiellä'],
      [3, 'Kunnan katuosuus'],
      [4, 'Yleisen tien työmaa'],
      [5, 'Yksityistie'],
      [9, 'Omistaja selvittämättä']
    ];

    var discontinuitys = [
      [1, 'Tien loppu'],
      [2, 'Epäjatkuva'],
      [3, 'ELY:n raja'],
      [4, 'Lievä epäjatkuvuus'],
      [5, 'Jatkuva']
    ];

    var getRoadType = function(administrativeClass,linkType){
      var value = 9;
      switch(administrativeClass.toString()){
        case 'State':
          if(linkType == 21){
            value = 2;
          }
          else{
            value = 1;
          }
          break;
        case 'Municipality':
          value = 3;
          break;
        case 'Private':
          value = 5;
          break;
      }
      var roadClass = _.find(allRoadTypes, function(x){return x[0] === value;});
      return roadClass && roadClass[1];
    };

    var getDiscontinuityType = function(discontinuity){
      var DiscontinuityType = _.find(discontinuitys, function(x){return x[0] === discontinuity;});
      return DiscontinuityType && DiscontinuityType[1];
    };

    var getLocalizedLinkType = function(linkType) {
      var localizedLinkType = _.find(linkTypes, function(x) { return x[0] === linkType; });
      return localizedLinkType && localizedLinkType[1];
    };

    var getVerticalLevelType = function(verticalLevel){
      var verticalLevelType = _.find(verticalLevelTypes, function(y) { return y[0] === verticalLevel; });
      return verticalLevelType && verticalLevelType[1];
    };

    var checkIfMultiSelection = function(mmlId){
      if(selectedLinkProperty.count() == 1){
        return mmlId;
      }
    };

    var dynamicField = function(labelText){
      var field;
      //If other fields get the same treatment they can be added here
      if(labelText === 'TIETYYPPI'){
        var roadTypes = "";
        _.each(selectedLinkProperty.get(), function(slp){
          var roadType = getRoadType(slp.administrativeClass, slp.linkType);
          if (roadTypes.length === 0) {
            roadTypes = roadType;
          } else if(roadTypes.search(roadType) === -1) {
            roadTypes = roadTypes + ", " + roadType;
          }
        });
        field = '<div class="form-group">' +
            '<label class="control-label">' + labelText + '</label>' +
            '<p class="form-control-static">' + roadTypes + '</p>' +
            '</div>' ;
      }
      return field;
    };

    var staticField = function(labelText, dataField) {
      return '<div class="form-group">' +
        '<label class="control-label">' + labelText + '</label>' +
        '<p class="form-control-static"><%- ' + dataField + ' %></p>' +
        '</div>';
    };

    var title = function() {
      if (selectedLinkProperty.count() == 1) {
        return '<span>Segmentin ID: ' + _.first(selectedLinkProperty.get()).id + '</span>';
      } else {
        return '<span>Ominaisuustiedot</span>';
      }
    };

    var buttons =
      '<div class="link-properties form-controls">' +
      '<button class="save btn btn-primary" disabled>Tallenna</button>' +
      '<button class="cancel btn btn-secondary" disabled>Peruuta</button>' +
      '</div>';

    var template = function(options) {
      var endDateField = selectedLinkProperty.count() == 1 && typeof selectedLinkProperty.get()[0].endDate !== 'undefined' ?
        staticField('LAKKAUTUS', 'endDate') : '';
      var roadTypes = selectedLinkProperty.count() == 1 ? staticField('TIETYYPPI', 'roadType') : dynamicField('TIETYYPPI');
      var staticSegmentIdField = selectedLinkProperty.count() == 1 ? staticField('SEGMENTIN ID', 'segmentId') : '';
      return _.template('' +
        '<header>' +
        title() + buttons +
        '</header>' +
        '<div class="wrapper read-only">' +
        '<div class="form form-horizontal form-dark">' +
        '<div class="form-group">' +
        '<p class="form-control-static asset-log-info">Muokattu viimeksi: <%- modifiedBy %> <%- modifiedAt %></p>' +
        '</div>' +
        '<div class="form-group">' +
        '<p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + selectedLinkProperty.count() + '</p>' +
        '</div>' +
        staticField('TIENUMERO', 'roadNumber') +
        staticField('TIEOSANUMERO', 'roadPartNumber') +
        staticField('AJORATA', 'trackCode') +
        staticField('ALKUETÄISYYS', 'startAddressM') +
        staticField('LOPPUETÄISUUS', 'endAddressM') +
        staticField('ELY', 'elyCode') +
        roadTypes +
        staticField('JATKUVUUS', 'discontinuity') +
        endDateField +
        '</div>' +
        '</div>' +
        '<footer>' + buttons + '</footer>', options);
    };

    var addressNumberString = function(minAddressNumber, maxAddressNumber) {
      if(!minAddressNumber && !maxAddressNumber) {
        return '';
      } else {
        var min = minAddressNumber || '';
        var max = maxAddressNumber || '';
        return min + '-' + max;
      }
    };

    var bindEvents = function() {
      var rootElement = $('#feature-attributes');
      var toggleMode = function(readOnly) {
        rootElement.find('.editable .form-control-static').toggle(readOnly);
        rootElement.find('select').toggle(!readOnly);
        rootElement.find('.form-controls').toggle(!readOnly);
      };
      eventbus.on('linkProperties:selected linkProperties:cancelled', function(linkProperties) {
        linkProperties.modifiedBy = linkProperties.modifiedBy || '-';
        linkProperties.modifiedAt = linkProperties.modifiedAt || '';
        linkProperties.localizedLinkTypes = getLocalizedLinkType(linkProperties.linkType) || 'Tuntematon';
        linkProperties.localizedAdministrativeClass = localizedAdministrativeClasses[linkProperties.administrativeClass] || 'Tuntematon';
        linkProperties.roadNameFi = linkProperties.roadNameFi || '';
        linkProperties.roadNameSe = linkProperties.roadNameSe || '';
        linkProperties.roadNameSm = linkProperties.roadNameSm || '';
        linkProperties.addressNumbersRight = addressNumberString(linkProperties.minAddressNumberRight, linkProperties.maxAddressNumberRight);
        linkProperties.addressNumbersLeft = addressNumberString(linkProperties.minAddressNumberLeft, linkProperties.maxAddressNumberLeft);
        linkProperties.verticalLevel = getVerticalLevelType(linkProperties.verticalLevel) || '';
        linkProperties.mmlId = checkIfMultiSelection(linkProperties.mmlId) || '';
        linkProperties.roadAddress = linkProperties.roadAddress || '';
        linkProperties.segmentId = linkProperties.segmentId || '';
        linkProperties.roadNumber = linkProperties.roadNumber || '';
        if (linkProperties.roadNumber > 0) {
          linkProperties.roadPartNumber = linkProperties.roadPartNumber || '';
          linkProperties.startAddressM = linkProperties.startAddressM || '0';
          linkProperties.trackCode = isNaN(parseFloat(linkProperties.trackCode)) ? '' : parseFloat(linkProperties.trackCode);
          linkProperties.elyCode = linkProperties.elyCode || '';
        } else {
          linkProperties.roadPartNumber = '';
          linkProperties.trackCode = '';
          linkProperties.startAddressM = '';
          linkProperties.elyCode = '';
        }
        linkProperties.endAddressM = linkProperties.endAddressM || '';
        linkProperties.discontinuity = getDiscontinuityType(linkProperties.discontinuity) || '';
        linkProperties.endDate = linkProperties.endDate || '';
        linkProperties.roadType = linkProperties.roadType || '';

        var trafficDirectionOptionTags = _.map(localizedTrafficDirections, function(value, key) {
          var selected = key === linkProperties.trafficDirection ? " selected" : "";
          return '<option value="' + key + '"' + selected + '>' + value + '</option>';
        }).join('');
        var functionalClassOptionTags = _.map(functionalClasses, function(value) {
          var selected = value == linkProperties.functionalClass ? " selected" : "";
          return '<option value="' + value + '"' + selected + '>' + value + '</option>';
        }).join('');
        var linkTypesOptionTags = _.map(linkTypes, function(value) {
          var selected = value[0] == linkProperties.linkType ? " selected" : "";
          return '<option value="' + value[0] + '"' + selected + '>' + value[1] + '</option>';
        }).join('');
        var defaultUnknownOptionTag = '<option value="" style="display:none;"></option>';
        var options =  { imports: { trafficDirectionOptionTags: defaultUnknownOptionTag.concat(trafficDirectionOptionTags),
          functionalClassOptionTags: defaultUnknownOptionTag.concat(functionalClassOptionTags),
          linkTypesOptionTags: defaultUnknownOptionTag.concat(linkTypesOptionTags) }};
        rootElement.html(template(options,linkProperties)(linkProperties));
        rootElement.find('.traffic-direction').change(function(event) {
          selectedLinkProperty.setTrafficDirection($(event.currentTarget).find(':selected').attr('value'));
        });
        rootElement.find('.functional-class').change(function(event) {
          selectedLinkProperty.setFunctionalClass(parseInt($(event.currentTarget).find(':selected').attr('value'), 10));
        });
        rootElement.find('.link-types').change(function(event) {
          selectedLinkProperty.setLinkType(parseInt($(event.currentTarget).find(':selected').attr('value'), 10));
        });
        toggleMode(applicationModel.isReadOnly());
      });
      eventbus.on('linkProperties:changed', function() {
        rootElement.find('.link-properties button').attr('disabled', false);
      });
      eventbus.on('linkProperties:unselected', function() {
        rootElement.empty();
      });
      eventbus.on('application:readOnly', toggleMode);
      rootElement.on('click', '.link-properties button.save', function() {
        selectedLinkProperty.save();
      });
      rootElement.on('click', '.link-properties button.cancel', function() {
        selectedLinkProperty.cancel();
      });


      eventbus.on('layer:selected', function(layer) {

      });

    };

    bindEvents();
  };
})(this);
