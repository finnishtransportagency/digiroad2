(function (root) {
  root.SplitForm = function(projectCollection, selectedProjectLinkProperty, projectLinkLayer, projectChangeTable, backend) {
    var LinkStatus = LinkValues.LinkStatus;
    var LinkGeomSource = LinkValues.LinkGeomSource;
    var SideCode = LinkValues.SideCode;
    var editableStatus = [LinkValues.ProjectStatus.Incomplete.value, LinkValues.ProjectStatus.ErroredInTR.value, LinkValues.ProjectStatus.Unknown.value];

    var currentProject = false;
    var selectedProjectLink = false;
    var markers = ['A', 'B'];
    var formCommon = new FormCommon('split-');

    var options =['Valitse'];

    var showProjectChangeButton = function() {
      return '<div class="split-form form-controls">' +
        formCommon.projectButtons() + '</div>';
    };

    var revertSplitButton = function () {
      return '<div class="form-group" style="margin-top:15px">' +
        '<button id="revertSplit" class="form-group revertSplit btn btn-primary">Palauta aihioksi</button>' +
        '</div>';
    };

    var selectedSplitData = function (selected) {
      var span = [];
      _.each(selected, function (sel) {
        if (sel) {
          var link = sel;
          var startM = (((applicationModel.getSelectedTool() === 'Cut' || !_.isUndefined(selected[0].connectedLinkId)) && selected.length == 2) ? Math.round(link.startMValue) : Math.min.apply(Math, _.map(selected, function (l) {
            return l.startAddressM;
          })));
          var endM = (((applicationModel.getSelectedTool() === 'Cut' || !_.isUndefined(selected[0].connectedLinkId)) && selected.length == 2) ? Math.round(link.endMValue) : Math.max.apply(Math, _.map(selected, function (l) {
            return l.endAddressM;
          })));
          var div = '<div class="project-edit-selections" style="display:inline-block;padding-left:8px;">' +
            '<div class="project-edit">' +
            ' TIE ' + '<span class="project-edit">' + link.roadNumber + '</span>' +
            ' OSA ' + '<span class="project-edit">' + link.roadPartNumber + '</span>' +
            ' AJR ' + '<span class="project-edit">' + link.trackCode + '</span>' +
            ' M:  ' + '<span class="project-edit">' + startM + ' - ' + endM + '</span>' +
            '</div>' +
            '</div>';
          span.push(div);
        }
      });
      return span;
    };

    var defineOptionModifiers = function (option, selection) {
      var isSplitMode = !_.isUndefined(selection.connectedLinkId);
      var linkStatus = selection.status;
      var targetLinkStatus = _.find(LinkStatus, function (ls) {
        return ls.description === option || (option === '' && ls.value == 99);
      });
      if (isSplitMode)
        return splitTransitionModifiers(targetLinkStatus, linkStatus);
      else
        return transitionModifiers(targetLinkStatus, linkStatus);
    };

    var transitionModifiers = function (targetStatus, currentStatus) {
      var mod;
      if (_.contains(targetStatus.transitionFrom, currentStatus))
        mod = '';
      else
        mod = 'disabled hidden';
      if (currentStatus == targetStatus.value)
        return mod + ' selected';
      else
        return mod;
    };

    var splitTransitionModifiers = function (targetStatus, currentStatus) {
      var mod;
      if (currentStatus === LinkStatus.Undefined.value) {
        if (_.contains([LinkStatus.New, LinkStatus.Unchanged, LinkStatus.Transfer, LinkStatus.Undefined, LinkStatus.NotHandled], targetStatus)) {
          mod = '';
        } else {
          if (targetStatus == LinkStatus.Undefined || targetStatus == LinkStatus.NotHandled)
            mod = 'selected disabled hidden';
          else
            mod = 'disabled hidden';
        }
      } else if (currentStatus === LinkStatus.New.value) {
        if (targetStatus === LinkStatus.New)
          mod = 'selected';
        else
          mod = 'disabled';
      } else if (currentStatus === LinkStatus.Unchanged.value || currentStatus === LinkStatus.Transfer.value) {
        if (targetStatus.value === currentStatus)
          mod = 'selected';
        else {
          if (_.contains([LinkStatus.Unchanged, LinkStatus.Transfer], targetStatus)) {
            mod = '';
          } else {
            mod = 'disabled hidden';
          }
        }
      }
      return mod;
    };

    var selectedProjectLinkTemplate = function(project, optionTags, selected) {
      var selection = (((applicationModel.getSelectedTool() == 'Cut' || !_.isUndefined(selected[0].connectedLinkId)) &&
      selected[0].roadLinkSource == LinkGeomSource.SuravageLinkInterface.value) ? selectedSplitData(selected) : formCommon.selectedData(selected));
      return _.template('' +
        '<header>' +
        formCommon.titleWithProjectName(project.name, currentProject) +
        '</header>' +
        '<div class="wrapper read-only">'+
        '<div class="form form-horizontal form-dark">'+
        '<div class="edit-control-group project-choice-group">'+
        formCommon.staticField('Lisätty järjestelmään', project.createdBy + ' ' + project.startDate)+
        formCommon.staticField('Muokattu viimeksi', project.modifiedBy + ' ' + project.dateModified)+
        '<div class="split-form-group editable form-editable-roadAddressProject"> '+
        selectionFormCutted(selection, selected)+
        ((selected.length == 2 && selected[0].linkId === selected[1].linkId) ? '' : formCommon.changeDirection(selected)) +
        formCommon.actionSelectedField()+
        ((!_.isUndefined(selected[0].connectedLinkId)) ? revertSplitButton(): '') +
        '</div>'+
        '</div>' +
        '</div>'+
        '</div>'+
        '<footer>' + formCommon.actionButtons('split-', projectCollection.isDirty()) + '</footer>');
    };

    var getSplitPointBySideCode = function (link) {
      if (link.sideCode == SideCode.AgainstDigitizing.value) {
        return _.first(link.points);
      } else {
        return _.last(link.points);
      }
    };
    var selectionFormCutted = function (selection, selected) {
      var firstLink = _.first(_.sortBy(selected, 'startAddressM'));
      var splitPoint = ((applicationModel.getSelectedTool() != "Cut" ? getSplitPointBySideCode(firstLink) : firstLink.splitPoint));

      return '<form id="roadAddressProjectFormCut" class="input-unit-combination split-form-group form-horizontal roadAddressProject">'+
          '<input type="hidden" id="splitx" value="' + splitPoint.x + '"/>' +
          '<input type="hidden" id="splity" value="' + splitPoint.y + '"/>' +
        '<label>Toimenpiteet,' + selection[0]  + '</label>' +
          '<span class="marker">'+markers[0]+'</span>'+
          dropdownOption(0, selected) +
          '<hr class="horizontal-line"/>' +
          '<label>Toimenpiteet,' + selection[1]  + '</label>' +
          '<span class="marker">'+markers[1]+'</span>' +
          dropdownOption(1, selected)+
        formCommon.newRoadAddressInfo(selected, selectedProjectLink[0]) +
          '</form>';
    };

    var dropdownOption = function (index, selected) {
      return '<div class="input-unit-combination">' +
        '<select class="split-form-control" id="dropdown_' + index + '" size="1">' +
        '<option id="drop_' + index + '_' + '" ' + defineOptionModifiers('', selected[index]) + '>Valitse</option>' +
        '<option id="drop_' + index + '_' + LinkStatus.Unchanged.description + '" value=' + LinkStatus.Unchanged.description + ' ' + defineOptionModifiers(LinkStatus.Unchanged.description, selected[index]) + '>Ennallaan</option>' +
        '<option id="drop_' + index + '_' + LinkStatus.Transfer.description + '" value=' + LinkStatus.Transfer.description + ' ' + defineOptionModifiers(LinkStatus.Transfer.description, selected[index]) + '>Siirto</option>' +
        '<option id="drop_' + index + '_' + LinkStatus.New.description + '" value=' + LinkStatus.New.description + ' ' + defineOptionModifiers(LinkStatus.New.description, selected[index]) + '>Uusi</option>' +
        '<option id="drop_' + index + '_' + LinkStatus.Terminated.description + '" value=' + LinkStatus.Terminated.description + ' ' + defineOptionModifiers(LinkStatus.Terminated.description, selected[index]) + '>Lakkautus</option>' +
        '<option id="drop_' + index + '_' + LinkStatus.Numbering.description + '" value=' + LinkStatus.Numbering.description + ' ' + defineOptionModifiers(LinkStatus.Numbering.description, selected[index]) + '>Numerointi</option>' +
        '<option id="drop_' + index + '_' + LinkStatus.Revert.description + '" value=' + LinkStatus.Revert.description + ' ' + defineOptionModifiers(LinkStatus.Revert.description, selected[index]) + '>Palautus aihioksi tai tieosoitteettomaksi</option>' +
        '</select>' +
        '</div>';
    };

    var emptyTemplate = function(project) {
      return _.template('' +
          '<header style ="display:-webkit-inline-box;">' +
          formCommon.titleWithProjectName(project.name, currentProject) +
          '</header>' +
          '<footer>'+showProjectChangeButton()+'</footer>');
    };

    var isProjectPublishable = function(){
      return projectCollection.getPublishableStatus();
    };

    var isProjectEditable = function(){
      return _.contains(editableStatus, projectCollection.getCurrentProject().project.statusCode);
    };

    var changeDropDownValue = function (statusCode) {
      var rootElement = $('#feature-attributes');
      if (statusCode === LinkStatus.Unchanged.value) {
        $("#dropDown_0 option[value="+ LinkStatus.New.description +"]").prop('disabled',true);
        $("#dropDown_0 option[value="+ LinkStatus.Unchanged.description +"]").attr('selected', 'selected').change();
      }
      else if(statusCode === LinkStatus.New.value){
        $("#dropDown_0 option[value="+ LinkStatus.New.description +"]").attr('selected', 'selected').change();
        projectCollection.setTmpDirty(projectCollection.getTmpDirty().concat(selectedProjectLink));
        rootElement.find('.new-road-address').prop("hidden", false);
        if(selectedProjectLink[0].id !== 0)
          rootElement.find('.changeDirectionDiv').prop("hidden", false);
      }
      else if (statusCode == LinkStatus.Transfer.value) {
        $("#dropDown_0 option[value="+ LinkStatus.New.description +"]").prop('disabled',true);
        $("#dropDown_0 option[value="+ LinkStatus.Transfer.description +"]").attr('selected', 'selected').change();
        if(selectedProjectLink[0].id !== 0)
          rootElement.find('.changeDirectionDiv').prop("hidden", false);
      }
      else if (statusCode == LinkStatus.Numbering.value) {
        $("#dropDown_0" ).val(LinkStatus.Numbering.description).change();
        if(selectedProjectLink[0].id !== 0)
          rootElement.find('.changeDirectionDiv').prop("hidden", false);
      }
      $('#discontinuityDropdown').val(selectedProjectLink[selectedProjectLink.length - 1].discontinuity);
      $('#roadTypeDropDown').val(selectedProjectLink[0].roadTypeId);
    };

    var disableFormInputs = function () {
      if (!isProjectEditable()) {
        $('#roadAddressProjectForm select').prop('disabled',true);
        $('#roadAddressProjectFormCut select').prop('disabled',true);
        $('.update').prop('disabled', true);
        $('.btn-edit-project').prop('disabled', true);
      }
    };

    var bindEvents = function() {

      var rootElement = $('#feature-attributes');
      eventbus.on('projectLink:split', function(selected) {
        selectedProjectLink = _.filter(selected, function (sel) {
          return sel.roadLinkSource == LinkGeomSource.SuravageLinkInterface.value;
        });
        currentProject = projectCollection.getCurrentProject();
        formCommon.clearInformationContent();
        rootElement.html(selectedProjectLinkTemplate(currentProject.project, options, selectedProjectLink));
        formCommon.replaceAddressInfo(backend, selectedProjectLink);
        formCommon.checkInputs('.split-');
        formCommon.toggleAdditionalControls();
        changeDropDownValue(selectedProjectLink[0].status);
        disableFormInputs();
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

      eventbus.on('roadAddress:projectSentSuccess', function() {
        new ModalConfirm("Muutosilmoitus lähetetty Tierekisteriin.");
        //TODO: make more generic layer change/refresh
        applicationModel.selectLayer('linkProperty');

        rootElement.empty();
        formCommon.clearInformationContent();

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

      eventbus.on('projectLink:projectLinksSplitSuccess', function () {
        eventbus.trigger('projectChangeTable:refresh');
        projectCollection.setTmpDirty([]);
        selectedProjectLinkProperty.cleanIds();
      });

      eventbus.on('roadAddress:changeDirectionFailed', function(error) {
        new ModalConfirm(error);
      });

      rootElement.on('click','.revertSplit', function () {
          projectCollection.removeProjectLinkSplit(projectCollection.getCurrentProject().project, selectedProjectLink);
      });

      eventbus.on('roadAddress:projectLinksSaveFailed', function (result) {
          if(applicationModel.getSelectedTool() == "Cut") {
              new ModalConfirm(result.toString());
          }
      });

      eventbus.on('roadAddressProject:discardChanges',function(){
        if(applicationModel.getSelectedTool() == "Cut") {
          cancelChanges();
        }
      });

      var saveChanges = function(){
        currentProject = projectCollection.getCurrentProject();
        //TODO revert dirtyness if others than ACTION_TERMINATE is choosen, because now after Lakkautus, the link(s) stay always in black color
        var statusDropdown_0 =$('#dropdown_0').val();
        var statusDropdown_1 = $('#dropdown_1').val();
        switch (statusDropdown_0){
          case LinkStatus.Unchanged.description : {
            if(!_.isUndefined(statusDropdown_1) && statusDropdown_1 == LinkStatus.New.description){
              projectCollection.saveCuttedProjectLinks(projectCollection.getTmpDirty(), LinkStatus.Unchanged.value, LinkStatus.New.value);
            }
            else if(_.isUndefined(statusDropdown_1)){
              projectCollection.saveProjectLinks(projectCollection.getTmpDirty(), LinkStatus.Unchanged.value);
            }
            break;
          }
          case LinkStatus.New.description : {
            if(!_.isUndefined(statusDropdown_1) && statusDropdown_1 == LinkStatus.Unchanged.description){
              projectCollection.saveCuttedProjectLinks(projectCollection.getTmpDirty(), LinkStatus.New.value, LinkStatus.Unchanged.value);
            }

            else if(!_.isUndefined(statusDropdown_1) && statusDropdown_1 == LinkStatus.Transfer.description){
              projectCollection.saveCuttedProjectLinks(projectCollection.getTmpDirty(), LinkStatus.New.value, LinkStatus.Transfer.value);
            }
            else if(_.isUndefined(statusDropdown_1)) {
              projectCollection.saveProjectLinks(projectCollection.getTmpDirty(), LinkStatus.New.value);
            }
            break;
          }
          case LinkStatus.Transfer.description : {
            if(!_.isUndefined(statusDropdown_1) && statusDropdown_1 == LinkStatus.New.description){
              projectCollection.saveCuttedProjectLinks(projectCollection.getTmpDirty(), LinkStatus.Unchanged.value, LinkStatus.New.value);
            }
            else if(_.isUndefined(statusDropdown_1)){
              projectCollection.saveProjectLinks(projectCollection.getTmpDirty(), LinkStatus.Transfer.value);
            }
            break;
          }
          case LinkStatus.Numbering.description : {
            projectCollection.saveProjectLinks(projectCollection.getTmpDirty(), LinkStatus.Numbering.value); break;
          }
          case LinkStatus.Terminated.description: {
            projectCollection.saveProjectLinks(projectCollection.getTmpDirty(), LinkStatus.Terminated.value); break;
          }
          case LinkStatus.Revert.description : {
            var separated = _.partition(selectedProjectLink, function (link) {
              return !_.isUndefined(link.connectedLinkId);
            });
            if (separated[0].length > 0) {
              projectCollection.revertChangesRoadlink(separated[0]);
            }
            if (separated[1].length > 0) {
              projectCollection.removeProjectLinkSplit(separated[1]);
            }
            break;
          }
        }
        selectedProjectLinkProperty.setDirty(false);
        rootElement.html(emptyTemplate(currentProject.project));
        formCommon.toggleAdditionalControls();
      };

      var cancelChanges = function() {
        selectedProjectLinkProperty.setDirty(false);
        if(projectCollection.isDirty()) {
          projectCollection.revertLinkStatus();
          projectCollection.setDirty([]);
          projectCollection.setTmpDirty([]);
          projectLinkLayer.clearHighlights();
          $('.wrapper').remove();
          eventbus.trigger('roadAddress:projectLinksEdited');
          eventbus.trigger('roadAddressProject:toggleEditingRoad', true);
          eventbus.trigger('roadAddressProject:reOpenCurrent');
        } else {
          eventbus.trigger('roadAddress:openProject', projectCollection.getCurrentProject());
          eventbus.trigger('roadLinks:refreshView');
        }
      };

      rootElement.on('click', '.split-form button.update', function() {
        eventbus.trigger('roadAddressProject:toggleEditingRoad', true);
        saveChanges();
      });

      rootElement.on('change', '#roadAddressProjectFormCut #dropdown_0, #roadAddressProjectFormCut #dropdown_1', function(){
        if (this.value == LinkStatus.New.description) {
          $('#drop_0_'+ LinkStatus.Revert.description).prop('disabled',true).prop('hidden',true);
          $('#drop_0_'+ LinkStatus.Transfer.description).prop('disabled',false).prop('hidden',false);
          $('#drop_0_'+ LinkStatus.Unchanged.description).prop('disabled',false).prop('hidden',false);
          $('#drop_0_'+ LinkStatus.New.description).prop('disabled',false).prop('hidden',true);
          $('#drop_1_'+ LinkStatus.New.description).prop('disabled',false).prop('hidden',true);
          $('#drop_1_'+ LinkStatus.Unchanged.description).prop('disabled',false).prop('hidden',false);
          $('#drop_1_'+ LinkStatus.Transfer.description).prop('disabled',false).prop('hidden',false);
          $('#drop_1_'+ LinkStatus.Revert.description).prop('disabled',true).prop('hidden',true);

        } else if ((this.value == LinkStatus.Unchanged.description )) {
          $('#drop_0_'+ LinkStatus.Revert.description).prop('disabled',true).prop('hidden',true);
          $('#drop_0_'+ LinkStatus.Transfer.description).prop('disabled',false).prop('hidden',false);
          $('#drop_0_'+ LinkStatus.Unchanged.description).prop('disabled',false).prop('hidden',true);
          $('#drop_0_'+ LinkStatus.New.description).prop('disabled',false).prop('hidden',false);
          $('#drop_1_'+ LinkStatus.New.description).prop('disabled',false).prop('hidden',false);
          $('#drop_1_'+ LinkStatus.Unchanged.description).prop('disabled',false).prop('hidden',true);
          $('#drop_1_'+ LinkStatus.Transfer.description).prop('disabled',false).prop('hidden',false);
          $('#drop_1_'+ LinkStatus.Revert.description).prop('disabled',true).prop('hidden',true);
        }
        else if ((this.value == LinkStatus.Transfer.description )) {
          $('#drop_0_' + LinkStatus.Revert.description).prop('disabled', true).prop('hidden', true);
          $('#drop_0_' + LinkStatus.Transfer.description).prop('disabled', false).prop('hidden', true);
          $('#drop_0_' + LinkStatus.Unchanged.description).prop('disabled', false).prop('hidden', true);
          $('#drop_0_' + LinkStatus.New.description).prop('disabled', false).prop('hidden', false);
          $('#drop_1_' + LinkStatus.New.description).prop('disabled', false).prop('hidden', false);
          $('#drop_1_' + LinkStatus.Unchanged.description).prop('disabled', false).prop('hidden', true);
          $('#drop_1_' + LinkStatus.Transfer.description).prop('disabled', false).prop('hidden', true);
          $('#drop_1_' + LinkStatus.Revert.description).prop('disabled', true).prop('hidden', true);
        }
      });

      rootElement.on('change', '#roadAddressProjectFormCut #dropdown_0, #roadAddressProjectFormCut #dropdown_1', function() {
        selectedProjectLinkProperty.setDirty(true);
        eventbus.trigger('roadAddressProject:toggleEditingRoad', false);
        $('#ajr').prop('disabled',false);
        $('#discontinuityDropdown').prop('disabled',false);
        $('#roadTypeDropDown').prop('disabled',false);

        if(this.value == LinkStatus.New.description){
          rootElement.find('.new-road-address').prop("hidden", false);
          projectCollection.setTmpDirty(_.filter(projectCollection.getTmpDirty(), function (l) { return l.status !== LinkStatus.Terminated.value;}).concat(selectedProjectLink));
        }
        if(this.value == LinkStatus.Unchanged.description){
          projectCollection.setDirty(projectCollection.getDirty().concat(_.map(selectedProjectLink, function (link) {
            return {'linkId': link.linkId, 'status': LinkStatus.Unchanged.value, 'roadLinkSource': link.roadLinkSource, 'points': link.points};
          })));
          projectCollection.setTmpDirty(projectCollection.getTmpDirty().concat(selectedProjectLink));
        }
        else if(this.value == LinkStatus.Transfer.description) {
          projectCollection.setDirty(_.filter(projectCollection.getDirty(), function(dirty) {return dirty.status === LinkStatus.Transfer.value;}).concat(_.map(selectedProjectLink, function (link) {
            return {'linkId': link.linkId, 'status': LinkStatus.Transfer.value, 'roadLinkSource': link.roadLinkSource, 'points': link.points};
          })));
          projectCollection.setTmpDirty(projectCollection.getDirty());
          rootElement.find('.new-road-address').prop("hidden", false);
        }
        else if(this.value == LinkStatus.Revert.description) {
          rootElement.find('.split-form button.update').prop("disabled", false);
        }
      });

      rootElement.on('change', '.split-form-group', function() {
        rootElement.find('.action-selected-field').prop("hidden", false);
      });

      rootElement.on('click', ' .split-form button.cancelLink', function(){
              cancelChanges();
      });

      rootElement.on('click', '.split-form button.send', function(){
        projectCollection.publishProject();
      });

      rootElement.on('click', '.split-form button.show-changes', function(){
        $(this).empty();
        projectChangeTable.show();
        var projectChangesButton = showProjectChangeButton();
        if(isProjectPublishable() && isProjectEditable()) {
          formCommon.setInformationContent();
          $('footer').html(formCommon.sendRoadAddressChangeButton('split-', projectCollection.getCurrentProject()));
        }
        else
          $('footer').html(projectChangesButton);
      });

      rootElement.on('keyup','.split-form-control.small-input', function () {
        formCommon.checkInputs('.split-');
      });

    };
    bindEvents();
  };
})(this);
