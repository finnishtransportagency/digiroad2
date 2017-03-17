(function (root) {
  root.RoadAddressProjectForm = function(selectedProject) {
    var currentProject ;


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
      '<textarea class="form-control large-input roadAddressProject" id="lisatiedot" value="'+dataField+'" onclick=""/>'+
      '</div>';
    };

    var inputFieldRequired = function(labelText, id, placeholder,  value) {
      var field = '<div class="form-group">' +
      '<label class="control-label required">' + labelText + '</label>' +
        '<input type="text" class="form-control" id = "'+id+'" placeholder = "'+placeholder+'" value="'+value+'"/>' +
        '</div>';
      return field;
    };

    var title = function() {
      return '<span class ="edit-mode-title">Tieosoitemuutosprojekti</span>';
    };

    var titleWithProjectName = function(projectName) {
      return '<span class ="edit-mode-title">'+projectName+'</span>';
    };

    var buttons =
      '<div class="project-form form-controls">' +
      '<button class="next btn btn-next" disabled>Seuraava</button>' +
      '<button class="save btn btn-tallena">Tallenna</button>' +
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
        headerButton +
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
        addSmallLabel('TIE') + addSmallLabel('AOSA') + addSmallLabel('LOSA') +
        '</div>' +
        '<div class="form-group">' +
        addSmallInputNumber('tie') + addSmallInputNumber('aosa') + addSmallInputNumber('losa') +
        '</div>' +
        '</form>' +

        '</div>' +'<label >' + 'PROJEKTIIN VALITUT TIEOSAT:' + '</label>'+
        '</div>' +

        '</div>' + '</div>' +
        '<footer>' + buttons + '</footer>');
    };

    var openProjectTemplate = function(project, formInfo) {
      return _.template('' +
        '<header>' +
        titleWithProjectName(project.name) +
        headerButton +
        '</header>' +
        '<div class="wrapper read-only">'+
        '<div class="form form-horizontal form-dark">'+
        '<div class="edit-control-group choice-group">'+
        staticField('Lisätty järjestelmään', project.createdBy + ' ' + project.startDate.toString())+
        staticField('Muokattu viimeksi', project.modifiedBy + ' ' + project.dateModified.toString())+
        '<div class="form-group editable form-editable-roadAddressProject"> '+

        '<form class="input-unit-combination form-group form-horizontal roadAddressProject">'+
        inputFieldRequired('*Nimi', 'nimi', '', project.name) +
        inputFieldRequired('*Alkupvm', 'alkupvm', 'pp.kk.vvvv', project.startDate)+
        largeInputField(project.additionalInfo)+
        '<div class="form-group">' +
        addSmallLabel('TIE')+ addSmallLabel('AOSA')+ addSmallLabel('LOSA')+
        '</div>'+
        '<div class="form-group">' +
        addSmallInputNumber('tie', project.roadNumber)+ addSmallInputNumber('aosa', project.startPart)+ addSmallInputNumber('losa', project.endPart)+
        '</div>'+
        '</form>' +

        '</div>'+
        '</div>' +
        '<div class = "form-result">' +
          '<label >PROJEKTIIN VALITUT TIEOSAT:</label>'+
          '<div>' +
          addSmallLabel('TIE')+ addSmallLabel('OSA')+ addSmallLabel('PITUUS')+ addSmallLabel('JATKUU')+ addSmallLabel('ELY')+
          '</div>'+
          formInfo +
        '</div></div></div>'+
        '<footer>' + buttons + '</footer>');
    };


    var addSmallLabel = function(label){
      return '<label class="control-label-small">'+label+'</label>';
    };

    var addSmallInputNumber = function(id, value){
      //Validate only numebers characters on "onkeypress"
      return '<input type="text" onkeypress="return event.charCode >= 48 && event.charCode <= 57" class="form-control small-input roadAddressProject" id="'+id+'" value="'+(_.isUndefined(value)? '' : value )+'" onclick=""/>';
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

      eventbus.on('roadAddress:newProject', function() {
        rootElement.html(newProjectTemplate());
        jQuery('.modal-overlay').remove();
        addDatePicker();
      });


      eventbus.on('roadAddress:selected roadAddress:cancelled', function(roadAddress) {

      });

      eventbus.on('roadAddress:projectSaved', function (result) {
        currentProject = result.project;
        var text = '';
        _.each(result.formInfo, function(line){
          text += '<div>' +
          addSmallLabel(line.roadNumber)+ addSmallLabel(line.roadPartNumber)+ addSmallLabel(line.RoadLength)+ addSmallLabel(line.discontinuity)+ addSmallLabel(line.ely)+
          '</div>';
        });
        rootElement.html(openProjectTemplate(result.project, text));

        jQuery('.modal-overlay').remove();
        addDatePicker();
      });

      rootElement.on('click', '.project-form button.save', function() {
        var data = $('#roadAddressProject').get(0);
        var dataJson = {name : data[0].value, startDate: data[1].value , additionalInfo :  data[2].value, roadNumber : data[3].value === '' ? 0 : parseInt(data[3].value), startPart: data[4].value === '' ? 0 : parseInt(data[4].value), endPart : data[5].value === '' ? 0 : parseInt(data[5].value) };
        var backend = new Backend();
        applicationModel.addSpinner();
        backend.createProject(dataJson,
          function(result) {
          eventbus.trigger('roadAddress:projectSaved', result);
        }, function() {
          eventbus.trigger('roadaddress:projectFailed');
        });
      });
      
      rootElement.on('click', 'button.cancel', function(){
        rootElement.find('header').toggle();
        rootElement.find('.wrapper').toggle();
        rootElement.find('footer').toggle();
      });

    };
    bindEvents();
  };
})(this);
