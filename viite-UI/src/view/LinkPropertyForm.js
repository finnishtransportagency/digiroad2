(function (root) {
  root.LinkPropertyForm = function(selectedLinkProperty) {
    var functionalClasses = [1, 2, 3, 4, 5, 6, 7, 8];
    var compactForm = false;
    var options;
    var floatingRoadLinkType = -1;
    var noAnomaly = 0;
    var noAddressAnomaly = 1;
    var geometryChangedAnomaly = 2;

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
      [1, 'Maantie'],
      [2, 'Lauttaväylä maantiellä'],
      [3, 'Kunnan katuosuus'],
      [4, 'Maantien työmaa'],
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

    var decodedAttributes = [
      {
        id: 'AJORATA',
        attributes: [
          {value: 0, description: "Yksiajoratainen osuus"},
          {value: 1, description: "Oikeanpuoleinen ajorata"},
          {value: 2, description: "Vasemmanpuoleinen ajorata"}
        ]
      },
      {
        id: 'ELY',
        attributes: [
          {value: 1, description: "Uusimaa"},
          {value: 2, description: "Varsinais-Suomi"},
          {value: 3, description: "Kaakkois-Suomi"},
          {value: 4, description: "Pirkanmaa"},
          {value: 8, description: "Pohjois-Savo"},
          {value: 9, description: "Keski-Suomi"},
          {value: 10, description: "Etelä-Pohjanmaa"},
          {value: 12, description: "Pohjois-Pohjanmaa"},
          {value: 14, description: "Lappi"}
        ]
      },
      {
        id: 'TIETYYPPI',
        attributes: [
          {value: 1, description: "Maantie"},
          {value: 2, description: "Lauttaväylä maantiellä"},
          {value: 3, description: "Kunnan katuosuus"},
          {value: 4, description: "Maantien työmaa"},
          {value: 5, description: "Yksityistie"},
          {value: 9, description: "Omistaja selvittämättä"},
          {value: 99, description: "Ei määritetty"}
        ]
      },
      {
        id: 'JATKUVUUS',
        attributes: [
          {value: 1, description: "Tien loppu"},
          {value: 2, description: "Epäjatkuva"},
          {value: 3, description: "ELY:n raja"},
          {value: 4, description: "Lievä epäjatkuvuus"},
          {value: 5, description: "Jatkuva"}
        ]
      }
    ];

    function getRoadType(askedRoadType) {
      var RoadType = _.find(allRoadTypes, function(x) {return x[0] === askedRoadType;});
      return RoadType && RoadType[1];
    }

    var getDiscontinuityType = function(discontinuity) {
      var DiscontinuityType = _.find(discontinuities, function(x) {return x[0] === discontinuity;});
      return DiscontinuityType && DiscontinuityType[1];
    };

    var getLocalizedLinkType = function(linkType) {
      var localizedLinkType = _.find(linkTypes, function(x) { return x[0] === linkType; });
      return localizedLinkType && localizedLinkType[1];
    };

    var getVerticalLevelType = function(verticalLevel) {
      var verticalLevelType = _.find(verticalLevelTypes, function(y) { return y[0] === verticalLevel; });
      return verticalLevelType && verticalLevelType[1];
    };

    var checkIfMultiSelection = function(mmlId) {
      if (selectedLinkProperty.count() == 1) {
        return mmlId;
      }
    };

    var dynamicField = function(labelText) {
      var floatingTransfer = (!applicationModel.isReadOnly() && compactForm);
      var field = '';
      //If other fields get the same treatment they can be added here
      if (labelText === 'TIETYYPPI') {
        var roadTypes = "";
        var uniqRoadTypes = _.uniq(_.pluck(selectedLinkProperty.get(), 'roadTypeId'));
        var decodedRoadTypes = "";
        _.each(uniqRoadTypes, function(rt) {
          if (decodedRoadTypes.length === 0) {
            decodedRoadTypes = rt + " " + decodeAttributes(labelText, rt);
          } else {
            decodedRoadTypes = decodedRoadTypes + ", " + rt + " " + decodeAttributes(labelText, rt);
          }
        });

        if (floatingTransfer) {
          field = '<div class="form-group">' +
            '<label class="control-label-floating">' + labelText + '</label>' +
            '<p class="form-control-static-floating">' + decodedRoadTypes + '</p>' +
            '</div>' ;
        } else {
          field = '<div class="form-group">' +
            '<label class="control-label">' + labelText + '</label>' +
            '<p class="form-control-static">' + decodedRoadTypes + '</p>' +
            '</div>';
        }
      } else if (labelText === 'VALITUT LINKIT') {
        var sources = !_.isEmpty(selectedLinkProperty.getSources()) ? selectedLinkProperty.getSources() : selectedLinkProperty.get();
        field = formFields(sources);
      } else if (labelText === 'ALKUETÄISYYS') {
        var startAddress =  _.min(_.pluck(selectedLinkProperty.get(), 'startAddressM'));
        if (floatingTransfer) {
          field = '<div class="form-group">' +
            '<label class="control-label-floating">' + labelText + '</label>' +
            '<p class="form-control-static-floating">' + startAddress + '</p>' +
            '</div>' ;
        } else {
          field = '<div class="form-group">' +
            '<label class="control-label">' + labelText + '</label>' +
            '<p class="form-control-static">' + startAddress + '</p>' +
            '</div>';
        }
      } else if (labelText === 'LOPPUETÄISUUS') {
        var endAddress = _.max(_.pluck(selectedLinkProperty.get(), 'endAddressM'));
        if (floatingTransfer) {
          field = '<div class="form-group">' +
            '<label class="control-label-floating">' + labelText + '</label>' +
            '<p class="form-control-static-floating">' + endAddress + '</p>' +
            '</div>' ;
        } else {
          field = '<div class="form-group">' +
            '<label class="control-label">' + labelText + '</label>' +
            '<p class="form-control-static">' + endAddress + '</p>' +
            '</div>';
        }
      }
      return field;
    };

    var formFields = function (sources) {
      var linkIds = "";
      var ids = "";
      var field;
      var linkCounter = 0;
      _.each(sources, function(slp) {
        var divId = "VALITUTLINKIT" + linkCounter;
        var linkid = slp.linkId.toString();
        var id = _.isUndefined(slp.id) ? '-1': slp.id.toString();
        if (linkIds.length === 0) {
          field = '<div class="form-group" id=' +divId +'>' +
            '<label class="control-label-floating">' + 'LINK ID:' + '</label>' +
            '<p class="form-control-static-floating">' + linkid + '</p>' +
            '</div>' ;
          linkIds = linkid;
          ids = id;
        } else if (linkIds.search(linkid) === -1 || ids.search(id) === -1) {
          field = field + '<div class="form-group" id=' +divId +'>' +
            '<label class="control-label-floating">' + 'LINK ID:' + '</label>' +
            '<p class="form-control-static-floating">' + linkid + '</p>' +
            '</div>' ;
          linkIds = linkIds + ", " + linkid;
          ids = ids + ", " + id;
        }
        linkCounter = linkCounter + 1;
      });
      return field;
    };

    var additionalSource = function(linkId, marker) {
      return (!_.isUndefined(marker)) ? '' +
      '<div class = "form-group" id = "aditionalSource">' +
      '<div style="display:inline-flex;justify-content:center;align-items:center;">' +
      '<label class="control-label-floating"> LINK ID:</label>' +
      '<span class="form-control-static-floating" style="display:inline-flex;width:auto;margin-right:5px">' + linkId + '</span>' +
      '<span class="marker">' + marker + '</span>' +
      '<button class="add-source btn btn-new" id="aditionalSourceButton-' + linkId + '" value="' + linkId + '">Lisää kelluva tieosoite</button>' +
      '</div>' +
      '</div>' : '' +
      '<div class = "form-group" id = "aditionalSource">' +
      '<div style="display:inline-flex;justify-content:center;align-items:center;">' +
      '<label class="control-label-floating"> LINK ID:</label>' +
      '<span class="form-control-static-floating" style="display:inline-flex;width:auto;margin-right:5px">' + linkId + '</span>' +
      '</div>' +
      '</div>';
    };

    var adjacentsTemplate = '' +
      '<div class="target-link-selection" id="adjacentsData">' +
      '<div class="form-group" id="adjacents">' +
      '<% if(!_.isEmpty(adjacentLinks)){ %>' +
      '<br><br><label class="control-label-adjacents">VALITTAVISSA OLEVAT TIELINKIT, JOILTA PUUTTUU TIEOSOITE:</label>' +
      ' <% } %>' +
      '<% _.forEach(adjacentLinks, function(l) { %>' +
      '<div style="display:inline-flex;justify-content:center;align-items:center;">' +
      '<label class="control-label-floating"> LINK ID: </label>' +
      '<span class="form-control-static-floating" style="display:inline-flex;width:auto;margin-right:5px"><%= l.linkId %></span>' +
      '<span class="marker"><%= l.marker %></span>' +
      '<button class="select-adjacent btn btn-new" id="sourceButton-<%= l.linkId %>" value="<%= l.linkId %>">Valitse</button>' +
      '</div>' +
      '</span>' +
      '</label>' +
      ' <% }) %>' +
      '</div>' +
      '</div>';

    var afterCalculationTemplate = '' +
      '<div class="form-group" id="afterCalculationInfo">' +
      ' <br><br><p><span style="margin-top:6px; color:#ffffff; padding-top:6px; padding-bottom:6px; line-height:15px;">TARKISTA TEKEMÄSI MUUTOKSET KARTTANÄKYMÄSTÄ.</span></p>' +
      ' <p><span style="margin-top:6px; color:#ffffff; padding-top:6px; padding-bottom:6px; line-height:15px;">JOS TEKEMÄSI MUUTOKSET OVAT OK, PAINA TALLENNA</span></p>' +
      ' <p><span style="margin-top:6px; color:#ffffff; padding-top:6px; padding-bottom:6px; line-height:15px;">JOS HALUAT KORJATA TEKEMÄSI MUUTOKSIA, PAINA PERUUTA</span></p>' +
      '</div>';

    var decodeAttributes = function(attr, value) {
      var attrObj = _.find(decodedAttributes, function (obj) { return obj.id === attr; });
      if (!_.isUndefined(attrObj)) {
        var attrValue = _.find(attrObj.attributes, function (obj) { return obj.value === value; });
        if (!_.isUndefined(attrValue)) {
          return attrValue.description;
        } else {
          return "Ei määritetty";
        }
      } else {
        return "";
      }
    };

    var staticField = function(labelText, dataField) {
      var floatingTransfer = (!applicationModel.isReadOnly() && compactForm);
      var field;

      if (floatingTransfer) {
        field = '<div class="form-group">' +
          '<label class="control-label-floating">' + labelText + '</label>' +
          '<p class="form-control-static-floating">' + dataField + " " + decodeAttributes(labelText, dataField) + '</p>' +
          '</div>';
      } else {
        field = '<div class="form-group">' +
          '<label class="control-label">' + labelText + '</label>' +
          '<p class="form-control-static">' + dataField + " " + decodeAttributes(labelText, dataField) + '</p>' +
          '</div>';
      }
      return field;
    };

    var title = function() {
      return '<span>Tieosoitteen ominaisuustiedot</span>';
    };

    var editButtons =
      '<div class="link-properties form-controls">' +
      '<button class="continue ready btn btn-continue" disabled>Valinta valmis</button>'  +
      '<button class="calculate btn btn-move" disabled>Siirrä</button>' +
      '<button class="save btn btn-save" disabled>Tallenna</button>' +
      '<button class="cancel btn btn-cancel" disabled>Peruuta</button>' +
      '</div>';

    var buttons =
      '<div class="link-properties form-controls">' +
      '<button class="save btn btn-save" disabled>Tallenna</button>' +
      '<button class="cancel btn btn-cancel" disabled>Peruuta</button>' +
      '</div>';

    var notificationFloatingTransfer = function(displayNotification) {
      if (displayNotification) {
        return '' +
          '<div class="form-group form-notification">' +
          '<p>Tien geometria on muuttunut. Korjaa tieosoitesegmentin sijainti vastaamaan nykyistä geometriaa.</p>' +
          '</div>';
      } else {
        return '';
      }
    };

    var template = function(options, linkProperty) {
      var roadTypes = selectedLinkProperty.count() == 1 ? staticField('TIETYYPPI', linkProperty.roadTypeId) : dynamicField('TIETYYPPI');
      var startAddress = selectedLinkProperty.count() == 1 ? staticField('ALKUETÄISYYS', linkProperty.startAddressM) : dynamicField('ALKUETÄISYYS');
      var endAddress = selectedLinkProperty.count() == 1 ? staticField('LOPPUETÄISUUS', linkProperty.endAddressM) : dynamicField('LOPPUETÄISUUS');
      return _.template('' +
        '<header>' +
        title() +
        '</header>' +
        '<div class="wrapper read-only">' +
        '<div class="form form-horizontal form-dark">' +
        '<div class="form-group">' +
        '<p class="form-control-static asset-log-info">Muokattu viimeksi: <%- modifiedBy %> <%- modifiedAt %></p>' +
        '</div>' +
        '<div class="form-group">' +
        '<p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + selectedLinkProperty.count() + '</p>' +
        '</div>' +
        staticField('TIENUMERO', linkProperty.roadNumber) +
        staticField('TIEOSANUMERO', linkProperty.roadPartNumber) +
        staticField('AJORATA', linkProperty.trackCode) +
        startAddress +
        endAddress +
        staticField('ELY', linkProperty.elyCode) +
        roadTypes +
        staticField('JATKUVUUS', linkProperty.discontinuity) +
        '</div>' +
        '<footer>' + '</footer>', options);
    };

    var templateFloating = function(options, linkProperty) {
      var startAddress = selectedLinkProperty.count() == 1 ? staticField('ALKUETÄISYYS', linkProperty.startAddressM) : dynamicField('ALKUETÄISYYS');
      var endAddress = selectedLinkProperty.count() == 1 ? staticField('LOPPUETÄISUUS', linkProperty.endAddressM) : dynamicField('LOPPUETÄISUUS');
      var roadTypes = selectedLinkProperty.count() == 1 ? staticField('TIETYYPPI', linkProperty.roadTypeId) : dynamicField('TIETYYPPI');
      return _.template('' +
        '<header>' +
        title() +
        '</header>' +
        '<div class="wrapper read-only-floating">' +
        '<div class="form form-horizontal form-dark">' +
        '<div class="form-group">' +
        '<p class="form-control-static asset-log-info">Muokattu viimeksi: <%- modifiedBy %> <%- modifiedAt %></p>' +
        '</div>' +
        '<div class="form-group">' +
        '<p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + selectedLinkProperty.count() + '</p>' +
        '</div>' +
        staticField('TIENUMERO', linkProperty.roadNumber) +
        staticField('TIEOSANUMERO', linkProperty.roadPartNumber) +
        staticField('AJORATA', linkProperty.trackCode) +
        startAddress +
        endAddress +
        roadTypes +
        notificationFloatingTransfer(true)   +
        '</div>' +
        '</div>' +
        '<footer>' + '</footer>', options);
    };

    var templateFloatingEditMode = function(options, linkProperty) {
      var startAddress = selectedLinkProperty.count() == 1 ? staticField('ALKUETÄISYYS', 'startAddressM') : dynamicField('ALKUETÄISYYS');
      var endAddress = selectedLinkProperty.count() == 1 ? staticField('LOPPUETÄISUUS', 'endAddressM') : dynamicField('LOPPUETÄISUUS');
      var roadTypes = selectedLinkProperty.count() == 1 ? staticField('TIETYYPPI', 'roadType') : dynamicField('TIETYYPPI');
      var linkIds = dynamicField('VALITUT LINKIT');
      return _.template('<div style="display: none" id="floatingEditModeForm">' +
        '<header>' +
        title() +
        '</header>' +
        '<div class="wrapper edit-mode-floating">' +
        '<div class="form form-horizontal form-dark">' +
        '<div class="form-group">' +
        '<p class="form-control-static asset-log-info">Muokattu viimeksi: <%- modifiedBy %> <%- modifiedAt %></p>' +
        '</div>' +
        '<div class="form-group">' +
        '<p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + selectedLinkProperty.count() + '</p>' +
        '</div>' +
        staticField('TIENUMERO',linkProperty.roadNumber) +
        staticField('TIEOSANUMERO', linkProperty.roadPartNumber) +
        startAddress +
        endAddress +
        staticField('AJORATA', linkProperty.trackCode) +
        roadTypes +
        notificationFloatingTransfer(true) +
        staticField('VALITUT LINKIT:', '') +
        linkIds  +
        '</div>' +
        '</div>' +
        '<footer>' + editButtons + '</footer> </div>', options);
    };

    var addressNumberString = function(minAddressNumber, maxAddressNumber) {
      if (!minAddressNumber && !maxAddressNumber) {
        return '';
      } else {
        var min = minAddressNumber || '';
        var max = maxAddressNumber || '';
        return min + '-' + max;
      }
    };

    var processAditionalFloatings = function(floatingRoads, value) {
      var floatingRoadsLinkId = _.map(floatingRoads, function (fr) {
        return fr.linkId;
      });
      if (!_.contains(floatingRoadsLinkId, value)) {
        applicationModel.addSpinner();
        eventbus.trigger("adjacents:additionalSourceSelected", floatingRoads, value);
        $('#feature-attributes').find('.link-properties button.continue').attr('disabled', false);
        $('#feature-attributes').find('.link-properties button.cancel').attr('disabled', false);
        applicationModel.setActiveButtons(true);
      }
    };

    var addOpenProjectButton = function() {
      var rootElement = $('#feature-attributes');
      rootElement.empty();
      var emptyFormDiv = '<div class="form-initial-state" id="emptyFormDiv">' +
        '<span class="header-noposition">Aloita valitsemalla projekti.</span>' +
        '<button id="formProjectButton" class="action-mode-btn btn btn-block btn-primary">Tieosoiteprojektit</button>' +
        '</div>' +
        '<p class="form form-horizontal">' +
        '<p><a id="floating-list-link" class="floating-stops" href="#work-list/floatingRoadAddress">KORJATTAVIEN LINKKIEN LISTA</a></p>' +
        '<p><a id="error-list-link" class="floating-stops" href="#work-list/roadAddressErrors">ROAD ADDRESS ERRORS</a></p>' +
        '</p>';
      rootElement.append(emptyFormDiv);
      $('[id=formProjectButton]').click(function() {
        $('[id=projectListButton]').click();
        return false;
      });
    };


    var bindEvents = function() {
      var rootElement = $('#feature-attributes');

      addOpenProjectButton();

      var switchMode = function (readOnly) {
        toggleMode(readOnly);
        var uniqFeaturesToKeep = _.uniq(selectedLinkProperty.getFeaturesToKeep());
        var firstFloatingSelected = _.first(_.filter(uniqFeaturesToKeep,function (feature) {
          return feature.roadLinkType === floatingRoadLinkType;
        }));
        var canStartTransfer = compactForm && !applicationModel.isReadOnly() && uniqFeaturesToKeep.length > 1 && uniqFeaturesToKeep[uniqFeaturesToKeep.length - 1].anomaly === noAddressAnomaly && uniqFeaturesToKeep[uniqFeaturesToKeep.length - 2].roadLinkType === floatingRoadLinkType;
        if (canStartTransfer)
          _.defer(function() {
            selectedLinkProperty.getLinkAdjacents(selectedLinkProperty.get()[0], firstFloatingSelected);
          });
      };

      var toggleMode = function(readOnly) {
        if (!applicationModel.isProjectOpen()) {
          rootElement.find('.editable .form-control-static').toggle(readOnly);
          rootElement.find('select').toggle(!readOnly);
          rootElement.find('.form-controls').toggle(!readOnly);
          var uniqFeaturesToKeep = _.uniq(selectedLinkProperty.getFeaturesToKeep());
          var lastFeatureToKeep = _.isUndefined(_.last(_.initial(uniqFeaturesToKeep))) ? _.last(uniqFeaturesToKeep) : _.last(_.initial(uniqFeaturesToKeep));
          var firstSelectedLinkProperty = _.first(selectedLinkProperty.get());
          if (!_.isEmpty(uniqFeaturesToKeep)) {
            if (readOnly) {
              if (lastFeatureToKeep.roadLinkType === floatingRoadLinkType) {
                rootElement.html(templateFloating(options, firstSelectedLinkProperty)(firstSelectedLinkProperty));
              } else {
                rootElement.html(template(options, firstSelectedLinkProperty)(firstSelectedLinkProperty));
              }
            } else {
              if (lastFeatureToKeep.roadLinkType === floatingRoadLinkType) {
                rootElement.html(templateFloatingEditMode(options, firstSelectedLinkProperty)(firstSelectedLinkProperty));
                if (applicationModel.getSelectionType() === 'floating' && firstSelectedLinkProperty.roadLinkType === floatingRoadLinkType) {
                  selectedLinkProperty.getLinkFloatingAdjacents(_.last(selectedLinkProperty.get()), firstSelectedLinkProperty);
                }
                $('#floatingEditModeForm').show();
              } else { //check if the before selected was a floating link and if the next one is unknown
                if (uniqFeaturesToKeep.length > 1 && uniqFeaturesToKeep[uniqFeaturesToKeep.length - 1].anomaly === noAddressAnomaly) {
                  rootElement.html(templateFloatingEditMode(options, firstSelectedLinkProperty)(firstSelectedLinkProperty));
                  $('#floatingEditModeForm').show();
                } else {
                  rootElement.html(template(options, firstSelectedLinkProperty)(firstSelectedLinkProperty));
                }
              }
            }
          } else if (!_.isEmpty(selectedLinkProperty.get())) {
            if (readOnly) {
              if (firstSelectedLinkProperty.roadLinkType === floatingRoadLinkType) {
                rootElement.html(templateFloating(options, firstSelectedLinkProperty)(firstSelectedLinkProperty));
              } else {
                rootElement.html(template(options, firstSelectedLinkProperty)(firstSelectedLinkProperty));
              }
            } else {
              if (_.last(selectedLinkProperty.get()).roadLinkType === floatingRoadLinkType) {
                applicationModel.toggleSelectionTypeFloating();
                rootElement.html(templateFloatingEditMode(options, firstSelectedLinkProperty)(firstSelectedLinkProperty));
                selectedLinkProperty.getLinkFloatingAdjacents(_.last(selectedLinkProperty.get()), firstSelectedLinkProperty);
                $('#floatingEditModeForm').show();
              } else {
                rootElement.html(template(options, firstSelectedLinkProperty)(firstSelectedLinkProperty));
              }
            }
          }
          rootElement.find('.form-controls').toggle(!readOnly);
          rootElement.find('.btn-move').prop("disabled", true);
          rootElement.find('.btn-continue').prop("disabled", false);
        }
      };

      eventbus.on('linkProperties:selected linkProperties:cancelled', function(linkProperties) {
        rootElement.empty();
        if (!_.isEmpty(selectedLinkProperty.get()) || !_.isEmpty(linkProperties)) {

          compactForm = !_.isEmpty(selectedLinkProperty.get()) && (selectedLinkProperty.get()[0].roadLinkType === floatingRoadLinkType || selectedLinkProperty.getFeaturesToKeep().length >= 1);
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
          linkProperties.roadType = linkProperties.roadType || '';
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

          rootElement.find('.traffic-direction').change(function(event) {
            selectedLinkProperty.setTrafficDirection($(event.currentTarget).find(':selected').attr('value'));
          });
          rootElement.find('.functional-class').change(function(event) {
            selectedLinkProperty.setFunctionalClass(parseInt($(event.currentTarget).find(':selected').attr('value'), 10));
          });
          rootElement.find('.link-types').change(function(event) {
            selectedLinkProperty.setLinkType(parseInt($(event.currentTarget).find(':selected').attr('value'), 10));
          });
          switchMode(applicationModel.isReadOnly());
        }
      });

      eventbus.on('form:showPropertyForm', function () {
        addOpenProjectButton();
      });

      eventbus.on('adjacents:added', function(sources, targets) {
        processAdjacents(sources,targets);
        applicationModel.removeSpinner();
      });

      eventbus.on('adjacents:aditionalSourceFound', function(sources, targets, additionalSourceLinkId) {
        $('#aditionalSource').remove();
        $('#adjacentsData').remove();
        processAdjacents(sources, targets, additionalSourceLinkId);
        applicationModel.removeSpinner();
      });

      var processAdjacents = function (sources, targets, additionalSourceLinkId) {
        var adjacents = _.reject(targets, function(t) {
          return t.roadLinkType == floatingRoadLinkType;
        });

        //singleLinkSelection case
        var floatingAdjacents = [];
        if (selectedLinkProperty.count() === 1) {
          floatingAdjacents = _.filter(targets, function(t) {
            return t.roadLinkType == floatingRoadLinkType;
          });
        }

        var fullTemplate = applicationModel.getCurrentAction() === applicationModel.actionCalculated ? afterCalculationTemplate : !_.isEmpty(floatingAdjacents) ? _.map(floatingAdjacents, function(fa) {
          return additionalSource(fa.linkId, fa.marker);
        })[0] + adjacentsTemplate : adjacentsTemplate;

        if (!_.isUndefined(additionalSourceLinkId)) {
          return $(".form-group[id^='VALITUTLINKIT']:last").append('<div style="display:inline-flex;justify-content:center;align-items:center;">' +
            '<label class="control-label-floating"> LINK ID:</label>' +
            '<span class="form-control-static-floating" style="display:inline-flex;width:auto;margin-right:5px">' + additionalSourceLinkId + '</span>' +
            '</div>');
        }

        $('[id^=VALITUTLINKIT]').remove();

        var nonFloatingFeatures = _.reject(selectedLinkProperty.getFeaturesToKeep(), function(t){
          return t.roadLinkType == floatingRoadLinkType;
        });

        var fields = formFields(_.map(nonFloatingFeatures, function(sId){
          return {'linkId' : sId.linkId};
        }));

        $('.form-group:last').after(fields);

        if ($(".form-group[id^='VALITUTLINKIT']:last").length !== 0 && $(".form-group[id^='VALITUTLINKIT']:last")[0].childNodes.length <= 2) {
          $(".form-group[id^='VALITUTLINKIT']:last").append($(_.template(fullTemplate)(_.merge({}, {"adjacentLinks": adjacents}))));
          $('#floatingEditModeForm').show();
          $('[id*="sourceButton"]').click({"sources": sources, "adjacents": adjacents},function(event) {
            eventbus.trigger("adjacents:nextSelected", event.data.sources, event.data.adjacents, event.currentTarget.value);
          });
          rootElement.find('.link-properties button.calculate').attr('disabled', false);
          rootElement.find('.link-properties button.cancel').attr('disabled', false);
          rootElement.find('.link-properties button.continue').attr('disabled', true);
          applicationModel.setActiveButtons(true);
          $('[id*="aditionalSourceButton"]').click(sources,function(event) {
            processAditionalFloatings(sources, event.currentTarget.value);
          });
        }
      };

      eventbus.on('linkProperties:changed', function() {
        rootElement.find('.link-properties button').attr('disabled', false);
      });

      eventbus.on('layer:selected', function(layer, previouslySelectedLayer, toggleStart) {
        if (layer === "linkProperty" && toggleStart) {
          addOpenProjectButton();
        }
      });

      eventbus.on('roadLayer:toggleProjectSelectionInForm', function(layer, noSave) {
        if (layer === "linkProperty") {
          addOpenProjectButton();
          if (noSave) {
            $('#formProjectButton').click();
          } else {
            eventbus.once('roadAddress:projectSaved', function() {
              $('#formProjectButton').click();
            });
          }
        }
      });

      eventbus.on('linkProperties:unselected', function() {
        if (('all' === applicationModel.getSelectionType() || 'floating' === applicationModel.getSelectionType()) && !applicationModel.isProjectOpen()) {
          addOpenProjectButton();
        }
      });
      eventbus.on('application:readOnly', toggleMode);
      rootElement.on('click', '.link-properties button.save', function() {
        if (applicationModel.getCurrentAction() === applicationModel.actionCalculated) {
          selectedLinkProperty.saveTransfer();
          applicationModel.setCurrentAction(-1);
          applicationModel.addSpinner();
        }
      });
      rootElement.on('click', '.link-properties button.cancel', function() {
        var action;
        if (applicationModel.isActiveButtons()) {
          action = applicationModel.actionCalculating;
        }
        applicationModel.setCurrentAction(action);
        eventbus.trigger('linkProperties:activateAllSelections');
        eventbus.trigger('roadLinks:refreshView');
        if ('all' === applicationModel.getSelectionType() || 'floating' === applicationModel.getSelectionType()) {
          selectedLinkProperty.clearAndReset(false);
          applicationModel.toggleSelectionTypeAll();
          selectedLinkProperty.close();
          $('#feature-attributes').empty();
        } else {
          applicationModel.toggleSelectionTypeFloating();
          selectedLinkProperty.cancelAndReselect(action);
        }
        applicationModel.setActiveButtons(false);
      });
      rootElement.on('click', '.link-properties button.calculate', function() {
        applicationModel.addSpinner();
        selectedLinkProperty.transferringCalculation();
        applicationModel.setActiveButtons(true);
      });
      rootElement.on('click', '.link-properties button.continue',function() {
        if (selectedLinkProperty.continueSelectUnknown()) {
          rootElement.find('.link-properties button.continue').attr('disabled', true);
          applicationModel.toggleSelectionTypeUnknown();
          applicationModel.setContinueButton(false);
          eventbus.trigger('linkProperties:deselectFeaturesSelected');
          eventbus.trigger('linkProperties:highlightSelectedFloatingFeatures');
          eventbus.trigger('linkProperties:activateInteractions');
          eventbus.trigger('linkProperties:deactivateDoubleClick');
        }
      });

      eventbus.on('adjacents:roadTransfer', function(result, sourceIds, targets) {
        $('#aditionalSource').remove();
        $('#adjacentsData').remove();
        rootElement.find('.link-properties button.save').attr('disabled', false);
        rootElement.find('.link-properties button.cancel').attr('disabled', false);
        rootElement.find('.link-properties button.calculate').attr('disabled', true);
        rootElement.find('.link-properties button.continue').attr('disabled', true);
        $('[id^=VALITUTLINKIT]').remove();

        var fields = formFields(_.map(targets, function(sId) {
            return {'linkId' : sId};
          })) + '' + afterCalculationTemplate;

        $('.form-group:last').after(fields);

        applicationModel.removeSpinner();
      });

      eventbus.on('adjacents:startedFloatingTransfer', function() {
        action = applicationModel.actionCalculating;
        rootElement.find('.link-properties button.cancel').attr('disabled', false);
        if (!applicationModel.isContinueButton()) {
          rootElement.find('.link-properties button.continue').attr('disabled', true);
        } else {
          rootElement.find('.link-properties button.continue').attr('disabled', false);
        }
        applicationModel.setActiveButtons(true);
        eventbus.trigger('layer:enableButtons', false);
      });

      eventbus.on('adjacents:floatingAdded', function(floatingRoads) {
        var floatingPart = '<br><label class="control-label-floating">VIERESSÄ KELLUVIA TIEOSOITTEITA:</label>';
        _.each(floatingRoads,function(fr) {
          floatingPart = floatingPart + additionalSource(fr.linkId, fr.marker);
        });
        $(".form-group:last").after(floatingPart);
        $('[id*="aditionalSourceButton"]').click(floatingRoads,function(event) {
          processAditionalFloatings(floatingRoads,event.currentTarget.value);
        });
      });
      eventbus.on('linkProperties:additionalFloatingSelected',function(data) {
        processAditionalFloatings(data.selectedFloatings, data.selectedLinkId);
      });

      eventbus.on('linkProperties:transferFailed',function(errorCode) {
        if (errorCode == 400) {
          return new ModalConfirm("Valittujen lähdelinkkien geometriaa ei saatu sovitettua kohdegeometrialle. Ota yhteyttä järjestelmätukeen.");
        } else if (errorCode == 401) {
          return new ModalConfirm("Sinulla ei ole käyttöoikeutta muutoksen tekemiseen.");
        } else if (errorCode == 412) {
          return new ModalConfirm("Täyttämättömien vaatimusten takia siirtoa ei saatu tehtyä. Ota yhteyttä järjestelmätukeen.");
        } else if (errorCode == 500) {
          return new ModalConfirm("Siirto ei onnistunut taustajärjestelmässä tapahtuneen virheen takia, ota yhteyttä järjestelmätukeen.");
        } else {
          return new ModalConfirm("Siirto ei onnistunut taustajärjestelmässä tapahtuneen tuntemattoman virheen takia, ota yhteyttä järjestelmätukeen.");
        }
      });

      eventbus.on('roadAddressProject:selected', function() {
        $('.wrapper').remove();
      });
    };
    bindEvents();

    return {
      getRoadType: getRoadType
    };
  };
})(this);
