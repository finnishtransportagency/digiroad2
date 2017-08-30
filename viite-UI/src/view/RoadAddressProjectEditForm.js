(function (root) {
  root.RoadAddressProjectEditForm = function(projectCollection, selectedProjectLinkProperty, projectLinkLayer, projectChangeTable) {
    var STATUS_NOT_HANDLED = 0;
    var STATUS_TERMINATED = 1;
    var STATUS_NEW = 2;
    var STATUS_TRANSFER = 3;
    var STATUS_UNCHANGED = 4;
    var STATUS_NUMBERING = 5;

    var currentProject = false;
    var selectedProjectLink = false;
    var backend=new Backend();
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
        '<div class="asset-log-info">' + 'Tarkista tekemäsi muutokset.' + '<br>' + 'Jos muutokset ok, tallenna.' + '</div>' +
        '</div>';
      return field;
    };
    var options =['Valitse'];

    var title = function() {
      return '<span class ="edit-mode-title">Uusi tieosoiteprojekti</span>';
    };

    var titleWithProjectName = function(projectName) {
      return '<span class ="edit-mode-title">'+projectName+'</span>';
    };

    var clearInformationContent = function() {
      $('#information-content').empty();
    };

    var sendRoadAddressChangeButton = function() {

      return '<div class="project-form form-controls">' +
        '<button class="show-changes btn btn-block btn-show-changes">Avaa projektin yhteenvetotaulukko</button>' +
        '<button id ="send-button" class="send btn btn-block btn-send">Tee tieosoitteenmuutosilmoitus</button></div>';
    };

    var showProjectChangeButton = function() {
      return '<div class="project-form form-controls">' +
        '<button class="show-changes btn btn-block btn-show-changes">Avaa projektin yhteenvetotaulukko</button>' +
        '<button disabled id ="send-button" class="send btn btn-block btn-send">Tee tieosoitteenmuutosilmoitus</button></div>';
    };

    var actionButtons = function() {
      var html = '<div class="project-form form-controls" id="actionButtons">' +
        '<button class="update btn btn-save"' + (projectCollection.isDirty() ? '' : 'disabled') + '>Tallenna</button>' +
        '<button class="cancelLink btn btn-cancel">Peruuta</button>' +
        '</div>';
      return html;
    };

    var selectedData = function (selected) {
      var span = '';
      if (selected[0]) {
        var link = selected[0];
        var startM = Math.min.apply(Math, _.map(selected, function(l) { return l.startAddressM; }));
        var endM = Math.max.apply(Math, _.map(selected, function(l) { return l.endAddressM; }));
        span = '<div class="project-edit-selections" style="display:inline-block;padding-left:8px;">' +
          '<div class="project-edit">' +
          ' TIE ' + '<span class="project-edit">' + link.roadNumber + '</span>' +
          ' OSA ' + '<span class="project-edit">' + link.roadPartNumber + '</span>' +
          ' AJR ' + '<span class="project-edit">' + link.trackCode + '</span>' +
          ' M:  ' + '<span class="project-edit">' + startM + ' - ' + endM + '</span>' +
           (selected.length > 1 ? ' (' + selected.length + ' linkkiä)' : '')+
          '</div>' +
          '</div>';
      }
      return span;
    };

    var selectedProjectLinkTemplate = function(project, optionTags, selected) {
      var selection = selectedData(selected);
      var status = _.uniq(_.map(selected, function(l) { return l.status; }));
      if (status.length == 1)
        status = status[0];
      else
        status = 0;
      var enableStatusNew = (selected[0].status !== STATUS_NOT_HANDLED && selected[0].status !== STATUS_TERMINATED)|| selected[0].roadLinkSource === 3;
      var terminationState = status == STATUS_TERMINATED ? ' selected' : selected[0].roadLinkSource === 3 ? 'disabled' : '';
      var toEdit = selected[0].id !== 0;
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
        '<label>Toimenpiteet,' + selection  + '</label>' +
        '<div class="input-unit-combination">' +
        '<select class="form-control" id="dropDown" size="1">'+
        '<option selected disabled hidden>Valitse</option>'+
        '<option value="ennallaan">Ennallaan</option>'+
        '<option value="siirto"' + (toEdit ? ' ' : ' disabled') + '>Siirto</option>'+
        '<option value="uusi"' + (enableStatusNew ? ' ' : ' disabled')+'>Uusi</option>'+
        '<option value="lakkautus"' + (terminationState) + '>Lakkautus</option>'+
        '<option value="numerointi"' + (toEdit ? ' ' : ' disabled') + '>Numerointi</option>'+
        '<option value="palautus"' + (toEdit ? ' ' : ' disabled') + '>Palautus aihioksi tai tieosoitteettomaksi</option>' +
        '</select>'+
        '</div>'+
        newRoadAddressInfo() +
        '</form>' +
        changeDirection()+
        actionSelectedField()+
        '</div>'+
        '</div>' +
        '</div>'+
        '</div>'+
        '<footer>' + actionButtons() + '</footer>');
    };

    var newRoadAddressInfo = function(){
      return '<div class="form-group new-road-address" hidden>' +
        '<div><label></label></div><div><label style = "margin-top: 50px">TIEOSOITTEEN TIEDOT</label></div>' +
        addSmallLabel('TIE') + addSmallLabel('OSA') + addSmallLabel('AJR')+ addSmallLabel('ELY')  + addSmallLabel('JATKUU')+
        '</div>' +
        '<div class="form-group new-road-address" id="new-address-input1" hidden>'+
          addSmallInputNumber('tie',(selectedProjectLink[0].roadNumber !== 0 ? selectedProjectLink[0].roadNumber : '')) +
          addSmallInputNumber('osa',(selectedProjectLink[0].roadPartNumber !== 0 ? selectedProjectLink[0].roadPartNumber : '')) +
          addSmallInputNumber('ajr',(selectedProjectLink[0].trackCode !== 99 ? selectedProjectLink[0].trackCode : '')) +
          addSmallInputNumberDisabled('ely', selectedProjectLink[0].elyCode) +
          addSelect() +
          addSmallLabel('TIETYYPPI') +
          roadTypeDropdown() +
        '</div>';
    };

    function replaceAddressInfo()
    {
      if (selectedProjectLink[0].roadNumber === 0 && selectedProjectLink[0].roadPartNumber === 0 && selectedProjectLink[0].trackCode === 99 )
      {
        backend.getNonOverridenVVHValuesForLink(selectedProjectLink[0].linkId, function (response) {
          if (response.success) {
            $('#tie').val(response.roadNumber);
            $('#osa').val(response.roadPartNumber);
          }
        });
      }
    }

    var roadTypeDropdown = function() {
      return '<select class="form-control" id="roadTypeDropDown" size = "1" style="width: auto !important; display: inline">' +
              '<option value = "1">1 Yleinen tie</option>'+
              '<option value = "2">2 Lauttaväylä yleisellä tiellä</option>'+
              '<option value = "3">3 Kunnan katuosuus</option>'+
              '<option value = "4">4 Yleisen tien työmaa</option>'+
              '<option value = "5">5 Yksityistie</option>'+
              '<option value = "9">9 Omistaja selvittämättä</option>' +
              '<option value = "99">99 Ei määritelty</option>' +
              '</select>';
    };

    var addSelect = function(){
      return '<select class="form-select-control" id="discontinuityDropdown" size="1">'+
      '<option value = "5" selected disabled hidden>5 Jatkuva</option>'+
      '<option value="1" >1 Tien loppu</option>'+
      '<option value="2" >2 Epäjatkuva</option>'+
      '<option value="3" >3 ELY:n raja</option>'+
      '<option value="4" >4 Lievä epäjatkuvuus</option>'+
      '<option value="5" >5 Jatkuva</option>'+
      '</select>';
    };

    var changeDirection = function () {
      return '<div hidden class="form-group changeDirectionDiv" style="margin-top:15px">' +
          '<button class="form-group changeDirection btn btn-primary">Käännä kasvusuunta</button>' +
          '</div>';
    };

    var addSmallLabel = function(label){
      return '<label class="control-label-small">'+label+'</label>';
    };

    var addSmallInputNumber = function(id, value){
      //Validate only number characters on "onkeypress" including TAB and backspace
      return '<input type="text" onkeypress="return (event.charCode >= 48 && event.charCode <= 57) || (event.keyCode == 8 || event.keyCode == 9)' +
        '"class="form-control small-input roadAddressProject" id="'+id+'" value="'+(_.isUndefined(value)? '' : value )+'" onclick=""/>';
    };

    var addSmallInputNumberDisabled = function(id, value){
      return '<input type="text" class="form-control small-input roadAddressProject" id="'+id+'" value="'+(_.isUndefined(value)? '' : value )+'" readonly="readonly"/>';
    };

    var emptyTemplate = function(project) {
      var selection = selectedData(selectedProjectLink);

      return _.template('' +
        '<header>' +
        titleWithProjectName(project.name) +
        '</header>' +
        '<footer>'+showProjectChangeButton()+'</footer>');
    };

    var isProjectPublishable = function(){
      return projectCollection.getPublishableStatus();
    };

    var checkInputs = function () {
        var rootElement = $('#feature-attributes');
        var inputs = rootElement.find('input');
        var filled = true;
        for (var i = 0; i < inputs.length; i++) {
            if (inputs[i].type === 'text' && !inputs[i].value) {
                filled = false;
            }
        }
        if (filled) {
            rootElement.find('.project-form button.update').prop("disabled", false);
        } else {
            rootElement.find('.project-form button.update').prop("disabled", true);
        }
    };

    var changeDropDownValue = function (statusCode) {
      if (statusCode === STATUS_UNCHANGED) {
        $("#dropDown").val('ennallaan').change();
      }
      $('#discontinuityDropdown').val(selectedProjectLink[selectedProjectLink.length - 1].discontinuity);
      $('#roadTypeDropDown').val(selectedProjectLink[0].roadTypeId);
    };

    var bindEvents = function() {

      var rootElement = $('#feature-attributes');
      var toggleMode = function(readOnly) {
        rootElement.find('.wrapper read-only').toggle();
      };

      eventbus.on('roadAddress:selected roadAddress:cancelled', function(roadAddress) {

      });

      eventbus.on('projectLink:clicked', function(selected) {
        selectedProjectLink = selected;
        currentProject = projectCollection.getCurrentProject();
        clearInformationContent();
        rootElement.html(selectedProjectLinkTemplate(currentProject.project, options, selectedProjectLink));
        replaceAddressInfo();
        checkInputs();
        // Change selected value in dropdown according to project link status
        changeDropDownValue(selectedProjectLink[0].status);
      });

      eventbus.on('roadAddress:projectFailed', function() {
        applicationModel.removeSpinner();
      });

      eventbus.on('roadAddress:projectLinksUpdateFailed',function(errorCode){
        applicationModel.removeSpinner();
        if (errorCode == 400){
          return new ModalConfirm("Päivitys epäonnistui puutteelisten tietojen takia. Ota yhteyttä järjestelmätukeen.");
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
        if (typeof data !== 'undefined' && typeof data.publishable !== 'undefined' && data.publishable) {
          eventbus.trigger('roadAddressProject:projectLinkSaved', data.id, data.publishable);
        }
        else {
          eventbus.trigger('roadAddressProject:projectLinkSaved', data.id, data.publishable);
          applicationModel.removeSpinner();
        }
      });

      eventbus.on('roadAddress:projectSentSuccess', function() {
        new ModalConfirm("Muutosilmoitus lähetetty Tierekisteriin.");
        //TODO: make more generic layer change/refresh
        applicationModel.selectLayer('linkProperty');

        rootElement.empty();
        clearInformationContent();

        selectedProjectLinkProperty.close();
        projectCollection.clearRoadAddressProjects();
        projectCollection.reset();
        applicationModel.setOpenProject(false);

        eventbus.trigger('roadAddressProject:deselectFeaturesSelected');
        eventbus.trigger('roadLinks:refreshView');
      });

      eventbus.on('roadAddress:projectSentFailed', function(error) {
        new ModalConfirm(error);
      });

      eventbus.on('roadAddress:projectLinksCreateSuccess', function () {
        rootElement.find('.changeDirectionDiv').prop("hidden", false);
      });

      eventbus.on('roadAddress:changeDirectionFailed', function(error) {
            new ModalConfirm(error);
      });

      rootElement.on('click','.changeDirection', function () {
          projectCollection.changeNewProjectLinkDirection(projectCollection.getCurrentProject().project.id, selectedProjectLinkProperty.get());
      });

      eventbus.on('roadAddress:projectLinksSaveFailed', function (result) {
        new ModalConfirm(result.toString());
      });

      rootElement.on('click', '.project-form button.update', function() {
        currentProject = projectCollection.getCurrentProject();
        if( $('[id=dropDown] :selected').val() == 'lakkautus') {
          projectCollection.saveProjectLinks(projectCollection.getTmpDirty(), STATUS_TERMINATED);
          rootElement.html(emptyTemplate(currentProject.project));
        }
        else if( $('[id=dropDown] :selected').val() === 'siirto'){
          projectCollection.createProjectLinks(projectCollection.getTmpDirty());
          rootElement.html(emptyTemplate(currentProject.project));
        }
        else if( $('[id=dropDown] :selected').val() === 'uusi'){
          projectCollection.createProjectLinks(selectedProjectLink);
        }
        else if( $('[id=dropDown] :selected').val() === 'ennallaan'){
          projectCollection.createProjectLinks(projectCollection.getTmpDirty());
          rootElement.html(emptyTemplate(currentProject.project));
        }
        else if( $('[id=dropDown] :selected').val() === 'numerointi'){
            projectCollection.createProjectLinks(projectCollection.getTmpDirty());
            rootElement.html(emptyTemplate(currentProject.project));
        }
        else if( $('[id=dropDown] :selected').val() === 'palautus'){
          projectCollection.revertChangesRoadlink(selectedProjectLink);
          rootElement.html(emptyTemplate(currentProject.project));
        }
      });

      rootElement.on('change', '#dropDown', function() {
        $('#ajr').prop('disabled',false);
        $('#discontinuityDropdown').prop('disabled',false);
        $('#roadTypeDropDown').prop('disabled',false);
        if(this.value == "lakkautus") {
          rootElement.find('.new-road-address').prop("hidden", true);
          rootElement.find('.changeDirectionDiv').prop("hidden", true);
          projectCollection.setDirty(projectCollection.getDirty().concat(_.map(selectedProjectLink, function (link) {
            return {'id': link.linkId, 'status': STATUS_TERMINATED};
          })));
          projectCollection.setTmpDirty(projectCollection.getTmpDirty().concat(selectedProjectLink));
          rootElement.find('.project-form button.update').prop("disabled", false);
        }
        else if(this.value == "uusi"){
          projectCollection.setTmpDirty(projectCollection.getTmpDirty().concat(selectedProjectLink));
          rootElement.find('.new-road-address').prop("hidden", false);
          if(selectedProjectLink[0].id !== 0)
            rootElement.find('.changeDirectionDiv').prop("hidden", false);
        }
        else if(this.value == "ennallaan"){
          rootElement.find('.new-road-address').prop("hidden", true);
          rootElement.find('.changeDirectionDiv').prop("hidden", true);
          projectCollection.setDirty(projectCollection.getDirty().concat(_.map(selectedProjectLink, function (link) {
            return {'id': link.linkId, 'status': STATUS_UNCHANGED};
          })));
          projectCollection.setTmpDirty(projectCollection.getTmpDirty().concat(selectedProjectLink));
        }
        else if(this.value == "siirto") {
          projectCollection.setDirty(projectCollection.getDirty().concat(_.map(selectedProjectLink, function (link) {
              return {'id': link.linkId, 'status': STATUS_TRANSFER};
          })));
          projectCollection.setTmpDirty(projectCollection.getDirty());
          rootElement.find('.new-road-address').prop("hidden", false);
          if(selectedProjectLink[0].id !== 0)
              rootElement.find('.changeDirectionDiv').prop("hidden", false);
        }
        else if(this.value == "numerointi") {
            $('#ajr').prop('disabled',true);
            $('#discontinuityDropdown').prop('disabled',true);
            $('#roadTypeDropDown').prop('disabled',true);
            projectCollection.setDirty(projectCollection.getDirty().concat(_.map(selectedProjectLink, function (link) {
                return {'id': link.linkId, 'status': STATUS_NUMBERING};
            })));
            projectCollection.setTmpDirty(projectCollection.getDirty());
            rootElement.find('.new-road-address').prop("hidden", false);
            rootElement.find('.changeDirectionDiv').prop("hidden", false);
            rootElement.find('.project-form button.update').prop("disabled", false);
        }
        else if(this.value == "palautus") {
          rootElement.find('.new-road-address').prop("hidden", true);
          rootElement.find('.changeDirectionDiv').prop("hidden", true);
          rootElement.find('.project-form button.update').prop("disabled", false);
        }
      });

      rootElement.on('change', '.form-group', function() {
        rootElement.find('.action-selected-field').prop("hidden", false);
      });

      rootElement.on('click', '.project-form button.cancelLink', function(){
        if(projectCollection.isDirty()) {
          projectCollection.revertLinkStatus();
          projectCollection.setDirty([]);
          projectCollection.setTmpDirty([]);
          projectLinkLayer.clearHighlights();
          $('.wrapper').remove();
          eventbus.trigger('roadAddress:projectLinksEdited');
        } else {
          eventbus.trigger('roadAddress:openProject', projectCollection.getCurrentProject());
          eventbus.trigger('roadLinks:refreshView');
        }
      });

      rootElement.on('click', '.project-form button.send', function(){
        projectCollection.publishProject();
      });

      rootElement.on('click', '.project-form button.show-changes', function(){
        $(this).empty();
        projectChangeTable.show();
        var publishButton = sendRoadAddressChangeButton();
        var projectChangesButton = showProjectChangeButton();
        if(isProjectPublishable()) {
          $('#information-content').html('' +
            '<div class="form form-horizontal">' +
            '<p>' + 'Validointi ok. Voit tehdä tieosoitteenmuutosilmoituksen' + '<br>' +
            'tai jatkaa muokkauksia.' + '</p>' +
            '</div>');
          $('footer').html(publishButton);
        }
        else
          $('footer').html(projectChangesButton);
      });

      rootElement.on('keyup','.form-control.small-input', function () {
         checkInputs();
      });

    };
    bindEvents();
  };
})(this);
