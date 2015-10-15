(function(root) {
    root.LayerSelectBox = function() {
        var groupDiv = $('<div class="panel-group select-layer"/>');
        var layerSelectDiv = $('<div class="panel"/>');
        var selectLayerButton = $('<button class="btn btn-sm action-mode-btn btn btn-block btn-primary">Valitse tietolaji</button>');
        var panelHeader = $('<div class="panel-header"></div>').append(selectLayerButton);

        var bindEvents = function() {
            var showLayerSelectMenu = function() {
               console.log("TODO: show layer selection menu");
            };
            selectLayerButton.on('click', function() {
                showLayerSelectMenu();
            });
        };

        bindEvents();
        this.element = groupDiv.append(layerSelectDiv.append(panelHeader));
    };
})(this);