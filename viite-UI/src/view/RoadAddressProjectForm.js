(function (root) {
  root.RoadAddressProjectForm = function(projectCollection) {

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
        '</div>' + '</div>' + '</div>' + '</div>' +
        '<footer>' + buttons + '</footer>');
    };

    var openProjectTemplate = function(project) {
      return _.template('' +
        '<header>' +
        title() +
        headerButton +
        '</header>' +
        '<div class="wrapper read-only">'+
        '<div class="form form-horizontal form-dark">'+
        '<div class="edit-control-group choice-group">'+
        staticField('Lisätty järjestelmään', project.creator)+
        staticField('Muokattu viimeksi', project.modifiedAt)+
        '<div class="form-group editable form-editable-roadAddressProject"> '+

        '<form class="input-unit-combination form-group form-horizontal roadAddressProject">'+
        inputFieldRequired('*Nimi', 'nimi', '', project.name) +
        inputFieldRequired('*Alkupvm', 'alkupvm', 'pp.kk.vvvv', project.startDate)+
        largeInputField()+
        '<div class="form-group">' +
        addSmallLabel('TIE')+ addSmallLabel('AOSA')+ addSmallLabel('LOSA')+
        '</div>'+
        '<div class="form-group">' +
        addSmallInputNumber('tie', project.roadNumber)+ addSmallInputNumber('aosa', project.startPart)+ addSmallInputNumber('losa', project.endPart)+
        '</div>'+
        '</form>' +
        '</div>' + '</div>' + '</div>' + '</div>'+
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

      rootElement.on('click', '.project-form button.save', function() {
        var data = $('#roadAddressProject').get(0);
        projectCollection.createProject(data);
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
