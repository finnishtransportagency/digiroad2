(function (root) {
  root.RoadAddressProjectForm = function(projectCollection) {
    var currentProject = false;
    var selectedProjectLink = false;
    var activeLayer = false;
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

    var inputFieldRequired = function(labelText, id, placeholder, value, maxLength) {
      var lengthLimit = '';
      if (maxLength)
        lengthLimit = 'maxlength="' + maxLength + '"';
      return '<div class="form-group input-required">' +
        '<label class="control-label required">' + labelText + '</label>' +
        '<input type="text" class="form-control" id = "'+id+'"'+lengthLimit+' placeholder = "'+placeholder+'" value="'+value+'"/>' +
        '</div>';
    };

    var title = function() {
      return '<span class ="edit-mode-title">Uusi tieosoiteprojekti</span>';
    };

    var titleWithProjectName = function(projectName) {
      return '<span class ="edit-mode-title">'+projectName+'</span>';
    };

    var actionButtons = function(ready) {
      var html = '<div class="project-form form-controls" id="actionButtons">' +
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

    var newProjectTemplate = function() {
      return _.template('' +
        '<header>' +
        title() +
        '</header>' +
        '<div class="wrapper read-only">' +
        '<div class="form form-horizontal form-dark">' +
        '<div class="edit-control-group choice-group">' +
        staticField('Lisätty järjestelmään', '-') +
        staticField('Muokattu viimeksi', '-') +
        '<div class="form-group editable form-editable-roadAddressProject"> ' +
        '<form  id="roadAddressProject"  class="input-unit-combination form-group form-horizontal roadAddressProject">' +
        inputFieldRequired('*Nimi', 'nimi', '', '', 32) +
        inputFieldRequired('*Alkupvm', 'alkupvm', 'pp.kk.vvvv', '') +
        largeInputField() +
        '<div class="form-group">' +
        '<label class="control-label"></label>' +
        addSmallLabel('TIE') + addSmallLabel('AOSA') + addSmallLabel('LOSA') +
        '</div>' +
        '<div class="form-group">' +
        '<label class="control-label">Tieosat</label>' +
        addSmallInputNumber('tie') + addSmallInputNumber('aosa') + addSmallInputNumber('losa') +  addReserveButton() +
        '</div>' +
        '</form>' +
        ' </div>'+
        '</div>' + '<div class = "form-result">'  +'<label >' + 'PROJEKTIIN VALITUT TIEOSAT:' + '</label>'+
        '<div style="margin-left: 20px;">' +
        addSmallLabel('TIE')+ addSmallLabel('OSA')+ addSmallLabel('PITUUS')+ addSmallLabel('JATKUU')+ addSmallLabel('ELY')+
        '</div>'+
        '<div id ="roadpartList">'+
        '</div></div>' +

        '</div> </div>'  +
        '<footer>' + actionButtons(false) + '</footer>');
    };

    var openProjectTemplate = function(project, formInfo) {
      return _.template('' +
        '<header>' +
        titleWithProjectName(project.name) +
        '</header>' +
        '<div class="wrapper read-only">'+
        '<div class="form form-horizontal form-dark">'+
        '<div class="edit-control-group choice-group">'+
        staticField('Lisätty järjestelmään', project.createdBy + ' ' + project.startDate)+
        staticField('Muokattu viimeksi', project.modifiedBy + ' ' + project.dateModified)+
        '<div class="form-group editable form-editable-roadAddressProject"> '+

        '<form id="roadAddressProject" class="input-unit-combination form-group form-horizontal roadAddressProject">'+
        inputFieldRequired('*Nimi', 'nimi', '', project.name, 32) +
        inputFieldRequired('*Alkupvm', 'alkupvm', 'pp.kk.vvvv', project.startDate)+
        largeInputField(project.additionalInfo)+
        '<div class="form-group">' +
        '<label class="control-label"></label>' +
        addSmallLabel('TIE')+ addSmallLabel('AOSA')+ addSmallLabel('LOSA')+
        '</div>'+
        '<div class="form-group">' +
        '<label class="control-label">Tieosat</label>' +
        addSmallInputNumber('tie')+ addSmallInputNumber('aosa')+ addSmallInputNumber('losa')+ addReserveButton() +
        '</div>'+
        '</form>' +

        '</div>'+
        '</div>' +
        '<div class = "form-result">' +
        '<label >PROJEKTIIN VALITUT TIEOSAT:</label>'+
        '<div style="margin-left: 20px;">'+
        addSmallLabel('TIE')+ addSmallLabel('OSA')+ addSmallLabel('PITUUS')+ addSmallLabel('JATKUU')+ addSmallLabel('ELY')+
        '</div>'+
        '<div id ="roadpartList">'+
        formInfo +
        '</div></div></div></div>'+
        '<footer>' + actionButtons(formInfo !== '') + '</footer>');
    };

    var selectedProjectLinkTemplate = function(project, optionTags, selected) {
      var selection = selectedData(selected);

      return _.template('' +
        '<header>' +
        titleWithProjectName(project.name) +
        '</header>' +
        '<footer>'+ showProjectChangeButton() +'</footer>');
    };

    var showProjectChangeButton = function() {
      return '<div class="project-form form-controls">' +
        '<button class="show-changes btn btn-block btn-show-changes">Avaa projektin yhteenvetotaulukko</button>' +
        '<button disabled id ="send-button" class="send btn btn-block btn-send">Tee tieosoitteenmuutosilmoitus</button></div>';
    };

    var addSmallLabel = function(label){
      return '<label class="control-label-small">'+label+'</label>';
    };

    var deleteButton = function(index){
      return '<button id="'+index+'" class="delete btn-delete">X</button>';
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

    var formIsInvalid = function(rootElement) {
      return !(rootElement.find('#nimi').val() && rootElement.find('#alkupvm').val() !== '');
    };

    var formIsValid = function(rootElement) {
      if (rootElement.find('#nimi').val() && rootElement.find('#alkupvm').val() !== ''){
        return false;
      }
      else {
        return true;
      }
    };

    var projDateEmpty = function(rootElement) {
      return !rootElement.find('#alkupvm').val();
    };

    var addReserveButton = function() {
      return '<button class="btn btn-reserve" disabled>Varaa</button>';
    };

    var bindEvents = function() {

      var rootElement = $('#feature-attributes');
      var toggleMode = function(readOnly) {
        rootElement.find('.wrapper read-only').toggle();
      };

      eventbus.on('roadAddress:newProject', function() {
        currentProject=undefined; //clears old data
        $("#roadAddressProject").html("");
        rootElement.html(newProjectTemplate());
        jQuery('.modal-overlay').remove();
        addDatePicker();
        applicationModel.setOpenProject(true);
        activeLayer = true;
        projectCollection.clearRoadAddressProjects();
      });

      eventbus.on('roadAddress:openProject', function(result) {
        currentProject = result.project;
        projectCollection.clearRoadAddressProjects();
        projectCollection.setCurrentRoadPartList(result.projectLinks);
        var text = '';
        var index = 0;
        _.each(result.projectLinks, function(line){  //TODO later list of already saved roadlinks has to be saved in  roadaddressprojectcollection.currentRoadSegmentList for reserve button to function properly now saved links are cleared when newones are reserved
          text += '<div style="display:inline-block;">' + deleteButton(index++) +
            addSmallLabel(line.roadNumber)+
            addSmallLabel(line.roadPartNumber)+ addSmallLabel(line.roadLength)+ addSmallLabel(line.discontinuity)+ addSmallLabel(line.ely) +
            '</div>';
        });
        rootElement.html(openProjectTemplate(currentProject, text));
        jQuery('.modal-overlay').remove();
        setTimeout(function(){}, 0);
        if(!_.isUndefined(currentProject))
          eventbus.trigger('linkProperties:selectedProject', result.linkId);
        applicationModel.setProjectButton(true);
        applicationModel.setProjectFeature(currentProject.id);
        applicationModel.setOpenProject(true);
        activeLayer = true;
        rootElement.find('.btn-reserve').prop("disabled", false);
        rootElement.find('.btn-next').prop("disabled", false);
      });

      eventbus.on('roadAddress:projectValidationFailed', function (result) {
        new ModalConfirm(result.success.toString());
        applicationModel.removeSpinner();
      });

      eventbus.on('roadAddress:projectValidationSucceed', function () {
        rootElement.find('.btn-next').prop("disabled", formIsInvalid(rootElement));
        rootElement.find('.btn-save').prop("disabled", formIsInvalid(rootElement));
      });

      eventbus.on('layer:selected', function(layer) {
        activeLayer = layer === 'linkPropertyLayer';
      });

      eventbus.on('roadAddress:projectFailed', function() {
        applicationModel.removeSpinner();
      });

      rootElement.on('click', '.project-form button.save', function() {
        var data = $('#roadAddressProject').get(0);
        applicationModel.addSpinner();
        eventbus.once('roadAddress:projectSaved', function (result) {
          currentProject = result.project;
          var text = '';
          var index = 0;
          _.each(result.formInfo, function(line){
            text += '<div style="display:inline-block;">' + ' '+ deleteButton(index++) +
              addSmallLabel(line.roadNumber)+ addSmallLabel(line.roadPartNumber)+ addSmallLabel(line.roadLength)+ addSmallLabel(line.discontinuity)+ addSmallLabel(line.ely) +
              '</div>';
          });
          rootElement.html(openProjectTemplate(currentProject, text));

          jQuery('.modal-overlay').remove();
          addDatePicker();
          if(!_.isUndefined(result.projectAddresses)) {
            eventbus.trigger('linkProperties:selectedProject', result.projectAddresses.linkId);
          }
        });
        if(_.isUndefined(currentProject) || currentProject.id === 0){
          projectCollection.createProject(data);
        } else {
          projectCollection.saveProject(data);
        }
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
        eventbus.once('roadAddress:projectSaved', function (result) {
          currentProject = result.project;
          jQuery('.modal-overlay').remove();
          if(!_.isUndefined(result.projectAddresses)) {
            eventbus.trigger('linkProperties:selectedProject', result.projectAddresses.linkId);
          }
          eventbus.trigger('roadAddressProject:openProject', result.project);
          rootElement.html(selectedProjectLinkTemplate(currentProject, options, selectedProjectLink));
          _.defer(function(){
            applicationModel.selectLayer('roadAddressProject');
          });
        });
        if(_.isUndefined(currentProject) || currentProject.id === 0){
          projectCollection.createProject(data);
        } else {
          projectCollection.saveProject(data);
        }
      });


      rootElement.on('click', '.project-form button.cancel', function(){
        if (activeLayer) {
          new GenericConfirmPopup('Haluatko varmasti peruuttaa? Mahdolliset tallentamattomat muutokset häviävät', {
            successCallback: function () {
              applicationModel.setOpenProject(false);
              rootElement.find('header').toggle();
              rootElement.find('.wrapper').toggle();
              rootElement.find('footer').toggle();
              projectCollection.clearRoadAddressProjects();
              eventbus.trigger('layer:enableButtons', true);
            }
          });
        } else {
          eventbus.trigger('roadAddress:openProject', projectCollection.getCurrentProject());
          eventbus.trigger('roadLinks:refreshView');
        }
      });

      rootElement.on('change', '.input-required', function() {
        rootElement.find('.project-form button.next').attr('disabled', formIsInvalid(rootElement));
        rootElement.find('.project-form button.save').attr('disabled', formIsInvalid(rootElement));
        rootElement.find('#roadAddressProject button.btn-reserve').attr('disabled', projDateEmpty(rootElement));
      });

    };
    bindEvents();
  };
})(this);
