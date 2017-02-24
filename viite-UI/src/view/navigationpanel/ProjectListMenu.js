(function (root) {
  root.ProjectListMenu = function (openProjects) {
    var projectList = $('<div class="project-list"></div>');
    projectList.append('<div style="float: left"><h1 style="margin-top: 30px; margin-left: 5px"> Tieosoiteprojektit </h1></div>');
    projectList.append('<div style="float: right; margin-top: 20px; margin-right: 20px"><button class="cancel btn btn-secondary" >Peruuta</button></div>');
    projectList.append('<div style="float: right; margin-top: 55px; margin-right: -60px"><button class="new btn btn-primary" >Uusi tieosoiteprojekti</button></div>');
    projectList.append('<hr style="margin-top: 90px">').hide();

    projectList.on('click', 'button.new', function() {
      eventbus.trigger("roadAddress:newProject");
    });

    function toggle() {
      projectList.toggle();
    }

    function hide() {
      // projectList.hide();
    }

    return {
      toggle: toggle,
      hide: hide,
      element: projectList
    };
  };
})(this);