(function (root) {
  root.SplitForm = function(projectCollection, selectedProjectLinkProperty, projectLinkLayer, projectChangeTable, backend) {
    var LinkStatus = LinkValues.LinkStatus;
    var LinkGeomSource = LinkValues.LinkGeomSource;
    var SideCode = LinkValues.SideCode;
    var editableStatus = [LinkValues.ProjectStatus.Incomplete.value, LinkValues.ProjectStatus.ErroredInTR.value, LinkValues.ProjectStatus.Unknown.value];

    var currentProject = false;
    var currentSplitData = false;
    var selectedProjectLink = false;
    var formCommon = new FormCommon('split-');

    var showProjectChangeButton = function() {
      return '<div class="split-form form-controls">' +
        formCommon.projectButtons() + '</div>';
    };

    var revertSplitButton = function () {
      return '<div class="form-group" style="margin-top:15px">' +
        '<button id="revertSplit" class="form-group revertSplit btn btn-primary">Palauta aihioksi</button>' +
        '</div>';
    };

    var selectedSplitData = function (selected, road) {
      var span = [];
      _.each(selected, function (sel) {
        if (sel) {
          var link = sel;
          var div = '<div class="project-edit-selections" style="display:inline-block;padding-left:8px;">' +
            '<div class="project-edit">' +
            ' TIE ' + '<span class="project-edit">' + road.roadNumber + '</span>' +
            ' OSA ' + '<span class="project-edit">' + road.roadPartNumber + '</span>' +
            ' AJR ' + '<span class="project-edit">' + road.trackCode + '</span>' +
            ' M:  ' + '<span class="project-edit">' + link.startAddressM + ' - ' + link.endAddressM + '</span>' +
            '</div>' +
            '</div>';
          span.push(div);
        }
      });
      return span;
    };

    var defineOptionModifiers = function (option, selection) {
      var isReSplitMode = !_.isUndefined(selection.connectedLinkId);
      var linkStatus = selection.status;
      var targetLinkStatus = _.find(LinkStatus, function (ls) {
        return ls.description === option || (option === '' && ls.value == 99);
      });
      if (isReSplitMode)
        return splitTransitionModifiers(targetLinkStatus, linkStatus);
      else
        return transitionModifiers(targetLinkStatus, linkStatus);
    };

    var transitionModifiers = function (targetStatus, currentStatus) {
      var mod;
      if (targetStatus.value === currentStatus)
        mod = ' selected';
      else
        mod = 'disabled hidden';
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

    var selectedProjectLinkTemplate = function(project, selected) {
      currentSplitData = selectedProjectLinkProperty.getPreSplitData();
      var selection = selectedSplitData(selected, currentSplitData);
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
        selectionFormCutted(selection, selected, currentSplitData)+
        ((selected.length == 2 && selected[0].linkId === selected[1].linkId) ? '' : formCommon.changeDirection(selected)) +
        formCommon.actionSelectedField()+
        ((!_.isUndefined(selected[0].connectedLinkId)) ? revertSplitButton(): '') +
        '</div>'+
        '</div>' +
        '</div>'+
        '</div>'+
        '<footer>' + formCommon.actionButtons('split-', selectedProjectLinkProperty.isDirty()) + '</footer>');
    };

    var getSplitPointBySideCode = function (link) {
      if (link.sideCode == SideCode.AgainstDigitizing.value) {
        return _.first(link.points);
      } else {
        return _.last(link.points);
      }
    };
    var selectionFormCutted = function (selection, selected, road) {
      var firstLink = _.first(_.sortBy(_.filter(selected, function (s) {return s.endMValue !== 0; }), 'startAddressM'));
      var splitPoint = ((applicationModel.getSelectedTool() != "Cut" ? getSplitPointBySideCode(firstLink) : firstLink.splitPoint));

      return '<form id="roadAddressProjectFormCut" class="input-unit-combination split-form-group form-horizontal roadAddressProject">'+
        '<input type="hidden" id="splitx" value="' + splitPoint.x + '"/>' +
        '<input type="hidden" id="splity" value="' + splitPoint.y + '"/>' +
        '<label>SUUMMITELMALINKKI</label>' + '<span class="marker">'+selected[0].marker+'</span>'+
      '<br>'+'<label>Toimenpiteet,' + selection[0] + '</label>' +
        dropdownOption(0) +
        '<hr class="horizontal-line"/>' +
        secondPartForm(selection[1], selected) +
        formCommon.newRoadAddressInfo(selected, selectedProjectLink[0], road) +
        '<hr class="horizontal-line"/>' +
        thirdPartForm(selection[2], selected) +
        '</form>';
    };

    var secondPartForm = function(selection, selected){
      if (selected[1].endAddressM !== selected[1].startAddressM) {
        return '<label>SUUMMITELMALINKKI</label>'+ '<span class="marker">'+selected[1].marker+'</span>' +
        '<br>'+'<label>Toimenpiteet,' + selection  + '</label>' +
          dropdownOption(1);
      } else return '';
    };

    var thirdPartForm = function(selection, selected){
      if (selected[2].endAddressM !== selected[2].startAddressM) {
      return '<label>NYKYLINKKI</label>'+ '<span class="marker">'+selected[2].marker+'</span>' +
        '<br>'+'<label>Toimenpiteet,' + selection  + '</label>' +
          dropdownOption(2);
      } else return '';
    };

    var dropdownOption = function (index) {
      return '<div class="input-unit-combination">' +
        '<select class="split-form-control" id="dropdown_' + index + '" hidden size="1">' +
        '<option id="drop_' + index + '_' + LinkStatus.Unchanged.description + '" value="' + LinkStatus.Unchanged.description + '"  hidden>Ennallaan</option>' +
        '<option id="drop_' + index + '_' + LinkStatus.Transfer.description + '" value="' + LinkStatus.Transfer.description + '" hidden>Siirto</option>' +
        '<option id="drop_' + index + '_' + LinkStatus.New.description + '" value="' + LinkStatus.New.description + '" hidden>Uusi</option>' +
        '<option id="drop_' + index + '_' + LinkStatus.Terminated.description + '" value="' + LinkStatus.Terminated.description + '" hidden> Lakkautus</option>' +
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
        $("#dropDown_0 option[value="+ LinkStatus.Transfer.description +"]").prop('disabled',false).prop('hidden', false);
        $("#dropDown_0 option[value="+ LinkStatus.Unchanged.description +"]").attr('selected', 'selected').change();
      }
      else if (statusCode == LinkStatus.Transfer.value) {
        $("#dropDown_0 option[value="+ LinkStatus.Unchanged.description +"]").prop('disabled',false).prop('hidden', false);
        $("#dropDown_0 option[value="+ LinkStatus.Transfer.description +"]").attr('selected', 'selected').change();
        if(selectedProjectLink[0].id !== 0)
          rootElement.find('.changeDirectionDiv').prop("hidden", false);
      }
      else if (statusCode == LinkStatus.New.value) {
        $("#dropDown_1 option[value="+ LinkStatus.New.description +"]").prop('disabled',false).prop('hidden', false);
        $("#dropDown_1 option[value="+ LinkStatus.New.description +"]").attr('selected', 'selected').change();
      }
      else if (statusCode == LinkStatus.Terminated.value) {
        $("#dropDown_2 option[value="+ LinkStatus.Terminated.description +"]").prop('disabled',false).prop('hidden', false);
        $("#dropDown_2 option[value="+ LinkStatus.Terminated.description +"]").attr('selected', 'selected').change();
      }
      $('#discontinuityDropdown').val(selectedProjectLink[0].discontinuity);
      $('#roadTypeDropDown').val(selectedProjectLink[0].roadType);
    };

    var disableFormInputs = function () {
      if (!isProjectEditable()) {
        $('#roadAddressProjectForm select').prop('disabled',true);
        $('#roadAddressProjectFormCut select').prop('disabled',true);
        $('.btn-edit-project').prop('disabled', true);
      }
    };

    var bindEvents = function() {
      var rootElement = $('#feature-attributes');
      eventbus.on('projectLink:split', function(selected) {
        selectedProjectLink = selected;
        currentProject = projectCollection.getCurrentProject();
        formCommon.clearInformationContent();
        rootElement.html(selectedProjectLinkTemplate(currentProject.project, selectedProjectLink));
        formCommon.replaceAddressInfo(backend, selectedProjectLink);
        formCommon.checkInputs('.split-');
        formCommon.toggleAdditionalControls();
        _.each(selectedProjectLink, function(link){
          changeDropDownValue(link.status);
        });
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
            projectCollection.saveCuttedProjectLinks(projectCollection.getTmpDirty(), LinkStatus.Unchanged.value, LinkStatus.New.value);
            break;
          }
          case LinkStatus.New.description : {
            if(statusDropdown_1 == LinkStatus.Unchanged.description){
              projectCollection.saveCuttedProjectLinks(projectCollection.getTmpDirty(), LinkStatus.New.value, LinkStatus.Unchanged.value);
            }
            else if(_.isUndefined(statusDropdown_1) || statusDropdown_1 == LinkStatus.Transfer.description){
              projectCollection.saveCuttedProjectLinks(projectCollection.getTmpDirty(), LinkStatus.New.value, LinkStatus.Transfer.value);
            }
            break;
          }
          case LinkStatus.Transfer.description : {
            projectCollection.saveCuttedProjectLinks(projectCollection.getTmpDirty(), LinkStatus.Transfer.value, LinkStatus.New.value);
            break;
          }
          case LinkStatus.Revert.description : {
            var separated = _.partition(selectedProjectLink, function (link) {
              return !_.isUndefined(link.connectedLinkId);
            });
            if (separated[0].length > 0) {
              projectCollection.revertChangesRoadlink(separated[0]);
            }
            if (separated[1].length > 0) {
              selectedProjectLinkProperty.revertSuravage();
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

      rootElement.on('change', '#roadAddressProjectFormCut #dropdown_0', function() {
        selectedProjectLinkProperty.setDirty(true);
        eventbus.trigger('roadAddressProject:toggleEditingRoad', false);
        var disabled = false;
        if(this.value == LinkStatus.Unchanged.description){
          projectCollection.setDirty(projectCollection.getDirty().concat(_.map(selectedProjectLink, function (link) {
            return {'linkId': link.linkId, 'status': LinkStatus.Unchanged.value, 'roadLinkSource': link.roadLinkSource, 'points': link.points};
          })));
          projectCollection.setTmpDirty(projectCollection.getTmpDirty().concat(selectedProjectLink));
          $("#dropDown_0 option[value="+ LinkStatus.Transfer.description +"]").prop('disabled',false).prop('hidden', false);
          disabled = true;
          $('#tie').val(currentSplitData.roadNumber);
          $('#osa').val(currentSplitData.roadPartNumber);
        }
        else if(this.value == LinkStatus.Transfer.description) {
          projectCollection.setDirty(_.filter(projectCollection.getDirty(), function(dirty) {return dirty.status === LinkStatus.Transfer.value;}).concat(_.map(selectedProjectLink, function (link) {
            return {'linkId': link.linkId, 'status': LinkStatus.Transfer.value, 'roadLinkSource': link.roadLinkSource, 'points': link.points};
          })));
          projectCollection.setTmpDirty(projectCollection.getDirty());
          rootElement.find('.new-road-address').prop("hidden", false);
          $("#dropDown_0 option[value="+ LinkStatus.Unchanged.description +"]").prop('disabled',false).prop('hidden', false);
          disabled = false;
        }
        $('#tie').prop('disabled', disabled);
        $('#osa').prop('disabled', disabled);
        $('#ajr').prop('disabled', disabled);
        $('#discontinuityDropdown').prop('disabled',false);
        $('#roadTypeDropDown').prop('disabled',false);
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
