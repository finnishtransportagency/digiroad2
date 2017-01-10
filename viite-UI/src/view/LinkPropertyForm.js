(function (root) {
  root.LinkPropertyForm = function(selectedLinkProperty) {
    var functionalClasses = [1, 2, 3, 4, 5, 6, 7, 8];
    var compactForm = false;
    var options;

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

    var discontinuities = [
      [1, 'Tien loppu'],
      [2, 'Epäjatkuva'],
      [3, 'ELY:n raja'],
      [4, 'Lievä epäjatkuvuus'],
      [5, 'Jatkuva']
    ];

    var floatingText = [
      [0, 'Ei'],
      [-1, 'Kyllä']
    ];

    var getDiscontinuityType = function(discontinuity){
      var DiscontinuityType = _.find(discontinuities, function(x){return x[0] === discontinuity;});
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

    var getFloatingType = function(floatingValue) {
      var floatingType =  _.find(floatingText, function (f) {
        return f[0] === floatingValue;
      });
      if(typeof floatingType == 'undefined'){
        floatingType = [0, 'Ei'];
      }
      return floatingType && floatingType[1];
    };

    var dynamicField = function(labelText){
      var floatingTransfer = (!applicationModel.isReadOnly() && compactForm);
      var field;
      //If other fields get the same treatment they can be added here
      if(labelText === 'TIETYYPPI'){
        var roadTypes = "";
        _.each(selectedLinkProperty.get(), function(slp){
          var roadType = slp.roadType;
          if (roadTypes.length === 0) {
            roadTypes = roadType;
          } else if(roadTypes.search(roadType) === -1) {
            roadTypes = roadTypes + ", " + roadType;
          }
        });
        if(floatingTransfer){
          field = '<div class="form-group">' +
            '<label class="control-label-floating">' + labelText + '</label>' +
            '<p class="form-control-static-floating">' + roadTypes + '</p>' +
            '</div>' ;
        } else {
          field = '<div class="form-group">' +
            '<label class="control-label">' + labelText + '</label>' +
            '<p class="form-control-static">' + roadTypes + '</p>' +
            '</div>';
          }
      } else if(labelText === 'VALITUT LINKIT'){
        var linkIds = "";
        var dynLinks = "";
        var id = 0;
        _.each(selectedLinkProperty.get(), function(slp){
          var divId = "VALITUTLINKIT" + id;
          var linkid = slp.linkId.toString();
          if (linkIds.length === 0) {
            field = '<div class="form-group" id=' +divId +'>' +
              '<label class="control-label-floating">' + 'LINK ID:' + '</label>' +
              '<p class="form-control-static-floating">' + linkid + '</p>' +
              '</div>' ;
            linkIds = linkid;
          } else if(linkIds.search(linkid) === -1){
            field = field + '<div class="form-group" id=' +divId +'>' +
              '<label class="control-label-floating">' + 'LINK ID:' + '</label>' +
              '<p class="form-control-static-floating">' + linkid + '</p>' +
              '</div>' ;
            linkIds = linkIds + ", " + linkid;
          }
          id = id + 1;
        });
      }
      return field;
    };

    var adjacentsTemplate = '' +
      '<br><br>' +
      '<div class="target-link-selection">' +
      '<label class="control-label-adjacents">LAITTAVISSA OLEVAT TEILINKIT, JOILTA PUUTTUU TIEOSOITE:</label>' +
      '<div class="form-group" id="adjacents">' +
      '<% _.forEach(adjacentLinks, function(l) { %>' +
      '<p class="form-control-static"> LINK ID <%= l.linkId %>' +
      '<span class="marker"><%= l.marker %></span>' +
      '<span class="edit-buttons">' +
      '<div class="edit buttons group" id="newSource-<%= l.linkId %>" adjacentLinkId="<%= l.linkId %>">' +
      '<button class="select-adjacent btn btn-new" id="sourceButton-<%= l.linkId %>" value="<%= l.linkId %>">Valitse</button>' +
      '</div>' +
      '</span>' +
      '</p>' +
      ' <% }) %>' +
      '</div>>' +
      '</div>';


    var staticField = function(labelText, dataField) {
      var floatingTransfer = (!applicationModel.isReadOnly() && compactForm);
      var field;
      if(floatingTransfer){
        field = '<div class="form-group">' +
        '<label class="control-label-floating">' + labelText + '</label>' +
        '<p class="form-control-static-floating"><%- ' + dataField + ' %></p>' +
        '</div>';
      } else {
        field = '<div class="form-group">' +
        '<label class="control-label">' + labelText + '</label>' +
        '<p class="form-control-static"><%- ' + dataField + ' %></p>' +
        '</div>';
      }
      return field;
    };

    var title = function() {
      return '<span>Tieosoitteen ominaisuustiedot</span>';
    };

    var buttons =
      '<div class="link-properties form-controls">' +
      '<button class="calculate btn btn-move" disabled>Siirrä</button>' +
      '<button class="save btn btn-primary" disabled>Tallenna</button>' +
      '<button class="cancel btn btn-secondary" disabled>Peruuta</button>' +
      '</div>';

    var notification = function(displayNotification) {
      if(displayNotification)
        return '' +
          '<div class="form-group form-notification">' +
          ' <p>Tien geometria on muuttunut. Korjaa tieosoitesegmentin sijainti valitsemalla ensimmäinen kohdelinkki, jolle haluat siirtää tieosoitesegmentin.</p>' +
          '</div>';
      else
        return '';
    };

    var notificationFloatingTransfer = function(displayNotification) {
      if(displayNotification)
        return '' +
          '<div class="form-group form-notification">' +
          ' <p>Kadun tai tien geometria on muuttunut, tarkista ja korjaa pysäkin sijainti.</p>' +
          '</div>';
      else
        return '';
    };


    var template = function(options) {
      var endDateField = selectedLinkProperty.count() == 1 && typeof selectedLinkProperty.get()[0].endDate !== 'undefined' ?
        staticField('LAKKAUTUS', 'endDate') : '';
      var roadTypes = selectedLinkProperty.count() == 1 ? staticField('TIETYYPPI', 'roadType') : dynamicField('TIETYYPPI');
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
        staticField('KELLUVA', 'floating') +
        endDateField  +
        '</div>' +
        '<footer>' + buttons + '</footer>', options);
    };

    var templateFloating = function(options) {
      var endDateField = selectedLinkProperty.count() == 1 && typeof selectedLinkProperty.get()[0].endDate !== 'undefined' ?
        staticField('LAKKAUTUS', 'endDate') : '';
      var roadTypes = selectedLinkProperty.count() == 1 ? staticField('TIETYYPPI', 'roadType') : dynamicField('TIETYYPPI');
      var linkIds = dynamicField('VALITUT LINKIT');
      return _.template('' +
        '<header>' +
        title() + buttons +
        '</header>' +
        '<div class="wrapper read-only-floating">' +
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
        roadTypes +
        notification(true)   +
        '</div>' +
        '</div>' +
        '<footer>' + buttons + '</footer>', options);
    };

    var templateFloatingEditMode = function(options) {
      var endDateField = selectedLinkProperty.count() == 1 && typeof selectedLinkProperty.get()[0].endDate !== 'undefined' ?
        staticField('LAKKAUTUS', 'endDate') : '';
      var roadTypes = selectedLinkProperty.count() == 1 ? staticField('TIETYYPPI', 'roadType') : dynamicField('TIETYYPPI');
      var linkIds = dynamicField('VALITUT LINKIT');
      return _.template('' +
        '<header>' +
        title() + buttons +
        '</header>' +
        '<div class="wrapper edit-mode-floating">' +
        '<div class="form form-horizontal form-dark">' +
        '<div class="form-group">' +
        '<p class="form-control-static asset-log-info">Muokattu viimeksi: <%- modifiedBy %> <%- modifiedAt %></p>' +
        '</div>' +
        '<div class="form-group">' +
        '<p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + selectedLinkProperty.count() + '</p>' +
        '</div>' +
        staticField('TIENUMERO', 'roadNumber') +
        staticField('TIENUMERO', 'roadPartNumber') +
        staticField('TIENUMERO', 'trackCode') +
        roadTypes +
        notificationFloatingTransfer(true) +
        staticField('VALITUT LINKIT:', '') +
        linkIds  +
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
        rootElement.find('.btn-move').toggle(false);
        if(compactForm){

          if(!applicationModel.isReadOnly()){
            rootElement.html(templateFloatingEditMode(options, selectedLinkProperty.get()[0])(selectedLinkProperty.get()[0]));
          } else {
            rootElement.html(templateFloating(options, selectedLinkProperty.get()[0])(selectedLinkProperty.get()[0]));
          }
          rootElement.find('.form-controls').toggle(!readOnly && compactForm);
          rootElement.find('.btn-move').toggle(!readOnly && compactForm);
        }
      };
      eventbus.on('linkProperties:selected linkProperties:cancelled', function(linkProperties) {
        compactForm = !_.isEmpty(selectedLinkProperty.get()) && selectedLinkProperty.get()[0].roadLinkType === -1;
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
        } else {
          linkProperties.roadPartNumber = '';
          linkProperties.trackCode = '';
          linkProperties.startAddressM = '';
        }
        linkProperties.elyCode = isNaN(parseFloat(linkProperties.elyCode)) ? '' : linkProperties.elyCode;
        linkProperties.endAddressM = linkProperties.endAddressM || '';
        linkProperties.discontinuity = getDiscontinuityType(linkProperties.discontinuity) || '';
        linkProperties.endDate = linkProperties.endDate || '';
        linkProperties.roadType = linkProperties.roadType || '';
        linkProperties.floating = getFloatingType(linkProperties.roadLinkType);
        linkProperties.roadLinkType = linkProperties.roadLinkType || '';

        var trafficDirectionOptionTags = _.map(localizedTrafficDirections, function (value, key) {
          var selected = key === linkProperties.trafficDirection ? " selected" : "";
          return '<option value="' + key + '"' + selected + '>' + value + '</option>';
        }).join('');
        var functionalClassOptionTags = _.map(functionalClasses, function (value) {
          var selected = value == linkProperties.functionalClass ? " selected" : "";
          return '<option value="' + value + '"' + selected + '>' + value + '</option>';
        }).join('');
        var linkTypesOptionTags = _.map(linkTypes, function (value) {
          var selected = value[0] == linkProperties.linkType ? " selected" : "";
          return '<option value="' + value[0] + '"' + selected + '>' + value[1] + '</option>';
        }).join('');
        var defaultUnknownOptionTag = '<option value="" style="display:none;"></option>';
        options = {
          imports: {
            trafficDirectionOptionTags: defaultUnknownOptionTag.concat(trafficDirectionOptionTags),
            functionalClassOptionTags: defaultUnknownOptionTag.concat(functionalClassOptionTags),
            linkTypesOptionTags: defaultUnknownOptionTag.concat(linkTypesOptionTags)
          }
        };
        if (compactForm){
          if(!applicationModel.isReadOnly()){
            rootElement.html(templateFloatingEditMode(options, linkProperties)(linkProperties));
          } else {
            rootElement.html(templateFloating(options, linkProperties)(linkProperties));
          }
        } else {
          rootElement.html(template(options, linkProperties)(linkProperties));
        }
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
      
      eventbus.on('adjacents:added', function(sources, targets) {
        $(".form-group[id^='VALITUTLINKIT']:last").append($(_.template(adjacentsTemplate)(_.merge({}, {"adjacentLinks": targets}))));
        $('[id*="sourceButton"]').click(sources,function(event) {
          eventbus.trigger("adjacents:nextSelected", sources, event.currentTarget.value);
          //TODO Uncomment for task 182
            //rootElement.find('.link-properties button.calculate').attr('disabled', false);
            //rootElement.find('.link-properties button.cancel').attr('disabled', false);
            //applicationModel.setActiveButtons(true);
        });
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
        applicationModel.setActiveButtons(false);
      });


      eventbus.on('layer:selected', function(layer) {

      });

    };

    bindEvents();
  };
})(this);
