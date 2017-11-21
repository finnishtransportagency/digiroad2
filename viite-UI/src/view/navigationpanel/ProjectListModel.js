(function (root) {
  root.ProjectListModel = function (projectCollection) {
    var projectStatus = LinkValues.ProjectStatus;
    var projectList = $('<div id="project-window" class="form-horizontal project-list"></div>').hide();
    projectList.append('<button class="close btn-close">x</button>');
    projectList.append('<div class="content">Tieosoiteprojektit</div>');
    projectList.append('<div class="content-new">' +
      '<label class="content-new label">PROJEKTIN NIMI</label>' +
      '<label class="content-new label" style="width: 100px">ELY</label>' +
      '<label class="content-new label" style="width: 100px">KÄYTTÄJÄ</label>' +
      '<label class="content-new label" style="width: 100px">TILA</label>' +
      '<div class="actions">' +
    '<button class="new btn btn-primary" style="margin-top:-5px;">Uusi tieosoiteprojekti</button></div>' +
      '</div>');
    projectList.append('<div id="project-list" style="width:810px; height:400px; overflow:auto;"></div>');

    var staticFieldProjectName = function(dataField) {
      var field;
      field = '<div>' +
        '<label class="control-label-projects-list" style="width: 300px">' + dataField + '</label>' +
        '</div>';
      return field;
    };

    var staticFieldProjectList = function(dataField) {
      var field;
      field = '<div>' +
        '<label class="control-label-projects-list">' + dataField + '</label>' +
        '</div>';
      return field;
    };

    function toggle() {
      $('.container').append('<div class="modal-overlay confirm-modal"><div class="modal-dialog"></div></div>');
      $('.modal-dialog').append(projectList.toggle());
      eventbus.trigger("roadAddressProject:deactivateAllSelections");
      bindEvents();
      fetchProjects();
    }

    function hide() {
      projectList.hide();
      eventbus.trigger("roadAddressProject:startAllInteractions");
      $('.modal-overlay').remove();
    }

    function fetchProjects(){
      projectCollection.getProjects();
    }

    function bindEvents(){

      eventbus.once('roadAddressProjects:fetched', function(projects){
        var unfinishedProjects = _.filter(projects, function(proj){
          return proj.statusCode < 6 && proj.statusCode > 0 ;
        });
        if(!_.isEmpty(unfinishedProjects)){
          var html = '<table style="align-content: left;align-items: left;table-layout: fixed;width: 100%;">';
          _.each(unfinishedProjects, function(proj) {
            var info = typeof(proj.statusInfo) !== "undefined" ? proj.statusInfo : 'Ei lisätietoja';
            if(proj.statusCode === projectStatus.ErroredInTR.value) {
              html += '<tr class="project-item">' +
                '<td>'+ staticFieldProjectName(proj.name)+'</td>'+
                '<td title="'+ info +'">'+ staticFieldProjectList(proj.statusDescription)+'</td>'+
                '<td>'+'<button class="project-open btn btn-new-error" style="alignment: right; margin-bottom:6px" id="reopen-project-'+proj.id +'" value="'+proj.id+'"">Avaa uudelleen</button>' +'</td>'+
                '</tr>' + '<tr style="border-bottom:1px solid darkgray; "><td colspan="100%"></td></tr>';
            } else {
              html += '<tr class="project-item">' +
                '<td style="width: 310px;">'+ staticFieldProjectName(proj.name)+'</td>'+
                '<td style="width: 110px;" title="'+ info +'">'+ staticFieldProjectList(proj.ely)+'</td>'+
                '<td style="width: 110px;" title="'+ info +'">'+ staticFieldProjectList(proj.createdBy)+'</td>'+
                '<td style="width: 110px;" title="'+ info +'">'+ staticFieldProjectList(proj.statusDescription)+'</td>'+
                '<td>'+'<button class="project-open btn btn-new" style="alignment: right; margin-bottom:6px; margin-left: 70px" id="open-project-'+proj.id +'" value="'+proj.id+'"">Avaa</button>' +'</td>'+
                '</tr>' + '<tr style="border-bottom:1px solid darkgray; "><td colspan="100%"></td></tr>';
            }
          });
          html += '</table>';
          $('#project-list').html($(html));
          $('[id*="open-project"]').click(function(event) {
            if(this.className === "project-open btn btn-new-error"){
              projectCollection.reOpenProjectById(parseInt(event.currentTarget.value));
              eventbus.once("roadAddressProject:reOpenedProject", function(successData){
                openProjectSteps(event);
              });
            }
            else {
              openProjectSteps(event);
            }
          });
        }
      });

      var openProjectSteps = function(event) {
        projectCollection.getProjectsWithLinksById(parseInt(event.currentTarget.value)).then(function(result){
          setTimeout(function(){}, 0);
          eventbus.trigger('roadAddress:openProject', result);
          if(applicationModel.isReadOnly()) {
            $('.edit-mode-btn:visible').click();
          }
        });
      };

      projectList.on('click', 'button.cancel', function() {
        hide();
      });

      projectList.on('click', 'button.new', function() {
        $('.project-list').append('<div class="modal-overlay confirm-modal"><div class="modal-dialog"></div></div>');
        eventbus.trigger('roadAddress:newProject');
        if(applicationModel.isReadOnly()) {
          $('.edit-mode-btn:visible').click();
        }
      });

      projectList.on('click', 'button.close', function() {
        $('.project-item').remove();
        hide();
      });
    }

    return {
      toggle: toggle,
      hide: hide,
      element: projectList,
      bindEvents: bindEvents
    };
  };
})(this);
