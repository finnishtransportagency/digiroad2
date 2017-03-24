(function (root) {
  root.ProjectListModel = function (projectCollection) {
    var projectList = $('<div id="project-window" class="form-horizontal project-list"></div>').hide();
    projectList.append('<button class="close btn-close">x</button>');
    projectList.append('<div class="content">Tieosoiteprojektit</div>');
    projectList.append('<div id="project-list" style="width:700px; height:400px; overflow:auto;"></div>');
    projectList.append('<div class="actions" style="position: absolute; bottom: 0px; right: 0px">' +
      '<button class="new btn btn-primary">Uusi tieosoiteprojekti</button></div>');

    var staticField = function(labelText, dataField) {
      var field;
      field = '<div>' +
        '<label class="control-label-projects">' + labelText + '</label>' +
        '<label class="control-label-projects">' + dataField + '</label>' +
        '</div>';
      return field;
    };

    function toggle() {
      $('.container').append('<div class="modal-overlay confirm-modal"><div class="modal-dialog"></div></div>');
      $('.modal-dialog').append(projectList.toggle());
      bindEvents();
      fetchProjects();
    }

    function hide() {
      projectList.hide();
      $('.modal-overlay').remove();
    }

    function fetchProjects(){
        projectCollection.getProjects().then(function(projects){
          var unfinishedProjects = _.filter(projects, function(proj){
            return proj.status === 1;
          });
          if(!_.isEmpty(unfinishedProjects)){
            var html = '<table align="center">';
            _.each(unfinishedProjects, function(proj) {
              html += '<tr class="project-item">' +
                '<td>'+ staticField('PROJEKTIN NIMI', proj.name)+'</td>'+
                '<td>'+ staticField('TILA', proj.status)+'</td>'+
                '<td>'+'<button class="project-open btn btn-new" id="open-project-'+proj.id +'" value="'+proj.id+'">Avaa</button>' +'</td>'+
                '</tr>';
            });
            html += '</table>';
            $('#project-list').html($(html));
            $('[id*="open-project"]').click(function(event) {
             projectCollection.getProjectsWithLinksById(parseInt(event.currentTarget.value)).then(function(result){
                setTimeout(function(){}, 0);
                eventbus.trigger('roadAddress:openProject', result);
              });

            });
          }
        });
      setTimeout(function(){}, 0);
      projectList.show();
    }

    function bindEvents(){

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