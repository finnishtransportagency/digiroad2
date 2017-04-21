(function (root) {
  root.RoadAddressProjectForm = function(projectCollection) {
    var currentProject;
    var staticField = function(labelText, dataField) {
      var field;
      field = '<div class="form-group">' +
        '<p class="form-control-static asset-log-info">' + labelText + ' : ' + dataField + '</p>' +
        '</div>';
      return field;
    };

    var largeInputField = function (dataField) {
      return '<div class="form-group">' +
        '<label class="control-label">LISÄTIEDOT</label>'+
        '<textarea class="form-control large-input roadAddressProject" id="lisatiedot">'+(dataField === undefined || dataField === null ? "" : dataField )+'</textarea>'+
        '</div>';
    };

    var inputFieldRequired = function(labelText, id, placeholder,  value) {
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

    var buttons =
      '<div class="project-form form-controls">' +
      '<button class="next btn btn-next" disabled>Seuraava</button>' +
      '<button class="save btn btn-tallena" disabled>Tallenna</button>' +
      '<button class="cancel btn btn-perruta">Peruuta</button>' +
      '</div>';

    var headerButton =
      '<div class="linear-asset form-controls">'+
      '<button class="cancel btn btn-secondary">Sulje projekti</button>'+
      '</div>';

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
        inputFieldRequired('*Nimi', 'nimi', '', '') +
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
          '<div style="margin-left: 15px;">' +
          '</div>'+
          addSmallLabel('TIE')+ addSmallLabel('OSA')+ addSmallLabel('PITUUS')+ addSmallLabel('JATKUU')+ addSmallLabel('ELY')+

          '<div id ="roadpartList">'+
          '</div></div>' +

        '</div> </div>'  +
        '<footer>' + buttons + '</footer>');
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
        inputFieldRequired('*Nimi', 'nimi', '', project.name) +
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
          '<div style="margin-left: 15px;">' +'</div>'+
            addSmallLabel('TIE')+ addSmallLabel('OSA')+ addSmallLabel('PITUUS')+ addSmallLabel('JATKUU')+ addSmallLabel('ELY')+
          '<div id ="roadpartList">'+
          formInfo +
          '</div></div></div></div>'+
        '<footer>' + buttons + '</footer>');
    };

    var addSmallLabel = function(label){
      return '<label class="control-label-small">'+label+'</label>';
    };

    var addSmallInputNumber = function(id, value){
      //Validate only numebers characters on "onkeypress" including TAB and backspace
      return '<input type="text" onkeypress="return (event.charCode >= 48 && event.charCode <= 57) || (event.keyCode == 8 || event.keyCode == 9)' +
          '" class="form-control small-input roadAddressProject" id="'+id+'" value="'+(_.isUndefined(value)? '' : value )+'" onclick=""/>';
    };

    var addDatePicker = function () {
      var $validFrom = $('#alkupvm');

      dateutil.addSingleDependentDatePicker($validFrom);

    };

    var formIsValid = function(rootElement) {
      if (rootElement.find('#nimi').val() && rootElement.find('#alkupvm').val() !== ''){
        return false;
      }
      else {
        return true;
      }
    };

    var addReserveButton = function() {
        return '<button class="btn btn-reserve">Varaa</button>';
    };

    var bindEvents = function() {

      var rootElement = $('#feature-attributes');
      var toggleMode = function(readOnly) {
        rootElement.find('.wrapper read-only').toggle();
      };

      eventbus.on('roadAddress:newProject', function() {
        rootElement.html(newProjectTemplate());
        jQuery('.modal-overlay').remove();
        addDatePicker();
        applicationModel.setOpenProject(true);
      });

      eventbus.on('roadAddress:openProject', function(result) {
        currentProject = result.projects;
        var text = '';
        _.each(result.projectLinks, function(line){
          text += '<div>' +
            '<button class="delete btn-delete-roadpart">x</button>'+addSmallLabel(line.roadNumber)+ addSmallLabel(line.roadPartNumber)+ addSmallLabel(line.RoadLength)+ addSmallLabel(line.discontinuity)+ addSmallLabel(line.ely) +
            '</div>';
        });
        rootElement.html(openProjectTemplate(currentProject, text));
        jQuery('.modal-overlay').remove();
        setTimeout(function(){}, 0);
        if(!_.isUndefined(currentProject))
          eventbus.trigger('linkProperties:selectedProject', currentProject.linkId);
          applicationModel.setProjectButton(true);
          applicationModel.setProjectFeature(currentProject.linkId);
      });

      eventbus.on('roadAddress:selected roadAddress:cancelled', function(roadAddress) {

      });

      eventbus.on('roadAddress:projectValidationFailed', function (result) {
        new ModalConfirm(result.success.toString());
          applicationModel.removeSpinner();
      });

      eventbus.on('roadAddress:projectSaved', function (result) {
        currentProject = result.project;
        var text = '';
        _.each(result.formInfo, function(line){
          text += '<div>' + ' '+
            addSmallLabel(line.roadNumber)+ addSmallLabel(line.roadPartNumber)+ addSmallLabel(line.RoadLength)+ addSmallLabel(line.discontinuity)+ addSmallLabel(line.ely) +
          '</div>';
        });
        rootElement.html(openProjectTemplate(result.project, text));

          jQuery('.modal-overlay').remove();
          addDatePicker();
          if(!_.isUndefined(result.projectAddresses)) {
            eventbus.trigger('linkProperties:selectedProject', result.projectAddresses.linkId);
        } else {
          jQuery('.modal-overlay').remove();
        }
      });

      rootElement.on('click', '.project-form button.save', function() {
        var data = $('#roadAddressProject').get(0);
        applicationModel.addSpinner();
        projectCollection.createProject(data, currentProject);
      });

      rootElement.on('click', '.btn-reserve', function() {
       var data;
        if ($('#roadAddressProject').get(0)!==null) {
         data = $('#roadAddressProject').get(0);
        projectCollection.checkIfReserved(data);
        return false;
       } else
       {
         data =$('#savedproject').get(0);
         projectCollection.checkIfReserved(data);
        return false;
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
        rootElement.find('.project-form button.save').attr('disabled', formIsValid(rootElement));
      });

    };
    bindEvents();
  };
})(this);
