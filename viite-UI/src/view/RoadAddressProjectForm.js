(function (root) {
  root.RoadAddressProjectForm = function(selectedProject) {

    // var discontinuities = [
    //   [1, 'Tien loppu'],
    //   [2, 'Epäjatkuva'],
    //   [3, 'ELY:n raja'],
    //   [4, 'Lievä epäjatkuvuus'],
    //   [5, 'Jatkuva']
    // ];
    //
    // var getDiscontinuityType = function(discontinuity){
    //   var DiscontinuityType = _.find(discontinuities, function(x){return x[0] === discontinuity;});
    //   return DiscontinuityType && DiscontinuityType[1];
    // };

    var dynamicField = function(labelText){
      var floatingTransfer = (!applicationModel.isReadOnly());
      var field;
      if(labelText === 'TIETYYPPI'){
        var roadTypes = "";
        _.each(selectedProject.get(), function(slp){
          var roadType = slp.roadType;
          if (roadTypes.length === 0) {
            roadTypes = roadType;
          } else if(roadTypes.search(roadType) === -1) {
            roadTypes = roadTypes + ", " + roadType;
          }
        });
          field = '<div class="form-group">' +
            '<label class="control-label">' + labelText + '</label>' +
            '<p class="form-control-static">' + roadTypes + '</p>' +
            '</div>';
      } else if(labelText === 'VALITUT LINKIT'){
        var sources = !_.isEmpty(selectedProject.getSources()) ? selectedProject.getSources() : selectedProject.get();
        field = formFields(sources);
      }
      return field;
    };

    var formFields = function (sources){
      var linkIds = "";
      var field;
      var id = 0;
      _.each(sources, function(slp){
        var divId = "VALITUTLINKIT" + id;
        var linkid = slp.linkId.toString();
        if (linkIds.length === 0) {
          field = '<div class="form-group" id=' +divId +'>' +
            '<label class="control-label">' + 'LINK ID:' + '</label>' +
            '<p class="form-control-static">' + linkid + '</p>' +
            '</div>' ;
          linkIds = linkid;
        } else if(linkIds.search(linkid) === -1){
          field = field + '<div class="form-group" id=' +divId +'>' +
            '<label class="control-label">' + 'LINK ID:' + '</label>' +
            '<p class="form-control-static">' + linkid + '</p>' +
            '</div>' ;
          linkIds = linkIds + ", " + linkid;
        }
        id = id + 1;
      });
      return field;
    };

    var staticField = function(labelText, fieldType, dataField) {
      var field;
      field = '<div class="form-group">' +
        '<label class="control-label">' + labelText + '</label>' +
        '<p class="form-control-static"><%- ' + dataField + ' %></p>' +
        '</div>';
      return field;
    };

    var createTextElement = function(readOnly, property) {
      var element;
      var elementType;

      if (readOnly) {
        elementType = $('<p />').addClass('form-control-static');
        element = elementType;

        // if (property.values[0]) {
        //   element.text(property.values[0].propertyDisplayValue);
        // } else {
        //   element.addClass('undefined').html('Ei m&auml;&auml;ritetty');
        // }
      } else {
        // elementType = property.propertyType === 'long_text' ?
        //   $('<textarea />').addClass('form-control') : $('<input type="text"/>').addClass('form-control');
        // element = elementType.bind('input', function(target){
        //   selectedMassTransitStopModel.setProperty(property.publicId, [{ propertyValue: target.currentTarget.value, propertyDisplayValue: target.currentTarget.value  }], property.propertyType, property.required);
        // });
        //
        // if(property.values[0]) {
        //   element.val(property.values[0].propertyDisplayValue);
        // }
      }

      return element;
    };

    var inputField = function(labelText) {
      var field = '<div class="form-group">' +
      '<label class="control-label">' + labelText + '</label>' +
        '<p input type="text" class="form-control-static"></p>' +
        '</div>';
      return field;
    };

    var title = function() {
      return '<span>Tieosoitemuutosprojekti</span>';
    };

    var buttons =
      '<div class="link-properties form-controls">' +
      '<button class="next btn btn-next" disabled>Seuraava</button>' +
      '<button class="save btn btn-tallena" disabled>Tallenna</button>' +
      '<button class="cancel btn btn-perruta" disabled>Peruuta</button>' +
      '</div>';

    // var projectTemplate = function() {
    //   var roadTypes = selectedProject.count() == 1 ? staticField('TIETYYPPI', 'roadType') : dynamicField('TIETYYPPI');
    //   return _.template('' +
    //     '<header>' +
    //     title() + buttons +
    //     '</header>' +
    //     '<div class="wrapper read-only">' +
    //     '<div class="form form-horizontal form-dark">' +
    //     '<div class="form-group">' +
    //     '<p class="form-control-static asset-log-info">Muokattu viimeksi: <%- modifiedBy %> <%- modifiedAt %></p>' +
    //     '</div>' +
    //     '<div class="form-group">' +
    //     '<p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + selectedProject.count() + '</p>' +
    //     '</div>' +
    //     //label + input type // $('<input type="text"/>').addClass('form-control');
    //     staticField('TIENUMERO', 'roadNumber') +
    //     staticField('TIEOSANUMERO', 'roadPartNumber') +
    //     staticField('AJORATA', 'trackCode') +
    //     staticField('ALKUETÄISYYS', 'startAddressM') +
    //     staticField('LOPPUETÄISUUS', 'endAddressM') +
    //     staticField('ELY', 'elyCode') +
    //     roadTypes +
    //     staticField('JATKUVUUS', 'discontinuity') +
    //     '</div>' +
    //     '<footer>' + buttons + '</footer>');
    // };

    var newProjectTemplate = function() {
      return _.template('' +
        '<header>' +
        title() +
        '</header>' +
        '<div class="wrapper read-only">' +
        '<div class="form form-horizontal form-dark">' +
        inputField('NIMI') +
        // staticField('TIENUMERO', 'roadNumber') +
        // staticField('TIEOSANUMERO', 'roadPartNumber') +
        // staticField('AJORATA', 'trackCode') +
        // staticField('ALKUETÄISYYS', 'startAddressM') +
        // staticField('LOPPUETÄISUUS', 'endAddressM') +
        // staticField('ELY', 'elyCode') +
        // roadTypes +
        // staticField('JATKUVUUS', 'discontinuity') +
        '</div>' +
        '<footer>' + buttons + '</footer>');
    };

    var bindEvents = function() {

      var rootElement = $('#feature-attributes');
      var toggleMode = function(readOnly) {
        rootElement.find('.editable .form-control-static').toggle(readOnly);
        rootElement.find('select').toggle(!readOnly);
        rootElement.find('.form-controls').toggle(!readOnly);
        rootElement.find('.btn-move').toggle(false);
        rootElement.html(projectTemplate(selectedProject.get()[0])(selectedProject.get()[0]));
          //
          // rootElement.find('.form-controls').toggle(!readOnly);
          // rootElement.find('.btn-move').toggle(!readOnly);
      };

      eventbus.on('roadAddress:newProject', function(linkProperties) {
        rootElement.html(newProjectTemplate());
      });

      eventbus.on('linkProperties:selected linkProperties:cancelled', function(linkProperties) {
        // rootElement.html(newProjectTemplate(selectedProject.get()[0]));
      });

      rootElement.on('click', '.link-properties button.save', function() {
        //TODO send of getRoadAddressProject() to the backend-utils;
        //for now, for 253 implementation, is go directly to back-utils instead of roadAddressProject controller
        var data = {'name': "project1"};

        backend.createProject(data, function() {
          eventbus.trigger('roadaddress:projectSaved');
        }, function() {
          eventbus.trigger('roadaddress:projectFailed');
        });
      });
      // eventbus.on('linkProperties:selected linkProperties:cancelled', function(linkProperties) {
      //   if(!_.isEmpty(selectedLinkProperty.get())){
      //     compactForm = !_.isEmpty(selectedLinkProperty.get()) && (selectedLinkProperty.get()[0].roadLinkType === -1 || selectedLinkProperty.getFeaturesToKeep().length >= 1);
      //     if(compactForm && !applicationModel.isReadOnly() && selectedLinkProperty.getFeaturesToKeep().length > 1)
      //       selectedLinkProperty.getLinkAdjacents(selectedLinkProperty.get()[0]);
      //     linkProperties.modifiedBy = linkProperties.modifiedBy || '-';
      //     linkProperties.modifiedAt = linkProperties.modifiedAt || '';
      //     linkProperties.roadNameFi = linkProperties.roadNameFi || '';
      //     linkProperties.roadNameSe = linkProperties.roadNameSe || '';
      //     linkProperties.roadNameSm = linkProperties.roadNameSm || '';
      //     linkProperties.addressNumbersRight = addressNumberString(linkProperties.minAddressNumberRight, linkProperties.maxAddressNumberRight);
      //     linkProperties.addressNumbersLeft = addressNumberString(linkProperties.minAddressNumberLeft, linkProperties.maxAddressNumberLeft);
      //     linkProperties.mmlId = checkIfMultiSelection(linkProperties.mmlId) || '';
      //     linkProperties.roadAddress = linkProperties.roadAddress || '';
      //     linkProperties.segmentId = linkProperties.segmentId || '';
      //     linkProperties.roadNumber = linkProperties.roadNumber || '';
      //     if (linkProperties.roadNumber > 0) {
      //       linkProperties.roadPartNumber = linkProperties.roadPartNumber || '';
      //       linkProperties.startAddressM = linkProperties.startAddressM || '0';
      //       linkProperties.trackCode = isNaN(parseFloat(linkProperties.trackCode)) ? '' : parseFloat(linkProperties.trackCode);
      //     } else {
      //       linkProperties.roadPartNumber = '';
      //       linkProperties.trackCode = '';
      //       linkProperties.startAddressM = '';
      //     }
      //     linkProperties.elyCode = isNaN(parseFloat(linkProperties.elyCode)) ? '' : linkProperties.elyCode;
      //     linkProperties.endAddressM = linkProperties.endAddressM || '';
      //     linkProperties.discontinuity = getDiscontinuityType(linkProperties.discontinuity) || '';
      //     linkProperties.roadType = linkProperties.roadType || '';
      //     linkProperties.roadLinkType = linkProperties.roadLinkType || '';
      //
      //     if (compactForm){
      //       if(!applicationModel.isReadOnly()){
      //         rootElement.html(templateFloatingEditMode(linkProperties)(linkProperties));
      //       } else {
      //         rootElement.html(templateFloating(linkProperties)(linkProperties));
      //       }
      //     } else {
      //       rootElement.html(template(linkProperties)(linkProperties));
      //     }
      //     toggleMode(applicationModel.isReadOnly());
      //   }
      // });

      eventbus.on('roadAddress:changed', function() {
        // rootElement.find('.link-properties button').attr('disabled', false);
      });
      eventbus.on('roadAddress:unselected', function() {
        // rootElement.empty();
      });
      eventbus.on('application:readOnly', toggleMode);
      rootElement.on('click', '.project button.save', function() {
        // selectedProject.save();
      });
      rootElement.on('click', '.project button.cancel', function() {
        // selectedProject.cancel(action);
      });

    };
    bindEvents();
  };
})(this);
