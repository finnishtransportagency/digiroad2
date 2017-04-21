(function (root) {
  root.ProjectListModel = function (projectCollection) {
    var projectList = $('<div id="project-window" class="form-horizontal project-list"></div>').hide();
    projectList.append('<button class="close btn-close">x</button>');
    projectList.append('<div class="content">Tieosoiteprojektit</div>');
    projectList.append('<div class="content-new">' +
      '<label class="content-new label">PROJEKTIN NIMI</label>' +
      '<label class="content-new label" style="width: 100px">TILA</label>' +
      '<div class="actions">' +
    '<button class="new btn btn-primary" style="margin-top:-5px;">Uusi tieosoiteprojekti</button></div>' +
      '</div>');
    projectList.append('<div id="project-list" style="width:700px; height:400px; overflow:auto;"></div>');

    var staticField = function(labelText, dataField) {
      var field;
      field = '<div>' +
        '<label class="control-label-projects">' + labelText + '</label>' +
        '<label class="control-label-projects">' + dataField + '</label>' +
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
      bindEvents();
      fetchProjects();
    }

    function hide() {
      projectList.hide();
      $('.modal-overlay').remove();
    }

    function fetchProjects(){
      console.log(projectCollection);
      projectCollection.getProjects().then(function(projects){
        var unfinishedProjects = _.filter(projects, function(proj){
          return proj.statusCode === 1;
        });
        if(!_.isEmpty(unfinishedProjects)){
          var html = '<table align="left" width="100%">';
          _.each(unfinishedProjects, function(proj) {
            html += '<tr class="project-item">' +
              '<td width="300px;">'+ staticFieldProjectList(proj.name)+'</td>'+
              '<td width="300px;">'+ staticFieldProjectList(proj.statusDescription)+'</td>'+
              '<td>'+'<button class="project-open btn btn-new" style="alignment: right; margin-bottom:6px" id="open-project-<%= proj.id %>">Avaa</button>' +'</td>'+
              '</tr>' + '<tr style="border-bottom:1px solid darkgray; "><td colspan="100%"></td></tr>';
          });
          html += '</table>';
          $('#project-list').html($(html));
          $('[id*="open-project"]').click(function(event) {
            projectCollection.getProjectsWithLinksById(parseInt(event.currentTarget.value)).then(function(result){
              setTimeout(function(){}, 0);
              eventbus.trigger('roadAddress:openProject', result);
              if(applicationModel.isReadOnly()) {
                $('.edit-mode-btn:visible').click();
              }
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
