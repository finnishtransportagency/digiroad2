(function(root) {
  root.ProjectSelectBox = function(projectListModel) {
    var groupDiv = $('<div class="panel-group open-projects"/>');
    var projectSelectDiv = $('<div class="panel"/>');
    var selectProjectButton = $('<button id="projectListButton" class="action-mode-btn btn btn-block btn-primary" style="display: none;">Tieosoiteprojektit</button>');
    var panelHeader = $('<div class="panel-header"></div>').append(selectProjectButton);
    var addedPanelHeader = groupDiv.append(projectSelectDiv.append(panelHeader));

    var bindEvents = function() {
      function selectLayerOrShowConfirmDialog() {
        if (applicationModel.isDirty()) {
          new Confirm();
        }
      }
      selectProjectButton.on('click', selectLayerOrShowConfirmDialog);
    };

    bindEvents();

    return {
      button: selectProjectButton,
      toggle: projectListModel.toggle,
      element: addedPanelHeader.append(projectListModel.element)
    };
  };
})(this);
