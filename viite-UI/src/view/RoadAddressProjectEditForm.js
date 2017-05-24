(function (root) {
  root.RoadAddressProjectEditForm = function(projectCollection) {
    var currentProject = false;
    var selectedProjectLink = false;
    var staticField = function(labelText, dataField) {
      var field;
      field = '<div class="form-group">' +
        '<p class="form-control-static asset-log-info">' + labelText + ' : ' + dataField + '</p>' +
        '</div>';
      return field;
    };
    var actionSelectedField = function() {
      //TODO: cancel and save buttons Viite-374
      var field;
      field = '<div class="form-group action-selected-field" hidden = "true">' +
        '<p class="asset-log-info">' + 'Tarkista tekemäsi muutokset.' + '<br>' + 'Jos muutokset ok, tallenna.' + '</p>' +
        '</div>';
      return field;
    };
    var options =['Valitse'];

    var largeInputField = function (dataField) {
      return '<div class="form-group">' +
        '<label class="control-label">LISÄTIEDOT</label>'+
        '<textarea class="form-control large-input roadAddressProject" id="lisatiedot">'+(dataField === undefined || dataField === null ? "" : dataField )+'</textarea>'+
        '</div>';
    };

    var inputFieldRequired = function(labelText, id, placeholder, value) {
      var field = '<div class="form-group input-required">' +
        '<label class="control-label required">' + labelText + '</label>' +
        '<input type="text" class="form-control" id = "'+id+'" placeholder = "'+placeholder+'" value="'+value+'"/>' +
        '</div>';
      return field;
    };

    var title = function() {
      return '<span class ="edit-mode-title">Uusi tieosoiteprojekti</span>';
    };

    var titleWithProjectName = function(projectName) {
      return '<span class ="edit-mode-title">'+projectName+'</span>';
    };

    var buttons = function(ready) {
      var html = '<div class="project-form form-controls">' +
        '<button class="next btn btn-next"';
      if (!ready)
        html = html + "disabled";
      html = html +
        '>Seuraava</button>' +
        '<button class="save btn btn-save" disabled>Tallenna</button>' +
        '<button class="cancel btn btn-cancel">Peruuta</button>' +
        '</div>';
      return html;
    };
    var sendRoadAddressChangeButton = function() {

      $('#information-content').append('' +
        '<div class="form form-horizontal">' +
        '<p>' + 'Validointi ok. Voit tehdä tieosoitteenmuutosilmoituksen' + '<br>' +
        'tai jatkaa muokkauksia.' + '</p>' +
        '</div>');

      var sendButton = '<div class="project-form form-controls">' +
        '<button class="send btn btn-block btn-send">Tee tieosoitteenmuutosilmoitus</button>';
      return sendButton;
    };

    var terminationButtons = function() {
      var html = '<div class="project-form form-controls">' +
        '<button class="update btn btn-save"';
      if (!selectedProjectLink)
        html = html + "disabled";
      html = html +
        '>Tallenna</button>' +
        '<button class="cancel btn btn-cancel">Peruuta</button>' +
        '</div>';
      return html;
    };

    var processSelectedLinks = function(selectedLinks){
      if(!_.isUndefined(selectedLinks)){
        return $(".form-group[id^='VALITUTLINKIT']:last").append('<div style="display:inline-flex;justify-content:center;align-items:center;">' +
          '<label class="control-label-floating"> LINK ID:</label>' +
          '<span class="form-control-static-floating" style="display:inline-flex;width:auto;margin-right:5px">' + additionalSourceLinkId + '</span>' +
          '</div>');
      }
    };

    var selectedData = function (selected) {
      var span = '';
      if (selected[0]) {
        var link = selected[0];
        var startM = Math.min.apply(Math, _.map(selected, function(l) { return l.startAddressM; }));
        var endM = Math.max.apply(Math, _.map(selected, function(l) { return l.endAddressM; }));
        span = '<div class="edit-control-group choice-group">' +
          '<label class="control-label-floating"> TIE </label>' +
          '<span class="form-control-static-floating" style="display:inline-flex;width:auto;margin-right:5px">' + link.roadNumber + '</span>' +
          '<label class="control-label-floating"> OSA </label>' +
          '<span class="form-control-static-floating" style="display:inline-flex;width:auto;margin-right:5px">' + link.roadPartNumber + '</span>' +
          '<label class="control-label-floating"> AJR </label>' +
          '<span class="form-control-static-floating" style="display:inline-flex;width:auto;margin-right:5px">' + link.trackCode + '</span>' +
          '<label class="control-label-floating"> M: </label>' +
          '<span class="form-control-static-floating" style="display:inline-flex;width:auto;margin-right:5px">' + startM + ' - ' + endM + '</span>' +
          '</div>' +
          '</div>';
      }
      return span;
    };

    var headerButton =
      '<div class="linear-asset form-controls">'+
      '<button class="cancel btn btn-secondary">Sulje projekti</button>'+
      '</div>';

    var selectedProjectLinkTemplate = function(project, optionTags, selected) {
      var selection = selectedData(selected);

      return _.template('' +
        '<header>' +
        titleWithProjectName(project.name) +
        '</header>' +
        '<div class="wrapper read-only">'+
        '<div class="form form-horizontal form-dark">'+
        '<div class="edit-control-group choice-group">'+
        staticField('Lisätty järjestelmään', project.createdBy + ' ' + project.startDate)+
        staticField('Muokattu viimeksi', project.modifiedBy + ' ' + project.dateModified)+
        '<div class="form-group editable form-editable-roadAddressProject" id="information-content"> '+
        '<form id="roadAddressProject" class="input-unit-combination form-group form-horizontal roadAddressProject">'+
        '<label>Toimenpiteet</label>'+
        selection +
        '<div class="input-unit-combination">' +
        '<select class="form-control" id="dropDown" size="1">'+
        '<option value="action1">Valitse</option>'+
        '<option value="action2">Lakkautus</option>'+
        '<option value="action3" disabled>Uusi</option>'+
        '<option value="action4" disabled>Numeroinnin muutos</option>'+
        '<option value="action5" disabled>Ennallaan</option>'+
        '<option value="action6" disabled>Kalibrointiarvon muutos</option>'+
        '<option value="action7" disabled>Siirto</option>'+
        '<option value="action8" disabled>Kalibrointipisteen siirto</option>'+
        '</select>'+
        '</div>'+
        '</form>' +
        actionSelectedField()+
        '</div>'+
        '</div>' +
        '</div>'+
        '</div>'+
        '<footer>' + terminationButtons() + '</footer>');
    };

    var addSmallLabel = function(label){
      return '<label class="control-label-small">'+label+'</label>';
    };

    var addSmallInputNumber = function(id, value){
      //Validate only number characters on "onkeypress" including TAB and backspace
      return '<input type="text" onkeypress="return (event.charCode >= 48 && event.charCode <= 57) || (event.keyCode == 8 || event.keyCode == 9)' +
        '" class="form-control small-input roadAddressProject" id="'+id+'" value="'+(_.isUndefined(value)? '' : value )+'" onclick=""/>';
    };

    var addDatePicker = function () {
      var $validFrom = $('#alkupvm');
      dateutil.addSingleDependentDatePicker($validFrom);
    };

    var bindEvents = function() {

      var rootElement = $('#feature-attributes');
      var toggleMode = function(readOnly) {
        rootElement.find('.wrapper read-only').toggle();
      };

      eventbus.on('roadAddress:selected roadAddress:cancelled', function(roadAddress) {

      });

      eventbus.on('layer:selected', function(layer) {
        if(layer !== 'roadAddressProject') {
          $('.wrapper').remove();
        }
      });

      eventbus.on('projectLink:clicked', function(selected) {
        selectedProjectLink = selected;
        rootElement.html(selectedProjectLinkTemplate(currentProject, options, selectedProjectLink));
      });

      eventbus.on('roadAddress:linksSaved', function() {
        // Projectinfo is not undefined and publishable is something like true.
        var ready = projectCollection.projectinfo && projectCollection.projectinfo.publishable;
        rootElement.find('.btn-send').prop("disabled", !ready);
      });

      eventbus.on('roadAddress:projectFailed', function() {
        applicationModel.removeSpinner();
      });

      eventbus.on('roadAddress:projectLinksUpdateFailed',function(errorCode){
        applicationModel.removeSpinner();
        //TODO 375 should modify text to show error status during Status update
        if (errorCode == 400){
          return new ModalConfirm("Valittujen lähdelinkkien geometriaa ei saatu sovitettua kohdegeometrialle. Ota yhteyttä järjestelmätukeen.");
        } else if (errorCode == 401){
          return new ModalConfirm("Sinulla ei ole käyttöoikeutta muutoksen tekemiseen.");
        } else if (errorCode == 412){
          return new ModalConfirm("Täyttämättömien vaatimusten takia siirtoa ei saatu tehtyä. Ota yhteyttä järjestelmätukeen.");
        } else if (errorCode == 500){
          return new ModalConfirm("Siirto ei onnistunut taustajärjestelmässä tapahtuneen virheen takia, ota yhteyttä järjestelmätukeen.");
        } else {
          return new ModalConfirm("Siirto ei onnistunut taustajärjestelmässä tapahtuneen tuntemattoman virheen takia, ota yhteyttä järjestelmätukeen.");
        }
      });

      eventbus.on('roadAddress:projectLinksUpdated',function(data){
        applicationModel.removeSpinner();
        if (typeof data !== 'undefined' && typeof data.publishable !== 'undefined' && data.publishable) {
          console.log(data);
          var publishButton = sendRoadAddressChangeButton();
          $('.form-controls').replaceWith(publishButton);
        }
          projectCollection.setDirty(_.map(selectedProjectLink, function(link) { return link.linkId; }));
      });

      rootElement.on('click', '.project-form button.update', function() {
        projectCollection.saveProjectLinks(selectedProjectLink, currentProject);
      });

      rootElement.on('change', '#dropDown', function() {
          projectCollection.setDirty(_.map(selectedProjectLink, function(link) { return link.linkId; }));
      });

      rootElement.on('click', '.btn-reserve', function() {
        var data;
        var lists = $('.roadAddressProject');
        if ($('#roadAddressProject').get(0)!==null) {
          data = $('#roadAddressProject').get(0);
        } else {
          data =$('#roadpartList').get(0);
        }
        projectCollection.checkIfReserved(data);
        return false;
      });

      rootElement.on('change', '.form-group', function() {
        rootElement.find('.action-selected-field').prop("hidden", false);
      });

      rootElement.on('click', '.project-form button.next', function(){
        var data = $('#roadAddressProject').get(0);
        applicationModel.addSpinner();
        applicationModel.selectLayer('roadAddressProject');
        eventbus.once('roadAddress:projectSaved', function (result) {
          currentProject = result.project;
          jQuery('.modal-overlay').remove();
          if(!_.isUndefined(result.projectAddresses)) {
            eventbus.trigger('roadAddressProject:openProject', result.project);
            rootElement.html(selectedProjectLinkTemplate(currentProject, options, selectedProjectLink));
          }
        });
        if(_.isUndefined(currentProject) || currentProject.id === 0){
          projectCollection.createProject(data, currentProject);
        } else {
          projectCollection.saveProject(data, currentProject);
        }
      });


      rootElement.on('click', '.project-form button.cancel', function(){
        new GenericConfirmPopup('Haluatko varmasti peruuttaa? Mahdolliset tallentamattomat muutokset häviävät', {
          successCallback: function () {
            applicationModel.setOpenProject(false);
            rootElement.find('header').toggle();
            rootElement.find('.wrapper').toggle();
            rootElement.find('footer').toggle();
            projectCollection.clearRoadAddressProjects();
          }
        });

      });

      rootElement.on('change', '.input-required', function() {
        rootElement.find('.project-form button.next').attr('disabled', formIsInvalid(rootElement));
        rootElement.find('.project-form button.save').attr('disabled', formIsInvalid(rootElement));
      });

      rootElement.on('click', '.project-form button.send', function(){
        projectCollection.publishProject();
      });
    };
    bindEvents();
  };
})(this);
