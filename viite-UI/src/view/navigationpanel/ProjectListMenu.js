(function (root) {
  root.ProjectListMenu = function (projectCollection) {
    var projectList = $('<div class="form-horizontal project-list"></div>');
    var header = $('<div class="content"> Tieosoiteprojektit </div>');
    var projects = projectCollection.getUnfinishedProjects();

    var staticField = function(labelText, dataField) {
      var field;
      field = '<div>' +
        '<label class="control-label-projects">' + labelText + '</label>' +
        '<label class="control-label-projects">' + dataField + '</label>' +
        '</div>';
      return field;
    };

    function addProjects() {

      var html = '<table>';

      _.each(projects, function(proj) {
        html += '<tr>' +
          '<td>'+ staticField('PROJEKTIN NIMI', proj.name)+'</td>'+
          '<td>'+ staticField('TILA', proj.state)+'</td>'+
          '<td>'+ staticField('TILA2', proj.state)+'</td>'+
          '</tr>';
      });

      html += '</table>';

      projectList.append($(html));
    }

    var openProjectTemplate = function(project) {
      return _.template('' +
        '<div>'+
        staticField('PROJEKTIN NIMI', project.name)+
        staticField('Avaa', project.name)+
        '</div>' +
        '<footer></footer>');
    };

    function toggle() {
      jQuery('.container').append('<div class="modal-overlay confirm-modal"><div class="modal-dialog"></div></div>');
      jQuery('.modal-dialog').append(projectList.toggle());
      bindEvents();
    }

    function hide() {
      projectList.hide();
      jQuery('.modal-overlay').remove();
    }

    function bindEvents(){
      projectList.on('click', 'button.cancel', function() {
        hide();
      });
      projectList.on('click', 'button.new', function() {
        jQuery('.project-list').append('<div class="modal-overlay confirm-modal"><div class="modal-dialog"></div></div>');
        eventbus.trigger("roadAddress:newProject");
        if(applicationModel.isReadOnly()) {
          $('.edit-mode-btn:visible').click();
        }
      });
      projectList.on('click', 'button.close', function() {
        hide();
      });
    }

    projectList.append('<button class="close btn-close">x</button>');
    projectList.append(header);
    addProjects();
    // projectList.html(openProjectTemplate(projectCollection.getUnfinishedProjects()));
    projectList.append('<div class="actions" style = "position: absolute; bottom: 0px; right: 0px" >' +
      '<button class="new btn btn-primary" >Uusi tieosoiteprojekti</button></div>').hide();

    return {
      toggle: toggle,
      hide: hide,
      element: projectList
    };
  };
})(this);