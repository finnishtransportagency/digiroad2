(function (root) {
  root.ProjectListMenu = function (openProjects) {
    var projectList = $('<div class="project-list">');
    projectList.append('<div style="float: left"><h1 style="margin-top: 30px; margin-left: 5px"> Tieosoiteprojektit </h1></div>');
    projectList.append('<div style="float: right; margin-top: 20px; margin-right: 20px"><button class="cancel btn btn-secondary" >Peruuta</button></div>');
    projectList.append('<div style="float: right; margin-top: 55px; margin-right: -60px"><button class="new btn btn-primary" >Uusi tieosoiteprojekti</button></div>');
    projectList.append('<hr style="margin-top: 90px">').hide();


    function toggle() {
      jQuery('#contentMap').append('<div class="modal-overlay confirm-modal"><div class="modal-dialog"></div></div>');
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
      });
    }

    return {
      toggle: toggle,
      hide: hide,
      element: projectList
    };
  };
})(this);