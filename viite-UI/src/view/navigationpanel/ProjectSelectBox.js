(function(root) {
  root.ProjectSelectBox = function(projectListModel) {
    var groupDiv = $('<div class="panel-group open-projects"/>');
    var projectSelectDiv = $('<div class="panel"/>');
    var selectProjectButton = $('<button id="projectListButton" class="action-mode-btn btn btn-block btn-primary" style="display: none;">Tieosoiteprojektit</button>');
    var panelHeader = $('<div class="panel-header"></div>').append(selectProjectButton);
    var addedPanelHeader = groupDiv.append(projectSelectDiv.append(panelHeader));

    function selectLayerOrShowConfirmDialog() {
      if (applicationModel.isProjectOpen()) {
        new ModalConfirm("Projektin muokkaus on kesken. Tallenna muutokset ja/tai poistu Peruuta-painikkeella.");
      } else {
        projectListModel.toggle();
      }
    }

    return {
      button: selectProjectButton,
      toggle: selectLayerOrShowConfirmDialog,
      element: addedPanelHeader.append(projectListModel.element)
    };
  };
})(this);
