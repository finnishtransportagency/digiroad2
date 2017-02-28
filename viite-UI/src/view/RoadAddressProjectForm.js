(function (root) {
  root.RoadAddressProjectForm = function(selectedProject) {


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

    var staticField = function(labelText, fieldType, dataField) {
      var field;
      field = '<div class="form-group">' +
        '<label class="control-label">' + labelText + '</label>' +
        '<p class="form-control-static"><%- ' + dataField + ' %></p>' +
        '</div>';
      return field;
    };


    var inputField = function(labelText) {
      var field = '<div class="form-group">' +
      '<label class="control-label">' + labelText + '</label>' +
        '<p input type="text" class="form-control-static"></p>' +
        '</div>';
      return field;
    };

    var title = function() {
      return '<span class ="edit-mode-title">Tieosoitemuutosprojekti</span>';
    };

    var buttons =
      '<div class="link-properties form-controls">' +
      '<button class="next btn btn-next" disabled>Seuraava</button>' +
      '<button class="save btn btn-tallena" disabled>Tallenna</button>' +
      '<button class="cancel btn btn-perruta" disabled>Peruuta</button>' +
      '</div>';

    var newProjectTemplate = function() {
      return _.template('' +
        '<header>' +
        title() +
        '<div class="linear-asset form-controls">'+
        '<button class="cancel btn btn-secondary">Sulje projekti</button>'+
        '</div>'+
        '</header>' +
        '<div class="wrapper read-only">'+
        '<div class="form form-horizontal form-dark linear-asset">'+
        '<div class="edit-control-group choice-group">'+
          '<div class="form-group editable form-editable-roadAddressProject"> '+

        '<form class="input-unit-combination form-group form-horizontal roadAddressProject">'+
        '<div class="form-group">' +
        '<label class="control-label required">*Nimi</label>'+
              '<input type="text" class="form-control roadAddressProject" id="nimi" value="" onclick=""/>'+
        '</div>'+
              '<div class="form-group">' +
              '<p class="form-control-static asset-log-info">Lisätty järjestelmään: -</p>' +
              '</div>'+
              '<div class="form-group">' +
              '<p class="form-control-static asset-log-info">Muokattu viimeksi: -</p>' +
              '</div>'+
        '<div class="form-group">' +
              '<label class="control-label required">*Alkupvm</label>'+
              '<input type="text" class="form-control" id="alkupvm" placeholder="pp.kk.vvvv">'+
        '</div>'+
        '<div class="form-group">' +
              '<label class="control-label">LISÄTIEDOT</label>'+
              '<textarea class="form-control large-input roadAddressProject" id="lisatiedot" value="" onclick=""/>'+
        '</div>'+

        '<div class="form-group">' +
        '<label class="control-label-small">TIE</label>'+
        '<label class="control-label-small">AOSA</label>'+
        '<label class="control-label-small">LOSA</label>'+
        '</div>'+
        '<div class="form-group">' +
        '<input type="text" class="form-control small-input roadAddressProject" id="tie" value="" onclick=""/>'+
        '<input type="text" class="form-control small-input roadAddressProject" id="aosa" value="" onclick=""/>'+
        '<input type="text" class="form-control small-input roadAddressProject" id="losa" value="" onclick=""/>'+
        '</div>'+
        '</form>' +
        '</div></div></div></div>'+
        '<footer>' + buttons + '</footer>');


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

      eventbus.on('roadAddress:newProject', function(linkProperties) {
        rootElement.html(newProjectTemplate());
        addDatePicker();
      });


      eventbus.on('linkProperties:selected linkProperties:cancelled', function(linkProperties) {
        // rootElement.html(newProjectTemplate(selectedProject.get()[0]));
      });

      rootElement.on('click', '.link-properties button.save', function() {
        //TODO send of getRoadAddressProject() to the backend-utils;
        //for now, for 253 implementation, is go directly to back-utils instead of roadAddressProject controller
        var data = {'name': "project1"};

        backend.createProject(data, function() {
          eventbus.trigger('roadaddress:projectSaved');
        }, function() {
          eventbus.trigger('roadaddress:projectFailed');
        });
      });
      
      rootElement.on('click', 'button.cancel', function(){
        jQuery('.modal-overlay').remove();
        rootElement.find('header').toggle();
        rootElement.find('.wrapper').toggle();
        rootElement.find('footer').toggle();
      });

    };
    bindEvents();
  };
})(this);
