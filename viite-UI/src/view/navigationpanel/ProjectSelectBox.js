(function(root) {
  root.ProjectSelectBox = function(projectListMenu) {
    var groupDiv = $('<div class="panel-group open-projects"/>');
    var projectSelectDiv = $('<div class="panel"/>');
    var selectProjectButton = $('<button id="projectListButton" class="action-mode-btn btn btn-block btn-primary" style="display: none;">Tieosoiteprojektit</button>');
    var panelHeader = $('<div class="panel-header"></div>').append(selectProjectButton);

    var bindEvents = function() {
      function selectLayerOrShowConfirmDialog() {
        if (applicationModel.isDirty()) {
          new Confirm();
        } else {
          
        }

      }
      selectProjectButton.on('click', selectLayerOrShowConfirmDialog);
    };

    bindEvents();

    return {
      button: selectProjectButton,
      toggle: projectListMenu.toggle,
      element: groupDiv.append(projectSelectDiv.append(panelHeader).append(projectListMenu.element))
    };
  };
})(this);
