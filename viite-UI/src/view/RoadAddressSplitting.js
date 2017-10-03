(function (root) {
  root.RoadAddressSplitting = function(projectCollection, selectedProjectLinkProperty, projectLinkLayer, projectChangeTable) {

    var currentProject = false;
    var selectedProjectLink = false;

    var bindEvents = function() {

      var rootElement = $('#feature-attributes');

      eventbus.on('projectLink:clicked', function (selected) {
        selectedProjectLink = selected;
        var projectForm = new ProjectForm(projectCollection, selectedProjectLink);
        currentProject = projectCollection.getCurrentProject();

        rootElement.html(projectForm.selectedProjectLinkTemplate(currentProject.project, 'split'));
      });
    };
    bindEvents();
  };
}(this));