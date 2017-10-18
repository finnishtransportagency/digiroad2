(function (root) {
  root.ProjectForm = function(projectCollection, selectedProjectLink) {

    var LinkStatus = projectCollection.getLinkStatus();
    var LinkGeomSource = LinkValues.LinkGeomSource;

    var addSmallInputNumber = function(id, value){
      //Validate only number characters on "onkeypress" including TAB and backspace
      return '<input type="text" onkeypress="return (event.charCode >= 48 && event.charCode <= 57) || (event.keyCode == 8 || event.keyCode == 9)' +
        '"class="form-control small-input roadAddressProject" id="'+id+'" value="'+(_.isUndefined(value)? '' : value )+'" onclick=""/>';
    };

    var addSmallInputNumberDisabled = function(id, value){
      return '<input type="text" class="form-control small-input roadAddressProject" id="'+id+'" value="'+(_.isUndefined(value)? '' : value )+'" readonly="readonly"/>';
    };

    var addSmallLabel = function(label){
      return '<label class="control-label-small">'+label+'</label>';
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

    var distanceValue = function() {
      return '<div id="distanceValue" hidden>' +
        '<div class="form-group" style="margin-top: 15px">' +
        '<img src="images/calibration-point.svg" style="margin-right: 5px" class="calibration-point"/>' +
        '<label class="control-label-small" style="display: inline">ETÄISYYSLUKEMA VALINNAN</label>' +
        '</div>' +
        '<div class="form-group">' +
        '<label class="control-label-small" style="float: left; margin-top: 10px">ALLUSSA</label>' +
        addSmallInputNumber('beginDistance', '--') +
        '<label class="control-label-small" style="float: left;margin-top: 10px">LOPUSSA</label>' +
        addSmallInputNumber('endDistance', '--') +
        '</div></div>';
    };

    var titleWithProjectName = function(projectName) {
      return '<span class ="edit-mode-title">'+projectName+'<button id="editProject_'+ currentProject.id +'" ' +
        'class="btn-edit-project" style="visibility:hidden;" value="' + currentProject.id + '"></button></span>' +
        '<span id="closeProjectSpan" class="rightSideSpan" style="visibility:hidden;">Poistu projektista</span>';
    };

    var staticField = function(labelText, dataField) {
      var field;
      field = '<div class="form-group">' +
        '<p class="form-control-static asset-log-info">' + labelText + ' : ' + dataField + '</p>' +
        '</div>';
      return field;
    };

    var defineOptionModifiers = function(option, selection) {
      var roadIsUnknownOrOther = projectCollection.roadIsUnknown(selection[0]) || projectCollection.roadIsOther(selection[0]) || selection[0].roadLinkSource === LinkGeomSource.SuravageLinkInterface.value;
      var toEdit = selection[0].id === 0;
      var modifiers = '';

      switch(option) {
        case LinkStatus.Unchanged.action: {
          if(roadIsUnknownOrOther){
            modifiers = 'disabled hidden';
          } else {
            modifiers = '';
          }
          break;
        }
        case LinkStatus.Transfer.action: {
          if(roadIsUnknownOrOther){
            modifiers = 'disabled hidden';
          } else if(toEdit){
            modifiers = 'disabled';
          }
          break;
        }
        case LinkStatus.New.action: {
          var enableStatusNew = (selection[0].status !== LinkStatus.NotHandled.value && selection[0].status !== LinkStatus.Terminated.value)|| selection[0].roadLinkSource === LinkGeomSource.SuravageLinkInterface.value;
          if(!roadIsUnknownOrOther) {
            if(!enableStatusNew)
              modifiers = 'disabled';
          }
          break;
        }
        case LinkStatus.Terminated.action: {
          if(roadIsUnknownOrOther){
            modifiers = 'disabled hidden';
          } else {
            var status = _.uniq(_.map(selection, function(l) { return l.status; }));
            if (status.length == 1)
              status = status[0];
            else
              status = 0;
            if (status === LinkStatus.Terminated.value){
              modifiers = 'selected';
            } else if(selection[0].roadLinkSource === LinkGeomSource.SuravageLinkInterface.value) {
              modifiers = 'disabled';
            }
          }
          break;
        }
        case LinkStatus.Numbering.action: {
          if(roadIsUnknownOrOther){
            modifiers = 'disabled hidden';
          } else if(toEdit) {
            modifiers = 'disabled';
          } else if(selection[0].status === LinkStatus.Terminated.value) {
            modifiers = 'hidden';
          }
          break;
        }
        case LinkStatus.Revert.action: {
          if(roadIsUnknownOrOther){
            modifiers = 'disabled hidden';
          } else if(toEdit) {
            modifiers = 'disabled';
          }
          break;
        }
        default: {
          modifiers = 'selected disabled hidden';
        }
      }
      return modifiers;
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
        distanceValue() +
        '</div>';
    };

    var changeDirection = function () {
      return '<div hidden class="form-group changeDirectionDiv" style="margin-top:15px">' +
        '<button class="form-group changeDirection btn btn-primary">Käännä kasvusuunta</button>' +
        '</div>';
    };

    var actionSelectedField = function() {
      //TODO: cancel and save buttons Viite-374
      var field;
      field = '<div class="form-group action-selected-field" hidden = "true">' +
        '<div class="asset-log-info">' + 'Tarkista tekemäsi muutokset.' + '<br>' + 'Jos muutokset ok, tallenna.' + '</div>' +
        '</div>';
      return field;
    };

    var actionButtons = function() {
      var html = '<div class="project-form form-controls" id="actionButtons">' +
        '<button class="update btn btn-save"' + (projectCollection.isDirty() ? '' : 'disabled') + ' style="width:auto;">Jatka Toimenpiteisiin</button>' +
        '<button class="cancelLink btn btn-cancel">Peruuta</button>' +
        '</div>';
      return html;
    };

    this.selectedProjectLinkTemplate = function(project, action) {
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
        '<label>Toimenpiteet,' + selectedProjectLink  + '</label>' +
        '<div class="input-unit-combination">' +
        '<select class="form-control" id="dropDown" size="1">'+
        '<option '+ defineOptionModifiers('', selected) +'>Valitse</option>'+
        '<option value='+ LinkStatus.Unchanged.action+' ' + defineOptionModifiers(LinkStatus.Unchanged.action, selected) + '>Ennallaan</option>'+
        '<option value='+ LinkStatus.Transfer.action + ' ' + defineOptionModifiers(LinkStatus.Transfer.action, selected) + '>Siirto</option>'+
        '<option value='+ LinkStatus.New.action + ' ' + defineOptionModifiers(LinkStatus.New.action, selected) +'>Uusi</option>'+
        '<option value='+ LinkStatus.Terminated.action + ' ' + defineOptionModifiers(LinkStatus.Terminated.action, selected) + '>Lakkautus</option>'+
        '<option value='+ LinkStatus.Numbering.action + ' ' + defineOptionModifiers(LinkStatus.Numbering.action, selected) + '>Numerointi</option>'+
        '<option value='+ LinkStatus.Revert.action + ' ' + defineOptionModifiers(LinkStatus.Revert.action, selected) + '>Palautus aihioksi tai tieosoitteettomaksi</option>' +
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
  };
}(this));