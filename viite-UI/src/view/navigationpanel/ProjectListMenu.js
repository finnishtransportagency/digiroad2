(function (root) {
  root.ProjectListMenu = function (openProjects) {
    var projectList = $('<div class="form-horizontal project-list"></div>');
    projectList.append('<div class="content"> Tieosoiteprojektit</div>');
    projectList.append('<div class="actions" style = "position: absolute; bottom: 0px; right: 0px" >' +
      '<button class="save btn btn-primary" >Uusi tieosoiteprojekti</button>' +
      '<button class="cancel btn btn-secondary" >Peruuta</button></div>').hide();

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
    }

    return {
      toggle: toggle,
      hide: hide,
      element: projectList
    };
  };
})(this);