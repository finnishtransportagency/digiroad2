(function (root) {
  root.ProjectForm = function (map, projectCollection, selectedProjectLinkProperty, projectLinkLayer) {
    var currentProject = false;
    var selectedProjectLink = false;
    var activeLayer = false;
    var hasReservedRoadParts = false;
    var ProjectStatus = LinkValues.ProjectStatus;
    var disabledInput = false;
    var editableStatus = [ProjectStatus.Incomplete.value, ProjectStatus.ErroredInTR.value, ProjectStatus.Unknown.value];
    var staticField = function (labelText, dataField) {
      var field;
      field = '<div class="form-group">' +
        '<p class="form-control-static asset-log-info">' + labelText + ' : ' + dataField + '</p>' +
        '</div>';
      return field;
    };

    var options = ['Valitse'];

    var largeInputField = function (dataField) {
      return '<div class="form-group">' +
        '<label class="control-label">LISÄTIEDOT</label>' +
        '<textarea class="form-control large-input roadAddressProject" id="lisatiedot" ' + (disabledInput ? 'disabled' : '') + '>' + (dataField === undefined || dataField === null ? "" : dataField ) + '</textarea>' +
        '</div>';
    };

    var inputFieldRequired = function (labelText, id, placeholder, value, maxLength) {
      var lengthLimit = '';
      if (maxLength)
        lengthLimit = 'maxlength="' + maxLength + '"';
      return '<div class="form-group input-required">' +
        '<label class="control-label required">' + labelText + '</label>' +
        '<input type="text" class="form-control" id = "' + id + '"' + lengthLimit + ' placeholder = "' + placeholder + '" value="' + value + '" ' + (disabledInput ? 'disabled' : '') + '/>' +
        '</div>';
    };

    var title = function () {
      return '<span class ="edit-mode-title">Uusi tieosoiteprojekti</span>';
    };

    var titleWithDeletingTool = function (projectName) {
      var deleteButtons = '<span id="deleteProjectSpan" class="deleteSpan">POISTA PROJEKTI  <button id="deleteProject_' + currentProject.id + '" ' +
        'class="btn-delete-project" value="' + currentProject.id + '"></button></span>';
      var toReturn = '<span class ="edit-mode-title">' + projectName + '</span>';
      if(currentProject.statusCode === ProjectStatus.Incomplete.value)
        return toReturn + deleteButtons;
      else
        return toReturn;
    };

    var titleWithEditingTool = function (projectName) {
      return '<span class ="edit-mode-title">'+projectName+'<button id="editProject_'+ currentProject.id +'" ' +
        'class="btn-edit-project" style="visibility:hidden;" value="' + currentProject.id + '"></button></span>' +
        '<span id="closeProjectSpan" class="rightSideSpan" style="visibility:hidden;">Poistu projektista</span>';
    };

    var actionButtons = function (ready) {
      var html = '<div class="project-form form-controls" id="actionButtons">' +
        '<button id="generalNext" class="save btn btn-save" style="width:auto;">Jatka Toimenpiteisiin</button>' +
        '<button id="saveAndCancelDialogue" class="cancel btn btn-cancel">Poistu</button>' +
        '</div>';
      return html;
    };

    var newProjectTemplate = function () {
      return _.template('' +
        '<header>' +
        title() +
        '</header>' +
        '<div class="wrapper read-only">' +
        '<div class="form form-horizontal form-dark">' +
        '<div class="edit-control-group project-choice-group">' +
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
        addSmallInputNumber('tie') + addSmallInputNumber('aosa') + addSmallInputNumber('losa') + addReserveButton() +
        '</div>' +
        '</form>' +
        ' </div>' +
        '</div>' + '<div class = "form-result">' + '<label >' + 'PROJEKTIIN VALITUT TIEOSAT:' + '</label>' +
        '<div>' +
        addSmallLabel('TIE') + addSmallLabel('OSA') + addSmallLabel('PITUUS') + addSmallLabel('JATKUU') + addSmallLabel('ELY') +
        '</div>' +
        '<div id ="roadpartList">' +
        '</div></div>' +
        '</div> </div>' +
        '<footer>' + actionButtons(false) + '</footer>');
    };

    var openProjectTemplate = function (project, reservedRoads, newReservedRoads) {
      return _.template('' +
        '<header>' +
        titleWithDeletingTool(project.name) +
        '</header>' +
        '<div class="wrapper read-only">' +
        '<div class="form form-horizontal form-dark">' +
        '<div class="edit-control-group project-choice-group">' +
        staticField('Lisätty järjestelmään', project.createdBy + ' ' + project.startDate) +
        staticField('Muokattu viimeksi', project.modifiedBy + ' ' + project.dateModified) +
        '<div class="form-group editable form-editable-roadAddressProject"> ' +
        '<form id="roadAddressProject" class="input-unit-combination form-group form-horizontal roadAddressProject">' +
        inputFieldRequired('*Nimi', 'nimi', '', project.name, 32) +
        inputFieldRequired('*Alkupvm', 'alkupvm', 'pp.kk.vvvv', project.startDate) +
        largeInputField(project.additionalInfo) +
        '<div class="form-group">' +
        '<label class="control-label"></label>' +
        addSmallLabel('TIE') + addSmallLabel('AOSA') + addSmallLabel('LOSA') +
        '</div>' +
        '<div class="form-group">' +
        '<label class="control-label">Tieosat</label>' +
        addSmallInputNumber('tie') + addSmallInputNumber('aosa') + addSmallInputNumber('losa') + addReserveButton() +
        '</div>' +
        '</form>' +
        '</div>' +
        '</div>' +
        '<div class = "form-result">' +
        '<label>PROJEKTIIN VALITUT TIEOSAT:</label>' +
        '<div style="margin-left: 16px;">' +
        addSmallLabel('TIE') + addSmallLabel('OSA') + addSmallLabel('PITUUS') + addSmallLabel('JATKUU') + addSmallLabel('ELY') +
        '</div>' +
        '<div id ="reservedRoads">' +
        reservedRoads +
        '</div></div></br></br>' +
        '<div class = "form-result">' +
        '<label>UUDET VARATUT TIEOSAT:</label>' +
        '<div style="margin-left: 16px;">' +
        addSmallLabel('TIE') + addSmallLabel('OSA') + addSmallLabel('PITUUS') + addSmallLabel('JATKUU') + addSmallLabel('ELY') +
        '</div>' +
        '<div id ="newReservedRoads">' +
        newReservedRoads +
        '</div></div></div></div>' +
        '<footer>' + actionButtons(reservedRoads !== '') + '</footer>');
    };

    var selectedProjectLinkTemplate = function (project) {
      return _.template('' +
        '<header>' +
        titleWithDeletingTool(project.name) +
        '</header>' +
        '<footer>' + showProjectChangeButton() + '</footer>');
    };

    var showProjectChangeButton = function () {
      return '<div class="project-form form-controls">' +
        '<button class="show-changes btn btn-block btn-show-changes">Avaa projektin yhteenvetotaulukko</button>' +
        '<button disabled id ="send-button" class="send btn btn-block btn-send">Tee tieosoitteenmuutosilmoitus</button></div>';
    };

    var addSmallLabel = function (label) {
      return '<label class="control-label-small">' + label + '</label>';
    };

    var addSmallLabelWithIds = function (label, id) {
      return '<label class="control-label-small" id=' + id + '>' + label + '</label>';
    };

    var addSmallInputNumber = function (id, value) {
      //Validate only number characters on "onkeypress" including TAB and backspace
      var smallNumberImput = '<input type="text" onkeypress="return (event.charCode >= 48 && event.charCode <= 57) || (event.keyCode == 8 || event.keyCode == 9)' +
        '" class="form-control small-input roadAddressProject" id="' + id + '" value="' + (_.isUndefined(value) ? '' : value ) + '" onclick="" ' + (disabledInput ? 'disabled' : '') + '/>';
      return smallNumberImput;
    };

    var addDatePicker = function () {
      var $validFrom = $('#alkupvm');
      dateutil.addSingleDependentDatePicker($validFrom);
    };

    var formIsInvalid = function (rootElement) {
      return !(rootElement.find('#nimi').val() && rootElement.find('#alkupvm').val() !== '');
    };

    var projDateEmpty = function (rootElement) {
      return !rootElement.find('#alkupvm').val();
    };

    var addReserveButton = function () {
      return '<button class="btn btn-reserve" disabled>Lisää</button>';
    };

    var bindEvents = function () {

      var rootElement = $('#feature-attributes');
      var toggleMode = function (readOnly) {
        rootElement.find('.wrapper read-only').toggle();
      };

      var removePart = function (roadNumber, roadPartNumber) {
        projectCollection.setDirtyRoadParts(projectCollection.deleteRoadPartFromList(projectCollection.getDirtyRoadParts(), roadNumber, roadPartNumber));
        projectCollection.setReservedDirtyRoadParts(projectCollection.deleteRoadPartFromList(projectCollection.getReservedDirtyRoadParts(), roadNumber, roadPartNumber));
        fillForm(projectCollection.getDirtyRoadParts(), projectCollection.getReservedDirtyRoadParts());
      };

      var writeHtmlList = function (list) {
        var text = '';
        var index = 0;
        _.each(list, function (line) {
          text += '<div style="display:inline-block;">' + projectCollection.getDeleteButton(index++, line.roadNumber, line.roadPartNumber) +
            addSmallLabel(line.roadNumber) +
            addSmallLabelWithIds(line.roadPartNumber, 'reservedRoadPartNumber') + addSmallLabelWithIds(line.roadLength, 'reservedRoadLength') + addSmallLabelWithIds(line.discontinuity, 'reservedDiscontinuity') + addSmallLabelWithIds(line.ely, 'reservedEly') +
            '</div>';
        });
        return text;
      };

      var toggleAditionalControls = function () {
        rootElement.find('header').replaceWith('<header>' +
        titleWithEditingTool(currentProject.name) +
        '</header>');
        $('[id^=editProject]').css('visibility', 'visible');
        $('#closeProjectSpan').css('visibility', 'visible');
      };

      var createOrSaveProject = function () {
        var data = $('#roadAddressProject').get(0);
        if (_.isUndefined(currentProject) || currentProject.id === 0) {
          projectCollection.setDirtyRoadParts(projectCollection.getReservedDirtyRoadParts());
          projectCollection.createProject(data, map.getView().getResolution());
        } else {
          projectCollection.saveProject(data, map.getView().getResolution());
        }
      };

      var deleteProject = function() {
        if(!_.isUndefined(currentProject) && currentProject.id !== 0){
          projectCollection.deleteProject(currentProject.id);
        }
      };

      var saveChanges = function () {
        applicationModel.addSpinner();
        eventbus.once('roadAddress:projectSaved', function (result) {
          hasReservedRoadParts = false;
          currentProject = result.project;
          currentProject.isDirty = false;
          disabledInput = !_.isUndefined(currentProject) && currentProject.statusCode === ProjectStatus.ErroredInTR.value;
          var text = '';
          var index = 0;
          projectCollection.setCurrentRoadPartList(result.formInfo);
          projectCollection.setReservedDirtyRoadParts([]);
          _.each(result.formInfo, function (line) {
            var button = projectCollection.getDeleteButton(index++, line.roadNumber, line.roadPartNumber);
            text += '<div class="form-reserved-roads-list">' + button +
              addSmallLabel(line.roadNumber) + addSmallLabel(line.roadPartNumber) + addSmallLabel(line.roadLength) + addSmallLabel(line.discontinuity) + addSmallLabel(line.ely) +
              '</div>';
          });
          rootElement.html(openProjectTemplate(currentProject, text, ''));

          jQuery('.modal-overlay').remove();
          addDatePicker();
          if (!_.isUndefined(result.projectAddresses)) {
            eventbus.trigger('linkProperties:selectedProject', result.projectAddresses.linkId, result.project);
          }
          selectedProjectLinkProperty.setDirty(false);
          eventbus.trigger('roadAddressProject:toggleEditingRoad', true);
        });
        createOrSaveProject();
      };

      var nextStage = function () {
        applicationModel.addSpinner();
        currentProject.isDirty = false;
        jQuery('.modal-overlay').remove();
        eventbus.trigger('roadAddressProject:openProject', currentProject);
        rootElement.html(selectedProjectLinkTemplate(currentProject));
        _.defer(function () {
          applicationModel.selectLayer('roadAddressProject');
          toggleAditionalControls();
        });
      };

      var createNewProject = function () {
        applicationModel.addSpinner();
        eventbus.once('roadAddress:projectSaved', function (result) {
          currentProject = result.project;
          currentProject.isDirty = false;
          disabledInput = !_.isUndefined(currentProject) && currentProject.statusCode === ProjectStatus.ErroredInTR.value;
          jQuery('.modal-overlay').remove();
          if (!_.isUndefined(result.projectAddresses)) {
            eventbus.trigger('linkProperties:selectedProject', result.projectAddresses, result.project);
          }
          eventbus.trigger('roadAddressProject:openProject', result.project);
          rootElement.html(selectedProjectLinkTemplate(currentProject));
          _.defer(function () {
            applicationModel.selectLayer('roadAddressProject');
            toggleAditionalControls();
            selectedProjectLinkProperty.setDirty(false);
            eventbus.trigger('roadAddressProject:toggleEditingRoad', true);
          });
        });
        createOrSaveProject();
      };

      var fillForm = function (currParts, newParts) {
        if (newParts.length === 0) {
          hasReservedRoadParts = false;
        }
        if (newParts.length === 0 && currParts.length === 0 && currentProject.id === 0) {
          rootElement.html(newProjectTemplate());
          addDatePicker();
        } else {
          rootElement.html(openProjectTemplate(currentProject, writeHtmlList(currParts), writeHtmlList(newParts)));
        }
        applicationModel.setProjectButton(true);
        applicationModel.setProjectFeature(currentProject.id);
        applicationModel.setOpenProject(true);
        activeLayer = true;
        rootElement.find('.btn-reserve').prop("disabled", false);
        rootElement.find('.btn-save').prop("disabled", false);
        rootElement.find('.btn-next').prop("disabled", false);
      };

      var disableFormInputs = function () {
        if (!isProjectEditable()) {
          $('#roadAddressProject input').prop('disabled', true);
          $('.btn-reserve').prop('disabled', true);
          $('.btn-delete').prop('hidden', true);
        }
      };

      eventbus.on('roadAddress:newProject', function () {
        currentProject = {
          id: 0,
          isDirty: false
        };
        disabledInput = !_.isUndefined(currentProject) && currentProject.statusCode === ProjectStatus.ErroredInTR.value;
        $("#roadAddressProject").html("");
        rootElement.html(newProjectTemplate());
        jQuery('.modal-overlay').remove();
        addDatePicker();
        applicationModel.setOpenProject(true);
        activeLayer = true;
        projectCollection.clearRoadAddressProjects();
        _.defer(function () {
          $('#generalNext').prop('disabled', true);
        });
      });

      eventbus.on('roadAddress:openProject', function (result) {
        currentProject = result.project;
        currentProject.isDirty = false;
        disabledInput = !_.isUndefined(currentProject) && currentProject.statusCode === ProjectStatus.ErroredInTR.value;
        projectCollection.clearRoadAddressProjects();
        projectCollection.setCurrentRoadPartList(result.projectLinks);
        var text = '';
        var index = 0;
        _.each(result.projectLinks, function (line) {
          var button = projectCollection.getDeleteButton(index++, line.roadNumber, line.roadPartNumber);
          text += '<div class="form-reserved-roads-list">' + button +
            addSmallLabel(line.roadNumber) +
            addSmallLabel(line.roadPartNumber) + addSmallLabel(line.roadLength) + addSmallLabel(line.discontinuity) + addSmallLabel(line.ely) +
            '</div>';
        });
        rootElement.html(openProjectTemplate(currentProject, text, ''));
        jQuery('.modal-overlay').remove();
        setTimeout(function () {
        }, 0);
        if (!_.isUndefined(currentProject))
          eventbus.trigger('linkProperties:selectedProject', result.linkId, result.project);
        applicationModel.setProjectButton(true);
        applicationModel.setProjectFeature(currentProject.id);
        applicationModel.setOpenProject(true);
        activeLayer = true;
        rootElement.find('.btn-reserve').prop("disabled", disabledInput);
        rootElement.find('.btn-next').prop("disabled", disabledInput);
        eventbus.trigger('roadAddressProject:clearTool');
        applicationModel.removeSpinner();
        disableFormInputs();
      });

      eventbus.on('roadAddress:projectValidationFailed', function (result) {
        new ModalConfirm(result.toString());
        applicationModel.removeSpinner();
      });

      eventbus.on('roadAddress:projectValidationSucceed', function () {
        rootElement.find('#generalNext').prop("disabled", formIsInvalid(rootElement));
        $('#saveEdit:disabled').prop('disabled', formIsInvalid(rootElement));
        currentProject.isDirty = true;
        hasReservedRoadParts = true;
      });

      eventbus.on('layer:selected', function (layer) {
        activeLayer = layer === 'linkPropertyLayer';
      });

      eventbus.on('roadAddress:projectFailed', function () {
        applicationModel.removeSpinner();
      });

      eventbus.on('roadAddressProject:reOpenCurrent', function () {
        reOpenCurrent();
      });

      rootElement.on('click', '[id^=editProject]', currentProject, function (eventObject) {
        var projectId = eventObject.currentTarget.value === "undefined" ? currentProject.id : eventObject.currentTarget.value;
        applicationModel.addSpinner();
        projectCollection.getProjectsWithLinksById(parseInt(projectId)).then(function (result) {
          rootElement.empty();
          setTimeout(function () {
          }, 0);
          eventbus.trigger('roadAddress:openProject', result);
          if (applicationModel.isReadOnly()) {
            $('.edit-mode-btn:visible').click();
          }
          _.defer(function () {
            loadEditbuttons();
          });
        });
      });

      var loadEditbuttons = function () {
        $('#activeButtons').empty();
        $('#actionButtons').html('<button id="saveEdit" class="save btn btn-save" disabled>Tallenna</button>' +
          '<button id="cancelEdit" class="cancel btn btn-cancel">Peruuta</button>');
        eventbus.trigger("roadAddressProject:clearAndDisableInteractions");
      };

      var saveAndNext = function () {
        saveChanges();
        eventbus.once('roadAddress:projectSaved', function () {
          selectedProjectLinkProperty.setDirty(false);
          nextStage();
        });
      };

      var isProjectEditable = function () {
        return _.isUndefined(projectCollection.getCurrentProject()) ||
          _.contains(editableStatus, projectCollection.getCurrentProject().project.statusCode);
      };

      rootElement.on('click', '#generalNext', function () {
        if (currentProject.statusCode === ProjectStatus.ErroredInTR.value) {
          currentProject.statusCode = ProjectStatus.Incomplete.value;
          currentProject.statusDescription = ProjectStatus.Incomplete.description;
          saveAndNext();
        } else if (currentProject.isDirty && !disabledInput) {
          if (currentProject.id === 0) {
            createNewProject();
          } else {
            saveAndNext();
          }
        } else {
          nextStage();
        }
        if (!isProjectEditable()) {
          $('.btn-edit-project').prop('disabled', true);
        }
      });

      var textFieldChangeHandler = function (eventData) {
        if (currentProject) {
          currentProject.isDirty = true;
        }
        var textIsNonEmpty = $('#nimi').val() !== "" && $('#alkupvm').val() !== "";
        var nextAreDisabled = $('#generalNext').is(':disabled') || $('#saveEdit').is(':disabled');
        var reservedRemoved = !_.isUndefined(eventData) && eventData.removedReserved;

        if ((textIsNonEmpty || reservedRemoved) && nextAreDisabled) {
          $('#generalNext').prop('disabled', false);
          $('#saveEdit:disabled').prop('disabled', false);
          currentProject.isDirty = true;
        }
      };

      rootElement.on('change', '#nimi', function () {
        textFieldChangeHandler();
      });
      rootElement.on('change', '#alkupvm', function () {
        textFieldChangeHandler();
      });
      rootElement.on('change', '#lisatiedot', function () {
        textFieldChangeHandler();
      });

      rootElement.on('click', '.btn-reserve', function () {
        var data;
        var lists = $('.roadAddressProject');
        if ($('#roadAddressProject').get(0) !== null) {
          data = $('#roadAddressProject').get(0);
        } else {
          data = $('#reservedRoads').get(0);
        }
        projectCollection.checkIfReserved(data);
        return false;
      });

      rootElement.on('click', '.btn-delete', function () {
        var id = this.id;
        var roadNumber = this.attributes.roadNumber.value;
        var roadPartNumber = this.attributes.roadPartNumber.value;
        if (!currentProject) {
          projectCollection.setReservedDirtyRoadParts(projectCollection.deleteRoadPartFromList(projectCollection.getReservedDirtyRoadParts(), roadNumber, roadPartNumber));
          $('#roadpartList').html(writeHtmlList(projectCollection.getReservedDirtyRoadParts()));
        } else if (projectCollection.getCurrentRoadPartList()[id] && projectCollection.getCurrentRoadPartList()[id].isDirty) {
          new GenericConfirmPopup('Haluatko varmasti poistaa tieosan varauksen ja \r\nsiihen mahdollisesti tehdyt tieosoitemuutokset?', {
            successCallback: function () {
              removePart(roadNumber, roadPartNumber);
              loadEditbuttons();
              _.defer(function () {
                textFieldChangeHandler({removedReserved: true});
              });
            }
          });
        }
        else {
          removePart(roadNumber, roadPartNumber);
        }
      });

      rootElement.on('change', '.form-group', function () {
        rootElement.find('.action-selected-field').prop("hidden", false);
      });


      var closeProjectMode = function (changeLayerMode, noSave) {
        eventbus.trigger("roadAddressProject:startAllInteractions");
        applicationModel.setOpenProject(false);
        rootElement.find('header').toggle();
        rootElement.find('.wrapper').toggle();
        rootElement.find('footer').toggle();
        projectCollection.clearRoadAddressProjects();
        eventbus.trigger('layer:enableButtons', true);
        if (changeLayerMode) {
          eventbus.trigger('roadAddressProject:clearOnClose');
          applicationModel.selectLayer('linkProperty', true, noSave);
        }
      };

      var displayCloseConfirmMessage = function (popupMessage, changeLayerMode) {
        var isDirty = !_.isUndefined(currentProject.isDirty) && currentProject.isDirty;
        new GenericConfirmPopup(popupMessage, {
          successCallback: function () {
            if (isDirty && !disabledInput) {
              createOrSaveProject();
              _.defer(function () {
                closeProjectMode(changeLayerMode);
              });
            } else {
              closeProjectMode(changeLayerMode);
            }
          },
          closeCallback: function () {
            closeProjectMode(changeLayerMode);
          }
        });
      };

      var displayDeleteConfirmMessage= function (popupMessage) {
        new GenericConfirmPopup(popupMessage, {
          successCallback: function () {
              deleteProject();
              closeProjectMode(true);
          },
          closeCallback: function () {
            closeProjectMode(true);
          }
        });
      };

      var cancelChanges = function() {
          projectCollection.revertLinkStatus();
          projectCollection.setDirty([]);
          projectCollection.setTmpDirty([]);
          projectLinkLayer.clearHighlights();
          $('.wrapper').remove();
          eventbus.trigger('roadAddress:projectLinksEdited');
          eventbus.trigger('roadAddressProject:toggleEditingRoad', true);
          eventbus.trigger('roadAddressProject:reOpenCurrent');
      };

      var reOpenCurrent = function () {
        rootElement.empty();
        selectedProjectLinkProperty.setDirty(false);
        nextStage();
        rootElement.html(selectedProjectLinkTemplate(currentProject));
        toggleAditionalControls();
        eventbus.trigger('roadAddressProject:enableInteractions');
      };

      rootElement.on('click', '#saveEdit', function () {
        saveAndNext();
        eventbus.trigger('roadAddressProject:enableInteractions');
      });

      rootElement.on('click', '#cancelEdit', function () {
        new GenericConfirmPopup('Haluatko tallentaa tekemäsi muutokset?', {
          successCallback: function () {

            if (!disabledInput) {
              saveAndNext();
            } else {
              reOpenCurrent();
            }
            eventbus.trigger('roadAddressProject:enableInteractions');
          },
          closeCallback: function () {
            cancelChanges();
          }
        });

      });

      rootElement.on('click', '#saveAndCancelDialogue', function (eventData) {
        var defaultPopupMessage = 'Haluatko tallentaa tekemäsi muutokset?';
        displayCloseConfirmMessage(defaultPopupMessage, true);
      });
      rootElement.on('click', '#closeProjectSpan', function () {
        displayCloseConfirmMessage("Haluatko tallentaa tekemäsi muutokset?", true);
      });

      rootElement.on('click', '#deleteProjectSpan', function(){
        //Insert the correct message for delete confirmation here!
        displayDeleteConfirmMessage("Haluatko varmasti poistaa tämän projektin?", true);
      });

      rootElement.on('change', '.input-required', function () {
        rootElement.find('.project-form button.next').attr('disabled', formIsInvalid(rootElement));
        rootElement.find('.project-form button.save').attr('disabled', formIsInvalid(rootElement));
        rootElement.find('#roadAddressProject button.btn-reserve').attr('disabled', projDateEmpty(rootElement));
      });

    };
    bindEvents();
  };
})(this);
